package com.hazeline.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** Live preview row of the Weather module (a small animated sky). Display only, not saved to the config. */
public class WeatherPreviewSetting extends Setting<String> {
	public WeatherPreviewSetting(String name, String description) {
		super(name, description, "");
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
