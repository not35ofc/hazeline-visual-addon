package com.hazeline.module.modules.weather;

/**
 * The geometry of Hazeline's rain, snow, wet-ground sheen, ripples and mist, free of any Minecraft
 * class so it can be run and previewed offline. Every method writes camera-relative quads (4 vertices
 * each) into a {@link QuadSink}; {@link WeatherDraw} feeds those into the game's vertex buffers.
 *
 * <p>Drops are not simulated: drop {@code i} gets its start position and speed from hashes of
 * {@code i}, and its position at time {@code t} is {@code start + velocity * t}, wrapped into a box
 * around the camera. So there is no per-drop state, a drop count change only adds or removes the
 * highest-numbered drops, and the same inputs always give the same picture.
 */
final class WeatherGeometry {
	private WeatherGeometry() {
	}

	/** Facts about the terrain columns (see {@link Columns}). */
	interface ColumnSource {
		/** Slot of the column, or -1 if it is not available (chunk not loaded). */
		int get(int x, int z);

		/** First free Y above the highest motion-blocking block. */
		int top(int slot);

		int light(int slot);

		/** {@link Columns#KIND_NONE} / RAIN / SNOW by biome. */
		byte kind(int slot);

		/** {@link Columns#MAT_NONE} / HARD / NATURAL / WATER. */
		byte mat(int slot);

		/** Y of the surface the sheen sits on. */
		float surface(int slot);
	}

	/** Receives vertices, four per quad, in order. */
	interface QuadSink {
		void vertex(float x, float y, float z, float u, float v, int argb, int light);
	}

	/** Settings of the rain streaks. {@code biome}: only draw over columns whose biome rains. */
	record Rain(float radius, float amount, float density, float quality, float opacity, int rgb, float speed, float windX, float windZ,
			float windStrength, float length, float size, int maxDrops, boolean biome) {
	}

	record Snow(float radius, float amount, float density, float quality, float opacity, int rgb, float speed, float windX, float windZ,
			float windStrength, float sway, float size, int maxFlakes, boolean biome) {
	}

	record Ground(float range, float wet, float gloss, float naturalMul, float threshold, int rgb, boolean biome) {
	}

	record Ripples(float range, float chance, float speed, float size, float wet, float threshold, int rgb, boolean biome) {
	}

	record Mist(float radius, float height, float density, float wisps, float quality, int maxPuffs, float driftX, float driftZ, int rgb) {
	}

