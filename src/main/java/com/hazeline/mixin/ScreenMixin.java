package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import com.hazeline.module.modules.menus.CustomMenus;

/**
 * Custom Menus: replaces the rotating panorama behind the menus. In 1.21.11 (Yarn)
 * {@code Screen#renderBackground} calls {@code renderPanoramaBackground} only while no world is
 * loaded, and the title screen calls it directly; {@code Screen} is the only class that declares it
 * (verified against the 1.21.11 bytecode).
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
	private void hazeline$customBackground(DrawContext context, float deltaTicks, CallbackInfo ci) {
		if (CustomMenus.drawScreenBackground((Screen) (Object) this, context)) {
			ci.cancel();
		}
	}
}
