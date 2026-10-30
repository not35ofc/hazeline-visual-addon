package com.hazeline.setting;

import java.util.List;
import java.util.function.Supplier;

/**
 * The GUI font choice. Stored exactly like a {@link ChoiceSetting} (the font id, e.g.
 * "hazeline:inter_medium"), but shown as the font picker: one preview row per font
 * ({@link com.hazeline.gui.component.FontPickerComponent}) instead of a dropdown.
 */
public class FontPickerSetting extends ChoiceSetting {
	public FontPickerSetting(String name, String description, String defaultId, Supplier<List<Choice>> options) {
		super(name, description, defaultId, options);
	}
}
