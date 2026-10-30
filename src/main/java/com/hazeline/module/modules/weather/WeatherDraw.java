package com.hazeline.module.modules.weather;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Everything the weather modules draw inside the world's weather pass (called from the mixin that
 * replaces {@code WeatherRendering#renderPrecipitation}):
 * <ul>
 *   <li><b>Rain streaks and snow flakes</b> (Weather): a fixed set of drops placed by hashing their index
 *       and wrapped around you, moved analytically from the game time (no per-drop state). A drop only
 *       exists above the first solid block of its column ({@link Columns}).</li>
 *   <li><b>Wet ground sheen and ripple rings</b> (Glossy Rain): flat quads just above the surface, whose
 *       per-corner alpha comes from smooth noise (puddles), the viewing angle and the distance.</li>
 *   <li><b>Mist wisps</b> (Mist): upright, camera-facing soft clouds that hug the ground.</li>
 *   <li><b>Lightning bolts</b> (Smooth Thunder): see {@link ThunderStrikes}.</li>
 * </ul>
 * Everything is camera-relative like vanilla's weather, uses vanilla's weather layers (lightmap + fog),
 * and any exception hands the weather back to vanilla (safe mode).
 */
public final class WeatherDraw {
	private WeatherDraw() {
	}

	/**
	 * Draws the weather features. Returns true if Hazeline replaced vanilla's rain / snow this frame
	 * (the caller then skips vanilla's drawing).
	 */
	public static boolean render(VertexConsumerProvider provider, Vec3d cam) {
		if (WeatherController.isFailed()) {
			return false;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		if (w == null || w.getRegistryKey() != World.OVERWORLD) {
			return false;
		}
		boolean take = WeatherController.takeover();
		boolean glossy = GlossyRain.activeIn(w) && WeatherController.wetness(1f) > 0.01f;
		boolean mist = Mist.activeIn(w) && WeatherController.mistFade(1f) > 0.01f;
		boolean bolts = SmoothThunder.activeIn(w) && ThunderStrikes.any();
		if (!take && !glossy && !mist && !bolts) {
			return false;
		}
		try {
			if (!WeatherTextures.ensure()) {
				return false;
			}
			Columns.frame(w);
			float tp = mc.getRenderTickCounter().getTickProgress(false);
			double t = (w.getTime() + (double) tp) / 20.0;
			Camera camera = mc.gameRenderer.getCamera();
			Frame f = new Frame(w, provider, cam, camera, tp, t);
			if (take) {
				float amount = WeatherController.precip(tp);
				if (amount > 0.004f) {
					WeatherController.Mode mode = WeatherController.mode();
					Weather wx = Weather.get();
					if (mode != WeatherController.Mode.SNOW) {
						drawRain(f, wx, amount, mode);
					}
					if (mode != WeatherController.Mode.RAIN) {
						drawSnow(f, wx, amount, mode);
					}
				}
			}
			if (glossy) {
				drawGround(f, GlossyRain.get());
			}
			if (mist) {
				drawMist(f, Mist.get());
			}
			if (bolts) {
				ThunderStrikes.drawBolts(provider, cam, tp);
			}
		} catch (Throwable e) {
			WeatherController.fail("drawing", e);
			return false;
		}
		return take;
	}

	/** Per-frame values shared by the draw routines. */
	private static final class Frame {
		final ClientWorld world;
		final VertexConsumerProvider provider;
		final Vec3d cam;
		final float tp;
		final double t;
		final Vector3f right = new Vector3f(1, 0, 0);
		final Vector3f up = new Vector3f(0, 1, 0);
		final WeatherGeometry.ColumnSource columns;

		Frame(ClientWorld world, VertexConsumerProvider provider, Vec3d cam, Camera camera, float tp, double t) {
			this.world = world;
			this.provider = provider;
			this.cam = cam;
			this.tp = tp;
			this.t = t;
			this.columns = Columns.source(world);
			Quaternionf q = camera.getRotation();
			right.rotate(q);
			up.rotate(q);
		}

		VertexConsumer buffer(Identifier texture) {
			RenderLayer layer = RenderLayers.weather(texture, MinecraftClient.usesImprovedTransparency());
			return provider.getBuffer(layer);
		}
	}

	private static void vtx(VertexConsumer vc, float x, float y, float z, float u, float v, int argb, int light) {
		vc.vertex(x, y, z).texture(u, v).color(argb).light(light);
	}


	// ------------------------------------------------------------------ drawing (geometry lives in WeatherGeometry)

	private static void drawRain(Frame f, Weather wx, float amount, WeatherController.Mode mode) {
		var s = new WeatherGeometry.Rain(wx.range.getFloat(), amount, wx.density.getFloat() / 100f, wx.quality.get().scale,
				wx.rainOpacity.getFloat() / 100f, wx.rainColor.get() & 0xFFFFFF, wx.speed.getFloat(), wx.windX(), wx.windZ(),
				wx.windStrength.getFloat() / 100f, wx.rainLength.getFloat(), wx.rainSize.getFloat(), Weather.MAX_RAIN,
				mode == WeatherController.Mode.BIOME);
		VertexConsumer vc = f.buffer(WeatherTextures.RAIN);
		WeatherGeometry.rain(f.columns, (x, y, z, u, v, argb, light) -> vtx(vc, x, y, z, u, v, argb, light), s, f.cam.x, f.cam.y, f.cam.z, f.t);
	}

	private static void drawSnow(Frame f, Weather wx, float amount, WeatherController.Mode mode) {
		var s = new WeatherGeometry.Snow(wx.range.getFloat(), amount, wx.density.getFloat() / 100f, wx.quality.get().scale,
				wx.snowOpacity.getFloat() / 100f, wx.snowColor.get() & 0xFFFFFF, wx.speed.getFloat(), wx.windX(), wx.windZ(),
				wx.windStrength.getFloat() / 100f, wx.snowDrift.getFloat() / 100f, wx.snowSize.getFloat(), Weather.MAX_SNOW,
				mode == WeatherController.Mode.BIOME);
		VertexConsumer vc = f.buffer(WeatherTextures.SNOW);
		WeatherGeometry.snow(f.columns, (x, y, z, u, v, argb, light) -> vtx(vc, x, y, z, u, v, argb, light), s,
				new float[] {f.right.x, f.right.y, f.right.z}, new float[] {f.up.x, f.up.y, f.up.z}, f.cam.x, f.cam.y, f.cam.z, f.t);
	}

	private static void drawGround(Frame f, GlossyRain g) {
		float wet = WeatherController.wetness(f.tp);
		if (wet <= 0.01f) {
			return;
		}
		boolean biome = WeatherController.mode() == WeatherController.Mode.BIOME;
		float threshold = WeatherGeometry.puddleThreshold(g.puddles.getFloat() / 100f);
		int rgb = WeatherMath.mix(g.sheenColor.get() | 0xFF000000, WeatherController.skyRgb() | 0xFF000000, g.skyTint.getFloat() / 100f) & 0xFFFFFF;
		var ground = new WeatherGeometry.Ground(g.range.getFloat(), wet, g.glossiness.getFloat() / 100f, g.natural.getFloat() / 100f,
				threshold, rgb, biome);
		VertexConsumer vc = f.buffer(WeatherTextures.SHEEN);
		WeatherGeometry.ground(f.columns, (x, y, z, u, v, argb, light) -> vtx(vc, x, y, z, u, v, argb, light), ground, f.cam.x, f.cam.y, f.cam.z);
		float rain = WeatherController.rainAmount(f.tp);
		if (g.ripples.get() && rain >= 0.05f) {
			float chance = g.rippleAmount.getFloat() / 100f * g.quality.get().scale * MathHelper.clamp(rain * 1.1f, 0f, 1f) * 0.22f;
			var rip = new WeatherGeometry.Ripples(Math.min(g.range.getFloat(), 22f), chance, g.rippleSpeed.getFloat(), g.rippleSize.getFloat(),
					wet, threshold, rgb, biome);
			VertexConsumer rc = f.buffer(WeatherTextures.RING);
			WeatherGeometry.ripples(f.columns, (x, y, z, u, v, argb, light) -> vtx(rc, x, y, z, u, v, argb, light), rip, f.cam.x, f.cam.y, f.cam.z, f.t);
		}
	}

	/** Mist colour for this frame: the setting, blended towards the sky, dimmed at night, warmed at dawn and dusk. */
	static int mistRgb(Mist m, float tp) {
		int c = m.color.get() & 0xFFFFFF;
		if (m.matchSky.get()) {
			int sky = WeatherController.skyRgb();
			int r = Math.min(255, Math.round(((sky >> 16) & 0xFF) * 1.12f));
			int g = Math.min(255, Math.round(((sky >> 8) & 0xFF) * 1.12f));
			int b = Math.min(255, Math.round((sky & 0xFF) * 1.12f));
			c = WeatherMath.mix(0xFF000000 | c, 0xFF000000 | (r << 16) | (g << 8) | b, 0.55f) & 0xFFFFFF;
		}
		if (m.timeTint.get()) {
			float day = WeatherController.daylight(tp);
			float tw = WeatherController.twilight(tp) * day;
			c = WeatherMath.mix(0xFF000000 | c, 0xFFFFC496, 0.38f * tw) & 0xFFFFFF;
			float dim = 0.38f + 0.62f * day;
			c = WeatherMath.scaleRgb(0xFF000000 | c, dim) & 0xFFFFFF;
		}
		return c;
	}

	private static void drawMist(Frame f, Mist m) {
		float dens = m.density.getFloat() / 100f * WeatherController.mistFade(f.tp);
		float ang = (float) Math.toRadians(m.driftAngle.getFloat());
		float drift = m.drift.getFloat();
		var s = new WeatherGeometry.Mist(m.range.getFloat(), m.height.getFloat(), dens, m.wisps.getFloat() / 100f, m.quality.get().scale,
				Mist.MAX_PUFFS, (float) Math.sin(ang) * drift, (float) -Math.cos(ang) * drift, mistRgb(m, f.tp));
		VertexConsumer vc = f.buffer(WeatherTextures.PUFF);
		WeatherGeometry.mist(f.columns, (x, y, z, u, v, argb, light) -> vtx(vc, x, y, z, u, v, argb, light), s, f.cam.x, f.cam.y, f.cam.z, f.t);
	}
}
