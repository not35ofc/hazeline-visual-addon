package com.hazeline.module.modules.menus;

import net.minecraft.client.gui.DrawContext;

import com.hazeline.gui.Colors;
import com.hazeline.gui.Shapes;
import com.hazeline.gui.Theme;
import com.hazeline.gui.text.TextPipelines;

/**
 * The wordmark (default "Hazeline", editable in the module): the first word in white, the rest in
 * the theme accent colour (a single word is all white), drawn with the mod's own text renderer in bold,
 * with an optional soft accent glow behind it.
 *
 * <p>On the loading screen the text renderer may be asked before the game has applied the mod's
 * shaders; text is then skipped for that frame (instead of locking the text renderer into its
 * fallback mode early), see {@link TextPipelines#coverageAvailable()}.
 */
final class MenuWordmark {
	static final String DEFAULT_TEXT = "Hazeline";
	/** Wordmark width in GUI px at 100 % (vanilla's logo region is 256 wide). */
	private static final float BASE_WIDTH = 200f;

	private MenuWordmark() {
	}

	/** Title screen: the wordmark replaces the logo at {@code y} (vanilla's logo is 44 px high from there). */
	static boolean draw(DrawContext g, float centerX, int y, float alpha, CustomMenus menus) {
		return drawBox(g, centerX, y + 22f, alpha, menus);
	}

	/** Centred on ({@code cx}, {@code cy}). */
	static boolean drawCentered(DrawContext g, float cx, float cy, float alpha, CustomMenus menus) {
		return drawBox(g, cx, cy, alpha, menus);
	}

	private static boolean drawBox(DrawContext g, float cx, float cy, float alpha, CustomMenus menus) {
		if (alpha <= 0.01f) {
			return true;
		}
		if (!TextPipelines.coverageAvailable()) {
			return false;
		}
		Theme.beginFrame();
		String text = menus.wordmarkText.get().strip();
		if (text.isEmpty()) {
			text = DEFAULT_TEXT;
		}
		int split = text.indexOf(' ');
		String left = split < 0 ? text : text.substring(0, split);
		String right = split < 0 ? "" : text.substring(split); // keeps the leading space
		float screenW = g.getScaledWindowWidth();
		float target = Math.min(BASE_WIDTH * (float) (menus.wordmarkSize.get() / 100.0), screenW * 0.9f);
		double rel = target / Math.max(1, Theme.widthOf(text, 1.0, true));
		rel = Math.max(0.5, Math.min(rel, 12.0));
		float leftW = Theme.widthOf(left, rel, true);
		float totalW = Theme.widthOf(text, rel, true);
		float h = Theme.heightOf(rel);
		float x = cx - totalW / 2f;
		float y = cy - h / 2f;
		int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
		int accent = Theme.accent();

		if (menus.wordmarkGlow.get()) {
			// A row of soft glows along the text, brighter in the middle.
			int n = Math.max(3, Math.round(totalW / (h * 0.9f)));
			for (int i = 0; i < n; i++) {
				float f = n == 1 ? 0.5f : i / (float) (n - 1);
				float gx = x + totalW * (0.08f + 0.84f * f);
				float strength = 1f - Math.abs(f - 0.5f) * 0.9f;
				Shapes.glow(g, gx, cy, h * 1.35f, Colors.withAlpha(accent, Math.round(70f * strength * alpha)));
			}
		}
		Theme.textOf(g, left, x, y, Colors.withAlpha(0xFFFFFFFF, a), rel, true);
		if (!right.isEmpty()) {
			Theme.textOf(g, right, x + leftW, y, Colors.withAlpha(accent, Math.round(Colors.alpha(accent) * alpha)), rel, true);
		}
		return true;
	}
}
