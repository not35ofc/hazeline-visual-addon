package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.state.WeatherRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.hazeline.module.modules.weather.WeatherController;
import com.hazeline.module.modules.weather.WeatherDraw;

/**
 * Weather module: replaces vanilla's rain and snow with Hazeline's own while it is on, and gives the
 * other weather modules their draw hook.
 *
 * <p>1.21.11 (Yarn), verified against the bytecode: {@code WeatherRendering#buildPrecipitationPieces}
 * fills a {@code WeatherRenderState} with rain / snow pieces, {@code renderPrecipitation(provider,
 * cameraPos, state)} draws them every frame (the weather pass always runs), and
 * {@code addParticlesAndSound} makes the splashes and the rain sound. While Hazeline's weather takes
 * over (or vanilla's would only add work) the first and third are skipped; the second hands the frame
 * to {@link WeatherDraw}, which draws our weather and the Glossy Rain / Mist / Smooth Thunder extras,
 * and returns whether vanilla's drawing should be skipped. All {@code require = 0}.
 */
@Mixin(WeatherRendering.class)
public abstract class WeatherRenderingMixin {
	@Inject(method = "buildPrecipitationPieces", at = @At("HEAD"), cancellable = true, require = 0)
	private void hazeline$skipVanillaPieces(World world, int ticks, float tickProgress, Vec3d cameraPos, WeatherRenderState state, CallbackInfo ci) {
		if (WeatherController.takeover()) {
			state.clear();
			ci.cancel();
		}
	}

	@Inject(method = "renderPrecipitation", at = @At("HEAD"), cancellable = true, require = 0)
	private void hazeline$drawWeather(VertexConsumerProvider provider, Vec3d cameraPos, WeatherRenderState state, CallbackInfo ci) {
		if (WeatherDraw.render(provider, cameraPos)) {
			ci.cancel();
		}
	}

	@Inject(method = "addParticlesAndSound", at = @At("HEAD"), cancellable = true, require = 0)
	private void hazeline$skipVanillaSplashes(ClientWorld world, Camera camera, int ticks, ParticlesMode particlesMode, int radius, CallbackInfo ci) {
		if (WeatherController.takeover()) {
			ci.cancel();
		}
	}
}
