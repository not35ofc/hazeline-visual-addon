package com.hazeline.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An on/off toggle. Rendered as a checkbox-style row in the GUI. */
public class BooleanSetting extends Setting<Boolean> {
	public BooleanSetting(String name, String description, boolean defaultValue) {
		super(name, description, defaultValue);
	}

	public void toggle() {
		set(!value);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
			set(json.getAsBoolean());
		}
	}
}
