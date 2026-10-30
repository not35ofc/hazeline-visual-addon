package com.hazeline.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.DrawContext;

import com.hazeline.appearance.Appearance;
import com.hazeline.appearance.FontManager;
import com.hazeline.gui.text.TextRenderer;
import com.hazeline.gui.text.TextRenderer.TextStyle;
import com.hazeline.module.ModuleManager;
import com.hazeline.module.modules.settings.ClickGuiModule;

/**
 * The single source of truth for how the click GUI looks. Every component reads colours,
 * the font, text size and all spacing from here; nothing else hardcodes them.
 *
 * <ul>
 *   <li><b>Colours</b> come from {@link Appearance} (Colors tab), with effects applied
 *       (rainbow accent). Derived tokens (hover, inset, track...) are computed from them so a
 *       theme only needs six colours.</li>
 *   <li><b>Text</b> is measured and drawn by {@link TextRenderer} (FreeType glyphs rasterised
 *       at the real screen pixel size); this class only picks the style (font, size, bold,
 *       shadow) so every component uses the same metrics for layout and drawing.</li>
 *   <li><b>Metrics</b> (row height, padding, indents, panel width) are multiples of
 *       {@link #scale()}, so hitboxes grow with the text and nothing overlaps.</li>
 * </ul>
 *
 * <p>Screens draw into a {@link DrawContext}, which in 1.21.11 records GUI render state
 * (drawn later by vanilla's GuiRenderer); methods here only queue fills and text.
 */
public final class Theme {
	/** Vanilla line height; font size 9 = scale 1. */
	private static final float BASE_LINE = 9f;

	private Theme() {
	}

	private static Appearance a() {
		return Appearance.get();
	}

	private static ClickGuiModule layout() {
		return ModuleManager.get().get(ClickGuiModule.class);
	}

	// =================================================================================== colours

	/**
	 * Fade of everything the menu draws (open/close animation). Applied to every colour below;
	 * always reset to 1 after a frame, so HUD elements that share these colours are unaffected.
	 */
	private static float globalAlpha = 1f;

	public static void setGlobalAlpha(float alpha) {
		globalAlpha = Math.max(0f, Math.min(1f, alpha));
	}

	public static float globalAlpha() {
		return globalAlpha;
	}

	/** {@code argb} with its alpha multiplied by the current menu fade. */
	public static int fade(int argb) {
		if (globalAlpha >= 1f) {
			return argb;
		}
		return Colors.withAlpha(argb, Math.round(Colors.alpha(argb) * globalAlpha));
	}

	private static int rawAccent() {
		int base = a().accent.get();
		if (!a().rainbowAccent.get()) {
			return base;
		}
		// Rainbow: keep the accent's saturation/brightness/alpha, rotate the hue over time.
		float[] hsv = Colors.toHsv(base);
		double cyclesPerMs = a().rainbowSpeed.get() / 10_000.0;
		float hue = (float) ((System.currentTimeMillis() * cyclesPerMs) % 1.0);
		return Colors.fromHsv(hue, Math.max(0.6f, hsv[1]), Math.max(0.7f, hsv[2]), Colors.alpha(base));
	}

	public static int accent() {
		return fade(rawAccent());
	}

	/** Accent with a custom alpha (0..255). */
	public static int accent(int alpha) {
		return fade(Colors.withAlpha(rawAccent(), alpha));
	}

	/** Window background. */
	public static int panelBackground() {
		return fade(a().panelBackground.get());
	}

	/** Sidebar, cards and fields (the "Panel header" colour). */
	public static int header() {
		return fade(a().panelHeader.get());
	}

	/** Bottom colour of the header gradient (= header colour when gradients are off). */
	public static int headerEnd() {
		return fade(a().gradientHeader.get() ? a().headerGradientEnd.get() : a().panelHeader.get());
	}

	public static int text() {
		return fade(a().text.get());
	}

	public static int textSecondary() {
		return fade(a().textSecondary.get());
	}

	public static int border() {
		return fade(a().border.get());
	}

	/** Translucent overlay for hovered rows (text colour at low alpha). */
	public static int hover() {
		return fade(Colors.withAlpha(a().text.get(), 0x16));
	}

	/** Card background: the header colour, a bit see-through so the glass window shows. */
	public static int card() {
		int c = a().panelHeader.get();
		return fade(Colors.withAlpha(c, Math.min(Colors.alpha(c), 0xC8)));
	}

	/** Card background while hovered. */
	public static int cardHover() {
		int c = Colors.mix(a().panelHeader.get(), a().text.get(), 0.06f);
		return fade(Colors.withAlpha(c, Math.min(Colors.alpha(a().panelHeader.get()), 0xE0)));
	}