	private static double wrap(double v, double origin, double span) {
		return origin + WeatherMath.mod(v - origin, span);
	}

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : Math.min(v, hi);
	}

	// ------------------------------------------------------------------ rain

	/** Returns the number of streaks considered. */
	static int rain(ColumnSource cols, QuadSink out, Rain s, double camX, double camY, double camZ, double t) {
		float radius = s.radius();
		float span = radius * 2f;
		float height = Math.max(span, 36f);
		double wanted = span * span * 1.5 * s.quality() * s.density() * s.amount();
		int n = (int) Math.min(s.maxDrops(), Math.ceil(wanted));
		if (n <= 0) {
			return 0;
		}
		float opacity = s.opacity() * clamp(0.4f + s.amount() * 0.75f, 0f, 1f);
		float fall = 21f * s.speed();
		float tilt = s.windStrength() * 0.85f;
		float vx = s.windX() * tilt * fall;
		float vz = s.windZ() * tilt * fall;
		float vy = -fall;
		float vlen = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
		float dx = vx / vlen, dy = vy / vlen, dz = vz / vlen; // unit fall direction
		float len0 = 1.5f * s.length() * clamp(0.75f + s.speed() * 0.25f, 0.8f, 1.6f);
		float hw0 = 0.03f * s.size();
		double ox = camX - radius;
		double oz = camZ - radius;
		double oy = camY - height * 0.4;

		for (int i = 0; i < n; i++) {
			float present = (float) Math.min(1.0, wanted - i);
			float sj = 0.88f + 0.24f * WeatherMath.rand(i, 4);
			double px = wrap(WeatherMath.rand(i, 1) * span + vx * sj * t, ox, span);
			double pz = wrap(WeatherMath.rand(i, 2) * span + vz * sj * t, oz, span);
			double py = wrap(WeatherMath.rand(i, 3) * height + vy * sj * t, oy, height);
			float rx = (float) (px - camX);
			float rz = (float) (pz - camZ);
			float hd = (float) Math.sqrt(rx * rx + rz * rz);
			if (hd > radius) {
				continue;
			}
			int slot = cols.get((int) Math.floor(px), (int) Math.floor(pz));
			if (slot < 0) {
				continue;
			}
			if (s.biome() && cols.kind(slot) != Columns.KIND_RAIN) {
				continue;
			}
			float top = cols.top(slot);
			float ly = (float) py; // lower end of the streak
			float len = len0 * (0.8f + 0.4f * WeatherMath.rand(i, 5)) * sj;
			float uy = ly - dy * len; // upper end (dy < 0, so higher)
			if (uy <= top) {
				continue;
			}
			float fy = ly;
			float vBottom = 1f;
			float lx = rx, lz = rz;
			float ux = rx - dx * len, uz = rz - dz * len;
			if (ly < top) {
				float k = (top - ly) / (uy - ly);
				lx = WeatherMath.lerp(rx, ux, k);
				lz = WeatherMath.lerp(rz, uz, k);
				fy = top;
				vBottom = 1f - k;
			}
			float cy = (float) (fy - camY);
			float uyr = (float) (uy - camY);
			float mx = (lx + ux) * 0.5f, my = (cy + uyr) * 0.5f, mz = (lz + uz) * 0.5f;
			float dist = (float) Math.sqrt(mx * mx + my * my + mz * mz);
			float vf = (float) ((py - oy) / height);
			float fade = (1f - WeatherMath.smoothstep(radius * 0.55f, radius, hd))
					* WeatherMath.smoothstep(0f, 0.08f, vf) * (1f - WeatherMath.smoothstep(0.92f, 1f, vf))
					* WeatherMath.smoothstep(0.6f, 2.8f, dist) * present;
			if (fade <= 0.004f || dist < 1e-3f) {
				continue;
			}
			// Sideways axis: perpendicular to both the streak and the view ray.
			float vxn = mx / dist, vyn = my / dist, vzn = mz / dist;
			float wxs = dy * vzn - dz * vyn, wys = dz * vxn - dx * vzn, wzs = dx * vyn - dy * vxn;
			float wl = (float) Math.sqrt(wxs * wxs + wys * wys + wzs * wzs);
			if (wl < 1e-3f) {
				continue;
			}
			float hw = hw0 * (1f + dist * 0.035f) / wl;
			wxs *= hw;
			wys *= hw;
			wzs *= hw;
			int argb = WeatherMath.argb(Math.round(opacity * fade * 255f), s.rgb());
			int light = cols.light(slot);
			out.vertex(ux - wxs, uyr - wys, uz - wzs, 0f, 0f, argb, light);
			out.vertex(ux + wxs, uyr + wys, uz + wzs, 1f, 0f, argb, light);
			out.vertex(lx + wxs, cy + wys, lz + wzs, 1f, vBottom, argb, light);
			out.vertex(lx - wxs, cy - wys, lz - wzs, 0f, vBottom, argb, light);
		}
		return n;
	}

	// ------------------------------------------------------------------ snow

	/** {@code right} and {@code up} are the camera's axes (billboards face the camera). */
	static int snow(ColumnSource cols, QuadSink out, Snow s, float[] right, float[] up, double camX, double camY, double camZ, double t) {
		float radius = s.radius();
		float span = radius * 2f;
		float height = Math.max(span, 36f);
		double wanted = span * span * 1.6 * s.quality() * s.density() * s.amount();
		int n = (int) Math.min(s.maxFlakes(), Math.ceil(wanted));
		if (n <= 0) {
			return 0;
		}
		float opacity = s.opacity() * clamp(0.45f + s.amount() * 0.7f, 0f, 1f);
		float vx = s.windX() * s.windStrength() * 3.2f;
		float vz = s.windZ() * s.windStrength() * 3.2f;
		float sway = s.sway() * 0.5f;
		float size0 = 0.17f * s.size();
		double ox = camX - radius;
		double oz = camZ - radius;
		double oy = camY - height * 0.4;
		float rX = right[0], rY = right[1], rZ = right[2];
		float uX = up[0], uY = up[1], uZ = up[2];

		for (int i = 0; i < n; i++) {
			float present = (float) Math.min(1.0, wanted - i);
			float sj = 0.7f + 0.6f * WeatherMath.rand(i, 14);
			double fallV = -2.3f * s.speed() * sj;
			double px = wrap(WeatherMath.rand(i, 11) * span + vx * t, ox, span);
			double pz = wrap(WeatherMath.rand(i, 12) * span + vz * t, oz, span);
			double py = wrap(WeatherMath.rand(i, 13) * height + fallV * t, oy, height);
			float ph = WeatherMath.rand(i, 15) * 6.2832f;
			float fr = 0.6f + WeatherMath.rand(i, 16) * 1.1f;
			double wob = t * fr;
			float ex = (float) Math.sin(wob + ph) * sway;
			float ez = (float) Math.cos(wob * 0.83 + ph * 1.7) * sway;
			float rx = (float) (px - camX) + ex;
			float rz = (float) (pz - camZ) + ez;
			float hd = (float) Math.sqrt(rx * rx + rz * rz);
			if (hd > radius) {
				continue;
			}
			int slot = cols.get((int) Math.floor(px), (int) Math.floor(pz));
			if (slot < 0) {
				continue;
			}
			if (s.biome() && cols.kind(slot) != Columns.KIND_SNOW) {
				continue;
			}
			float top = cols.top(slot);
			float landing = WeatherMath.smoothstep(top, top + 0.6f, (float) py);
			if (landing <= 0f) {
				continue;
			}
			float ry = (float) (py - camY);
			float dist = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
			float vf = (float) ((py - oy) / height);
			float fade = (1f - WeatherMath.smoothstep(radius * 0.55f, radius, hd))
					* WeatherMath.smoothstep(0f, 0.08f, vf) * (1f - WeatherMath.smoothstep(0.92f, 1f, vf))
					* WeatherMath.smoothstep(0.5f, 2.2f, dist) * landing * present;
			if (fade <= 0.004f) {
				continue;
			}
			float half = size0 * (0.6f + 0.8f * WeatherMath.rand(i, 17)) * (1f + dist * 0.03f) * 0.5f;
			int argb = WeatherMath.argb(Math.round(opacity * fade * 255f), s.rgb());
			int light = cols.light(slot);
			float ax = rX * half, ay = rY * half, az = rZ * half;
			float bx = uX * half, by = uY * half, bz = uZ * half;
			out.vertex(rx - ax - bx, ry - ay - by, rz - az - bz, 0f, 1f, argb, light);
			out.vertex(rx + ax - bx, ry + ay - by, rz + az - bz, 1f, 1f, argb, light);
			out.vertex(rx + ax + bx, ry + ay + by, rz + az + bz, 1f, 0f, argb, light);
			out.vertex(rx - ax + bx, ry - ay + by, rz - az + bz, 0f, 0f, argb, light);
		}
		return n;
	}

	// ------------------------------------------------------------------ glossy ground

	private static final int PUDDLE_SEED = 7331;

	/** Puddle mask 0..1 at a world position (smooth, ~4 block features). */
	static float puddle(float x, float z, float threshold) {
		float n = WeatherMath.fbm(x * 0.21f, z * 0.21f, PUDDLE_SEED);
		return WeatherMath.smoothstep(threshold - 0.1f, threshold + 0.1f, n);
	}

	/** Threshold of the puddle noise for a "Puddles" coverage 0..1. */
	static float puddleThreshold(float coverage) {
		return 0.78f - 0.45f * coverage;
	}

	/** Wet sheen over the ground blocks around the camera. Returns the number of quads. */
	static int ground(ColumnSource cols, QuadSink out, Ground s, double camX, double camY, double camZ) {
		float range = s.range();
		int cx = (int) Math.floor(camX);
		int cz = (int) Math.floor(camZ);
		int r = (int) Math.ceil(range);
		float[] a = new float[4];
		int quads = 0;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				float qx = x + 0.5f - (float) camX;
				float qz = z + 0.5f - (float) camZ;
				float hd2 = qx * qx + qz * qz;
				if (hd2 > range * range) {
					continue;
				}
				int slot = cols.get(x, z);
				if (slot < 0) {
					continue;
				}
				byte mat = cols.mat(slot);
				if (mat != Columns.MAT_HARD && mat != Columns.MAT_NATURAL) {
					continue;
				}
				if (s.biome() && cols.kind(slot) == Columns.KIND_SNOW) {
					continue;
				}
				float matMul = mat == Columns.MAT_NATURAL ? s.naturalMul() : 1f;
				if (matMul <= 0.01f) {
					continue;
				}
				float y = cols.surface(slot);
				float sum = 0f;
				for (int c = 0; c < 4; c++) {
					float wx = x + (c & 1);
					float wz = z + (c >> 1);
					float rx = wx - (float) camX;
					float rz = wz - (float) camZ;
					float ry = y - (float) camY;
					float hd = (float) Math.sqrt(rx * rx + rz * rz);
					float dist = (float) Math.sqrt(hd * hd + ry * ry);
					float cosT = dist < 0.01f ? 1f : Math.abs(ry) / dist;
					float fres = 0.25f + 0.75f * (float) Math.pow(1f - cosT, 2.0);
					float level = s.wet() * s.gloss() * (0.38f + 0.62f * puddle(wx, wz, s.threshold()));
					float edge = 1f - WeatherMath.smoothstep(range * 0.55f, range, hd);
					a[c] = Math.min(0.7f, level * fres * 1.5f) * edge * matMul;
					sum += a[c];
				}
				if (sum < 0.02f) {
					continue;
				}
				float yy = y + 0.006f + 0.0005f * (float) Math.sqrt(hd2);
				float x0 = x - (float) camX, x1 = x0 + 1f;
				float z0 = z - (float) camZ, z1 = z0 + 1f;
				float y0 = yy - (float) camY;
				int light = cols.light(slot);
				out.vertex(x0, y0, z0, 0f, 0f, WeatherMath.argb(Math.round(a[0] * 255f), s.rgb()), light);
				out.vertex(x0, y0, z1, 0f, 1f, WeatherMath.argb(Math.round(a[2] * 255f), s.rgb()), light);
				out.vertex(x1, y0, z1, 1f, 1f, WeatherMath.argb(Math.round(a[3] * 255f), s.rgb()), light);
				out.vertex(x1, y0, z0, 1f, 0f, WeatherMath.argb(Math.round(a[1] * 255f), s.rgb()), light);
				quads++;
			}
		}
		return quads;
	}

	/** Ripple rings on puddles and water. Returns the number of rings. */
	static int ripples(ColumnSource cols, QuadSink out, Ripples s, double camX, double camY, double camZ, double t) {
		float range = s.range();
		int cx = (int) Math.floor(camX);
		int cz = (int) Math.floor(camZ);
		int r = (int) Math.ceil(range);
		int rings = 0;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				float hd = (float) Math.hypot(x + 0.5 - camX, z + 0.5 - camZ);
				if (hd > range) {
					continue;
				}
				int h0 = WeatherMath.hash(x, z, 991);
				if (WeatherMath.unit(h0) > s.chance()) {
					continue; // cheap early-out for most columns
				}
				int slot = cols.get(x, z);
				if (slot < 0) {
					continue;
				}
				byte mat = cols.mat(slot);
				if (mat == Columns.MAT_NONE) {
					continue;
				}
				if (s.biome() && cols.kind(slot) == Columns.KIND_SNOW) {
					continue;
				}
				float pud = mat == Columns.MAT_WATER ? 1f : puddle(x + 0.5f, z + 0.5f, s.threshold());
				if (pud < 0.3f) {
					continue;
				}
				float edge = 1f - WeatherMath.smoothstep(range * 0.6f, range, hd);
				for (int e = 0; e < 2; e++) {
					int h = WeatherMath.hash(h0, e, 17);
					if (e == 1 && WeatherMath.unit(WeatherMath.hash(h, 3)) > 0.4f) {
						continue;
					}
					float period = (0.9f + 0.9f * WeatherMath.unit(WeatherMath.hash(h, 4))) / s.speed();
					float phase = WeatherMath.unit(WeatherMath.hash(h, 5)) * period;
					float life = period * 0.8f;
					float local = (float) WeatherMath.mod(t + phase, period);
					if (local > life) {
						continue;
					}
					float u = local / life;
					float ease = 1f - (1f - u) * (1f - u);
					float rad = (0.06f + 0.42f * s.size() * ease) / 0.8f; // the ring sits at 0.8 of the half size
					float alpha = (1f - u) * (1f - u) * pud * edge * clamp(0.4f + s.wet() * 0.8f, 0f, 1f) * 1.0f;
					if (alpha < 0.02f) {
						continue;
					}
					float px = x + 0.18f + 0.64f * WeatherMath.unit(WeatherMath.hash(h, 6));
					float pz = z + 0.18f + 0.64f * WeatherMath.unit(WeatherMath.hash(h, 7));
					float y = cols.surface(slot) + 0.012f;
					float x0 = px - rad - (float) camX, x1 = px + rad - (float) camX;
					float z0 = pz - rad - (float) camZ, z1 = pz + rad - (float) camZ;
					float yy = y - (float) camY;
					int argb = WeatherMath.argb(Math.round(Math.min(1f, alpha) * 255f), s.rgb());
					int light = cols.light(slot);
					out.vertex(x0, yy, z0, 0f, 0f, argb, light);
					out.vertex(x0, yy, z1, 0f, 1f, argb, light);
					out.vertex(x1, yy, z1, 1f, 1f, argb, light);
					out.vertex(x1, yy, z0, 1f, 0f, argb, light);
					rings++;
				}
			}
		}
		return rings;
	}

	// ------------------------------------------------------------------ mist

	/** Upright, camera-facing mist clouds hugging the ground. Returns the number of puffs. */
	static int mist(ColumnSource cols, QuadSink out, Mist s, double camX, double camY, double camZ, double t) {
		int n = Math.round(s.maxPuffs() * s.quality() * s.wisps() * (0.4f + 0.6f * s.density()));
		if (n <= 0 || s.density() <= 0.01f) {
			return 0;
		}
		float radius = s.radius();
		float span = radius * 2f;
		float hgt = s.height();
		double ox = camX - radius;
		double oz = camZ - radius;
		int drawn = 0;
		for (int i = 0; i < n; i++) {
			double jitter = 0.7 + 0.6 * WeatherMath.rand(i, 22);
			double px = wrap(WeatherMath.rand(i, 21) * span + s.driftX() * t * jitter, ox, span);
			double pz = wrap(WeatherMath.rand(i, 23) * span + s.driftZ() * t * jitter, oz, span);
			float rx = (float) (px - camX);
			float rz = (float) (pz - camZ);
			float hd = (float) Math.sqrt(rx * rx + rz * rz);
			if (hd > radius) {
				continue;
			}
			int slot = cols.get((int) Math.floor(px), (int) Math.floor(pz));
			if (slot < 0) {
				continue;
			}
			float ground = cols.surface(slot);
			if (Math.abs(ground - camY) > 30.0) {
				continue;
			}
			float size = (4.5f + 5.5f * WeatherMath.rand(i, 24)) * (0.65f + hgt / 9f);
			float h = Math.max(1.8f, hgt * (0.75f + 0.5f * WeatherMath.rand(i, 25)));
			float bob = (float) Math.sin(t * 0.35 + WeatherMath.rand(i, 26) * 6.28) * 0.25f;
			float cy = ground + h * 0.4f + bob - (float) camY;
			float dist = (float) Math.sqrt(rx * rx + cy * cy + rz * rz);
			float near = WeatherMath.smoothstep(1.5f, 7f, dist);
			float edge = 1f - WeatherMath.smoothstep(radius * 0.5f, radius, hd);
			float alpha = 0.5f * s.density() * near * edge * (0.7f + 0.6f * WeatherMath.rand(i, 27));
			if (alpha < 0.004f) {
				continue;
			}
			// Upright billboard: sideways = perpendicular to the horizontal view direction.
			float hl = Math.max(0.001f, hd);
			float sx = rz / hl, sz = -rx / hl;
			float hw = size * 0.5f;
			float hh = h * 0.5f;
			int argb = WeatherMath.argb(Math.round(Math.min(1f, alpha) * 255f), s.rgb());
			int light = cols.light(slot);
			out.vertex(rx - sx * hw, cy - hh, rz - sz * hw, 0f, 1f, argb, light);
			out.vertex(rx + sx * hw, cy - hh, rz + sz * hw, 1f, 1f, argb, light);
			out.vertex(rx + sx * hw, cy + hh, rz + sz * hw, 1f, 0f, argb, light);
			out.vertex(rx - sx * hw, cy + hh, rz - sz * hw, 0f, 0f, argb, light);
			drawn++;
		}
		return drawn;
	}
}
