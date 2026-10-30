package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.WorldRenderState;

import com.hazeline.module.modules.render.HitBoxes;

/**
 * Hit Boxes: draws the chosen entities' hitboxes inside the world pass.
 *
 * <p>1.21.11 bytecode: the main world-draw lambda of {@code WorldRenderer} draws entities, block
 * entities, the solid target-block outline, then calls the private {@code collectGizmos()} (once per
 * frame, its only call site) and draws the gizmos (vanilla's F3+B hitboxes) with
 * {@code bufferBuilders.getEntityVertexConsumers()}; translucent terrain follows. We draw at HEAD of
 * {@code collectGizmos} into the same entity consumers, using {@code worldRenderState} (the frame's
 * camera position). {@code require = 0}: a changed target only means no hitboxes, never a crash.
 */
@Mixin(WorldRenderer.class)
public abstract class HitboxMixin {
	@Shadow
	@Final
	private BufferBuilderStorage bufferBuilders;

	@Shadow
	@Final
	private WorldRenderState worldRenderState;

	@Inject(method = "collectGizmos", at = @At("HEAD"), require = 0)
	private void hazeline$hitboxes(CallbackInfo ci) {
		HitBoxes.render(bufferBuilders.getEntityVertexConsumers(), worldRenderState);
	}
}
