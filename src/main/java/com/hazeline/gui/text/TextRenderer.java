package com.hazeline.gui.text;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.resource.Resource;
import net.minecraft.util.math.ColorHelper;

import com.hazeline.Hazeline;
import com.hazeline.appearance.FontManager;

/**
 * The click GUI's text renderer. Every piece of click GUI text (panels, module rows, settings,
 * tooltips, the Appearance editor, font previews) is measured and drawn through this class, so
 * layout code measures text with exactly the metrics it is drawn with.
 *
 * <h2>Pipeline: font size → physical pixels → atlas</h2>
 * <ol>
 *   <li>{@code em = fontSize / 9 × emAt9(font) × guiScale × relScale} - the size of one em in
 *       <em>screen</em> pixels, kept exact to 1/64 px. {@code guiScale} is the window's integer
 *       GUI scale (also when "Auto"; 1.21.11's GUI projection is exactly framebuffer / guiScale),
 *       {@code emAt9} per font makes the five fonts look equally big at the same setting.</li>
 *   <li>Glyphs are rasterised by FreeType at exactly that size (light hinting, 8-bit
 *       anti-aliasing) into a {@link GlyphAtlas} for (face, size): single-channel coverage,
 *       2 px padding, linear filtering, clamp to edge, no mipmaps.</li>
 *   <li>{@link TextEngine} lays the string out with real advances and GPOS kerning on a float
 *       pen; glyphs use the copy rendered for their 1/4-px subpixel position.</li>
 *   <li>Quads are emitted in screen pixels with the GUI scale undone ({@code scale(1/guiScale)}),
 *       starting from the string's origin snapped to the <em>screen</em> pixel grid, so one atlas
 *       texel covers exactly one screen pixel.</li>
 * </ol>
 *
 * <p>Rebuilds: atlases are keyed by (face, size), so a different GUI scale, window size (Auto
 * scale), font or font size simply selects or creates another atlas. Font size changes are
 * debounced ({@value #DEBOUNCE_MS} ms): while the slider moves, the last atlas is drawn scaled.
 * Atlases unused for {@value #FREE_AFTER_MS} ms are freed (their textures closed), also while
 * the menu is closed ({@link #tick()}).
 *
 * <p>Fallback: if a font's .ttf fails to load, a warning is logged and text in that font is drawn
 * with the vanilla font; if FreeType itself is unavailable, all click GUI text uses the vanilla
 * font. Characters a font lacks are taken from Inter Medium.
 */
public final class TextRenderer {
	private static final long DEBOUNCE_MS = 150;
	private static final long FREE_AFTER_MS = 5000;
	private static final long SWEEP_EVERY_MS = 1000;
	/** Shadow: 1 screen pixel down-right, colour at 25 % brightness, 70 % of the text's alpha. */
	private static final float SHADOW_BRIGHTNESS = 0.25f;
	private static final float SHADOW_ALPHA = 0.7f;

	/** How a piece of text is drawn. {@code relScale} is relative to the GUI font size setting. */
	public record TextStyle(String fontId, double relScale, boolean bold, boolean shadow) {
	}

	private static TextEngine engine;
	private static GpuPageStore store;
	private static boolean initialised;
	private static boolean vanilla;
	private static String freeTypeVersion = "?";
	private static int pagesCreatedBefore; // pages of stores that were replaced
	private static int storeSwitches;

	private static double requestedSize = -1;
	private static double effectiveSize = -1;
	private static long requestChangedAt;
	private static long lastSweep;

	private TextRenderer() {
	}

	private static long now() {
		return System.nanoTime() / 1_000_000L;
	}

	// ============================================================================ lifecycle

