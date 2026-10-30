package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

import com.hazeline.module.modules.weather.WeatherFog;
import com.hazeline.module.modules.weather.WeatherController;

/**
 * Weather and Mist: the atmospheric fog (the only fog in open air) reads the rain / thunder gradient for
 * its colour and for how much it closes in (those reads follow Hazeline's weather), and afterwards the
 * fog distance is pulled in by the Weather module's "Fog closeness" / "Constant haze" and Mist's
 * distance haze. In the Overworld only. {@code require = 0}.
 */
@Mixin(AtmosphericFogModifier.class)
public abstract class WeatherFogDistanceMixin {
	@ModifyExpressionValue(method = "getFogColor", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/world/ClientWorld;getRainGradient(F)F"), require = 0)
	private float hazeline$colourRain(float vanilla) {
		return WeatherController.visualRain(vanilla);
	}

	@ModifyExpressionValue(method = "getFogColor", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/world/ClientWorld;getThunderGradient(F)F"), require = 0)
	private float hazeline$colourThunder(float vanilla) {
		return WeatherController.visualThunder(vanilla);
	}

	@ModifyExpressionValue(method = "method_76304", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/world/ClientWorld;getRainGradient(F)F"), require = 0)
	private float hazeline$distanceRain(float vanilla) {
		return WeatherController.visualRain(vanilla);
	}

	@Inject(method = "applyStartEndModifier", at = @At("TAIL"), require = 0)
	private void hazeline$closerFog(FogData data, Camera camera, ClientWorld world, float viewDistance, RenderTickCounter counter, CallbackInfo ci) {
		if (world == null || world.getRegistryKey() != World.OVERWORLD) {
			return;
		}
		float close = WeatherFog.closeness(counter.getTickProgress(false));
		if (close > 0.001f) {
			float end = data.environmentalEnd;
			data.environmentalEnd = Math.max(16f, end + (end * 0.3f - end) * close);
			data.environmentalStart = Math.max(0f, data.environmentalStart * (1f - 0.85f * close));
		}
	}
}
