package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;

import com.hazeline.module.modules.render.NameTag;

/**
 * Name Tag: while its F5 tag is on, the tag Minecraft would draw for <b>you</b> is removed, so the Name
 * Tag replaces it instead of overlapping it.
 *
 * <p>Vanilla never labels the camera entity, but mods can turn that on - Essential's "Show own nametag"
 * ({@code EssentialConfig.showOwnNametag}) injects at HEAD of {@code LivingEntityRenderer#hasLabel} and
 * returns true for the camera entity (verified in Essential 1.5.1's {@code MixinRendererLivingEntity}).
 * Such mods only make vanilla fill {@code EntityRenderState#displayName}; {@code PlayerEntityRenderer}
 * draws it in {@code method_4213} via {@code submitLabel} only when it is non-null (verified in the
 * 1.21.11 bytecode, together with {@code playerName}). Clearing both for the local player at the end of
 * {@code updateRenderState} removes that tag whichever mod asked for it. Other players are untouched.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class NameTagOwnLabelMixin {
	@Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
			at = @At("RETURN"), require = 0)
	private void hazeline$hideOwnLabel(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
		if (NameTag.replacesOwnLabel(entity)) {
			state.displayName = null;
			state.playerName = null;
		}
	}
}
