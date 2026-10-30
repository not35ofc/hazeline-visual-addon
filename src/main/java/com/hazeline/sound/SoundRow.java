package com.hazeline.sound;

import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.EnumSetting;
import com.hazeline.setting.NumberSetting;

/**
 * One row of the Sounds and Clicks module (Click GUI, In game or Game menus): one sound pack with
 * its own volume, pitch variation and release sound.
 */
public record SoundRow(String label, BooleanSetting enabled, EnumSetting<SoundPack> pack, NumberSetting volume,
		BooleanSetting pitchVariation, BooleanSetting releaseSound) {

	/** Whether this row would make any sound right now (switched on and volume above 0). */
	public boolean audible() {
		return enabled.get() && volume.get() > 0;
	}

	/** 0..1 */
	public float volumeFactor() {
		return (float) (volume.get() / 100.0);
	}
}
