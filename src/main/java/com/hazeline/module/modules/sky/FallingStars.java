package com.hazeline.module.modules.sky;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Vector3f;

/**
 * Falling-star simulation (render thread only).
 *
 * <p>Stars live on the unit sky sphere around the camera, in world-aligned directions (they do not
 * turn with the camera). Each star travels along a great circle: it starts at {@code start} and is
 * rotated around {@code axis} by {@code angularSpeed * age}. The axis is chosen so the star's
 * initial motion points "down" the sky, turned by the fall angle. Because the path is analytic, a
 * trail point is simply the position a moment earlier - trails stay smooth at any frame rate.
 *
 * <p>Everything advances by real frame time (delta-time), clamped to 0.1 s per frame so a hitch
 * never makes stars jump.
 */
public final class FallingStars {
	private static final FallingStars INSTANCE = new FallingStars();

	/** One shooting star. */
	public static final class Star {
		final Vector3f start = new Vector3f();
		final Vector3f axis = new Vector3f();
		float angularSpeed; // radians per second
		float travel; // total angle before it has fully faded, radians
		float age; // seconds
		float size; // relative size multiplier
		float phase; // twinkle phase
		float brightness; // per-star base brightness 0.75..1
	}

	private final List<Star> stars = new ArrayList<>();
	private long lastNanos;
	private float spawnBudget;
	private float clock;

	private FallingStars() {
	}

	public static FallingStars get() {
		return INSTANCE;
	}

	public List<Star> stars() {
		return stars;
	}

	public float clock() {
		return clock;
	}

	public void clear() {
		stars.clear();
		spawnBudget = 0;
		lastNanos = 0;
	}

	/** Advances the simulation to now. Call once per frame before drawing. */
	public void update(CustomSky sky) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		advance(sky, dt);
	}

	/** Advances the simulation by {@code dt} seconds (also used by the offline preview). */
	void advance(CustomSky sky, float dt) {
		clock += dt;

		int maxActive = maxActive(sky);
		float speed = sky.starSpeed.getFloat();
		for (int i = stars.size() - 1; i >= 0; i--) {
			Star s = stars.get(i);
			s.age += dt;
			if (s.age * s.angularSpeed >= s.travel || belowHorizon(s)) {
				stars.remove(i);
			}
		}
		if (!sky.stars.get() || maxActive <= 0) {
			stars.clear();
			spawnBudget = 0;
			return;
		}
		spawnBudget = Math.min(spawnBudget + dt * sky.spawnRate.getFloat(), 4f);
		while (spawnBudget >= 1f) {
			spawnBudget -= 1f;
			if (stars.size() < maxActive) {
				stars.add(spawn(sky, speed));
			}
		}
	}

	/** Star count after the Quality scale, always within the hard cap. */
	public static int maxActive(CustomSky sky) {
		int wanted = (int) Math.round(sky.starCount.get() * sky.quality.get().countScale);
		return Math.max(0, Math.min(CustomSky.MAX_ACTIVE_STARS, wanted));
	}

	private static boolean belowHorizon(Star s) {
		Vector3f p = position(s, 0f, new Vector3f());
		return p.y < -0.02f;
	}

	private static Star spawn(CustomSky sky, float speed) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		Star s = new Star();
		// Start high in the sky: elevation 25..80 degrees, any azimuth.
		double elevation = Math.toRadians(25 + r.nextDouble() * 55);
		double azimuth = r.nextDouble() * Math.PI * 2;
		s.start.set((float) (Math.cos(elevation) * Math.cos(azimuth)), (float) Math.sin(elevation),
				(float) (Math.cos(elevation) * Math.sin(azimuth))).normalize();

		// "Down the sky" at the start point: world down projected onto the tangent plane.
		Vector3f down = new Vector3f(0, -1, 0).fma(s.start.y, s.start).normalize(); // (-Y) - (p.(-Y)) p
		double angle = sky.fallAngle.get();
		if (sky.directionMode.get() == CustomSky.DirectionMode.RANDOM) {
			angle += (r.nextDouble() * 2 - 1) * sky.angleRange.get();
		}
		double a = Math.toRadians(angle);
		// Rotate "down" around the start direction by the fall angle (Rodrigues; down is perpendicular to start).
		Vector3f side = new Vector3f(s.start).cross(down);
		Vector3f motion = new Vector3f(down).mul((float) Math.cos(a)).add(side.mul((float) Math.sin(a))).normalize();
		// Rotation axis so that d/dt position = axis x position = motion at the start point.
		s.axis.set(s.start).cross(motion).normalize();

		s.angularSpeed = (float) (Math.toRadians(14) * speed * (0.8 + r.nextDouble() * 0.4));
		s.travel = (float) Math.toRadians(25 + r.nextDouble() * 30);
		s.size = (float) (0.8 + r.nextDouble() * 0.4);
		s.phase = (float) (r.nextDouble() * Math.PI * 2);
		s.brightness = (float) (0.75 + r.nextDouble() * 0.25);
		return s;
	}

	/** Unit-sphere position of {@code s} {@code secondsAgo} before now (clamped to its spawn). */
	public static Vector3f position(Star s, float secondsAgo, Vector3f out) {
		float t = Math.max(0f, s.age - secondsAgo);
		return out.set(s.start).rotateAxis(t * s.angularSpeed, s.axis.x, s.axis.y, s.axis.z);
	}

	/** 0..1 visibility: quick fade in, fade out over the last 30 % of its travel and near the horizon. */
	public static float fade(Star s) {
		float fadeIn = Math.min(1f, s.age / 0.25f);
		float progress = s.age * s.angularSpeed / s.travel;
		float fadeOut = progress < 0.7f ? 1f : Math.max(0f, 1f - (progress - 0.7f) / 0.3f);
		float elevation = position(s, 0f, new Vector3f()).y;
		float horizon = Math.max(0f, Math.min(1f, elevation / 0.08f));
		return fadeIn * fadeOut * horizon;
	}
}
