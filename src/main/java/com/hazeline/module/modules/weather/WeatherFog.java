package com.hazeline.module.modules.weather;

import org.joml.Vector4f;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

/** Fog helpers for the weather mixins: how close the fog comes and what colour it is tinted to. */
public final class WeatherFog {
	private WeatherFog() {
	}

	/** 0..1 how much closer than vanilla the fog should end (Weather closeness + Mist haze). */
	public static float closeness(float tp) {
		if (WeatherController.isFailed()) {
			return 0f;
		}
		float weather = 0f;
		Weather wx = Weather.get();
		if (wx != null && wx.isEnabled()) {
			weather = WeatherController.fogStrength() * wx.fogDistance.getFloat() / 100f;
		}
		float haze = 0f;
		Mist m = Mist.get();
		if (m != null && m.isEnabled() && m.haze.get()) {
			haze = m.hazeAmount.getFloat() / 100f * 0.7f * WeatherController.mistFade(tp);
		}
		return 1f - (1f - weather) * (1f - haze);
	}

	/** Blends the frame's fog colour towards the weather fog colour and the mist colour. */
	public static Vector4f tint(Vector4f fog, float tp) {
		if (WeatherController.isFailed()) {
			return fog;
		}
		WeatherController.noteFog(fog.x, fog.y, fog.z);
		float r = fog.x, g = fog.y, b = fog.z;
		Weather wx = Weather.get();
		if (wx != null && wx.isEnabled()) {
			float k = WeatherController.fogStrength() * wx.fogTint.getFloat() / 100f;
			if (k > 0.001f) {
				int c = wx.fogColor.get();
				// The tint colour is scaled by the fog's own brightness so it also darkens at night.
				float lum = Math.max(fog.x, Math.max(fog.y, fog.z));
				float day = Math.min(1f, 0.25f + lum * 1.2f);
				r += (((c >> 16) & 0xFF) / 255f * day - r) * k;
				g += (((c >> 8) & 0xFF) / 255f * day - g) * k;
				b += ((c & 0xFF) / 255f * day - b) * k;
			}
		}
		Mist m = Mist.get();
		if (m != null && m.isEnabled() && m.haze.get()) {
			float k = m.hazeAmount.getFloat() / 100f * 0.55f * WeatherController.mistFade(tp);
			if (k > 0.001f) {
				int c = WeatherDraw.mistRgb(m, tp);
				r += (((c >> 16) & 0xFF) / 255f - r) * k;
				g += (((c >> 8) & 0xFF) / 255f - g) * k;
				b += ((c & 0xFF) / 255f - b) * k;
			}
		}
		if (r == fog.x && g == fog.y && b == fog.z) {
			return fog;
		}
		return new Vector4f(r, g, b, fog.w);
	}

	static boolean overworld() {
		ClientWorld w = MinecraftClient.getInstance().world;
		return w != null && w.getRegistryKey() == World.OVERWORLD;
	}
}
