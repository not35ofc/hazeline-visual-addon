package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;

import com.hazeline.sound.InputSounds;

/**
 * Sounds and Clicks "Vanilla button click" (Game menus). 1.21.11 plays the menu click from several
 * places (ClickableWidget#playClickSound, TabManager, WorldListWidget entries, RecipeBookWidget, Realms,
 * CrafterScreen...), all as {@code minecraft:ui.button.click} through {@code SoundManager#play(SoundInstance)},
 * which returns {@code SoundSystem.PlayResult}; vanilla itself returns {@code NOT_STARTED} for sounds it
 * does not play, so that is what a muted click returns. {@code require = 0}.
 */
@Mixin(SoundManager.class)
public abstract class VanillaClickMixin {
	@Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void hazeline$muteVanillaClick(SoundInstance sound, CallbackInfoReturnable<SoundSystem.PlayResult> cir) {
		if (sound != null && InputSounds.muteVanillaClick(sound.getId())) {
			cir.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
		}
	}
}
