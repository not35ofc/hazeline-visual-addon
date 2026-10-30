package com.hazeline.setting;

/**
 * Placeholder setting for the colour preset chips (built-in + saved presets). Not persisted
 * itself; the saved presets live in {@link com.hazeline.appearance.Appearance}.
 */
public class PresetsSetting extends InfoSetting {
	public PresetsSetting() {
		super("Presets", "Click to apply a preset. Right click a saved preset to delete it.", () -> "");
	}
}
