package com.hazeline.gui;

/** ARGB / HSV colour maths used by {@link Theme} and the colour picker. */
public final class Colors {
	private Colors() {
	}

	public static int alpha(int argb) {
		return (argb >>> 24) & 0xFF;
	}

	public static int red(int argb) {
		return (argb >> 16) & 0xFF;
	}

	public static int green(int argb) {
		return (argb >> 8) & 0xFF;
	}

	public static int blue(int argb) {
		return argb & 0xFF;
	}

	public static int argb(int a, int r, int g, int b) {
		return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	public static int withAlpha(int argb, int alpha) {
		return (clamp(alpha) << 24) | (argb & 0x00FFFFFF);
	}

	/** Multiplies RGB by {@code factor} (0..1 darkens), keeps alpha. */
	public static int darken(int argb, float factor) {
		return argb(alpha(argb), Math.round(red(argb) * factor), Math.round(green(argb) * factor), Math.round(blue(argb) * factor));
	}

	/** Linear interpolation of all four channels, t = 0 gives {@code from}. */
	public static int lerp(int from, int to, float t) {
		float k = Math.max(0, Math.min(1, t));
		return argb(Math.round(alpha(from) + (alpha(to) - alpha(from)) * k),
				Math.round(red(from) + (red(to) - red(from)) * k),
				Math.round(green(from) + (green(to) - green(from)) * k),
				Math.round(blue(from) + (blue(to) - blue(from)) * k));
	}

	/** Mixes {@code other} into {@code base} by {@code amount}, keeping base's alpha. */
	public static int mix(int base, int other, float amount) {
		return withAlpha(lerp(base, other, amount), alpha(base));
	}

	/** Returns {hue 0..1, saturation 0..1, value 0..1}. */
	public static float[] toHsv(int argb) {
		float r = red(argb) / 255f;
		float g = green(argb) / 255f;
		float b = blue(argb) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float h;
		if (delta == 0) {
			h = 0;
		} else if (max == r) {
			h = ((g - b) / delta) % 6f;
		} else if (max == g) {
			h = (b - r) / delta + 2f;
		} else {
			h = (r - g) / delta + 4f;
		}
		h /= 6f;
		if (h < 0) {
			h += 1f;
		}
		float s = max == 0 ? 0 : delta / max;
		return new float[] {h, s, max};
	}

	public static int fromHsv(float h, float s, float v, int alpha) {
		float hh = ((h % 1f) + 1f) % 1f * 6f;
		int sector = (int) Math.floor(hh);
		float f = hh - sector;
		float p = v * (1 - s);
		float q = v * (1 - s * f);
		float t = v * (1 - s * (1 - f));
		float r, g, b;
		switch (sector) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return argb(alpha, Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}
}
