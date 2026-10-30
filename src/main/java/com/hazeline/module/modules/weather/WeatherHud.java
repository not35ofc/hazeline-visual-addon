package com.hazeline.module.modules.weather;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import org.joml.Matrix3x2fStack;

import com.hazeline.Hazeline;
import com.hazeline.render.ScreenProjector;

/**
 * Screen-space parts of the weather modules, drawn under the normal HUD: the soft sky flash, the
 * glow in the clouds and the bright screen edges of Smooth Thunder, and the sun glow with light rays
 * of Mist. A flat colour wash cannot tell sky from ground, so the flash is strongest at the top and
 * lighter towards the bottom.
 */
public final class WeatherHud {
	/** Smoothed "is the sun visible from here" (0..1) for the light shafts. */
	private static float sunVisible;

	private WeatherHud() {
	}

	public static void registerEvents() {
		HudElementRegistry.addFirst(Hazeline.id("weather_overlay"), (g, tick) -> {
			if (WeatherController.isFailed()) {
				return;
			}
			MinecraftClient mc = MinecraftClient.getInstance();
			ClientWorld w = mc.world;
			if (w == null || w.getRegistryKey() != World.OVERWORLD) {
				return;
			}
			try {
				float tp = tick.getTickProgress(false);
				if (SmoothThunder.activeIn(w)) {
					thunder(g, tp);
				}
				if (Mist.activeIn(w) || WeatherController.mistFade(tp) > 0.01f) {
					shafts(g, mc, w, tp);
				}
			} catch (Throwable t) {
				WeatherController.fail("overlay", t);
			}
		});
	}

	// ------------------------------------------------------------------ thunder

	private static void thunder(DrawContext g, float tp) {
		SmoothThunder st = SmoothThunder.get();
		if (!ThunderStrikes.any() || !WeatherTextures.ensure()) {
			return;
		}
		int sw = g.getScaledWindowWidth();
		int sh = g.getScaledWindowHeight();
		int flash = st.flashColor.get() & 0xFFFFFF;
		float sky = ThunderStrikes.skyFlash(tp);
		if (sky > 0.004f) {
			int top = WeatherMath.argb(Math.round(MathHelper.clamp(sky * 0.9f, 0f, 0.9f) * 255f), flash);
			int bottom = WeatherMath.argb(Math.round(MathHelper.clamp(sky * 0.42f, 0f, 0.6f) * 255f), flash);
			g.fillGradient(0, 0, sw, sh, top, bottom);
		}
		// Glow in the clouds where each live bolt is.
		List<float[]> glows = new ArrayList<>();
		ThunderStrikes.glows(tp, glows);
		if (!glows.isEmpty()) {
			ScreenProjector proj = ScreenProjector.create(g);
			if (proj != null) {
				int color = st.boltColor.get() & 0xFFFFFF;
				for (float[] glow : glows) {
					float[] p = proj.project(glow[0], glow[1], glow[2]);
					if (p == null) {
						continue;
					}
					float dist = Math.max(40f, glow[4]);
					float radius = sh * (0.22f + 0.5f * MathHelper.clamp(120f / dist, 0f, 1f));
					float off = 1f - WeatherMath.smoothstep(1.1f, 2.2f, Math.max(Math.abs(p[2]), Math.abs(p[3])));
					int alpha = Math.round(MathHelper.clamp(glow[3] * 0.85f * off, 0f, 1f) * 255f);
					if (alpha > 2) {
						sprite(g, WeatherTextures.GLOW, WeatherTextures.GLOW_S, p[0], p[1], radius, radius * 0.75f, WeatherMath.argb(alpha, color));
					}
				}
			}
		}
		float edge = ThunderStrikes.edgeFlash(tp);
		if (edge > 0.01f) {
			int alpha = Math.round(MathHelper.clamp(edge, 0f, 1f) * 255f);
			g.drawTexture(RenderPipelines.GUI_TEXTURED, WeatherTextures.VIGNETTE, 0, 0, 0f, 0f, sw, sh, WeatherTextures.VIGNETTE_S,
					WeatherTextures.VIGNETTE_S, WeatherMath.argb(alpha, flash));
		}
	}

