package com.hazeline.setting;

import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/**
 * Read-only text shown in the GUI (e.g. "Current version: v1.1.0"). The text is pulled from
 * a supplier every frame, so it always reflects live state. Not saved to the config.
 */
public class InfoSetting extends Setting<String> {
	private final Supplier<String> text;

	public InfoSetting(String name, String description, Supplier<String> text) {
		super(name, description, "");
		this.text = text;
	}

	public String getText() {
		String value = text.get();
		return value == null ? "" : value;
	}

	@Override
	public boolean isPersistent() {
		return false;
	}

	@Override
	public JsonElement toJson() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void fromJson(JsonElement json) {
	}
}
