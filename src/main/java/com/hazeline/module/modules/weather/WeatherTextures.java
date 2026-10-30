package com.hazeline.module.modules.weather;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import com.hazeline.Hazeline;

/**
 * The small generated textures the weather modules draw with (rain streak, snow flake, ripple ring,
 * mist puff, ground sheen, screen vignette, light rays). Every image is white with an alpha shape, so
 * the vertex colour tints it; they are created in code (no files in the jar) and registered with the
 * TextureManager on first use. The pixel generators are plain {@code int[]} functions so they can be
 * previewed offline.
 */
public final class WeatherTextures {
	public static final Identifier RAIN = Hazeline.id("weather/rain");
	public static final Identifier SNOW = Hazeline.id("weather/snow");
	public static final Identifier RING = Hazeline.id("weather/ring");
	public static final Identifier PUFF = Hazeline.id("weather/puff");
	public static final Identifier SHEEN = Hazeline.id("weather/sheen");
	public static final Identifier VIGNETTE = Hazeline.id("weather/vignette");
	public static final Identifier RAYS = Hazeline.id("weather/rays");
	public static final Identifier GLOW = Hazeline.id("weather/glow");

	public static final int VIGNETTE_S = WeatherPixels.VIGNETTE_S;
	public static final int RAYS_S = WeatherPixels.RAYS_S;
	public static final int GLOW_S = WeatherPixels.GLOW_S;

	private static boolean ready;
	private static boolean failed;

	private WeatherTextures() {
	}

	/** Registers the textures once. Returns false if that failed (callers then fall back to vanilla weather). */
	public static boolean ensure() {
		if (ready) {
			return true;
		}
		if (failed) {
			return false;
		}
		try {
			var tm = MinecraftClient.getInstance().getTextureManager();
			tm.registerTexture(RAIN, smooth("hazeline rain", WeatherPixels.RAIN_W, WeatherPixels.RAIN_H, WeatherPixels.rain()));
			tm.registerTexture(SNOW, smooth("hazeline snow", WeatherPixels.SNOW_S, WeatherPixels.SNOW_S, WeatherPixels.snow()));
			tm.registerTexture(RING, smooth("hazeline ripple", WeatherPixels.RING_S, WeatherPixels.RING_S, WeatherPixels.ring()));
			tm.registerTexture(PUFF, smooth("hazeline mist", WeatherPixels.PUFF_S, WeatherPixels.PUFF_S, WeatherPixels.puff()));
			tm.registerTexture(SHEEN, smooth("hazeline sheen", WeatherPixels.SHEEN_S, WeatherPixels.SHEEN_S, WeatherPixels.sheen()));
			tm.registerTexture(VIGNETTE, smooth("hazeline vignette", WeatherPixels.VIGNETTE_S, WeatherPixels.VIGNETTE_S, WeatherPixels.vignette()));
			tm.registerTexture(RAYS, smooth("hazeline rays", WeatherPixels.RAYS_S, WeatherPixels.RAYS_S, WeatherPixels.rays()));
			tm.registerTexture(GLOW, smooth("hazeline glow", WeatherPixels.GLOW_S, WeatherPixels.GLOW_S, WeatherPixels.glow()));
			ready = true;
		} catch (Throwable t) {
			failed = true;
			Hazeline.LOGGER.error("Weather: could not create the weather textures, vanilla weather stays on", t);
		}
		return ready;
	}

	// ------------------------------------------------------------------ registration helpers

	/** Linear filtering, clamped edges (vanilla's NativeImageBackedTexture defaults to nearest). */
	private static final class SmoothTexture extends NativeImageBackedTexture {
		SmoothTexture(String label, NativeImage image) {
			super(() -> label, image);
			this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
		}
	}

	private static NativeImageBackedTexture smooth(String label, int w, int h, int[] argb) {
		NativeImage image = new NativeImage(w, h, false);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				image.setColorArgb(x, y, argb[y * w + x]);
			}
		}
		return new SmoothTexture(label, image);
	}
}
