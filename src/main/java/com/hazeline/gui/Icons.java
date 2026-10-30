package com.hazeline.gui;

import net.minecraft.client.gui.DrawContext;

import com.hazeline.module.Category;

/**
 * Small vector icons for the menu, drawn with smooth shapes ({@link Shapes}) so they stay crisp at
 * any GUI scale. Every icon fits a {@code size} x {@code size} GUI pixel square.
 */
public final class Icons {
	public enum Icon {
		SWORD, EYE, COMPASS, PERSON, SPEED, SLIDERS, PALETTE, KEYBOARD, SEARCH, LIST, GRID, CHEVRON_RIGHT, CHEVRON_LEFT,
		CHEVRON_DOWN, CHEVRON_UP, CHECK, LOGO, RESET, MOON, WINDOW, SPEAKER, NOTE, CLOUD
	}

	public static final Icon CHEVRON_RIGHT = Icon.CHEVRON_RIGHT;
	public static final Icon CHEVRON_LEFT = Icon.CHEVRON_LEFT;
	public static final Icon CHEVRON_DOWN = Icon.CHEVRON_DOWN;
	public static final Icon CHEVRON_UP = Icon.CHEVRON_UP;
	public static final Icon CHECK = Icon.CHECK;
	public static final Icon SEARCH = Icon.SEARCH;
	public static final Icon LIST = Icon.LIST;
	public static final Icon GRID = Icon.GRID;

	private Icons() {
	}

	public static Icon of(Category category) {
		return switch (category) {
			case RENDER -> Icon.EYE;
			case SKY -> Icon.MOON;
			case WEATHER -> Icon.CLOUD;
			case MENUS -> Icon.WINDOW;
			case SOUNDS -> Icon.SPEAKER;
			case MUSIC -> Icon.NOTE;
			case SETTINGS -> Icon.SLIDERS;
		};
	}

