package com.hazeline.module.modules.render;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import com.hazeline.gui.Colors;

/**
 * Geometry, colours and fade tracking for {@link HitBoxes}. Each tracked entity keeps its last box so
 * it can fade out in place after it died / left. Everything else is rebuilt every frame.
 */
final class HitboxRenderer {
	/** Longest line piece when colours change along an edge (smooth gradients / waves). */
	private static final double SEGMENT = 0.125;
	/** Fill faces are pushed out by this much so they do not z-fight with the entity's own faces. */
	private static final double FILL_INFLATE = 0.002;
	private static final double CYCLES_PER_SECOND = 0.25;
	private static final long START = System.nanoTime();

	/** One entity on screen (or fading out). Coordinates are world coordinates. */
	private static final class Track {
		Entity entity;
		double x0, y0, z0, x1, y1, z1;
		boolean hasLook;
		double ex, ey, ez, dx, dy, dz;
		float hurt;
		/** Fade progress 0..1 (linear; eased when drawn). */
		float fade;
		boolean target;
	}

	private static final Map<Integer, Track> TRACKS = new HashMap<>();
	private static final Map<EntityType<?>, Boolean> TYPE_CACHE = new IdentityHashMap<>();
	private static Set<String> cachedFor;
	private static ClientWorld lastWorld;
	private static long lastFrame;

	private HitboxRenderer() {
	}

	static boolean hasTracks() {
		return !TRACKS.isEmpty();
	}

	static void clear() {
		TRACKS.clear();
	}

	/** Per-frame colour values shared by every vertex. */
	private record Frame(HitBoxes m, int[] set, boolean rainbow, float sat, float bri, double time, boolean spatial, double spread,
			float hurtStrength, int hurtColor) {
	}

	static void render(HitBoxes m, VertexConsumerProvider.Immediate consumers, WorldRenderState worldState) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld world = mc.world;
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0f : (float) Math.min(0.1, Math.max(0, (now - lastFrame) / 1.0e9));
		lastFrame = now;
		if (world == null || mc.player == null || worldState.cameraRenderState == null) {
			TRACKS.clear();
			lastWorld = null;
			return;
		}
		if (world != lastWorld) {
			TRACKS.clear(); // other world / dimension: entity ids mean something else now
			lastWorld = world;
		}
		Vec3d cam = worldState.cameraRenderState.pos;
		boolean enabled = m.isEnabled();
		updateTracks(m, mc, world, cam, enabled);
		advanceFades(m, dt);
		if (TRACKS.isEmpty()) {
			return;
		}

		BlockOutlines.Animation anim = m.animation.get();
		boolean multi = m.multiColor();
		double seconds = (now - START) / 1.0e9;
		double time = multi && anim != BlockOutlines.Animation.STATIC
				? seconds * m.speed.get() * CYCLES_PER_SECOND * (m.reverse.get() ? -1 : 1) : 0;
		Frame f = new Frame(m, m.colourSet(), m.palette.get() == OutlinePalette.RAINBOW, m.rainbowSaturation.getFloat() / 100f,
				m.rainbowBrightness.getFloat() / 100f, time, multi && anim != BlockOutlines.Animation.FADE, m.spread.get(),
				m.hurtFlash.get() ? m.hurtStrength.getFloat() / 100f : 0f, m.hurtColor.get());

