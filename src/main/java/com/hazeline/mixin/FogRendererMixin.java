package com.hazeline.mixin;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.ColorHelper;

import com.hazeline.module.modules.sky.CustomSky;
import com.hazeline.module.modules.sky.CustomSkyRenderer;

/**
 * Custom Sky "Match fog to sky": the fog colour (also the colour the frame is cleared to) becomes
 * the custom horizon colour, so distant terrain fades into the custom sky instead of vanilla blue.
 *
 * <p>1.21.11 (Yarn): {@code FogRenderer#getFogColor(camera, tickProgress, world, viewDistance,
 * skyDarkness)} computes the frame's fog colour. It is only changed in the Overworld, in open air
 * (not under water, in lava or powder snow) and without Blindness or Darkness, so those keep their
 * vanilla look. The alpha component is left as vanilla computed it.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@ModifyReturnValue(method = "getFogColor", at = @At("RETURN"))
	private Vector4f hazeline$matchFogToSky(Vector4f fog, Camera camera, float tickProgress, ClientWorld world, int viewDistance,
			float skyDarkness) {
		if (!CustomSky.activeIn(world) || !CustomSky.get().matchFog.get()) {
			return fog;
		}
		CameraSubmersionType submersion = camera.getSubmersionType();
		if (submersion == CameraSubmersionType.WATER || submersion == CameraSubmersionType.LAVA
				|| submersion == CameraSubmersionType.POWDER_SNOW) {
			return fog;
		}
		Entity entity = camera.getFocusedEntity();
		if (entity instanceof LivingEntity living
				&& (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS))) {
			return fog;
		}
		int horizon = CustomSkyRenderer.horizonColor(CustomSky.get());
		float light = CustomSkyRenderer.lastLight();
		return new Vector4f(ColorHelper.getRed(horizon) / 255f * light, ColorHelper.getGreen(horizon) / 255f * light,
				ColorHelper.getBlue(horizon) / 255f * light, fog.w);
	}
}
