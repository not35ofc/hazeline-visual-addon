package com.hazeline.module.modules.weather;

/** Small deterministic helpers shared by the weather renderers: hashing, smooth value noise, easing. */
final class WeatherMath {
	private WeatherMath() {
	}

	/** 32-bit integer hash (murmur3 finaliser). */
	static int hash(int a) {
		a ^= a >>> 16;
		a *= 0x85EBCA6B;
		a ^= a >>> 13;
		a *= 0xC2B2AE35;
		a ^= a >>> 16;
		return a;
	}

	static int hash(int a, int b) {
		return hash(a * 0x9E3779B1 + hash(b) + 0x7F4A7C15);
	}

	static int hash(int a, int b, int c) {
		return hash(hash(a, b), c);
	}

	/** Uniform value in [0, 1) from a hash. */
	static float unit(int h) {
		return (h >>> 8) * (1f / (1 << 24));
	}

	/** Uniform value in [0, 1) for an index and a salt. */
	static float rand(int index, int salt) {
		return unit(hash(index, salt));
	}

	static float smooth(float t) {
		t = clamp01(t);
		return t * t * (3f - 2f * t);
	}

	static float smoothstep(float edge0, float edge1, float x) {
		return smooth((x - edge0) / (edge1 - edge0));
	}

	static float clamp01(float v) {
		return v < 0f ? 0f : Math.min(v, 1f);
	}

	static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** Positive modulo for floats. */
	static float mod(float v, float m) {
		float r = v % m;
		return r < 0 ? r + m : r;
	}

	/** Positive modulo for doubles. */
	static double mod(double v, double m) {
		double r = v % m;
		return r < 0 ? r + m : r;
	}

	/** Smooth 2D value noise in [0, 1] (bicubic-eased lattice interpolation). */
	static float noise(float x, float y, int seed) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		float fx = smooth(x - x0);
		float fy = smooth(y - y0);
		float a = unit(hash(x0, y0, seed));
		float b = unit(hash(x0 + 1, y0, seed));
		float c = unit(hash(x0, y0 + 1, seed));
		float d = unit(hash(x0 + 1, y0 + 1, seed));
		return lerp(lerp(a, b, fx), lerp(c, d, fx), fy);
	}

	/** Two octaves of {@link #noise}, in [0, 1]. */
	static float fbm(float x, float y, int seed) {
		return noise(x, y, seed) * 0.62f + noise(x * 2.07f + 11.3f, y * 2.07f - 7.1f, seed + 17) * 0.38f;
	}

	static int argb(int a, int rgb) {
		return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
	}

	/** Multiplies the alpha of an ARGB colour. */
	static int withAlpha(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(alpha));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** Linear blend of two ARGB colours (all four channels). */
	static int mix(int c0, int c1, float t) {
		t = clamp01(t);
		int a = Math.round(lerp((c0 >>> 24) & 0xFF, (c1 >>> 24) & 0xFF, t));
		int r = Math.round(lerp((c0 >> 16) & 0xFF, (c1 >> 16) & 0xFF, t));
		int g = Math.round(lerp((c0 >> 8) & 0xFF, (c1 >> 8) & 0xFF, t));
		int b = Math.round(lerp(c0 & 0xFF, c1 & 0xFF, t));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Multiplies the RGB of a colour by {@code f} (alpha kept). */
	static int scaleRgb(int argb, float f) {
		int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * f));
		int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * f));
		int b = Math.min(255, Math.round((argb & 0xFF) * f));
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}
}