	private static void ensureInit() {
		if (initialised) {
			return;
		}
		initialised = true;
		try {
			boolean coverage = TextPipelines.coverageAvailable();
			store = new GpuPageStore(coverage);
			engine = new TextEngine(store);
			freeTypeVersion = engine.freeTypeVersion();
			int loaded = 0;
			for (FontManager.FontEntry font : FontManager.FONTS) {
				try {
					byte[] regular = readFont(font.regularFile());
					byte[] bold = null;
					try {
						bold = readFont(font.boldFile());
					} catch (IOException e) {
						Hazeline.LOGGER.warn("Hazeline: bold file {} of {} not found, bold will be synthesised", font.boldFile(), font.label());
					}
					TextEngine.Family family = engine.addFamily(font.id(), regular, bold);
					if (bold != null && !family.hasRealBold()) {
						Hazeline.LOGGER.warn("Hazeline: bold file {} of {} failed to load, bold will be synthesised", font.boldFile(), font.label());
					}
					loaded++;
				} catch (Exception e) {
					Hazeline.LOGGER.warn("Hazeline: font {} ({}) failed to load; text in this font uses {} instead",
							font.label(), font.regularFile(), FontManager.FONTS.get(0).label(), e);
				}
			}
			if (loaded == 0) {
				throw new IllegalStateException("none of the bundled fonts could be loaded");
			}
			engine.setFallbackFamily(FontManager.DEFAULT_ID);
			Hazeline.LOGGER.info("Hazeline text renderer ready: FreeType {}, {} fonts, {} atlases", freeTypeVersion, loaded,
					coverage ? "single-channel (RED8) with the coverage shader" : "RGBA8 (coverage shader unavailable)");
		} catch (Throwable t) {
			Hazeline.LOGGER.warn("Hazeline text renderer unavailable, the click GUI falls back to the vanilla font", t);
			vanilla = true;
			if (engine != null) {
				try {
					engine.close();
				} catch (Throwable ignored) {
					// best effort
				}
				engine = null;
			}
		}
	}

	private static byte[] readFont(String file) throws IOException {
		try {
			Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(Hazeline.id("font/" + file));
			if (resource.isPresent()) {
				try (InputStream in = resource.get().getInputStream()) {
					return in.readAllBytes();
				}
			}
		} catch (RuntimeException ignored) {
			// resource manager not ready - fall through to the class path
		}
		try (InputStream in = TextRenderer.class.getResourceAsStream("/assets/hazeline/font/" + file)) {
			if (in == null) {
				throw new IOException("Font file " + file + " not found");
			}
			return in.readAllBytes();
		}
	}

	/**
	 * Call once at the start of every frame that draws click GUI text: applies the font size
	 * debounce, checks the shader, and frees unused atlases.
	 */
	public static void beginFrame() {
		ensureInit();
		if (vanilla) {
			return;
		}
		long now = now();
		double requested = FontManager.fontSizeSetting();
		if (requested != requestedSize) {
			requestedSize = requested;
			requestChangedAt = now;
		}
		if (effectiveSize < 0) {
			effectiveSize = requestedSize;
		} else if (effectiveSize != requestedSize && now - requestChangedAt >= DEBOUNCE_MS) {
			effectiveSize = requestedSize;
		}

		boolean coverage = TextPipelines.coverageAvailable();
		if (coverage != store.isSingleChannel()) {
			GpuPageStore old = store;
			store = new GpuPageStore(coverage);
			engine.replaceStore(store);
			pagesCreatedBefore += old.pagesCreated();
			old.closeAll();
			storeSwitches++;
			Hazeline.LOGGER.warn("Hazeline text: coverage shader {}; atlases rebuilt as {}", coverage ? "available again" : "unavailable",
					coverage ? "RED8" : "RGBA8");
		}

		sweep(now);
	}

	/**
	 * Called every client tick (render thread): frees atlases unused for {@value #FREE_AFTER_MS} ms,
	 * so their textures are released even while no Hazeline screen is open.
	 */
	public static void tick() {
		if (initialised && !vanilla && engine != null) {
			sweep(now());
		}
	}

	private static void sweep(long now) {
		if (now - lastSweep >= SWEEP_EVERY_MS) {
			lastSweep = now;
			engine.freeUnused(now - FREE_AFTER_MS);
		}
	}

	/** Frees all textures and FreeType objects (client shutdown). */
	public static void shutdown() {
		if (engine != null) {
			engine.close();
			engine = null;
		}
		if (store != null) {
			store.closeAll();
		}
	}

	// ============================================================================ sizing

	private static double requested() {
		return requestedSize < 0 ? FontManager.fontSizeSetting() : requestedSize;
	}

	private static double effective() {
		return effectiveSize < 0 ? requested() : effectiveSize;
	}

	private static int guiScale() {
		try {
			return Math.max(1, MinecraftClient.getInstance().getWindow().getScaleFactor());
		} catch (RuntimeException e) {
			return 2;
		}
	}

	/**
	 * Resolved size of a style this frame. {@code size64}: em size of the atlas in 1/64 screen
	 * pixels; {@code scale}: 1, or target/atlas size while a font size change is debounced.
	 */
	private record Size(String family, int size64, float scale, int guiScale) {
	}

