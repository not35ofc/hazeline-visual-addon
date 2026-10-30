package com.hazeline.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import com.hazeline.module.modules.render.NameTag;

/**
 * Live preview row of the Name Tag module (chat line + the tag above your head). Display only,
 * not saved to the config.
 */
public class NamePreviewSetting extends Setting<String> {
	private final NameTag module;

	public NamePreviewSetting(String name, String description, NameTag module) {
		super(name, description, "");
		this.module = module;
	}

	public NameTag getModule() {
		return module;
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
