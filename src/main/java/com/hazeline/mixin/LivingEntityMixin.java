package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.entity.LivingEntity;

import com.hazeline.module.modules.render.SwingSpeed;

/** Swing Speed: the swing animation duration of the client's own player. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyReturnValue(method = "getHandSwingDuration", at = @At("RETURN"))
	private int hazeline$swingSpeed(int original) {
		return SwingSpeed.duration(this, original);
	}
}