	private static Size size(TextStyle style) {
		int gs = guiScale();
		String family = style.fontId();
		double emAt9 = FontManager.emAt9(family);
		int atlasSize = TextEngine.size64For(effective(), emAt9, style.relScale(), gs);
		int targetSize = TextEngine.size64For(requested(), emAt9, style.relScale(), gs);
		float scale = atlasSize == targetSize ? 1f : targetSize / (float) atlasSize;
		return new Size(family, atlasSize, scale, gs);
	}

	/** True if {@code style} must use the vanilla font (FreeType unavailable or this font's .ttf failed). */
	private static boolean useVanilla(TextStyle style) {
		return vanilla || !engine.hasFamily(style.fontId());
	}

	private static TextEngine.TextLayout layout(String text, TextStyle style, Size size) {
		TextEngine.TextLayout layout = engine.layout(text, size.family(), style.bold(), size.size64());
		layout.touch(now());
		return layout;
	}

	// ============================================================================ measuring (GUI units)

	/**
	 * Rendered width of {@code text} in GUI pixels: the pen advance or, if the last glyph's ink
	 * reaches further (or the shadow does), that - so text measured with this never gets cut off.
	 */
	public static float getWidth(String text, TextStyle style) {
		ensureInit();
		if (text == null || text.isEmpty()) {
			return 0f;
		}
		if (useVanilla(style)) {
			return vanillaWidth(text, style);
		}
		Size size = size(style);
		TextEngine.TextLayout layout = layout(text, style, size);
		float px = Math.max(layout.advance(), layout.inkRight()) * size.scale() + (style.shadow() ? 1f : 0f);
		return px / size.guiScale();
	}

	/** Pen advance of {@code text} in GUI pixels (where the next character would start; use for carets). */
	public static float getAdvance(String text, TextStyle style) {
		ensureInit();
		if (text == null || text.isEmpty()) {
			return 0f;
		}
		if (useVanilla(style)) {
			return vanillaWidth(text, style);
		}
		Size size = size(style);
		return layout(text, style, size).advance() * size.scale() / size.guiScale();
	}

	/** Height of a line of text (ascent + descent of the font) in GUI pixels. */
	public static float getHeight(TextStyle style) {
		ensureInit();
		if (useVanilla(style)) {
			return 8f * vanillaScale(style);
		}
		Size size = size(style);
		float px = (engine.ascent(size.family(), style.bold(), size.size64()) + engine.descent(size.family(), style.bold(), size.size64())) * size.scale();
		return px / size.guiScale();
	}

	/** Distance from the top of the line box to the baseline, in GUI pixels. */
	public static float getAscent(TextStyle style) {
		ensureInit();
		if (useVanilla(style)) {
			return 7f * vanillaScale(style);
		}
		Size size = size(style);
		return engine.ascent(size.family(), style.bold(), size.size64()) * size.scale() / size.guiScale();
	}

	// ============================================================================ drawing

	/** Draws {@code text} with the top of its line box at {@code y}. */
	public static void drawString(DrawContext g, String text, float x, float y, int color, TextStyle style) {
		ensureInit();
		if (text == null || text.isEmpty() || ColorHelper.getAlpha(color) == 0) {
			return;
		}
		if (useVanilla(style)) {
			vanillaDraw(g, text, x, y, color, style);
			return;
		}
		Size size = size(style);
		TextEngine.TextLayout layout = layout(text, style, size);
		if (layout.glyphs().isEmpty()) {
			return;
		}
		int gs = size.guiScale();
		Vector2f origin = g.getMatrices().transformPosition(new Vector2f(x, y));
		// Snap only the string's start point, and to the screen pixel grid (not the GUI grid).
		float originX = Math.round(origin.x * gs);
		float baseline = Math.round(origin.y * gs + layout.ascent() * size.scale());

		ScreenRect scissor = g.scissorStack.peekLast();
		Matrix3x2f pose = unscale(gs);
		if (style.shadow()) {
			int shadow = ColorHelper.withAlpha(Math.round(ColorHelper.getAlpha(color) * SHADOW_ALPHA), ColorHelper.scaleRgb(color, SHADOW_BRIGHTNESS) & 0xFFFFFF);
			submit(g, layout, originX + 1f, baseline + 1f, size.scale(), shadow, pose, scissor, gs);
		}
		// Submitted after the shadow, so where they overlap the GUI layer tree puts the text above it.
		submit(g, layout, originX, baseline, size.scale(), color, pose, scissor, gs);
	}

	/** Draws {@code text} centred horizontally on {@code centerX}. */
	public static void drawCenteredString(DrawContext g, String text, float centerX, float y, int color, TextStyle style) {
		drawString(g, text, centerX - getAdvance(text, style) / 2f, y, color, style);
	}

