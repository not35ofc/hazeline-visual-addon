package com.hazeline.module.modules.menus;

import net.minecraft.client.gui.DrawContext;

import com.hazeline.gui.Colors;
import com.hazeline.gui.Shapes;
import com.hazeline.gui.Theme;

/**
 * Loading screen progress bar: a thin rounded track, a rounded fill in the theme accent colour and a
 * soft glow along the fill that is strongest at its leading end. Drawn at vanilla's bar height
 * (83.25 % of the screen height).
 */
final class MenuLoadingBar {
	private MenuLoadingBar() {
	}

	/** @param progress 0..1 (vanilla's smoothed reload progress), @param opacity 0..1 */
	static void draw(DrawContext g, float progress, float opacity, CustomMenus menus) {
		if (opacity <= 0.01f) {
			return;
		}
		int screenW = g.getScaledWindowWidth();
		int screenH = g.getScaledWindowHeight();
		int barW = Math.max(40, Math.round(screenW * (float) (menus.barWidth.get() / 100.0)));
		int barH = 4;
		int x1 = (screenW - barW) / 2;
		int x2 = x1 + barW;
		int y1 = Math.round(screenH * 0.8325f) - barH / 2;
		int y2 = y1 + barH;
		int radius = barH / 2;
		int accent = Theme.accent();
		int accentRgb = accent & 0x00FFFFFF;
		float p = Math.max(0f, Math.min(1f, progress));

		// Track
		int track = Colors.withAlpha(accentRgb, Math.round(46f * opacity));
		Shapes.roundedRect(g, x1, y1, x2, y2, radius, track, track, true, true);

		int fillW = Math.max(barH, Math.round(barW * p));
		int fx2 = x1 + fillW;
		float cy = (y1 + y2) / 2f;

		// Glow along the fill (strongest at the leading end), under the fill itself.
		int glows = Math.max(2, Math.min(24, fillW / 10));
		for (int i = 0; i < glows; i++) {
			float f = glows == 1 ? 1f : (i + 1) / (float) glows;
			float gx = x1 + fillW * f;
			float strength = 0.25f + 0.75f * f * f;
			Shapes.glow(g, gx, cy, 11f, Colors.withAlpha(accentRgb, Math.round(60f * strength * opacity)));
		}
		Shapes.glow(g, fx2, cy, 17f, Colors.withAlpha(accentRgb, Math.round(120f * opacity)));

		// Fill (brighter at the top edge)
		int top = Colors.withAlpha(Colors.lerp(accent | 0xFF000000, 0xFFFFFFFF, 0.25f), Math.round(255f * opacity));
		int bottom = Colors.withAlpha(accentRgb, Math.round(255f * opacity));
		Shapes.roundedRect(g, x1, y1, fx2, y2, radius, top, bottom, true, true);
	}
}
