package com.hazeline.mixin;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

import com.hazeline.module.modules.weather.WeatherFog;

/**
 * Weather and Mist: tints the frame's fog colour (also the colour the frame is cleared to, so the far
 * terrain fades into it) towards the Weather "Fog colour" in bad weather / haze and towards the Mist
 * colour. Open air in the Overworld only; the unmodified vanilla colour is also recorded so Mist's
 * "Match sky" can follow it. {@code require = 0}.
 */
@Mixin(FogRenderer.class)
public abstract class WeatherFogMixin {
	@ModifyReturnValue(method = "getFogColor", at = @At("RETURN"), require = 0)
	private Vector4f hazeline$weatherFog(Vector4f fog, Camera camera, float tickProgress, ClientWorld world, int viewDistance, float skyDarkness) {
		if (world == null || world.getRegistryKey() != World.OVERWORLD) {
			return fog;
		}
		CameraSubmersionType submersion = camera.getSubmersionType();
		if (submersion == CameraSubmersionType.WATER || submersion == CameraSubmersionType.LAVA
				|| submersion == CameraSubmersionType.POWDER_SNOW) {
			return fog;
		}
		return WeatherFog.tint(fog, tickProgress);
	}
}
