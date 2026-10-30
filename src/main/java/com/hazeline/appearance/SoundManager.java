package com.hazeline.appearance;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

import com.hazeline.Hazeline;
import com.hazeline.module.modules.sounds.SoundsAndClicks;
import com.hazeline.sound.KeySound;
import com.hazeline.sound.SoundPack;
import com.hazeline.sound.SoundRow;

/**
 * Plays Hazeline's sounds through Minecraft's own sound engine.
 *
 * <p><b>How sounds work in 1.21.11:</b> a sound event is declared in {@code assets/<ns>/sounds.json}
 * and its .ogg files live in {@code assets/<ns>/sounds/}. The client needs no registry entry to play it:
 * a {@link PositionedSoundInstance} built from the event {@link Identifier} is resolved against the
 * loaded sounds.json (an event with several files picks one at random each time). Sounds are played
 * on the MASTER category, relative to the listener, without attenuation; the engine always multiplies
 * by Minecraft's Master volume.
 *
 * <p>Since 1.35.0 the settings live in the Sounds and Clicks module: {@link #play(SoundAction)} is the
 * click GUI's action sound and uses that module's Click GUI row. Packs with their own GUI sounds
 * (Clicky, Creamy) play {@code <pack>.<action>}; the other packs map each action to a key sound.
 */
public final class SoundManager {
	private static final SoundManager INSTANCE = new SoundManager();
	private static final float PITCH_VARIATION = 0.06f;
	private static final long SLIDER_TICK_INTERVAL_MS = 35;

	private long lastSliderTick;
	/** Counts every click GUI action sound that was played; InputSounds compares it around an input event. */
	private int actionCount;
	/** Whether the last action sound was a single key press (so a release sound may follow). */
	private boolean lastActionWasPress;

	private SoundManager() {
	}

	public static SoundManager get() {
		return INSTANCE;
	}

	public int actionCount() {
		return actionCount;
	}

	public boolean lastActionWasPress() {
		return lastActionWasPress;
	}

	/** Plays the click GUI sound for {@code action} (Click GUI row). Call on the client thread. */
	public void play(SoundAction action) {
		SoundsAndClicks module = SoundsAndClicks.get();
		if (module == null || !module.isEnabled() || !module.gui.audible()) {
			return;
		}
		if (action == SoundAction.SLIDER) {
			long now = System.currentTimeMillis();
			if (now - lastSliderTick < SLIDER_TICK_INTERVAL_MS) {
				return;
			}
			lastSliderTick = now;
		}
		SoundRow row = module.gui;
		SoundPack pack = row.pack().get();
		String event;
		float pitch = 1.0f;
		boolean press;
		if (pack.hasGuiSounds()) {
			event = pack.id() + "." + action.fileName();
			press = false; // these files already contain the release
		} else {
			KeySound key = switch (action) {
				case OPEN, KEYBIND -> KeySound.ENTER;
				case CLOSE -> KeySound.BACKSPACE;
				case ENABLE, DISABLE -> KeySound.SPACE;
				case CLICK, SLIDER -> KeySound.GENERIC;
			};
			event = key.event(pack, false);
			pitch = action == SoundAction.ENABLE ? 1.06f : action == SoundAction.DISABLE ? 0.94f : 1.0f;
			press = action != SoundAction.SLIDER;
		}
		actionCount++;
		lastActionWasPress = press;
		playEvent(row, event, pitch, action.volumeFactor(), 0);
	}

	/**
	 * Plays {@code hazeline:<event>} with the row's volume and pitch variation, {@code delayTicks}
	 * client ticks later (0 = now). Never throws.
	 */
	public void playEvent(SoundRow row, String event, float pitch, float volumeFactor, int delayTicks) {
		float volume = row.volumeFactor() * volumeFactor;
		if (volume <= 0) {
			return;
		}
		if (row.pitchVariation().get()) {
			pitch *= 1.0f + (ThreadLocalRandom.current().nextFloat() * 2 - 1) * PITCH_VARIATION;
		}
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.getSoundManager() == null) {
				return;
			}
			PositionedSoundInstance sound = new PositionedSoundInstance(Hazeline.id(event), SoundCategory.MASTER, volume, pitch,
					SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true);
			if (delayTicks > 0) {
				mc.getSoundManager().play(sound, delayTicks);
			} else {
				mc.getSoundManager().play(sound);
			}
		} catch (RuntimeException e) {
			Hazeline.LOGGER.debug("Could not play {}: {}", event, e.toString());
		}
	}
}
