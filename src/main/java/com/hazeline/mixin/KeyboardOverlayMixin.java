package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Keyboard;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;

import com.hazeline.gui.GuiOverlay;

/**
 * Click GUI overlay ({@link GuiOverlay}): opens it with the menu key on any screen, and while it is
 * open sends key presses, releases and typed characters to it instead of to the screen underneath.
 * In 1.21.11 {@code Keyboard#onKey} calls {@code Screen#keyPressed} (press/repeat) and
 * {@code Screen#keyReleased} once each, and {@code Keyboard#onChar} calls {@code Screen#charTyped}
 * once (verified against the 1.21.11 bytecode).
 */
@Mixin(Keyboard.class)
public abstract class KeyboardOverlayMixin {
	@WrapOperation(method = "onKey", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/Screen;keyPressed(Lnet/minecraft/client/input/KeyInput;)Z"))
	private boolean hazeline$keyPressed(Screen screen, KeyInput key, Operation<Boolean> original) {
		Boolean handled = GuiOverlay.keyPressed(screen, key);
		return handled != null ? handled : original.call(screen, key);
	}

	@WrapOperation(method = "onKey", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/Screen;keyReleased(Lnet/minecraft/client/input/KeyInput;)Z"))
	private boolean hazeline$keyReleased(Screen screen, KeyInput key, Operation<Boolean> original) {
		Boolean handled = GuiOverlay.keyReleased(screen, key);
		return handled != null ? handled : original.call(screen, key);
	}

	@WrapOperation(method = "onChar", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/Screen;charTyped(Lnet/minecraft/client/input/CharInput;)Z"))
	private boolean hazeline$charTyped(Screen screen, CharInput input, Operation<Boolean> original) {
		Boolean handled = GuiOverlay.charTyped(screen, input);
		return handled != null ? handled : original.call(screen, input);
	}
}
