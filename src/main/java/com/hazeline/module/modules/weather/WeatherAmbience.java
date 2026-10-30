package com.hazeline.module.modules.weather;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import com.hazeline.Hazeline;

/**
 * Hazeline's own rain and wind sound while the Weather module is on (vanilla's rain sound is not
 * played then), and the little splash particles where rain lands. Two looping, non-positional sounds
 * whose volume follows the weather smoothly and drops when you are under cover.
 */
final class WeatherAmbience {
	static final Identifier RAIN = Hazeline.id("weather.rain_loop");
	static final Identifier WIND = Hazeline.id("weather.wind_loop");

	private static Loop rain;
	private static Loop wind;
	private static final Random RNG = Random.create();

	private WeatherAmbience() {
	}

	private static final class Loop extends MovingSoundInstance {
		float target;
		boolean stopping;

		Loop(Identifier id) {
			super(SoundEvent.of(id), SoundCategory.WEATHER, Random.create());
			this.repeat = true;
			this.repeatDelay = 0;
			this.attenuationType = SoundInstance.AttenuationType.NONE;
			this.relative = true;
			this.volume = 0.01f;
		}

		@Override
		public void tick() {
			// About one second to fade in or out.
			volume += MathHelper.clamp(target - volume, -0.025f, 0.025f);
			if (stopping && volume <= 0.012f) {
				setDone();
			}
		}
	}

	static void tick(MinecraftClient mc, ClientWorld w) {
		float rainVol = 0f;
		float windVol = 0f;
		Weather wx = Weather.get();
		if (w != null && wx != null && wx.isEnabled() && WeatherController.takeover()) {
			float shelter = 1f - 0.65f * WeatherController.sheltered();
			float p = WeatherController.precip(1f);
			if (wx.ambience.get()) {
				rainVol = WeatherController.rainAmount(1f) * wx.ambienceVolume.getFloat() / 100f * shelter;
			}
			if (wx.windSound.get()) {
				float wind = wx.windStrength.getFloat() / 100f;
				float level = p * (0.3f + 0.7f * wind) + WeatherController.thunder(1f) * 0.25f;
				windVol = MathHelper.clamp(level, 0f, 1f) * wx.windVolume.getFloat() / 100f * (0.5f + 0.5f * shelter);
			}
			if (wx.splashes.get()) {
				splashes(mc, w, wx, p);
			}
		}
		rain = update(mc, rain, RAIN, rainVol * 0.9f);
		wind = update(mc, wind, WIND, windVol * 0.9f);
	}

	private static Loop update(MinecraftClient mc, Loop loop, Identifier id, float volume) {
		if (loop != null && loop.isDone()) {
			loop = null;
		}
		if (volume > 0.012f) {
			if (loop == null) {
				loop = new Loop(id);
				mc.getSoundManager().play(loop);
			}
			loop.stopping = false;
			loop.target = volume;
		} else if (loop != null) {
			loop.target = 0f;
			loop.stopping = true;
		}
		return loop;
	}

	/** Splash particles on the ground around the player, only where rain (not snow) falls. */
	private static void splashes(MinecraftClient mc, ClientWorld w, Weather wx, float precip) {
		Camera camera = mc.gameRenderer.getCamera();
		if (!camera.isReady() || WeatherController.mode() == WeatherController.Mode.SNOW) {
			return;
		}
		int attempts = Math.round(precip * precip * 20f * wx.quality.get().scale);
		if (attempts <= 0) {
			return;
		}
		Vec3d cam = camera.getCameraPos();
		float radius = Math.min(14f, wx.range.getFloat());
		for (int i = 0; i < attempts; i++) {
			double x = cam.x + (RNG.nextDouble() * 2 - 1) * radius;
			double z = cam.z + (RNG.nextDouble() * 2 - 1) * radius;
			int idx = Columns.get(w, MathHelper.floor(x), MathHelper.floor(z));
			if (idx < 0) {
				continue;
			}
			if (WeatherController.mode() == WeatherController.Mode.BIOME && Columns.KIND[idx] != Columns.KIND_RAIN) {
				continue;
			}
			if (Columns.MAT[idx] == Columns.MAT_NONE) {
				continue;
			}
			w.addParticleClient(ParticleTypes.RAIN, x, Columns.SURFACE[idx] + 0.05, z, 0.0, 0.0, 0.0);
		}
	}
}
