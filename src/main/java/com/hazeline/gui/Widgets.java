package com.hazeline.gui;

import net.minecraft.client.gui.DrawContext;

/** Shared controls of the menu (switches, value boxes, buttons), all built from {@link Theme}. */
public final class Widgets {
	private Widgets() {
	}

	/** Switch width/height in GUI px. */
	public static int switchW() {
		return Theme.px(19);
	}

	public static int switchH() {
		return Theme.px(10);
	}

	/** A pill switch; {@code progress} 0 = off, 1 = on (animated by the caller via {@link Anim#toggle}). */
	public static void toggleSwitch(DrawContext g, int x, int y, float progress, boolean hovered) {
		int w = switchW();
		int h = switchH();
		int off = hovered ? Colors.mix(Theme.track(), Theme.text(), 0.1f) : Theme.track();
		int track = Colors.lerp(off, Theme.accent(), progress);
		Theme.roundedRect(g, x, y, x + w, y + h, h / 2, track);
		float r = h / 2f - Math.max(1f, Theme.px(1.5f));
		float cx = x + h / 2f + (w - h) * progress;
		Shapes.disc(g, cx, y + h / 2f, r, Colors.lerp(Theme.textSecondary(), Theme.text(), 0.4f + 0.6f * progress));
	}

	/** Rounded box with text inside (values, keys, dropdown heads). */
	public static void valueBox(DrawContext g, int x, int y, int w, int h, String text, boolean active, boolean hovered) {
		int r = Math.min(Theme.cornerRadius(), h / 2);
		Theme.roundedRect(g, x, y, x + w, y + h, r, hovered ? Colors.mix(Theme.inset(), Theme.text(), 0.05f) : Theme.inset());
		Theme.roundedOutline(g, x, y, w, h, r, active ? Theme.accent() : Theme.border());
		String shown = Theme.fitOf(text, w - Theme.px(6), Theme.SMALL, false);
		Theme.boxTextOf(g, shown, x + (w - Theme.widthOf(shown, Theme.SMALL, false)) / 2f, y, h,
				active ? Theme.text() : Theme.textSecondary(), Theme.SMALL, false);
	}

	/** Rounded button; {@code primary} = filled with the accent colour. */
	public static void button(DrawContext g, int x, int y, int w, int h, String label, boolean primary, float hover) {
		int r = Math.min(Theme.cornerRadius(), h / 2);
		int bg = primary ? Colors.lerp(Theme.accent(220), Theme.accent(), hover)
				: Colors.lerp(Theme.inset(), Colors.mix(Theme.inset(), Theme.text(), 0.08f), hover);
		Theme.roundedRect(g, x, y, x + w, y + h, r, bg);
		if (!primary) {
			Theme.roundedOutline(g, x, y, w, h, r, Colors.lerp(Theme.border(), Theme.accent(160), hover));
		}
		String shown = Theme.fitOf(label, w - Theme.px(6), Theme.SMALL, false);
		Theme.boxTextOf(g, shown, x + (w - Theme.widthOf(shown, Theme.SMALL, false)) / 2f, y, h, Theme.text(), Theme.SMALL, false);
	}

	/** Width of a button that fits {@code label}. */
	public static int buttonWidth(String label) {
		return Theme.widthOf(label, Theme.SMALL, false) + Theme.px(14);
	}

	public static int buttonHeight() {
		return Theme.px(14);
	}
}
