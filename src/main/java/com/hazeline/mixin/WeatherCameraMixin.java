package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import com.hazeline.module.modules.weather.WeatherController;

/**
 * Smooth Thunder "Screen shake": after the camera is positioned for the frame, its view direction is
 * nudged by a few tenths of a degree for close lightning. Only the rendered camera moves; the player's
 * real aim is untouched. {@code Camera#update(world, entity, thirdPerson, inverseView, tickProgress)}.
 * {@code require = 0}.
 */
@Mixin(Camera.class)
public abstract class WeatherCameraMixin {
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	public abstract float getYaw();

	@Shadow
	public abstract float getPitch();

	@Inject(method = "update", at = @At("TAIL"), require = 0)
	private void hazeline$thunderShake(World world, Entity focused, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
		float[] shake = WeatherController.cameraShake(tickProgress);
		if (shake != null) {
			this.setRotation(this.getYaw() + shake[0], this.getPitch() + shake[1]);
		}
	}
}
