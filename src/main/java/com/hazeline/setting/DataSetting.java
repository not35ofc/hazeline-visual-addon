package com.hazeline.setting;

/**
 * A hidden, persistent text value for data a module keeps in {@code config.json} (for example the
 * Custom Sky's saved presets as JSON). It never shows in the GUI and is not cleared by
 * "Reset to defaults" - user-made data is only removed by its own delete action.
 */
public class DataSetting extends TextSetting {
	public DataSetting(String name, String defaultValue) {
		super(name, "", defaultValue, 200_000, true);
		visibleWhen(() -> false);
	}

	@Override
	public void reset() {
		// intentionally kept: user data, not a preference
	}
}
