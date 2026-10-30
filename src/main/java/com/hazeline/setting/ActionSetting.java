package com.hazeline.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A clickable button row that runs an action. Not saved to the config. */
public class ActionSetting extends Setting<Boolean> {
	private final Runnable action;

	public ActionSetting(String name, String description, Runnable action) {
		super(name, description, false);
		this.action = action;
	}

	public void run() {
		action.run();
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