	/** Draws {@code text} vertically centred in a box (using the font's ascent and descent). */
	public static void drawInBox(DrawContext g, String text, float x, float boxY, float boxHeight, int color, TextStyle style) {
		drawString(g, text, x, boxY + (boxHeight - getHeight(style)) / 2f, color, style);
	}

	/** {@code scale(1/guiScale)}: maps screen pixels back into GUI space (shared, never modified). */
	private static final Matrix3x2f[] UNSCALE = new Matrix3x2f[16];

	private static Matrix3x2f unscale(int gs) {
		if (gs >= UNSCALE.length) {
			return new Matrix3x2f().scaling(1f / gs);
		}
		Matrix3x2f m = UNSCALE[gs];
		if (m == null) {
			m = new Matrix3x2f().scaling(1f / gs);
			UNSCALE[gs] = m;
		}
		return m;
	}

	/**
	 * One element per atlas page used by the string. The quads come from the layout's cache
	 * ({@link TextEngine.TextLayout#runs()}), so per frame only the origin and colour change.
	 */
	private static void submit(DrawContext g, TextEngine.TextLayout layout, float originX, float baseline, float scale, int color,
			Matrix3x2f pose, ScreenRect scissor, int gs) {
		for (TextQuads.Run run : layout.runs()) {
			var view = store.view(run.atlas().pageHandle(run.page()));
			if (view == null) {
				continue;
			}
			int bx0 = (int) Math.floor((originX + run.minX() * scale) / gs);
			int by0 = (int) Math.floor((baseline + run.minY() * scale) / gs);
			int bx1 = (int) Math.ceil((originX + run.maxX() * scale) / gs);
			int by1 = (int) Math.ceil((baseline + run.maxY() * scale) / gs);
			ScreenRect bounds = new ScreenRect(bx0, by0, Math.max(1, bx1 - bx0), Math.max(1, by1 - by0));
			if (scissor != null) {
				bounds = scissor.intersection(bounds);
				if (bounds == null) {
					continue; // completely clipped
				}
			}
			TextureSetup textures = TextureSetup.of(view, GpuPageStore.sampler());
			g.state.addSimpleElement(new TextRenderState(TextPipelines.pipeline(store.isSingleChannel()), textures, pose,
					run, originX, baseline, scale, color, scissor, bounds));
		}
	}

	// ============================================================================ vanilla fallback

	private static float vanillaScale(TextStyle style) {
		return (float) (requested() / 9.0 * style.relScale());
	}

	private static Text vanillaComponent(String text, TextStyle style) {
		return Text.literal(text).setStyle(Style.EMPTY.withBold(style.bold()));
	}

	private static float vanillaWidth(String text, TextStyle style) {
		net.minecraft.client.font.TextRenderer font = MinecraftClient.getInstance().textRenderer;
		return font.getWidth(vanillaComponent(text, style)) * vanillaScale(style);
	}

	private static void vanillaDraw(DrawContext g, String text, float x, float y, int color, TextStyle style) {
		float scale = vanillaScale(style);
		Matrix3x2fStack pose = g.getMatrices();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		g.drawText(MinecraftClient.getInstance().textRenderer, vanillaComponent(text, style), 0, 0, color, style.shadow());
		pose.popMatrix();
	}

	public static boolean isVanillaFallback() {
		ensureInit();
		return vanilla;
	}

	// ============================================================================ debug: glyph atlas overlay