	/** Draws {@code icon} with its top-left corner at (x, y). */
	public static void draw(DrawContext g, Icon icon, float x, float y, float size, int color) {
		float s = size;
		float t = Math.max(1f, s / 7.5f); // stroke
		switch (icon) {
			case SWORD -> {
				line(g, x, y, s, 0.22f, 0.78f, 0.84f, 0.16f, t, color);
				line(g, x, y, s, 0.14f, 0.58f, 0.42f, 0.86f, t, color);
				line(g, x, y, s, 0.12f, 0.88f, 0.24f, 0.76f, t, color);
			}
			case MOON -> {
				// Filled crescent: radial strokes from the outer circle inwards until they reach the
				// "bite" circle, plus a small four-point star.
				float cx = x + s * 0.44f;
				float cy = y + s * 0.56f;
				float r = s * 0.36f;
				float bx = cx + r * 0.42f;
				float by = cy - r * 0.30f;
				float br = r * 0.80f;
				for (int i = 0; i < 48; i++) {
					double a = Math.PI * 2 * i / 48;
					float ox = cx + (float) Math.cos(a) * r;
					float oy = cy + (float) Math.sin(a) * r;
					if (Math.hypot(ox - bx, oy - by) < br) {
						continue; // this edge point is inside the bite
					}
					float ix = ox;
					float iy = oy;
					for (int k = 1; k <= 12; k++) { // walk towards the centre until inside the bite
						float qx = ox + (cx - ox) * k / 12f;
						float qy = oy + (cy - oy) * k / 12f;
						if (Math.hypot(qx - bx, qy - by) < br) {
							break;
						}
						ix = qx;
						iy = qy;
					}
					Shapes.capsule(g, ox, oy, ix, iy, t * 0.9f, color);
				}
				line(g, x, y, s, 0.80f, 0.08f, 0.80f, 0.32f, t * 0.75f, color);
				line(g, x, y, s, 0.68f, 0.20f, 0.92f, 0.20f, t * 0.75f, color);
			}
			case WINDOW -> {
				// A screen: rounded outline, a small star in it and a progress bar underneath.
				int r = Math.max(1, Math.round(s * 0.12f));
				Theme.roundedOutline(g, Math.round(x + s * 0.06f), Math.round(y + s * 0.14f), Math.round(s * 0.88f), Math.round(s * 0.72f),
						r, color);
				line(g, x, y, s, 0.50f, 0.28f, 0.50f, 0.54f, t * 0.75f, color);
				line(g, x, y, s, 0.37f, 0.41f, 0.63f, 0.41f, t * 0.75f, color);
				line(g, x, y, s, 0.26f, 0.70f, 0.74f, 0.70f, t * 0.8f, color);
			}
			case CLOUD -> {
				// Cloud (three discs on a flat base) with two rain drops below.
				Shapes.disc(g, x + s * 0.32f, y + s * 0.40f, s * 0.17f, color);
				Shapes.disc(g, x + s * 0.52f, y + s * 0.30f, s * 0.21f, color);
				Shapes.disc(g, x + s * 0.72f, y + s * 0.42f, s * 0.16f, color);
				line(g, x, y, s, 0.30f, 0.50f, 0.74f, 0.50f, t * 1.9f, color);
				line(g, x, y, s, 0.34f, 0.72f, 0.28f, 0.88f, t * 0.8f, color);
				line(g, x, y, s, 0.56f, 0.72f, 0.50f, 0.88f, t * 0.8f, color);
				line(g, x, y, s, 0.78f, 0.72f, 0.72f, 0.88f, t * 0.8f, color);
			}
			case NOTE -> {
				// Two beamed eighth notes: filled heads, stems, a thick beam.
				Shapes.disc(g, x + s * 0.28f, y + s * 0.76f, s * 0.13f, color);
				Shapes.disc(g, x + s * 0.72f, y + s * 0.68f, s * 0.13f, color);
				line(g, x, y, s, 0.39f, 0.76f, 0.39f, 0.24f, t, color);
				line(g, x, y, s, 0.83f, 0.68f, 0.83f, 0.16f, t, color);
				line(g, x, y, s, 0.39f, 0.25f, 0.83f, 0.17f, t * 1.7f, color);
			}
			case SPEAKER -> {
				// Speaker: box + cone (drawn as strokes, the box filled with a short thick stroke) and two sound waves.
				line(g, x, y, s, 0.16f, 0.42f, 0.16f, 0.58f, t * 2.2f, color);
				line(g, x, y, s, 0.10f, 0.38f, 0.28f, 0.38f, t * 0.8f, color);
				line(g, x, y, s, 0.10f, 0.62f, 0.28f, 0.62f, t * 0.8f, color);
				line(g, x, y, s, 0.10f, 0.38f, 0.10f, 0.62f, t * 0.8f, color);
				line(g, x, y, s, 0.28f, 0.38f, 0.48f, 0.20f, t * 0.8f, color);
				line(g, x, y, s, 0.28f, 0.62f, 0.48f, 0.80f, t * 0.8f, color);
				line(g, x, y, s, 0.48f, 0.20f, 0.48f, 0.80f, t * 0.8f, color);
				line(g, x, y, s, 0.30f, 0.42f, 0.30f, 0.58f, t * 1.6f, color);
				line(g, x, y, s, 0.38f, 0.34f, 0.38f, 0.66f, t * 1.6f, color);
				for (int w = 0; w < 2; w++) {
					float r = w == 0 ? 0.16f : 0.30f;
					float px = 0, py = 0;
					for (int i = 0; i <= 8; i++) {
						double a = Math.toRadians(-45 + 90.0 * i / 8);
						float qx = 0.52f + r * (float) Math.cos(a);
						float qy = 0.50f + r * (float) Math.sin(a);
						if (i > 0) {
							line(g, x, y, s, px, py, qx, qy, t * 0.8f, color);
						}
						px = qx;
						py = qy;
					}
				}
			}
			case EYE -> {
				Shapes.ring(g, x + s * 0.5f, y + s * 0.5f, s * 0.42f, color);
				Shapes.disc(g, x + s * 0.5f, y + s * 0.5f, s * 0.17f, color);
			}
			case COMPASS -> {
				Shapes.ring(g, x + s * 0.5f, y + s * 0.5f, s * 0.44f, color);
				line(g, x, y, s, 0.36f, 0.64f, 0.66f, 0.34f, t, color);
				Shapes.disc(g, x + s * 0.66f, y + s * 0.34f, t * 0.9f, color);
			}
			case PERSON -> {
				Shapes.disc(g, x + s * 0.5f, y + s * 0.3f, s * 0.2f, color);
				int r = Math.max(1, Math.round(s * 0.2f));
				Theme.roundedRect(g, Math.round(x + s * 0.16f), Math.round(y + s * 0.58f), Math.round(x + s * 0.84f),
						Math.round(y + s * 0.94f), r, color, true, false);
			}
			case SPEED -> {
				line(g, x, y, s, 0.12f, 0.25f, 0.72f, 0.25f, t, color);
				line(g, x, y, s, 0.28f, 0.5f, 0.9f, 0.5f, t, color);
				line(g, x, y, s, 0.12f, 0.75f, 0.62f, 0.75f, t, color);
			}
			case SLIDERS -> {
				for (int i = 0; i < 3; i++) {
					float yy = 0.22f + i * 0.28f;
					line(g, x, y, s, 0.1f, yy, 0.9f, yy, t * 0.8f, color);
					float kx = i == 1 ? 0.68f : (i == 0 ? 0.32f : 0.5f);
					Shapes.disc(g, x + s * kx, y + s * yy, t * 1.5f, color);
				}
			}
			case PALETTE -> {
				Shapes.ring(g, x + s * 0.5f, y + s * 0.5f, s * 0.44f, color);
				Shapes.disc(g, x + s * 0.34f, y + s * 0.38f, t * 0.95f, color);
				Shapes.disc(g, x + s * 0.6f, y + s * 0.3f, t * 0.95f, color);
				Shapes.disc(g, x + s * 0.7f, y + s * 0.56f, t * 0.95f, color);
			}
			case KEYBOARD -> {
				int r = Math.max(1, Math.round(s * 0.12f));
				Theme.roundedOutline(g, Math.round(x + s * 0.04f), Math.round(y + s * 0.2f), Math.round(s * 0.92f), Math.round(s * 0.62f),
						r, color);
				for (int i = 0; i < 3; i++) {
					Shapes.disc(g, x + s * (0.28f + i * 0.22f), y + s * 0.42f, t * 0.6f, color);
				}
				line(g, x, y, s, 0.3f, 0.64f, 0.7f, 0.64f, t * 0.8f, color);
			}
			case SEARCH -> {
				Shapes.ring(g, x + s * 0.42f, y + s * 0.42f, s * 0.3f, color);
				line(g, x, y, s, 0.64f, 0.64f, 0.9f, 0.9f, t, color);
			}
			case LIST -> {
				for (int i = 0; i < 3; i++) {
					float yy = 0.24f + i * 0.26f;
					Shapes.disc(g, x + s * 0.14f, y + s * yy, t * 0.7f, color);
					line(g, x, y, s, 0.34f, yy, 0.9f, yy, t * 0.8f, color);
				}
			}
			case GRID -> {
				int c = Math.max(1, Math.round(s * 0.38f));
				int r = Math.max(1, c / 4);
				int x0 = Math.round(x + s * 0.08f);
				int y0 = Math.round(y + s * 0.08f);
				int x1 = Math.round(x + s * 0.54f);
				int y1 = Math.round(y + s * 0.54f);
				Theme.roundedRect(g, x0, y0, x0 + c, y0 + c, r, color);
				Theme.roundedRect(g, x1, y0, x1 + c, y0 + c, r, color);
				Theme.roundedRect(g, x0, y1, x0 + c, y1 + c, r, color);
				Theme.roundedRect(g, x1, y1, x1 + c, y1 + c, r, color);
			}
			case CHEVRON_RIGHT -> {
				line(g, x, y, s, 0.38f, 0.2f, 0.66f, 0.5f, t, color);
				line(g, x, y, s, 0.66f, 0.5f, 0.38f, 0.8f, t, color);
			}
			case CHEVRON_LEFT -> {
				line(g, x, y, s, 0.62f, 0.2f, 0.34f, 0.5f, t, color);
				line(g, x, y, s, 0.34f, 0.5f, 0.62f, 0.8f, t, color);
			}
			case CHEVRON_DOWN -> {
				line(g, x, y, s, 0.2f, 0.38f, 0.5f, 0.66f, t, color);
				line(g, x, y, s, 0.5f, 0.66f, 0.8f, 0.38f, t, color);
			}
			case CHEVRON_UP -> {
				line(g, x, y, s, 0.2f, 0.62f, 0.5f, 0.34f, t, color);
				line(g, x, y, s, 0.5f, 0.34f, 0.8f, 0.62f, t, color);
			}
			case CHECK -> {
				line(g, x, y, s, 0.18f, 0.52f, 0.42f, 0.76f, t * 1.1f, color);
				line(g, x, y, s, 0.42f, 0.76f, 0.84f, 0.26f, t * 1.1f, color);
			}
			case RESET -> {
				Shapes.ring(g, x + s * 0.5f, y + s * 0.52f, s * 0.36f, color);
				line(g, x, y, s, 0.5f, 0.06f, 0.66f, 0.16f, t, color);
				line(g, x, y, s, 0.5f, 0.26f, 0.66f, 0.16f, t, color);
			}
			case LOGO -> {
				// Three wavy "noodle" strokes.
				for (int i = 0; i < 3; i++) {
					float yy = 0.26f + i * 0.24f;
					line(g, x, y, s, 0.1f, yy + 0.05f, 0.37f, yy - 0.05f, t, color);
					line(g, x, y, s, 0.37f, yy - 0.05f, 0.63f, yy + 0.05f, t, color);
					line(g, x, y, s, 0.63f, yy + 0.05f, 0.9f, yy - 0.05f, t, color);
				}
			}
		}
	}

	private static void line(DrawContext g, float x, float y, float s, float ax, float ay, float bx, float by, float t, int color) {
		Shapes.capsule(g, x + s * ax, y + s * ay, x + s * bx, y + s * by, t, color);
	}
}
