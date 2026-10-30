package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;

import com.hazeline.module.modules.render.BlockOutlines;

/**
 * Custom Block Outlines: replaces the vanilla target-block outline.
 *
 * <p>1.21.11 bytecode: {@code WorldRenderer#renderTargetBlockOutline(Immediate, MatrixStack, boolean, WorldRenderState)}
 * reads {@code worldRenderState.outlineRenderState}, returns unless its translucent flag matches the boolean,
 * then draws it with {@code RenderLayers.lines()} and calls {@code drawCurrentLayer()}. While the module is on we
 * draw our own outline and cancel; {@code require = 0} so a changed target only means "vanilla outline", never a
 * crash at start-up.
 */
@Mixin(WorldRenderer.class)
public abstract class BlockOutlineMixin {
	@Inject(method = "renderTargetBlockOutline", at = @At("HEAD"), cancellable = true, require = 0)
	private void hazeline$customOutline(VertexConsumerProvider.Immediate consumers, MatrixStack matrices, boolean translucent,
			WorldRenderState worldState, CallbackInfo ci) {
		if (BlockOutlines.render(consumers, matrices, translucent, worldState)) {
			ci.cancel();
		}
	}
}
