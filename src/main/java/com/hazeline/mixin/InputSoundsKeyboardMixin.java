package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;

import com.hazeline.sound.InputSounds;

/**
 * Sounds and Clicks: key sounds. 1.21.11 {@code Keyboard#onKey(long window, int action, KeyInput)} is
 * the single entry point for every key event (in game and on screens; verified against the bytecode).
 * HEAD: decide the row and play in game / game menu presses and all releases; every RETURN: finish click
 * GUI presses after the menu handled the key. {@code require = 0}: a failed target only loses the sounds.
 */
@Mixin(Keyboard.class)
public abstract class InputSoundsKeyboardMixin {
	@Inject(method = "onKey", at = @At("HEAD"), require = 0)
	private void hazeline$keySoundHead(long window, int action, KeyInput input, CallbackInfo ci) {
		InputSounds.beforeKey(window, action, input.key());
	}

	@Inject(method = "onKey", at = @At("RETURN"), require = 0)
	private void hazeline$keySoundReturn(long window, int action, KeyInput input, CallbackInfo ci) {
		InputSounds.after();
	}
}
