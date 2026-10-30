package com.hazeline.module.modules.weather;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import com.hazeline.Hazeline;
import com.hazeline.module.modules.render.OutlinePipelines;

/**
 * The lightning of Smooth Thunder: when a strike happens, its bolt is generated once (a jagged main
 * strand with side branches), flickers through a few return strokes, lights the sky with a soft flash
 * that fades slowly, trembles the camera when close, and plays a rolling thunder sound that arrives
 * after the light. Strikes are purely visual / audible and client-side.
 */
final class ThunderStrikes {
	static final Identifier SOUND_NEAR = Hazeline.id("weather.thunder_near");
	static final Identifier SOUND_MID = Hazeline.id("weather.thunder_mid");
	static final Identifier SOUND_FAR = Hazeline.id("weather.thunder_far");

	/** Blocks per second the thunder travels at when "Delay by distance" is on (real sound is ~343). */
	private static final float SOUND_SPEED = 343f;
	private static final int MAX_STRIKES = 6;

	/** One generated lightning strike. */
	static final class Strike {
		double gx, gy, gz;
		float distance;
		/** Unit direction on the ground plane from the player to the strike. */
		float dirX, dirZ;
		float start;
		float[] pulseTime;
		float[] pulseStrength;
		/** Segment endpoints x0 y0 z0 x1 y1 z1 and the level (0 main, 1 branch, 2 twig). */
		float[] segs;
		byte[] levels;
		int count;
		boolean soundDone;
		float soundAt;
		float shakeYawPhase, shakePitchPhase;
		int seed;

		float age(float now) {
			return now - start;
		}
	}

	private static final List<Strike> ACTIVE = new ArrayList<>();
	private static final Random RNG = Random.create();
	private static int ticks;
	private static float countdown = 8f;
	private static float pendingSecond = -1f;

	private ThunderStrikes() {
	}

	static void clear() {
		ACTIVE.clear();
		countdown = 6f;
		pendingSecond = -1f;
	}

	static float now(float tp) {
		return (ticks + tp) / 20f;
	}

	// ------------------------------------------------------------------ per tick

	static void tick(MinecraftClient mc, ClientWorld w) {
		ticks++;
		float now = now(0f);
		if (w == null) {
			ACTIVE.clear();
			return;
		}
		ACTIVE.removeIf(s -> s.age(now) > 4.5f);
		SmoothThunder st = SmoothThunder.get();
		if (st != null && st.isEnabled()) {
			float thunder = WeatherController.thunder(1f);
			if (thunder > 0.25f) {
				countdown -= 0.05f * (0.55f + thunder * 0.9f);
				if (countdown <= 0f) {
					strike(mc, w, st);
					float gap = st.gap.getFloat();
					countdown = gap * (0.35f + RNG.nextFloat() * 1.3f);
					// Sometimes a second strike follows soon after (thunderstorms come in bursts).
					pendingSecond = RNG.nextFloat() < 0.25f ? 0.4f + RNG.nextFloat() * 1.1f : -1f;
				} else if (pendingSecond > 0f) {
					pendingSecond -= 0.05f;
					if (pendingSecond <= 0f) {
						strike(mc, w, st);
						pendingSecond = -1f;
					}
				}
			}
		}
		// Thunder sounds whose light has already arrived.
		for (Strike s : ACTIVE) {
			if (!s.soundDone && s.age(now) >= s.soundAt) {
				s.soundDone = true;
				playThunder(mc, s);
			}
		}
	}

	/** The "Test strike" button. */
	static void testStrike() {
		MinecraftClient mc = MinecraftClient.getInstance();
		SmoothThunder st = SmoothThunder.get();
		if (mc.world != null && st != null) {
			try {
				strike(mc, mc.world, st);
			} catch (Throwable t) {
				WeatherController.fail("test strike", t);
			}
		}
	}

	// ------------------------------------------------------------------ creating a strike

