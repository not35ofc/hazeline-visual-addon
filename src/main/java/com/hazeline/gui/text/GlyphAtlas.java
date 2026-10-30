package com.hazeline.gui.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All glyphs of one face at one exact pixel size (em size in 1/64 screen pixels), packed into one
 * or more texture pages.
 *
 * <ul>
 *   <li>Single channel: each texel is the 8-bit coverage FreeType produced (no colour; text is
 *       tinted by the vertex colour when drawn).</li>
 *   <li>{@value #PADDING} px of empty (zero) texels around every glyph, so linear filtering never
 *       pulls in a neighbouring glyph.</li>
 *   <li>Each glyph can exist in {@value #SUBPIXEL_STEPS} horizontal subpixel positions
 *       (0, 1/4, 1/2, 3/4 px), see {@link #glyph}.</li>
 *   <li>The first page is created with printable ASCII (32-126) plus a few common symbols already
 *       in it, in all subpixel positions, sized so they fit; anything else is added lazily.</li>
 * </ul>
 * Pages live in a {@link PageStore} (GPU textures in game, plain arrays in tests).
 */
public final class GlyphAtlas implements AutoCloseable {
	public static final int PADDING = 2;
	public static final int SUBPIXEL_STEPS = 4;
	private static final int MIN_PAGE = 128;
	private static final int MAX_PAGE = 2048;
	/** Common non-ASCII symbols pre-cached when the font has them. */
	private static final String EXTRA_PRECACHE = "•…–—°±×←→↑↓✓";

	/** A glyph in the atlas. Empty glyphs (space) have width/height 0 and no page. */
	public record AtlasGlyph(int page, int x, int y, int width, int height, int left, int top) {
		public boolean isEmpty() {
			return width == 0 || height == 0;
		}
	}

	private final FreeTypeFace face;
	private final int size64;
	private final int emboldenPx;
	private final PageStore store;
	private final Map<Integer, AtlasGlyph> glyphs = new HashMap<>();
	private final List<Page> pages = new ArrayList<>();
	private boolean closed;
	private long lastUsed;

	private static final class Page {
		final int handle;
		final int size;
		int shelfX;
		int shelfY;
		int shelfHeight;

		Page(int handle, int size) {
			this.handle = handle;
			this.size = size;
		}

		/** Shelf (row) packing. Returns {x, y} of the padded cell or null if the page is full. */
		int[] allocate(int w, int h) {
			if (w > size || h > size) {
				return null;
			}
			if (shelfX + w > size) {
				shelfY += shelfHeight;
				shelfX = 0;
				shelfHeight = 0;
			}
			if (shelfY + h > size) {
				return null;
			}
			int[] at = {shelfX, shelfY};
			shelfX += w;
			shelfHeight = Math.max(shelfHeight, h);
			return at;
		}
	}

	public GlyphAtlas(FreeTypeFace face, int size64, int emboldenPx, PageStore store) {
		this.face = face;
		this.size64 = size64;
		this.emboldenPx = emboldenPx;
		this.store = store;
		precache();
	}

	/** Em size in 1/64 screen pixels. */
	public int size64() {
		return size64;
	}

	/** Em size in screen pixels. */
	public float ppem() {
		return size64 / 64f;
	}

	public FreeTypeFace face() {
		return face;
	}

	public PageStore store() {
		return store;
	}

	public int pageCount() {
		return pages.size();
	}

	public int pageHandle(int page) {
		return pages.get(page).handle;
	}

	public int pageSize(int page) {
		return pages.get(page).size;
	}

	public int glyphCount() {
		return glyphs.size();
	}

	/** Every glyph bitmap in this atlas (read-only; diagnostics and tests). */
	public java.util.Collection<AtlasGlyph> glyphs() {
		return java.util.Collections.unmodifiableCollection(glyphs.values());
	}

	public boolean isClosed() {
		return closed;
	}

	public long lastUsed() {
		return lastUsed;
	}

	public void touch(long now) {
		lastUsed = now;
	}

	private static int key(int glyph, int subpixel) {
		return glyph * SUBPIXEL_STEPS + subpixel;
	}

	/** The glyph rendered at subpixel position {@code subpixel}/{@value #SUBPIXEL_STEPS} px, rasterised on first use. */
	public AtlasGlyph glyph(int glyph, int subpixel) {
		int key = key(glyph, subpixel);
		AtlasGlyph g = glyphs.get(key);
		if (g == null) {
			FreeTypeFace.RasterGlyph raster = face.render(glyph, size64, subpixel * 64 / SUBPIXEL_STEPS, emboldenPx);
			g = insert(raster);
			glyphs.put(key, g);
		}
		return g;
	}

	private AtlasGlyph insert(FreeTypeFace.RasterGlyph r) {
		if (r.isEmpty()) {
			return new AtlasGlyph(-1, 0, 0, 0, 0, r.left(), r.top());
		}
		int cw = r.width() + PADDING * 2;
		int ch = r.height() + PADDING * 2;
		for (int i = pages.size() - 1; i >= 0; i--) {
			int[] at = pages.get(i).allocate(cw, ch);
			if (at != null) {
				return upload(i, at, r);
			}
		}
		// New page: same size as the last one (or big enough for this glyph).
		int size = pages.isEmpty() ? MIN_PAGE : pages.get(pages.size() - 1).size;
		while (size < Math.max(cw, ch) && size < MAX_PAGE) {
			size *= 2;
		}
		Page page = new Page(store.createPage(size), size);
		pages.add(page);
		int[] at = page.allocate(cw, ch);
		if (at == null) {
			throw new IllegalStateException("Glyph " + r.width() + "x" + r.height() + " does not fit a " + size + " px page");
		}
		return upload(pages.size() - 1, at, r);
	}

	private AtlasGlyph upload(int pageIndex, int[] cell, FreeTypeFace.RasterGlyph r) {
		int x = cell[0] + PADDING;
		int y = cell[1] + PADDING;
		store.upload(pages.get(pageIndex).handle, x, y, r.width(), r.height(), r.coverage());
		return new AtlasGlyph(pageIndex, x, y, r.width(), r.height(), r.left(), r.top());
	}

	/**
	 * Rasterises the pre-cache set first, then picks the smallest power-of-two page that holds
	 * all of it, so the common case is one page uploaded once.
	 */
	private void precache() {
		List<int[]> wanted = new ArrayList<>(); // {glyphIndex, subpixel}
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (int cp = 32; cp <= 126; cp++) {
			addPrecache(face.glyphIndex(cp), seen, wanted);
		}
		for (int i = 0; i < EXTRA_PRECACHE.length(); i++) {
			int index = face.glyphIndex(EXTRA_PRECACHE.charAt(i));
			if (index != 0) {
				addPrecache(index, seen, wanted);
			}
		}
		List<FreeTypeFace.RasterGlyph> rasters = new ArrayList<>(wanted.size());
		long area = 0;
		int maxSide = 0;
		for (int[] w : wanted) {
			FreeTypeFace.RasterGlyph r = face.render(w[0], size64, w[1] * 64 / SUBPIXEL_STEPS, emboldenPx);
			rasters.add(r);
			if (!r.isEmpty()) {
				int cw = r.width() + PADDING * 2;
				int ch = r.height() + PADDING * 2;
				area += (long) cw * ch;
				maxSide = Math.max(maxSide, Math.max(cw, ch));
			}
		}
		int size = MIN_PAGE;
		while (size < MAX_PAGE && !fitsInOnePage(rasters, size, area, maxSide)) {
			size *= 2;
		}
		pages.add(new Page(store.createPage(size), size));
		for (int i = 0; i < wanted.size(); i++) {
			int[] w = wanted.get(i);
			glyphs.put(key(w[0], w[1]), insert(rasters.get(i)));
		}
	}

	private static void addPrecache(int glyphIndex, java.util.Set<Integer> seen, List<int[]> out) {
		if (seen.add(glyphIndex)) {
			for (int s = 0; s < SUBPIXEL_STEPS; s++) {
				out.add(new int[] {glyphIndex, s});
			}
		}
	}

	/** Simulates the shelf packer on a page of {@code size}. */
	private static boolean fitsInOnePage(List<FreeTypeFace.RasterGlyph> rasters, int size, long area, int maxSide) {
		if (maxSide > size || area > (long) size * size) {
			return false;
		}
		Page sim = new Page(-1, size);
		for (FreeTypeFace.RasterGlyph r : rasters) {
			if (!r.isEmpty() && sim.allocate(r.width() + PADDING * 2, r.height() + PADDING * 2) == null) {
				return false;
			}
		}
		return true;
	}

	/** Bytes of texture memory this atlas uses (pages × size² × bytes per texel). */
	public long textureBytes() {
		long total = 0;
		for (Page p : pages) {
			total += (long) p.size * p.size * store.bytesPerTexel();
		}
		return total;
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		for (Page p : pages) {
			store.deletePage(p.handle);
		}
		pages.clear();
		glyphs.clear();
	}

	/** Where atlas pages live: GPU textures in game, plain arrays in the offline tests. */
	public interface PageStore {
		/** Creates a {@code size}×{@code size} page filled with zero coverage; returns its handle. */
		int createPage(int size);

		/** Writes a {@code width}×{@code height} coverage block (rows top-down, tightly packed). */
		void upload(int page, int x, int y, int width, int height, byte[] coverage);

		void deletePage(int page);

		int bytesPerTexel();
	}
}
