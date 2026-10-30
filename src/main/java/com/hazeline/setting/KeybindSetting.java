package com.hazeline.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.util.InputUtil;

/**
 * A keyboard key (GLFW key code). {@link #NONE} (-1) means unbound.
 *
 * <p>Module keybinds are deliberately kept out of the vanilla Controls screen so users can
 * create as many as they like; only the "open menu" key is a real {@code KeyBinding}.
 */
public class KeybindSetting extends Setting<Integer> {
	public static final int NONE = -1;

	public KeybindSetting(String name, String description, int defaultKey) {
		super(name, description, defaultKey);
	}

	public boolean isBound() {
		return value != NONE;
	}

	/** Human readable key name, e.g. "R", "Left Shift" or "None". */
	public String getKeyName() {
		if (!isBound()) {
			return "None";
		}
		return InputUtil.Type.KEYSYM.createFromCode(value).getLocalizedText().getString();
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsInt());
		}
	}
}
