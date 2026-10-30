package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;

import com.hazeline.module.modules.menus.CustomMenus;

/**
 * Custom Menus: replaces the Minecraft logo (and the "Java Edition" badge) with the Hazeline
 * wordmark. {@code draw(DrawContext, int, float)} forwards to this four-argument overload with
 * y = 30 (verified against the 1.21.11 bytecode), so one hook covers both.
 */
@Mixin(LogoDrawer.class)
public abstract class LogoDrawerMixin {
	@Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;IFI)V", at = @At("HEAD"), cancellable = true)
	private void hazeline$wordmark(DrawContext context, int screenWidth, float alpha, int y, CallbackInfo ci) {
		if (CustomMenus.drawTitleLogo(context, screenWidth, alpha, y)) {
			ci.cancel();
		}
	}
}
