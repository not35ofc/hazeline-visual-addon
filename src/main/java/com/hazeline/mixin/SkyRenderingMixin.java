package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.MoonPhase;

import com.hazeline.module.modules.sky.CustomSky;
import com.hazeline.module.modules.sky.CustomSkyRenderer;

/**
 * Custom Sky hooks into vanilla's sky renderer (1.21.11, Yarn).
 *
 * <p>{@code WorldRenderer#renderSky} calls these only for an Overworld-type skybox; the End sky
 * ({@code renderEndSky}) is never touched and the Nether has no sky pass. {@link CustomSky#active()}
 * additionally requires the current world to be the Overworld itself.
 * <ul>
 *   <li>{@code renderTopSky(skyColor)} - the sky disc: replaced by the gradient dome + falling stars
 *       (unless the custom shader is unavailable, then vanilla draws as usual);</li>
 *   <li>{@code renderGlowingSky} - vanilla's orange sunrise/sunset fan: hidden while active, it
 *       would tint the custom colours;</li>
 *   <li>{@code renderSun} / {@code renderMoon} (private, called from
 *       {@code renderCelestialBodies}) - hidden by the "Show sun" / "Show moon" switches.
 *       Vanilla's static stars are left as they are.</li>
 * </ul>
 */
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {
	@Inject(method = "renderTopSky", at = @At("HEAD"), cancellable = true)
	private void hazeline$customSky(int skyColor, CallbackInfo ci) {
		if (CustomSky.active() && CustomSkyRenderer.render(CustomSky.get(), skyColor)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true)
	private void hazeline$hideSunriseGlow(MatrixStack matrices, float sunAngle, int color, CallbackInfo ci) {
		if (CustomSky.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
	private void hazeline$hideSun(float alpha, MatrixStack matrices, CallbackInfo ci) {
		if (CustomSky.active() && !CustomSky.get().showSun.get()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
	private void hazeline$hideMoon(MoonPhase phase, float alpha, MatrixStack matrices, CallbackInfo ci) {
		if (CustomSky.active() && !CustomSky.get().showMoon.get()) {
			ci.cancel();
		}
	}
}
