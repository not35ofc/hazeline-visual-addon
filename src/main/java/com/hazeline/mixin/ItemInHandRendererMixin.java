package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.util.math.MatrixStack;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.util.Hand;
import net.minecraft.util.Arm;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;

import com.hazeline.module.modules.render.HandView;

/**
 * Hand View. In 1.21.11 (Yarn) {@code HeldItemRenderer#renderFirstPersonItem} has exactly one
 * {@code MatrixStack#push} (start of the hand) and draws with {@code renderItem} (twice: crossbow
 * branch and general branch) or {@code renderArmHoldingItem} (empty main hand) - verified against
 * the 1.21.11 bytecode.
 */
@Mixin(HeldItemRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "renderFirstPersonItem",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
	private void hazeline$handOffset(AbstractClientPlayerEntity player, float frameInterp, float xRot, Hand hand, float attack,
			ItemStack itemStack, float inverseArmHeight, MatrixStack poseStack, OrderedRenderCommandQueue submitNodeCollector, int lightCoords,
			CallbackInfo ci) {
		HandView.applyOffset(hand, poseStack);
	}

	@WrapOperation(method = "renderFirstPersonItem",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"))
	private void hazeline$itemTransform(HeldItemRenderer self, LivingEntity entity, ItemStack stack, ItemDisplayContext context,
			MatrixStack poseStack, OrderedRenderCommandQueue collector, int light, Operation<Void> original) {
		HandView.applyItemTransform(poseStack);
		original.call(self, entity, stack, context, poseStack, collector, light);
	}

	@WrapOperation(method = "renderFirstPersonItem",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderArmHoldingItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IFFLnet/minecraft/util/Arm;)V"))
	private void hazeline$armTransform(HeldItemRenderer self, MatrixStack poseStack, OrderedRenderCommandQueue collector, int light,
			float inverseArmHeight, float attack, Arm arm, Operation<Void> original) {
		HandView.applyItemTransform(poseStack);
		original.call(self, poseStack, collector, light, inverseArmHeight, attack, arm);
	}
}
