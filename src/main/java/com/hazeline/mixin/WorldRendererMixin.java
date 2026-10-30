package com.hazeline.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.joml.Vector4f;

import com.hazeline.render.ScreenProjector;

/**
 * Records this frame's world view rotation and projection for {@link ScreenProjector} (Name Tag F5 tag).
 *
 * <p>In 1.21.11 {@code GameRenderer#renderWorld} calls
 * {@code WorldRenderer.render(allocator, tickCounter, renderBlockOutline, camera, positionMatrix,
 * projectionMatrix, cullProjectionMatrix, fogBuffer, fogColor, shouldRenderSky)}: positionMatrix is
 * the camera rotation, projectionMatrix the projection with view bobbing / hurt tilt applied (verified
 * against the 1.21.11 bytecode). Read-only: nothing is changed.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void hazeline$captureMatrices(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
			Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f cullProjectionMatrix, GpuBufferSlice fogBuffer,
			Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci) {
		ScreenProjector.captureFrame(positionMatrix, projectionMatrix, camera.getCameraPos());
	}
}