		MatrixStack.Entry entry = new MatrixStack().peek();
		// Fill first, lines on top (Immediate#getBuffer draws the previous layer when the layer changes).
		if (m.mode.get().fill()) {
			float base = m.fillOpacity.getFloat() / 100f;
			VertexConsumer quads = consumers.getBuffer(OutlinePipelines.fill(false));
			for (Track t : TRACKS.values()) {
				float a = base * eased(t.fade);
				if (a > 0.002f) {
					fill(quads, entry, f, t, a, cam);
				}
			}
		}
		boolean outline = m.mode.get().outline();
		boolean look = m.lookLine.get();
		if (outline || look) {
			float width = mc.getWindow().getMinimumLineWidth() * m.lineWidth.getFloat();
			float base = m.outlineOpacity.getFloat() / 100f;
			VertexConsumer lines = consumers.getBuffer(OutlinePipelines.lines(false));
			for (Track t : TRACKS.values()) {
				float fade = eased(t.fade);
				if (fade <= 0.002f) {
					continue;
				}
				if (outline) {
					boxEdges(lines, entry, f, t, base * fade, width, cam);
				}
				if (look && t.hasLook) {
					lookLine(lines, entry, f, t, m, fade, width, cam);
				}
			}
		}
		consumers.drawCurrentLayer();
	}

	// ------------------------------------------------------------------ tracking

	private static void updateTracks(HitBoxes m, MinecraftClient mc, ClientWorld world, Vec3d cam, boolean enabled) {
		for (Track t : TRACKS.values()) {
			t.target = false;
		}
		if (enabled) {
			Set<String> ids = m.entities.get();
			if (ids != cachedFor) {
				TYPE_CACHE.clear();
				cachedFor = ids;
			}
			double range = m.range.get();
			double rangeSq = range * range;
			Camera camera = mc.gameRenderer.getCamera();
			Entity self = mc.getCameraEntity();
			for (Entity e : world.getEntities()) {
				if (!TYPE_CACHE.computeIfAbsent(e.getType(), type -> ids.contains(EntityType.getId(type).toString()))) {
					continue;
				}
				if (e == self && (!m.showSelf.get() || !camera.isThirdPerson())) {
					continue;
				}
				if (e.isRemoved() || e.isInvisibleTo(mc.player) || e.squaredDistanceTo(cam) > rangeSq) {
					continue;
				}
				Track t = TRACKS.computeIfAbsent(e.getId(), id -> new Track());
				t.entity = e;
				t.target = true;
			}
		}
		// Refresh geometry from live entities (also the ones fading out), drop entities that left.
		for (Track t : TRACKS.values()) {
			Entity e = t.entity;
			if (e == null) {
				continue;
			}
			if (e.isRemoved() || world.getEntityById(e.getId()) != e) {
				t.entity = null; // keep the last box and fade out in place
				t.target = false;
				continue;
			}
			float td = mc.getRenderTickCounter().getTickProgress(!world.getTickManager().shouldSkipTick(e));
			Vec3d lerped = e.getLerpedPos(td);
			Vec3d pos = e.getEntityPos();
			Box box = e.getBoundingBox();
			double ox = lerped.x - pos.x, oy = lerped.y - pos.y, oz = lerped.z - pos.z;
			t.x0 = box.minX + ox;
			t.y0 = box.minY + oy;
			t.z0 = box.minZ + oz;
			t.x1 = box.maxX + ox;
			t.y1 = box.maxY + oy;
			t.z1 = box.maxZ + oz;
			Vec3d eye = e.getCameraPosVec(td);
			Vec3d dir = e.getRotationVec(td);
			t.hasLook = true;
			t.ex = eye.x;
			t.ey = eye.y;
			t.ez = eye.z;
			t.dx = dir.x;
			t.dy = dir.y;
			t.dz = dir.z;
			t.hurt = 0f;
			if (e instanceof LivingEntity living && living.hurtTime > 0 && living.maxHurtTime > 0) {
				t.hurt = clamp01((living.hurtTime - td) / living.maxHurtTime);
			}
		}
	}

	private static void advanceFades(HitBoxes m, float dt) {
		boolean smooth = m.smoothFade.get();
		float step = smooth ? dt / Math.max(0.01f, m.fadeTime.getFloat()) : 1f;
		Iterator<Track> it = TRACKS.values().iterator();
		while (it.hasNext()) {
			Track t = it.next();
			if (t.target) {
				t.fade = Math.min(1f, t.fade + step);
			} else {
				t.fade = Math.max(0f, t.fade - step);
				if (t.fade <= 0f) {
					it.remove();
				}
			}
		}
	}

	// ------------------------------------------------------------------ colours

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	private static float eased(float t) {
		return t * t * (3 - 2 * t);
	}

	/** Colour at world position (x, y, z) of track t. */
	private static int colorAt(Frame f, Track t, double x, double y, double z, float alpha) {
		double tt = f.time;
		if (f.spatial) {
			double cx = (t.x0 + t.x1) / 2, cy = (t.y0 + t.y1) / 2, cz = (t.z0 + t.z1) / 2;
			double half = Math.max(0.05, (t.y1 - t.y0) / 2);
			double around = Math.atan2(z - cz, x - cx) / (2 * Math.PI) + 0.5;
			double height = (y - cy) / half * 0.12;
			tt += (around + height) * f.spread;
		}
		int c = sample(f, tt);
		if (t.hurt > 0 && f.hurtStrength > 0) {
			c = Colors.lerp(c, f.hurtColor, eased(t.hurt) * f.hurtStrength);
		}
		return Colors.withAlpha(c, Math.round(Colors.alpha(c) * alpha));
	}

	/** Colour at cycle position t (wraps every 1.0) - the same blend as Custom Block Outlines. */
	private static int sample(Frame f, double t) {
		double u = t - Math.floor(t);
		if (f.rainbow) {
			return Colors.fromHsv((float) u, f.sat, f.bri, 255);
		}
		int[] set = f.set;
		if (set.length == 1) {
			return set[0];
		}
		double s = u * set.length;
		int i = (int) Math.floor(s) % set.length;
		float k = (float) (s - Math.floor(s));
		return Colors.lerp(set[i], set[(i + 1) % set.length], k);
	}

	// ------------------------------------------------------------------ geometry

	private static void boxEdges(VertexConsumer vc, MatrixStack.Entry entry, Frame f, Track t, float alpha, float width, Vec3d cam) {
		double x0 = t.x0, y0 = t.y0, z0 = t.z0, x1 = t.x1, y1 = t.y1, z1 = t.z1;
		// bottom, top, verticals
		edge(vc, entry, f, t, x0, y0, z0, x1, y0, z0, alpha, width, cam);
		edge(vc, entry, f, t, x1, y0, z0, x1, y0, z1, alpha, width, cam);
		edge(vc, entry, f, t, x1, y0, z1, x0, y0, z1, alpha, width, cam);
		edge(vc, entry, f, t, x0, y0, z1, x0, y0, z0, alpha, width, cam);
		edge(vc, entry, f, t, x0, y1, z0, x1, y1, z0, alpha, width, cam);
		edge(vc, entry, f, t, x1, y1, z0, x1, y1, z1, alpha, width, cam);
		edge(vc, entry, f, t, x1, y1, z1, x0, y1, z1, alpha, width, cam);
		edge(vc, entry, f, t, x0, y1, z1, x0, y1, z0, alpha, width, cam);
		edge(vc, entry, f, t, x0, y0, z0, x0, y1, z0, alpha, width, cam);
		edge(vc, entry, f, t, x1, y0, z0, x1, y1, z0, alpha, width, cam);
		edge(vc, entry, f, t, x1, y0, z1, x1, y1, z1, alpha, width, cam);
		edge(vc, entry, f, t, x0, y0, z1, x0, y1, z1, alpha, width, cam);
	}

	private static void edge(VertexConsumer vc, MatrixStack.Entry entry, Frame f, Track t, double x1, double y1, double z1, double x2,
			double y2, double z2, float alpha, float width, Vec3d cam) {
		double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0e-6) {
			return;
		}
		float nx = (float) (dx / len), ny = (float) (dy / len), nz = (float) (dz / len);
		boolean varies = f.spatial || t.hurt > 0;
		int pieces = f.spatial ? Math.max(1, Math.min(32, (int) Math.ceil(len / SEGMENT))) : 1;
		for (int i = 0; i < pieces; i++) {
			double a = i / (double) pieces, b = (i + 1) / (double) pieces;
			double ax = x1 + dx * a, ay = y1 + dy * a, az = z1 + dz * a;
			double bx = x1 + dx * b, by = y1 + dy * b, bz = z1 + dz * b;
			int ca = colorAt(f, t, ax, ay, az, alpha);
			int cb = varies ? colorAt(f, t, bx, by, bz, alpha) : ca;
			vc.vertex(entry, (float) (ax - cam.x), (float) (ay - cam.y), (float) (az - cam.z)).color(ca).normal(entry, nx, ny, nz)
					.lineWidth(width);
			vc.vertex(entry, (float) (bx - cam.x), (float) (by - cam.y), (float) (bz - cam.z)).color(cb).normal(entry, nx, ny, nz)
					.lineWidth(width);
		}
	}

	private static void lookLine(VertexConsumer vc, MatrixStack.Entry entry, Frame f, Track t, HitBoxes m, float fade, float width,
			Vec3d cam) {
		double len = m.lookLength.get();
		double bx = t.ex + t.dx * len, by = t.ey + t.dy * len, bz = t.ez + t.dz * len;
		float alpha = m.outlineOpacity.getFloat() / 100f * fade;
		if (m.lookUsesBoxColour.get()) {
			edge(vc, entry, f, t, t.ex, t.ey, t.ez, bx, by, bz, alpha, width, cam);
			return;
		}
		int c = m.lookColor.get();
		c = Colors.withAlpha(c, Math.round(Colors.alpha(c) * alpha));
		float nx = (float) t.dx, ny = (float) t.dy, nz = (float) t.dz;
		vc.vertex(entry, (float) (t.ex - cam.x), (float) (t.ey - cam.y), (float) (t.ez - cam.z)).color(c).normal(entry, nx, ny, nz)
				.lineWidth(width);
		vc.vertex(entry, (float) (bx - cam.x), (float) (by - cam.y), (float) (bz - cam.z)).color(c).normal(entry, nx, ny, nz)
				.lineWidth(width);
	}

	private static void fill(VertexConsumer vc, MatrixStack.Entry entry, Frame f, Track t, float alpha, Vec3d cam) {
		double e = FILL_INFLATE;
		double x0 = t.x0 - e, y0 = t.y0 - e, z0 = t.z0 - e, x1 = t.x1 + e, y1 = t.y1 + e, z1 = t.z1 + e;
		quad(vc, entry, f, t, alpha, cam, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1); // -Y
		quad(vc, entry, f, t, alpha, cam, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0); // +Y
		quad(vc, entry, f, t, alpha, cam, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0); // -Z
		quad(vc, entry, f, t, alpha, cam, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1); // +Z
		quad(vc, entry, f, t, alpha, cam, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0); // -X
		quad(vc, entry, f, t, alpha, cam, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1); // +X
	}

	private static void quad(VertexConsumer vc, MatrixStack.Entry entry, Frame f, Track t, float alpha, Vec3d cam, double ax, double ay,
			double az, double bx, double by, double bz, double cx, double cy, double cz, double dx, double dy, double dz) {
		vc.vertex(entry, (float) (ax - cam.x), (float) (ay - cam.y), (float) (az - cam.z)).color(colorAt(f, t, ax, ay, az, alpha));
		vc.vertex(entry, (float) (bx - cam.x), (float) (by - cam.y), (float) (bz - cam.z)).color(colorAt(f, t, bx, by, bz, alpha));
		vc.vertex(entry, (float) (cx - cam.x), (float) (cy - cam.y), (float) (cz - cam.z)).color(colorAt(f, t, cx, cy, cz, alpha));
		vc.vertex(entry, (float) (dx - cam.x), (float) (dy - cam.y), (float) (dz - cam.z)).color(colorAt(f, t, dx, dy, dz, alpha));
	}
}
