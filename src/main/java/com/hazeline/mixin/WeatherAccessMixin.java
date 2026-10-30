package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

import com.hazeline.module.modules.weather.WeatherController;

/**
 * Weather module: sky colour, cloud colour, fog colour and distance, and light all read the rain and
 * thunder gradient through the environment-attribute {@code WeatherAttributes.WeatherAccess}. Swapping
 * the values it returns for CLIENT worlds only (the integrated server's world keeps the real ones)
 * makes all of that follow Hazeline's weather, with the "Sky darkness" setting applied. Nothing
 * gameplay-related reads this path. The anonymous class's outer-world field is the synthetic
 * {@code field_64352}. {@code require = 0}.
 */
@Mixin(targets = "net.minecraft.world.attribute.WeatherAttributes$WeatherAccess$1")
public abstract class WeatherAccessMixin {
	@Shadow
	@Final
	World field_64352;

	@ModifyReturnValue(method = "getRainGradient", at = @At("RETURN"), require = 0)
	private float hazeline$rain(float original) {
		return this.field_64352 instanceof ClientWorld ? WeatherController.visualRain(original) : original;
	}

	@ModifyReturnValue(method = "getThunderGradient", at = @At("RETURN"), require = 0)
	private float hazeline$thunder(float original) {
		return this.field_64352 instanceof ClientWorld ? WeatherController.visualThunder(original) : original;
	}
}
