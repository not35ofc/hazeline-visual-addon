package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;

import com.hazeline.sound.InputSounds;

/**
 * Sounds and Clicks: mouse button sounds. 1.21.11 {@code Mouse#onMouseButton(long window, MouseInput,
 * int action)} handles every button event (in game and on screens, incl. Fabric API's screen mouse
 * events used by the click GUI overlay; verified against the bytecode). Same HEAD/RETURN split as the
 * keyboard mixin. {@code require = 0}.
 */
@Mixin(Mouse.class)
public abstract class InputSoundsMouseMixin {
	@Inject(method = "onMouseButton", at = @At("HEAD"), require = 0)
	private void hazeline$mouseSoundHead(long window, MouseInput input, int action, CallbackInfo ci) {
		InputSounds.beforeMouse(window, input.button(), action);
	}

	@Inject(method = "onMouseButton", at = @At("RETURN"), require = 0)
	private void hazeline$mouseSoundReturn(long window, MouseInput input, int action, CallbackInfo ci) {
		InputSounds.after();
	}
}
