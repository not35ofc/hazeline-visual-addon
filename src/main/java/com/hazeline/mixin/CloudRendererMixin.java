package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.util.math.Vec3d;

import com.hazeline.module.modules.sky.CustomSky;

/**
 * Custom Sky "Show clouds": skips {@code CloudRenderer#renderClouds} (1.21.11, Yarn) in the
 * Overworld while the custom sky is on and the switch is off. Other dimensions are untouched.
 */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
	private void hazeline$hideClouds(int color, CloudRenderMode mode, float cloudHeight, Vec3d cameraPos, long ticks, float tickProgress,
			CallbackInfo ci) {
		if (CustomSky.active() && !CustomSky.get().showClouds.get()) {
			ci.cancel();
		}
	}
}