	/**
	 * Draws the atlas pages of {@code style} (regular and bold) in the top-left corner, at 1:1
	 * screen pixels where they fit, with statistics. Used by Appearance > Font > "Show glyph atlas".
	 */
	public static void drawAtlasOverlay(DrawContext g, TextStyle style, int screenWidth, int screenHeight) {
		ensureInit();
		g.createNewRootLayer();
		TextStyle info = new TextStyle(FontManager.DEFAULT_ID, 0.85, false, false);
		int margin = 6;
		if (useVanilla(style)) {
			g.fill(margin, margin, margin + 260, margin + 20, 0xE0000000);
			drawString(g, "Glyph atlas: vanilla font fallback active for this font (see log)", margin + 4, margin + 5, 0xFFFFFFFF, info);
			return;
		}
		Size size = size(style);
		int gs = size.guiScale();
		List<GlyphAtlas> shown = new ArrayList<>();
		shown.add(engine.atlasFor(size.family(), false, size.size64()));
		GlyphAtlas bold = engine.atlasFor(size.family(), true, size.size64());
		if (bold != shown.get(0)) {
			shown.add(bold);
		}
		long now = now();
		for (GlyphAtlas a : shown) {
			a.touch(now);
		}

		List<String> lines = new ArrayList<>();
		long liveBytes = 0;
		int livePages = 0;
		for (GlyphAtlas a : engine.liveAtlases()) {
			liveBytes += a.textureBytes();
			livePages += a.pageCount();
		}
		lines.add(String.format(Locale.ROOT, "Glyph atlas - %s, %.2f px/em (GUI scale %d), %s", FontManager.label(size.family()), size.size64() / 64f, gs,
				store.isSingleChannel() ? "RED8 + coverage shader" : "RGBA8 fallback"));
		for (GlyphAtlas a : shown) {
			lines.add(String.format(Locale.ROOT, "%s: %d page(s) of %dx%d, %d glyphs (incl. 4 subpixel positions)", a.face().name(),
					a.pageCount(), a.pageSize(0), a.pageSize(0), a.glyphCount()));
		}
		lines.add(String.format(Locale.ROOT, "Live: %d atlases, %d textures, %d KB  |  created since start: %d atlases, %d textures%s",
				engine.liveAtlases().size(), livePages, liveBytes / 1024, engine.atlasesCreated(), pagesCreatedBefore + store.pagesCreated(),
				storeSwitches > 0 ? "  |  format switches: " + storeSwitches : ""));
		lines.add("FreeType " + freeTypeVersion + "  |  font size " + requested() + (effective() != requested() ? " (atlas " + effective() + ", debouncing)" : ""));

		float lineH = getHeight(info) + 2;
		float textBlock = lines.size() * lineH + 4;
		float maxW = 0;
		for (String l : lines) {
			maxW = Math.max(maxW, getWidth(l, info));
		}

		// Page images in screen pixels, downscaled only if they would not fit in 70 % of the screen.
		float availW = screenWidth * 0.7f * gs;
		float availH = (screenHeight * 0.8f - textBlock) * gs;
		float totalW = 0, maxH = 0;
		for (GlyphAtlas a : shown) {
			for (int p = 0; p < a.pageCount(); p++) {
				totalW += a.pageSize(p) + 4 * gs;
				maxH = Math.max(maxH, a.pageSize(p));
			}
		}
		float fit = Math.min(1f, Math.min(availW / Math.max(1, totalW), availH / Math.max(1, maxH)));
		float boxW = Math.max(maxW + 8, totalW * fit / gs + 8);
		float boxH = textBlock + maxH * fit / gs + 8;
		g.fill(margin, margin, margin + (int) Math.ceil(boxW), margin + (int) Math.ceil(boxH), 0xF0000000);
		float ty = margin + 4;
		for (String l : lines) {
			drawString(g, l, margin + 4, ty, 0xFFFFFFFF, info);
			ty += lineH;
		}
		float px = (margin + 4) * gs;
		float py = ty * gs;
		Matrix3x2f pose = unscale(gs);
		for (GlyphAtlas a : shown) {
			for (int p = 0; p < a.pageCount(); p++) {
				var view = store.view(a.pageHandle(p));
				if (view == null) {
					continue;
				}
				float s = a.pageSize(p) * fit;
				TextQuads.Run quad = new TextQuads.Run(a, p, new float[] {0f, 0f, s, s, 0f, 0f, 1f, 1f}, 1, 0f, 0f, s, s);
				ScreenRect bounds = new ScreenRect((int) Math.floor(px / gs), (int) Math.floor(py / gs),
						(int) Math.ceil(s / gs) + 1, (int) Math.ceil(s / gs) + 1);
				// Outline so the page edges are visible.
				g.fill((int) Math.floor(px / gs) - 1, (int) Math.floor(py / gs) - 1, (int) Math.ceil((px + s) / gs) + 1,
						(int) Math.ceil((py + s) / gs) + 1, 0xFF404040);
				g.fill((int) Math.floor(px / gs), (int) Math.floor(py / gs), (int) Math.ceil((px + s) / gs), (int) Math.ceil((py + s) / gs),
						0xFF000000);
				g.state.addSimpleElement(new TextRenderState(TextPipelines.pipeline(store.isSingleChannel()),
						TextureSetup.of(view, GpuPageStore.sampler()), pose, quad, px, py, 1f, 0xFFFFFFFF,
						g.scissorStack.peekLast(), bounds));
				px += s + 4 * gs;
			}
		}
	}
}