	private static void strike(MinecraftClient mc, ClientWorld w, SmoothThunder st) {
		var camera = mc.gameRenderer.getCamera();
		if (!camera.isReady()) {
			return;
		}
		Vec3d cam = camera.getCameraPos();
		float minD = st.minDistance.getFloat();
		float maxD = Math.max(minD + 10f, st.maxDistance.getFloat());
		float dist = minD + (maxD - minD) * (float) Math.pow(RNG.nextFloat(), 1.25);

		float angle; // radians, compass: 0 = north (-Z), pi/2 = east (+X)
		switch (st.direction.get()) {
			case AHEAD -> {
				float yaw = camera.getYaw(); // 0 = south (+Z), 90 = west (-X): the view direction is (-sin yaw, cos yaw)
				float forward = (float) Math.toRadians(yaw) + (float) Math.PI; // the same direction as a compass angle (sin a, -cos a)
				angle = forward + spread(st);
			}
			case FIXED -> angle = (float) Math.toRadians(st.fixedAngle.getFloat()) + spread(st);
			default -> angle = RNG.nextFloat() * MathHelper.TAU;
		}
		float dx = (float) Math.sin(angle);
		float dz = (float) -Math.cos(angle);

		Strike s = new Strike();
		s.distance = dist;
		s.dirX = dx;
		s.dirZ = dz;
		s.gx = cam.x + dx * dist;
		s.gz = cam.z + dz * dist;
		int bx = MathHelper.floor(s.gx);
		int bz = MathHelper.floor(s.gz);
		s.gy = w.getChunkManager().isChunkLoaded(bx >> 4, bz >> 4)
				? w.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz)
				: Math.floor(cam.y) - 2;
		s.start = now(0f);
		s.seed = RNG.nextInt();
		s.shakeYawPhase = RNG.nextFloat() * 6.28f;
		s.shakePitchPhase = RNG.nextFloat() * 6.28f;

		// Return strokes: a hard first flash, then one or two weaker re-strikes.
		int pulses = RNG.nextFloat() < 0.55f ? 3 : 2;
		s.pulseTime = new float[pulses];
		s.pulseStrength = new float[pulses];
		s.pulseTime[0] = 0f;
		s.pulseStrength[0] = 1f;
		s.pulseTime[1] = 0.07f + RNG.nextFloat() * 0.07f;
		s.pulseStrength[1] = 0.6f + RNG.nextFloat() * 0.3f;
		if (pulses == 3) {
			s.pulseTime[2] = s.pulseTime[1] + 0.09f + RNG.nextFloat() * 0.12f;
			s.pulseStrength[2] = 0.4f + RNG.nextFloat() * 0.3f;
		}
		s.soundAt = st.soundDelay.get() ? dist / SOUND_SPEED : 0.15f;

