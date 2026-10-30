package com.hazeline.gui;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Tiny animation helper for the menu: values that ease toward a target using real elapsed time,
 * so hover fades, switches and page changes are equally smooth at any FPS.
 * {@link #beginFrame()} is called once per frame by the screen.
 */
public final class Anim {
	private static final Map<Object, float[]> VALUES = new IdentityHashMap<>();
	private static long lastNanos;
	private static float dt;

	private Anim() {
	}

	public static void beginFrame() {
		long now = System.nanoTime();
		dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
	}

	/** Seconds since the previous frame (0 on the first frame). */
	public static float dt() {
		return dt;
	}

	public static void reset() {
		VALUES.clear();
		lastNanos = 0;
	}

	/**
	 * Eases the value stored for {@code key} toward {@code target}; {@code speed} ≈ 1 / seconds
	 * to get most of the way. Returns the new value. Instant when animations are off.
	 */
	public static float approach(Object key, int slot, float target, float speed) {
		float[] v = VALUES.computeIfAbsent(key, k -> new float[] {Float.NaN, Float.NaN, Float.NaN, Float.NaN});
		if (Float.isNaN(v[slot]) || !Theme.animations()) {
			v[slot] = target;
			return target;
		}
		float k = 1f - (float) Math.exp(-speed * dt);
		v[slot] += (target - v[slot]) * k;
		if (Math.abs(target - v[slot]) < 0.002f) {
			v[slot] = target;
		}
		return v[slot];
	}

	/** Hover fade 0..1 for {@code key} (slot 0). */
	public static float hover(Object key, boolean hovered) {
		return approach(key, 0, hovered ? 1f : 0f, 14f);
	}

	/** Switch / toggle position 0..1 for {@code key} (slot 1). */
	public static float toggle(Object key, boolean on) {
		return approach(key, 1, on ? 1f : 0f, 16f);
	}

	public static float easeOutCubic(float t) {
		float f = 1f - Math.max(0f, Math.min(1f, t));
		return 1f - f * f * f;
	}
}