	// ------------------------------------------------------------------ mist light shafts

	private static void shafts(DrawContext g, MinecraftClient mc, ClientWorld w, float tp) {
		Mist m = Mist.get();
		if (m == null || !m.shafts.get() || !WeatherTextures.ensure()) {
			return;
		}
		float strength = m.shaftStrength.getFloat() / 100f * WeatherController.mistFade(tp) * (0.35f + 0.65f * m.density.getFloat() / 100f);
		float[] sun = WeatherController.sunDirection(tp);
		if (strength <= 0.01f || sun == null) {
			return;
		}
		float above = WeatherMath.smoothstep(-0.04f, 0.16f, sun[1]);
		if (above <= 0.01f) {
			return;
		}
		// Is the sun blocked by terrain? One ray, smoothed over frames.
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		Vec3d end = cam.add(sun[0] * 120.0, sun[1] * 120.0, sun[2] * 120.0);
		HitResult hit = w.raycast(new RaycastContext(cam, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
				mc.getCameraEntity()));
		float target = hit.getType() == HitResult.Type.MISS ? 1f : 0f;
		sunVisible += (target - sunVisible) * 0.08f;
		float rainDim = 1f - 0.8f * WeatherController.precip(tp);
		float visibility = strength * above * sunVisible * rainDim;
		if (visibility <= 0.01f) {
			return;
		}
		ScreenProjector proj = ScreenProjector.create(g);
		if (proj == null) {
			return;
		}
		float[] p = proj.project(cam.x + sun[0] * 200.0, cam.y + sun[1] * 200.0, cam.z + sun[2] * 200.0);
		if (p == null) {
			return;
		}
		float off = 1f - WeatherMath.smoothstep(1.0f, 2.2f, Math.max(Math.abs(p[2]), Math.abs(p[3])));
		if (off <= 0.01f) {
			return;
		}
		int sh = g.getScaledWindowHeight();
		int warm = WeatherMath.mix(0xFFFFF4DC, 0xFFFFC890, WeatherController.twilight(tp)) & 0xFFFFFF;
		float a = visibility * off;
		sprite(g, WeatherTextures.GLOW, WeatherTextures.GLOW_S, p[0], p[1], sh * 0.7f, sh * 0.7f, WeatherMath.argb(Math.round(MathHelper.clamp(a * 0.42f, 0f, 1f) * 255f), warm));
		float spin = (float) (Math.sin(System.nanoTime() * 1e-10) * 0.05);
		spriteRotated(g, WeatherTextures.RAYS, WeatherTextures.RAYS_S, p[0], p[1], sh * 1.25f, spin,
				WeatherMath.argb(Math.round(MathHelper.clamp(a * 0.34f, 0f, 1f) * 255f), warm));
	}

	// ------------------------------------------------------------------ sprite helpers

	static void sprite(DrawContext g, Identifier tex, int size, float cx, float cy, float rw, float rh, int color) {
		Matrix3x2fStack pose = g.getMatrices();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(rw * 2f / size, rh * 2f / size);
		int half = size / 2;
		g.drawTexture(RenderPipelines.GUI_TEXTURED, tex, -half, -half, 0f, 0f, size, size, size, size, color);
		pose.popMatrix();
	}

	static void spriteRotated(DrawContext g, Identifier tex, int size, float cx, float cy, float radius, float angle, int color) {
		Matrix3x2fStack pose = g.getMatrices();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.rotate(angle);
		float s = radius * 2f / size;
		pose.scale(s, s);
		int half = size / 2;
		g.drawTexture(RenderPipelines.GUI_TEXTURED, tex, -half, -half, 0f, 0f, size, size, size, size, color);
		pose.popMatrix();
	}
}