		buildBolt(s, cam, st);
		ACTIVE.add(s);
		while (ACTIVE.size() > MAX_STRIKES) {
			ACTIVE.remove(0);
		}
	}

	private static float spread(SmoothThunder st) {
		return (float) Math.toRadians((RNG.nextFloat() - 0.5f) * st.spread.getFloat());
	}

	private static void buildBolt(Strike s, Vec3d cam, SmoothThunder st) {
		Random r = Random.create(s.seed);
		List<float[]> out = new ArrayList<>();
		float topY = Math.max((float) cam.y + 60f, 190f);
		float sx = (float) (s.gx + (r.nextFloat() - 0.5f) * 18f);
		float sz = (float) (s.gz + (r.nextFloat() - 0.5f) * 18f);
		float[][] main = jagged(r, sx, topY, sz, (float) s.gx, (float) s.gy, (float) s.gz, 6, 0.16f);
		addPath(out, main, 0);

		float branching = st.branching.getFloat() / 100f;
		if (branching > 0f) {
			int tries = 3 + Math.round(branching * 8f);
			for (int i = 0; i < tries; i++) {
				if (r.nextFloat() > 0.35f + 0.5f * branching) {
					continue;
				}
				int at = 4 + r.nextInt(Math.max(1, main.length - 8));
				float[] p = main[at];
				float remaining = p[1] - (float) s.gy;
				if (remaining < 12f) {
					continue;
				}
				float ang = r.nextFloat() * MathHelper.TAU;
				float spreadLen = remaining * (0.25f + r.nextFloat() * 0.35f);
				float ex = p[0] + (float) Math.cos(ang) * spreadLen * 0.8f;
				float ez = p[2] + (float) Math.sin(ang) * spreadLen * 0.8f;
				float ey = p[1] - remaining * (0.30f + r.nextFloat() * 0.45f);
				float[][] branch = jagged(r, p[0], p[1], p[2], ex, ey, ez, 4, 0.2f);
				addPath(out, branch, 1);
				if (r.nextFloat() < 0.5f * branching + 0.1f && branch.length > 6) {
					float[] q = branch[2 + r.nextInt(branch.length - 4)];
					float a2 = ang + (r.nextFloat() - 0.5f) * 2.4f;
					float l2 = spreadLen * 0.45f;
					float[][] twig = jagged(r, q[0], q[1], q[2], q[0] + (float) Math.cos(a2) * l2, q[1] - l2 * 0.9f,
							q[2] + (float) Math.sin(a2) * l2, 3, 0.25f);
					addPath(out, twig, 2);
				}
			}
		}
		s.count = out.size();
		s.segs = new float[s.count * 6];
		s.levels = new byte[s.count];
		for (int i = 0; i < s.count; i++) {
			float[] seg = out.get(i);
			System.arraycopy(seg, 0, s.segs, i * 6, 6);
			s.levels[i] = (byte) seg[6];
		}
	}

	private static void addPath(List<float[]> out, float[][] pts, int level) {
		for (int i = 0; i + 1 < pts.length; i++) {
			out.add(new float[] {pts[i][0], pts[i][1], pts[i][2], pts[i + 1][0], pts[i + 1][1], pts[i + 1][2], level});
		}
	}

	/** Midpoint-displacement polyline from a to b with 2^iterations segments. */
	private static float[][] jagged(Random r, float ax, float ay, float az, float bx, float by, float bz, int iterations, float roughness) {
		List<float[]> pts = new ArrayList<>();
		pts.add(new float[] {ax, ay, az});
		pts.add(new float[] {bx, by, bz});
		float disp = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az)) * roughness;
		for (int it = 0; it < iterations; it++) {
			List<float[]> next = new ArrayList<>(pts.size() * 2);
			for (int i = 0; i + 1 < pts.size(); i++) {
				float[] p = pts.get(i);
				float[] q = pts.get(i + 1);
				next.add(p);
				float mx = (p[0] + q[0]) / 2f + (r.nextFloat() - 0.5f) * 2f * disp;
				float my = (p[1] + q[1]) / 2f + (r.nextFloat() - 0.5f) * disp * 0.4f;
				float mz = (p[2] + q[2]) / 2f + (r.nextFloat() - 0.5f) * 2f * disp;
				next.add(new float[] {mx, my, mz});
			}
			next.add(pts.get(pts.size() - 1));
			pts = next;
			disp *= 0.52f;
		}
		return pts.toArray(new float[0][]);
	}

	// ------------------------------------------------------------------ sound

	private static void playThunder(MinecraftClient mc, Strike s) {
		SmoothThunder st = SmoothThunder.get();
		if (st == null || !st.thunderSound.get()) {
			return;
		}
		float vol = st.thunderVolume.getFloat() / 100f;
		if (vol <= 0f) {
			return;
		}
		float k = MathHelper.clamp(s.distance / 300f, 0f, 1f);
		Identifier id = s.distance < 110f ? SOUND_NEAR : s.distance < 210f ? SOUND_MID : SOUND_FAR;
		float volume = vol * (1.05f - 0.55f * WeatherMath.smooth(k));
		float pitch = 0.9f + RNG.nextFloat() * 0.2f;
		// A source 8 blocks away towards the strike with no distance fade: panned to the right side, never quieter by distance.
		mc.getSoundManager().play(new PositionedSoundInstance(id, SoundCategory.WEATHER, volume, pitch, RNG, false, 0,
				SoundInstance.AttenuationType.NONE, s.dirX * 8.0, 0.0, s.dirZ * 8.0, true));
	}

	/** A thunder sound from the server was swapped for our own gentle rumble. */
	static void serverThunder() {
		MinecraftClient mc = MinecraftClient.getInstance();
		SmoothThunder st = SmoothThunder.get();
		if (mc.world == null || st == null || !st.thunderSound.get()) {
			return;
		}
		float vol = st.thunderVolume.getFloat() / 100f;
		mc.getSoundManager().play(new PositionedSoundInstance(SOUND_MID, SoundCategory.WEATHER, vol * 0.85f, 0.92f + RNG.nextFloat() * 0.16f, RNG,
				false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true));
	}

	// ------------------------------------------------------------------ visuals over time

	/** Brightness of the bolt itself (0..1+) for a strike at {@code age} seconds. */
	private static float boltLevel(Strike s, float age) {
		float v = 0f;
		for (int i = 0; i < s.pulseTime.length; i++) {
			float d = age - s.pulseTime[i];
			if (d >= 0f) {
				v = Math.max(v, s.pulseStrength[i] * (float) Math.exp(-d / 0.06f));
			}
		}
		if (age >= 0f) {
			v = Math.max(v, 0.22f * (float) Math.exp(-age / 0.28f));
		}
		return v;
	}

	/** Soft sky flash 0..1 for one strike: quick rise at each return stroke, slow fade. */
	private static float flashLevel(Strike s, float age, float fadeSeconds) {
		float tau = fadeSeconds / 4f;
		float v = 0f;
		for (int i = 0; i < s.pulseTime.length; i++) {
			float d = age - s.pulseTime[i];
			if (d >= 0f) {
				float rise = Math.min(1f, d / 0.035f);
				v = Math.max(v, s.pulseStrength[i] * rise * (float) Math.exp(-d / tau));
			}
		}
		return v;
	}

	private static float nearness(Strike s) {
		return MathHelper.lerp(WeatherMath.smooth((s.distance - 60f) / 300f), 1f, 0.35f);
	}

	/** Brightness of the whole sky from all strikes (0..1). */
	static float skyFlash(float tp) {
		SmoothThunder st = SmoothThunder.get();
		if (st == null || ACTIVE.isEmpty()) {
			return 0f;
		}
		float now = now(tp);
		float fade = st.flashFade.getFloat();
		float v = 0f;
		for (Strike s : ACTIVE) {
			v = Math.max(v, flashLevel(s, s.age(now), fade) * nearness(s));
		}
		return v * st.flashStrength.getFloat() / 100f;
	}

	/** Bright edge vignette for close strikes (0..1). */
	static float edgeFlash(float tp) {
		SmoothThunder st = SmoothThunder.get();
		if (st == null || ACTIVE.isEmpty()) {
			return 0f;
		}
		float now = now(tp);
		float v = 0f;
		for (Strike s : ACTIVE) {
			float near = 1f - WeatherMath.smooth(s.distance / 220f);
			v = Math.max(v, boltLevel(s, s.age(now)) * near);
		}
		return v * st.vignette.getFloat() / 100f;
	}

	/** Glow spots for the cloud glow: world position of a point in the clouds above each live strike, with its brightness. */
	static void glows(float tp, List<float[]> out) {
		SmoothThunder st = SmoothThunder.get();
		if (st == null || ACTIVE.isEmpty() || st.cloudGlow.getFloat() <= 0f) {
			return;
		}
		float now = now(tp);
		float k = st.cloudGlow.getFloat() / 100f;
		for (Strike s : ACTIVE) {
			float age = s.age(now);
			float level = Math.max(boltLevel(s, age), flashLevel(s, age, 1.0f) * 0.7f);
			if (level < 0.02f) {
				continue;
			}
			out.add(new float[] {(float) s.gx, 185f, (float) s.gz, level * k * nearness(s), s.distance});
		}
	}

	/** Camera shake in degrees {yaw, pitch} for close strikes. */
	static float[] shake(float tp) {
		SmoothThunder st = SmoothThunder.get();
		if (st == null || ACTIVE.isEmpty() || st.shake.getFloat() <= 0f) {
			return null;
		}
		float now = now(tp);
		float range = st.shakeRange.getFloat();
		float yaw = 0f;
		float pitch = 0f;
		for (Strike s : ACTIVE) {
			if (s.distance >= range) {
				continue;
			}
			float age = s.age(now);
			if (age < 0f || age > 1.4f) {
				continue;
			}
			float closeness = 1f - s.distance / range;
			float a = st.shake.getFloat() / 100f * 1.6f * closeness * closeness * (float) Math.exp(-age / 0.32f);
			yaw += a * (float) Math.sin(age * 58f + s.shakeYawPhase);
			pitch += a * 0.7f * (float) Math.sin(age * 67f + s.shakePitchPhase);
		}
		return new float[] {yaw, pitch};
	}

	static boolean any() {
		return !ACTIVE.isEmpty();
	}

	// ------------------------------------------------------------------ bolt drawing (world pass)

	/** Draws every live bolt as camera-facing ribbons (a thin hot core inside two soft glows). */
	static void drawBolts(VertexConsumerProvider provider, Vec3d cam, float tp) {
		SmoothThunder st = SmoothThunder.get();
		if (st == null || ACTIVE.isEmpty()) {
			return;
		}
		float now = now(tp);
		RenderLayer layer = OutlinePipelines.fill(false);
		VertexConsumer vc = null;
		int color = st.boltColor.get();
		float bright = st.boltBrightness.getFloat() / 100f;
		float thick = st.boltThickness.getFloat();
		float coreMix = 0.72f;
		int core = WeatherMath.mix(color, 0xFFFFFFFF, coreMix);
		for (Strike s : ACTIVE) {
			float level = boltLevel(s, s.age(now)) * bright;
			if (level < 0.015f) {
				continue;
			}
			if (vc == null) {
				vc = provider.getBuffer(layer);
			}
			for (int i = 0; i < s.count; i++) {
				int o = i * 6;
				float x0 = (float) (s.segs[o] - cam.x), y0 = (float) (s.segs[o + 1] - cam.y), z0 = (float) (s.segs[o + 2] - cam.z);
				float x1 = (float) (s.segs[o + 3] - cam.x), y1 = (float) (s.segs[o + 4] - cam.y), z1 = (float) (s.segs[o + 5] - cam.z);
				float mx = (x0 + x1) * 0.5f, my = (y0 + y1) * 0.5f, mz = (z0 + z1) * 0.5f;
				float dist = (float) Math.sqrt(mx * mx + my * my + mz * mz);
				if (dist < 2f) {
					continue;
				}
				float lv = s.levels[i] == 0 ? 1f : s.levels[i] == 1 ? 0.62f : 0.4f;
				float half = 0.05f * thick * (1f + dist / 28f) * lv;
				// Ribbon axis and the direction across it (perpendicular to the view ray).
				float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
				float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
				if (al < 1e-3f) {
					continue;
				}
				float ux = ax / al, uy = ay / al, uz = az / al;
				float vx = mx / dist, vy = my / dist, vz = mz / dist;
				float wx = uy * vz - uz * vy, wy = uz * vx - ux * vz, wz = ux * vy - uy * vx;
				float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
				if (wl < 1e-3f) {
					continue;
				}
				wx /= wl;
				wy /= wl;
				wz /= wl;
				float fadeTail = lv; // branches are dimmer
				ribbon(vc, x0, y0, z0, x1, y1, z1, ux, uy, uz, wx, wy, wz, half * 8f, WeatherMath.withAlpha(color, 0.09f * level * fadeTail));
				ribbon(vc, x0, y0, z0, x1, y1, z1, ux, uy, uz, wx, wy, wz, half * 3.2f, WeatherMath.withAlpha(color, 0.30f * level * fadeTail));
				ribbon(vc, x0, y0, z0, x1, y1, z1, ux, uy, uz, wx, wy, wz, half, WeatherMath.withAlpha(core, Math.min(1f, level) * (0.6f + 0.4f * fadeTail)));
			}
		}
	}

	private static void ribbon(VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float ux, float uy, float uz,
			float wx, float wy, float wz, float half, int argb) {
		// Overlap the ends a little so consecutive segments join without gaps.
		float e = half * 0.7f;
		float ax = x0 - ux * e, ay = y0 - uy * e, az = z0 - uz * e;
		float bx = x1 + ux * e, by = y1 + uy * e, bz = z1 + uz * e;
		vc.vertex(ax - wx * half, ay - wy * half, az - wz * half).color(argb);
		vc.vertex(ax + wx * half, ay + wy * half, az + wz * half).color(argb);
		vc.vertex(bx + wx * half, by + wy * half, bz + wz * half).color(argb);
		vc.vertex(bx - wx * half, by - wy * half, bz - wz * half).color(argb);
	}
}
