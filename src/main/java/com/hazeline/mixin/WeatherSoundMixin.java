package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundEvents;

import com.hazeline.module.modules.weather.WeatherController;

/**
 * Smooth Thunder "Replace server thunder": the server's lightning plays its thunder crack and impact
 * through {@code SoundManager#play(SoundInstance)}; those two sounds are swapped for Hazeline's gentle
 * rolling thunder (a muted sound returns {@code NOT_STARTED}, like vanilla does for sounds it skips).
 * {@code require = 0}.
 */
@Mixin(SoundManager.class)
public abstract class WeatherSoundMixin {
	@Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void hazeline$swapServerThunder(SoundInstance sound, CallbackInfoReturnable<SoundSystem.PlayResult> cir) {
		if (sound == null || !WeatherController.replacesServerThunder()) {
			return;
		}
		var id = sound.getId();
		if (id.equals(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER.id())) {
			WeatherController.onServerThunder();
			cir.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
		} else if (id.equals(SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT.id())) {
			cir.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
		}
	}
}
