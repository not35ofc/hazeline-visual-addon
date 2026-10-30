package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.render.SkyRendering;

import com.hazeline.module.modules.weather.WeatherController;

/**
 * Weather module: {@code SkyRendering#updateRenderState} copies the rain gradient into the sky state
 * (it fades the sun, moon and stars in rain); it should see Hazeline's weather too. {@code require = 0}.
 */
@Mixin(SkyRendering.class)
public abstract class WeatherSkyMixin {
	@ModifyExpressionValue(method = "updateRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/world/ClientWorld;getRainGradient(F)F"), require = 0)
	private float hazeline$visualRain(float vanilla) {
		return WeatherController.visualRain(vanilla);
	}
}