	/** Sidebar background (slightly darker than the cards). */
	public static int sidebar() {
		int c = Colors.darken(a().panelHeader.get(), 0.85f);
		return fade(Colors.withAlpha(c, Math.min(Colors.alpha(a().panelHeader.get()), 0xD8)));
	}

	/** Slightly darker area for nested content (input fields, dropdown lists). */
	public static int inset() {
		int bg = a().panelBackground.get();
		return fade(Colors.withAlpha(Colors.darken(bg, 0.82f), Math.max(0x90, Colors.alpha(bg))));
	}

	/** Empty part of slider tracks and switches (a bit lighter than outlines so it shows on hover). */
	public static int track() {
		return fade(Colors.mix(a().border.get(), a().text.get(), 0.14f));
	}

	/** Slider knobs and markers. */
	public static int knob() {
		return text();
	}

	/** Light / dark squares behind colours with transparency. */
	public static int checkerLight() {
		return fade(Colors.mix(0xFFFFFFFF, a().border.get(), 0.1f));
	}

	public static int checkerDark() {
		return fade(Colors.mix(0xFF999999, a().border.get(), 0.1f));
	}

	/** Darkening of the world behind the GUI. */
	public static int screenDim() {
		return fade(Colors.withAlpha(0xFF000000, (int) Math.round(layout().screenDim.get() * 255)));
	}

	/** Outer backdrop of popups (tooltips). */
	public static int popupBackground() {
		int bg = a().panelBackground.get();
		return fade(Colors.withAlpha(bg, Math.max(0xF0, Colors.alpha(bg))));
	}

	// =================================================================================== metrics

	/** Text scale: selected font size / 9. Everything else scales with it. */
	public static float scale() {
		return (float) (a().fontSize.get() / BASE_LINE);
	}

	/** Scales a design-size value (designed for font size 9) to the current scale. */
	public static int px(float designPixels) {
		return Math.max(1, Math.round(designPixels * scale()));
	}

	/** Height of one row (text plus padding). */
	public static int rowHeight() {
		return px(14);
	}

	/** Small gap (2 px at scale 1). */
	public static int pad() {
		return px(2);
	}

	/** Indent of nested rows (4 px at scale 1). */
	public static int indent() {
		return px(4);
	}

	/** Width of a narrow column (font picker list etc.); 120 design px. */
	public static int panelWidth() {
		return px(120);
	}

	public static boolean animations() {
		return layout().animations.get();
	}

	public static int cornerRadius() {
		return Math.round(layout().cornerRadius.getInt() * scale());
	}

	// =================================================================================== text

	/** Style of normal GUI text: selected font, font size setting, configured shadow. */
	public static TextStyle style(boolean bold) {
		return new TextStyle(FontManager.currentId(), 1.0, bold, a().textShadow.get());
	}

	/** Normal GUI font at {@code relScale} × the normal size (descriptions, titles, labels). */
	public static TextStyle style(double relScale, boolean bold) {
		return new TextStyle(FontManager.currentId(), relScale, bold, a().textShadow.get());
	}

	/** Small text: descriptions, counts, section labels. */
	public static final double SMALL = 0.8;
	/** Page titles. */
	public static final double TITLE = 1.45;

	public static int widthOf(String text, double relScale, boolean bold) {
		return (int) Math.ceil(TextRenderer.getWidth(text, style(relScale, bold)));
	}

	public static float heightOf(double relScale) {
		return TextRenderer.getHeight(style(relScale, false));
	}

	public static void textOf(DrawContext g, String text, float x, float y, int color, double relScale, boolean bold) {
		TextRenderer.drawString(g, text, x, y, color, style(relScale, bold));
	}

	/** Text of a given size vertically centred in a box. */
	public static void boxTextOf(DrawContext g, String text, float x, float boxY, float boxH, int color, double relScale,
			boolean bold) {
		TextRenderer.drawInBox(g, text, x, boxY, boxH, color, style(relScale, bold));
	}

