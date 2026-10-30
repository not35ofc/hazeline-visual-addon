package com.hazeline.gui.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minecraft-independent core of the click GUI text renderer: fonts, glyph atlases and layout.
 * Everything here works in <b>physical (screen) pixels</b>; the caller converts from GUI units.
 * Sizes are passed as {@code size64}: the em size in 1/64 screen pixels (FreeType's 26.6 format),
 * so text is rasterised at the exact physical size, not a rounded one.
 *
 * <p>Layout rules (they are what make spacing even):
 * <ul>
 *   <li>The pen position is a float for the whole string. Each glyph advances it by its exact
 *       design advance ({@code hmtx} × size / unitsPerEm) plus GPOS pair kerning - nothing is
 *       rounded per glyph.</li>
 *   <li>A glyph is drawn at {@code floor(pen)} using the copy rasterised for the fractional part
 *       of the pen, quantised to 1/4 px. So bitmaps always land on whole pixels (1 texel = 1
 *       pixel, no blur) and the spacing error stays below 1/8 px.</li>
 *   <li>The caller snaps only the string's start point (baseline and x) to the pixel grid.</li>
 * </ul>
 */
public final class TextEngine implements AutoCloseable {
	private static final int LAYOUT_CACHE_SIZE = 4096;

	/** One selectable font: regular face plus optional real bold face. */
	public static final class Family {
		final String id;
		final FreeTypeFace regular;
		final SfntFont regularMetrics;
		final FreeTypeFace bold;
		final SfntFont boldMetrics;

		Family(String id, FreeTypeFace regular, SfntFont regularMetrics, FreeTypeFace bold, SfntFont boldMetrics) {
			this.id = id;
			this.regular = regular;
			this.regularMetrics = regularMetrics;
			this.bold = bold;
			this.boldMetrics = boldMetrics;
		}

		public boolean hasRealBold() {
			return bold != null;
		}
	}

	/**
	 * One glyph of a laid-out string: atlas entry plus its top-left pixel relative to the string
	 * origin on the baseline. {@code penX} is the exact (unrounded) pen position the glyph was laid
	 * out at, {@code glyphIndex} the font's glyph id (both for diagnostics and tests).
	 */
	public record PlacedGlyph(GlyphAtlas atlas, GlyphAtlas.AtlasGlyph glyph, int x, int y, float penX, int glyphIndex) {
	}

	/**
	 * A laid-out string in physical pixels. {@code advance} is the exact (float) pen advance,
	 * {@code inkLeft..inkRight} the rendered extent. The textured quads for drawing are built
	 * once, on first use, and reused every frame ({@link #runs()}).
	 */
	public static final class TextLayout {
		private final float advance;
		private final float ascent;
		private final float descent;
		private final List<PlacedGlyph> glyphs;
		private final GlyphAtlas[] atlases;
		private final int inkLeft;
		private final int inkRight;
		private List<TextQuads.Run> runs;

		TextLayout(float advance, float ascent, float descent, List<PlacedGlyph> glyphs, GlyphAtlas[] atlases, int inkLeft, int inkRight) {
			this.advance = advance;
			this.ascent = ascent;
			this.descent = descent;
			this.glyphs = glyphs;
			this.atlases = atlases;
			this.inkLeft = inkLeft;
			this.inkRight = inkRight;
		}

		public float advance() {
			return advance;
		}

		public float ascent() {
			return ascent;
		}

		public float descent() {
			return descent;
		}

		public List<PlacedGlyph> glyphs() {
			return glyphs;
		}

		public int inkLeft() {
			return inkLeft;
		}

		public int inkRight() {
			return inkRight;
		}

		/** Quads relative to the string origin (x = 0, y = baseline), one run per atlas page; cached. */
		public List<TextQuads.Run> runs() {
			if (runs == null) {
				runs = TextQuads.build(this);
			}
			return runs;
		}

		boolean isValid() {
			for (GlyphAtlas a : atlases) {
				if (a.isClosed()) {
					return false;
				}
			}
			return true;
		}

		public void touch(long now) {
			for (GlyphAtlas a : atlases) {
				a.touch(now);
			}
		}
	}

	private record LayoutKey(String text, String family, boolean bold, int size64) {
	}

	private final long library;
	private final Map<String, Family> families = new LinkedHashMap<>();
	private String fallbackFamily;
	private GlyphAtlas.PageStore store;
	private final Map<String, GlyphAtlas> atlases = new HashMap<>();
	private final LinkedHashMap<LayoutKey, TextLayout> layoutCache = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<LayoutKey, TextLayout> eldest) {
			return size() > LAYOUT_CACHE_SIZE;
		}
	};
	private int atlasesCreated;

	/**
	 * Em size in 1/64 <em>screen</em> pixels (at least 1 px) for a GUI font size setting:
	 * {@code fontSize / 9 × emAt9 × guiScale × relScale}, where {@code emAt9} is the font's em in
	 * GUI pixels at font size 9 and {@code relScale} the text's size relative to normal GUI text.
	 */
	public static int size64For(double fontSize, double emAt9, double relScale, int guiScale) {
		return (int) Math.max(64, Math.round(fontSize / 9.0 * emAt9 * guiScale * relScale * 64.0));
	}

	public TextEngine(GlyphAtlas.PageStore store) {
		this.library = FreeTypeFace.createLibrary();
		this.store = Objects.requireNonNull(store);
	}

	public String freeTypeVersion() {
		return FreeTypeFace.version(library);
	}

	/**
	 * Adds a font. {@code boldBytes} may be null (then bold is synthesised by emboldening the
	 * outlines by 1 px). Throws if the regular file cannot be opened.
	 */
	public Family addFamily(String id, byte[] regularBytes, byte[] boldBytes) {
		SfntFont regularMetrics = SfntFont.parse(regularBytes);
		FreeTypeFace regular = FreeTypeFace.open(library, regularBytes, id);
		FreeTypeFace bold = null;
		SfntFont boldMetrics = null;
		if (boldBytes != null) {
			try {
				boldMetrics = SfntFont.parse(boldBytes);
				bold = FreeTypeFace.open(library, boldBytes, id + " (bold)");
			} catch (RuntimeException e) {
				bold = null;
				boldMetrics = null;
			}
		}
		Family family = new Family(id, regular, regularMetrics, bold, boldMetrics);
		families.put(id, family);
		if (fallbackFamily == null) {
			fallbackFamily = id;
		}
		return family;
	}

	public boolean hasFamily(String id) {
		return families.containsKey(id);
	}

	public Family family(String id) {
		Family f = families.get(id);
		return f != null ? f : families.get(fallbackFamily);
	}

	/** Font used for characters the selected font lacks (the first family added). */
	public void setFallbackFamily(String id) {
		if (families.containsKey(id)) {
			fallbackFamily = id;
		}
	}

	// ------------------------------------------------------------------------- metrics

	private static SfntFont metrics(Family f, boolean bold) {
		return bold && f.boldMetrics != null ? f.boldMetrics : f.regularMetrics;
	}

	/** Pixels per font unit at {@code size64}. */
	private static float unitScale(SfntFont m, int size64) {
		return size64 / 64f / m.unitsPerEm;
	}

	/** Ascent (above the baseline) in pixels at {@code size64}. */
	public float ascent(String familyId, boolean bold, int size64) {
		SfntFont m = metrics(family(familyId), bold);
		return m.ascender * unitScale(m, size64);
	}

	/** Descent (below the baseline, positive) in pixels at {@code size64}. */
	public float descent(String familyId, boolean bold, int size64) {
		SfntFont m = metrics(family(familyId), bold);
		return -m.descender * unitScale(m, size64);
	}

	public float lineGap(String familyId, boolean bold, int size64) {
		SfntFont m = metrics(family(familyId), bold);
		return m.lineGap * unitScale(m, size64);
	}

	// ------------------------------------------------------------------------- atlases

	public GlyphAtlas atlas(FreeTypeFace face, int size64, int emboldenPx) {
		String key = face.name() + "@" + size64 + (emboldenPx > 0 ? "+b" + emboldenPx : "");
		GlyphAtlas atlas = atlases.get(key);
		if (atlas == null || atlas.isClosed()) {
			atlas = new GlyphAtlas(face, size64, emboldenPx, store);
			atlases.put(key, atlas);
			atlasesCreated++;
		}
		return atlas;
	}

	/** Atlas holding the given family/weight at {@code size64} (creating it if needed). */
	public GlyphAtlas atlasFor(String familyId, boolean bold, int size64) {
		Family f = family(familyId);
		if (bold && f.bold != null) {
			return atlas(f.bold, size64, 0);
		}
		return atlas(f.regular, size64, bold ? syntheticBoldPx(size64) : 0);
	}

	/** Synthetic emboldening for fonts without a bold file: 1 px (2 px from 40 px per em up). */
	private static int syntheticBoldPx(int size64) {
		return size64 >= 40 * 64 ? 2 : 1;
	}

	public int atlasesCreated() {
		return atlasesCreated;
	}

	public List<GlyphAtlas> liveAtlases() {
		return new ArrayList<>(atlases.values());
	}

	/** Frees atlases not used since {@code olderThan}; returns how many were freed. */
	public int freeUnused(long olderThan) {
		int freed = 0;
		Iterator<GlyphAtlas> it = atlases.values().iterator();
		while (it.hasNext()) {
			GlyphAtlas a = it.next();
			if (a.lastUsed() < olderThan) {
				a.close();
				it.remove();
				freed++;
			}
		}
		if (freed > 0) {
			layoutCache.values().removeIf(l -> !l.isValid());
		}
		return freed;
	}

	/** Frees every atlas and switches to another page store (e.g. after the texture format changed). */
	public void replaceStore(GlyphAtlas.PageStore newStore) {
		clearAtlases();
		store = Objects.requireNonNull(newStore);
	}

	public GlyphAtlas.PageStore store() {
		return store;
	}

	public void clearAtlases() {
		for (GlyphAtlas a : atlases.values()) {
			a.close();
		}
		atlases.clear();
		layoutCache.clear();
	}

	// ------------------------------------------------------------------------- layout

	/** Lays out {@code text} at {@code size64} (cached). Characters below U+0020 are skipped. */
	public TextLayout layout(String text, String familyId, boolean bold, int size64) {
		Family family = family(familyId);
		LayoutKey key = new LayoutKey(text, family.id, bold, size64);
		TextLayout cached = layoutCache.get(key);
		if (cached != null && cached.isValid()) {
			return cached;
		}
		TextLayout layout = doLayout(text, family, bold, size64);
		layoutCache.put(key, layout);
		return layout;
	}

	private TextLayout doLayout(String text, Family family, boolean bold, int size64) {
		Family fallback = families.get(fallbackFamily);
		List<PlacedGlyph> placed = new ArrayList<>(text.length());
		List<GlyphAtlas> used = new ArrayList<>(2);
		float pen = 0f;
		FreeTypeFace prevFace = null;
		SfntFont prevMetrics = null;
		int prevGlyph = -1;
		int inkLeft = Integer.MAX_VALUE;
		int inkRight = Integer.MIN_VALUE;

		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			if (cp < 0x20) {
				continue;
			}
			// Resolve the face: selected font, then the fallback font, else the selected font's .notdef box.
			FreeTypeFace face;
			SfntFont m;
			int embolden;
			int glyph;
			Family source = family;
			glyph = (bold && source.bold != null ? source.bold : source.regular).glyphIndex(cp);
			if (glyph == 0 && fallback != null && fallback != family) {
				int fb = (bold && fallback.bold != null ? fallback.bold : fallback.regular).glyphIndex(cp);
				if (fb != 0) {
					source = fallback;
					glyph = fb;
				}
			}
			if (bold && source.bold != null) {
				face = source.bold;
				m = source.boldMetrics;
				embolden = 0;
			} else {
				face = source.regular;
				m = source.regularMetrics;
				embolden = bold ? syntheticBoldPx(size64) : 0;
			}
			float scale = unitScale(m, size64);

			float placement = 0f;
			float extraAdvance = 0f;
			if (prevFace == face && prevMetrics == m) {
				SfntFont.Kerning.Adjustment adj = m.kerning.get(prevGlyph, glyph);
				if (!adj.isZero()) {
					pen += adj.advance1() * scale;
					placement = adj.placement2() * scale;
					extraAdvance = adj.advance2() * scale;
				}
			}

			GlyphAtlas atlas = atlas(face, size64, embolden);
			if (!used.contains(atlas)) {
				used.add(atlas);
			}
			float drawX = pen + placement;
			int ix = (int) Math.floor(drawX);
			int sub = Math.round((drawX - ix) * GlyphAtlas.SUBPIXEL_STEPS);
			if (sub == GlyphAtlas.SUBPIXEL_STEPS) {
				ix++;
				sub = 0;
			}
			GlyphAtlas.AtlasGlyph g = atlas.glyph(glyph, sub);
			if (!g.isEmpty()) {
				int x = ix + g.left();
				placed.add(new PlacedGlyph(atlas, g, x, -g.top(), drawX, glyph));
				inkLeft = Math.min(inkLeft, x);
				inkRight = Math.max(inkRight, x + g.width());
			}
			pen += m.advance(glyph) * scale + embolden + extraAdvance;
			prevFace = face;
			prevMetrics = m;
			prevGlyph = glyph;
		}

		SfntFont primary = metrics(family, bold);
		float ascent = primary.ascender * unitScale(primary, size64);
		float descent = -primary.descender * unitScale(primary, size64);
		if (placed.isEmpty()) {
			inkLeft = 0;
			inkRight = 0;
		}
		return new TextLayout(pen, ascent, descent, List.copyOf(placed), used.toArray(new GlyphAtlas[0]), inkLeft, inkRight);
	}

	@Override
	public void close() {
		clearAtlases();
		for (Family f : families.values()) {
			f.regular.close();
			if (f.bold != null) {
				f.bold.close();
			}
		}
		families.clear();
		FreeTypeFace.destroyLibrary(library);
	}
}
