package com.hazeline.module.modules.weather;

/**
 * Pixel generators for the weather textures: plain {@code int[]} ARGB images (white with an alpha
 * shape, tinted by the vertex colour), free of Minecraft classes so they can be previewed offline.
 * {@link WeatherTextures} uploads them.
 */
public final class WeatherPixels {
	static final int RAIN_W = 8, RAIN_H = 64;
	static final int SNOW_S = 32;
	static final int RING_S = 64;
	static final int PUFF_S = 128;
	static final int SHEEN_S = 8;
	static final int VIGNETTE_S = 128;
	static final int RAYS_S = 256;
	static final int GLOW_S = 128;

	private WeatherPixels() {
	}

	private static int[] build(int w, int h, Shape shape) {
		int[] px = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float a = shape.alpha((x + 0.5f) / w, (y + 0.5f) / h, x, y);
				px[y * w + x] = (Math.round(WeatherMath.clamp01(a) * 255f) << 24) | 0xFFFFFF;
			}
		}
		return px;
	}

	private interface Shape {
		float alpha(float u, float v, int x, int y);
	}

	/** Rain streak: soft across, tail fading towards the top (v = 0), a bright head near the bottom. */
	static int[] rain() {
		return build(RAIN_W, RAIN_H, (u, v, x, y) -> {
			float dx = (u - 0.5f) * 2f;
			float across = (float) Math.exp(-dx * dx * 3.0f);
			float along = WeatherMath.smoothstep(0f, 0.88f, v);
			along *= 1f - WeatherMath.smoothstep(0.93f, 1f, v) * 0.85f;
			float core = (float) Math.exp(-dx * dx * 22f) * 0.45f; // thin bright core = a glassy glint
			return (across * 0.85f + core * 1.1f) * along;
		});
	}

	/** Snow flake: soft round disc with a brighter centre. */
	static int[] snow() {
		return build(SNOW_S, SNOW_S, (u, v, x, y) -> {
			float d = (float) Math.hypot(u - 0.5f, v - 0.5f) * 2f;
			if (d >= 1f) {
				return 0f;
			}
			float body = 1f - d * d;
			return body * body * 0.8f + (float) Math.exp(-d * d * 14f) * 0.5f;
		});
	}

	/** Ripple ring: a thin soft ring (radius ~0.8 of the half size) with a faint inner glow. */
	static int[] ring() {
		return build(RING_S, RING_S, (u, v, x, y) -> {
			float d = (float) Math.hypot(u - 0.5f, v - 0.5f) * 2f;
			if (d >= 1f) {
				return 0f;
			}
			float r = (d - 0.80f) / 0.085f;
			float ring = (float) Math.exp(-r * r);
			float inner = (float) Math.exp(-((d - 0.55f) / 0.12f) * ((d - 0.55f) / 0.12f)) * 0.18f;
			return (ring + inner) * (1f - WeatherMath.smoothstep(0.9f, 1f, d));
		});
	}

	/** Mist puff: a soft blob whose density is broken up by smooth noise, fading to 0 at the edge. */
	static int[] puff() {
		return build(PUFF_S, PUFF_S, (u, v, x, y) -> {
			float dx = (u - 0.5f) * 2f;
			float dy = (v - 0.5f) * 2f;
			float d = (float) Math.hypot(dx, dy);
			if (d >= 1f) {
				return 0f;
			}
			float fall = 1f - WeatherMath.smooth(d);
			fall *= fall;
			float n = WeatherMath.fbm(u * 5.5f, v * 5.5f, 4711);
			return fall * (0.45f + 0.75f * n);
		});
	}

	/** Ground sheen: flat white (the sheen comes from per-vertex alpha). */
	static int[] sheen() {
		return build(SHEEN_S, SHEEN_S, (u, v, x, y) -> 1f);
	}

	/** Screen vignette: transparent in the middle, opaque at the edges. */
	static int[] vignette() {
		return build(VIGNETTE_S, VIGNETTE_S, (u, v, x, y) -> {
			float dx = (u - 0.5f) * 2f;
			float dy = (v - 0.5f) * 2f;
			float d = (float) Math.sqrt((dx * dx + dy * dy) * 0.5f) * 1.25f;
			return WeatherMath.smoothstep(0.45f, 1.0f, d);
		});
	}

	/** Soft radial glow (bright core, long Gaussian-like falloff). */
	static int[] glow() {
		return build(GLOW_S, GLOW_S, (u, v, x, y) -> {
			float d = (float) Math.hypot(u - 0.5f, v - 0.5f) * 2f;
			if (d >= 1f) {
				return 0f;
			}
			float g = (float) (Math.exp(-d * d * 6.0) - Math.exp(-6.0)) / (float) (1 - Math.exp(-6.0));
			return Math.max(g, d < 0.1f ? 1f : 0f);
		});
	}

	/** Light rays: angular streaks fanning out of the centre, fading with distance. */
	static int[] rays() {
		return build(RAYS_S, RAYS_S, (u, v, x, y) -> {
			float dx = (u - 0.5f) * 2f;
			float dy = (v - 0.5f) * 2f;
			float d = (float) Math.hypot(dx, dy);
			if (d >= 1f) {
				return 0f;
			}
			float ang = (float) Math.atan2(dy, dx);
			// Periodic 1D noise around the circle: sample a lattice at angle * k and wrap it.
			float k = ang / (float) (2 * Math.PI) * 24f;
			int i0 = (int) Math.floor(k);
			float f = WeatherMath.smooth(k - i0);
			float a = WeatherMath.unit(WeatherMath.hash(Math.floorMod(i0, 24), 91));
			float b = WeatherMath.unit(WeatherMath.hash(Math.floorMod(i0 + 1, 24), 91));
			float streak = WeatherMath.lerp(a, b, f);
			streak = WeatherMath.smoothstep(0.35f, 0.95f, streak);
			float fall = (1f - d) * (1f - d);
			return streak * fall * 0.9f + (float) Math.exp(-d * d * 18f) * 0.5f;
		});
	}


	/** The pixels of a named texture, for the offline previews. */
	public static int[] pixels(String name) {
		return switch (name) {
			case "rain" -> rain();
			case "snow" -> snow();
			case "ring" -> ring();
			case "puff" -> puff();
			case "vignette" -> vignette();
			case "rays" -> rays();
			case "glow" -> glow();
			default -> sheen();
		};
	}
}