	/** Cuts {@code text} with "..." so it fits in {@code maxWidth} at the given size. */
	public static String fitOf(String text, int maxWidth, double relScale, boolean bold) {
		if (text == null) {
			return "";
		}
		if (widthOf(text, relScale, bold) <= maxWidth) {
			return text;
		}
		String s = text;
		while (!s.isEmpty() && widthOf(s + "...", relScale, bold) > maxWidth) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	/** Style of text in a specific bundled font at {@code relScale} × the normal size (font previews). */
	public static TextStyle styleIn(String fontId, double relScale, boolean bold) {
		return new TextStyle(fontId, relScale, bold, a().textShadow.get());
	}

	/** Call at the start of every frame that draws GUI text (font size debounce, atlas cleanup). */
	public static void beginFrame() {
		TextRenderer.beginFrame();
	}

	/** Draws the glyph atlas debug overlay when Appearance > Font > "Show glyph atlas" is on. */
	public static void debugOverlay(DrawContext g, int screenWidth, int screenHeight) {
		if (a().showGlyphAtlas.get()) {
			TextRenderer.drawAtlasOverlay(g, style(false), screenWidth, screenHeight);
		}
	}

	public static int textWidth(String text) {
		return textWidth(text, false);
	}

	/** Rendered width of {@code text} in GUI pixels, rounded up (never smaller than what is drawn). */
	public static int textWidth(String text, boolean bold) {
		return (int) Math.ceil(TextRenderer.getWidth(text, style(bold)));
	}

	/** Exact pen advance of {@code text} in GUI pixels (caret positions in text fields). */
	public static float textAdvance(String text) {
		return TextRenderer.getAdvance(text, style(false));
	}

	/** Height of a line of text (font ascent + descent) in GUI pixels. */
	public static float textHeight() {
		return TextRenderer.getHeight(style(false));
	}

	/** Y offset that vertically centres a line of text inside a box of {@code height}. */
	public static int textYOffset(int height) {
		return Math.round((height - textHeight()) / 2f);
	}

	public static void text(DrawContext g, String text, int x, int y, int color) {
		text(g, text, x, y, color, false);
	}

	/** Draws text with the top of its line box at {@code y}, in the selected font and size. */
	public static void text(DrawContext g, String text, int x, int y, int color, boolean bold) {
		TextRenderer.drawString(g, text, x, y, color, style(bold));
	}

	/** Text vertically centred in a row starting at {@code rowY} (centred on the font's ascent/descent). */
	public static void rowText(DrawContext g, String text, int x, int rowY, int rowHeight, int color) {
		rowText(g, text, x, rowY, rowHeight, color, false);
	}

	public static void rowText(DrawContext g, String text, int x, int rowY, int rowHeight, int color, boolean bold) {
		TextRenderer.drawInBox(g, text, x, rowY, rowHeight, color, style(bold));
	}

	// ------------------------------------------------- text in a specific font (font picker previews)

	/** Line height of {@code fontId} at {@code relScale} × normal size, in GUI pixels, rounded up. */
	public static int textHeightIn(String fontId, double relScale) {
		return (int) Math.ceil(TextRenderer.getHeight(styleIn(fontId, relScale, false)));
	}

	/** The tallest line height of all bundled fonts at {@code relScale} (uniform rows in the font picker). */
	public static int glyphHeightIn(double relScale) {
		int max = 1;
		for (FontManager.FontEntry font : FontManager.FONTS) {
			max = Math.max(max, textHeightIn(font.id(), relScale));
		}
		return max;
	}

	/** Rendered width of {@code text} in font {@code fontId} at {@code relScale} × normal size. */
	public static int textWidthIn(String fontId, String text, double relScale, boolean bold) {
		return (int) Math.ceil(TextRenderer.getWidth(text, styleIn(fontId, relScale, bold)));
	}

	/** Like {@link #text} but in font {@code fontId} and at {@code relScale} × the normal text size. */
	public static void textIn(DrawContext g, String fontId, String text, int x, int y, int color,
			double relScale, boolean bold) {
		TextRenderer.drawString(g, text, x, y, color, styleIn(fontId, relScale, bold));
	}

	/** {@link #textIn} vertically centred in a box of {@code height} starting at {@code boxY}. */
	public static void rowTextIn(DrawContext g, String fontId, String text, int x, int boxY, int height, int color,
			double relScale, boolean bold) {
		TextRenderer.drawInBox(g, text, x, boxY, height, color, styleIn(fontId, relScale, bold));
	}

	/** Cuts {@code text} with "..." so it fits in {@code maxWidth} when drawn in {@code fontId}. */
	public static String fitIn(String fontId, String text, int maxWidth, double relScale, boolean bold) {
		if (textWidthIn(fontId, text, relScale, bold) <= maxWidth) {
			return text;
		}
		String s = text;
		while (!s.isEmpty() && textWidthIn(fontId, s + "...", relScale, bold) > maxWidth) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	/** Cuts {@code text} with "..." so it fits in {@code maxWidth}. */
	public static String fit(String text, int maxWidth) {
		if (textWidth(text) <= maxWidth) {
			return text;
		}
		String s = text;
		while (!s.isEmpty() && textWidth(s + "...") > maxWidth) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	/** Splits text into lines no wider than {@code maxWidth} at the given text size. */
	public static List<String> wrapOf(String text, int maxWidth, double relScale) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (widthOf(candidate, relScale, false) > maxWidth && !line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	/** Splits text into lines no wider than {@code maxWidth}. */
	public static List<String> wrap(String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (textWidth(candidate) > maxWidth && !line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	/**
	 * Themed tooltip box in the GUI font and size (vanilla tooltips would ignore the font size).
	 * Drawn in a new stratum so it is above everything else.
	 */
	public static void tooltip(DrawContext g, String text, int mouseX, int mouseY, int screenWidth, int screenHeight) {
		if (text == null || text.isEmpty()) {
			return;
		}
		g.createNewRootLayer();
		int padding = pad() * 2;
		List<String> lines = wrap(text, Math.min(screenWidth / 2, px(200)));
		int glyphs = (int) Math.ceil(textHeight());
		int lineHeight = Math.max(px(11), glyphs + 1);
		int w = 0;
		for (String line : lines) {
			w = Math.max(w, textWidth(line));
		}
		w += padding * 2;
		int h = lines.size() * lineHeight + padding * 2 - (lineHeight - glyphs);
		int x = mouseX + px(10);
		int y = mouseY + px(6);
		if (x + w > screenWidth) {
			x = Math.max(0, mouseX - w - px(4));
		}
		if (y + h > screenHeight) {
			y = Math.max(0, screenHeight - h);
		}
		roundedRect(g, x, y, x + w, y + h, cornerRadius(), popupBackground());
		roundedOutline(g, x, y, w, h, cornerRadius(), accent(160));
		int ty = y + padding;
		for (String line : lines) {
			text(g, line, x + padding, ty, text());
			ty += lineHeight;
		}
	}

	// =================================================================================== shapes

	public static void outline(DrawContext g, int x, int y, int w, int h, int color) {
		g.drawStrokedRectangle(x, y, w, h, color);
	}

	/** Rounded rectangle with all four corners rounded (smooth corners, see {@link Shapes}). */
	public static void roundedRect(DrawContext g, int x1, int y1, int x2, int y2, int radius, int color) {
		Shapes.roundedRect(g, x1, y1, x2, y2, radius, color, color, true, true);
	}

	public static void roundedRect(DrawContext g, int x1, int y1, int x2, int y2, int radius, int color,
			boolean roundTop, boolean roundBottom) {
		Shapes.roundedRect(g, x1, y1, x2, y2, radius, color, color, roundTop, roundBottom);
	}

	/** Rounded rectangle with an optional vertical gradient (top colour to bottom colour). */
	public static void roundedRect(DrawContext g, int x1, int y1, int x2, int y2, int radius,
			int topColor, int bottomColor, boolean roundTop, boolean roundBottom) {
		Shapes.roundedRect(g, x1, y1, x2, y2, radius, topColor, bottomColor, roundTop, roundBottom);
	}

	/** 1 px outline with the same rounded corners as {@link #roundedRect}. */
	public static void roundedOutline(DrawContext g, int x, int y, int w, int h, int radius, int color) {
		Shapes.roundedOutline(g, x, y, x + w, y + h, radius, color);
	}

	/** Smooth filled circle, centre and radius in GUI pixels (fractional values allowed). */
	public static void circle(DrawContext g, float cx, float cy, float radius, int color) {
		Shapes.disc(g, cx, cy, radius, color);
	}

	/** Checkerboard behind translucent colour previews. */
	public static void checkerboard(DrawContext g, int x1, int y1, int x2, int y2) {
		g.fill(x1, y1, x2, y2, checkerLight());
		int cell = Math.max(2, px(3));
		for (int y = y1; y < y2; y += cell) {
			for (int x = x1 + (((y - y1) / cell) % 2) * cell; x < x2; x += cell * 2) {
				g.fill(x, y, Math.min(x + cell, x2), Math.min(y + cell, y2), checkerDark());
			}
		}
	}

	/** Small paintbrush icon drawn from rectangles (no texture needed), {@code size} px square. */
	public static void brushIcon(DrawContext g, int x, int y, int size, int handleColor, int tipColor) {
		int unit = Math.max(1, size / 8);
		// Handle: diagonal from bottom-left to the middle.
		for (int i = 0; i < 4; i++) {
			g.fill(x + i * unit, y + size - (i + 2) * unit, x + (i + 2) * unit, y + size - i * unit, handleColor);
		}
		// Ferrule.
		g.fill(x + 4 * unit, y + 2 * unit, x + 6 * unit, y + 4 * unit, textSecondary());
		// Bristles / paint.
		g.fill(x + 5 * unit, y, x + 8 * unit, y + 3 * unit, tipColor);
	}

	public static boolean isHovered(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
