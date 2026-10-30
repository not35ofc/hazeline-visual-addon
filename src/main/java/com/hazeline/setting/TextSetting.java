package com.hazeline.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

/**
 * A single line of editable text (preset name, theme code). Rendered as a text field that
 * supports typing, Backspace, Ctrl+V / Ctrl+C. Persisted only if {@code persistent}.
 */
public class TextSetting extends Setting<String> {
	private final int maxLength;
	private final boolean persistent;

	public TextSetting(String name, String description, String defaultValue, int maxLength, boolean persistent) {
		super(name, description, defaultValue);
		this.maxLength = maxLength;
		this.persistent = persistent;
	}

	public int getMaxLength() {
		return maxLength;
	}

	@Override
	protected String sanitize(String newValue) {
		String s = newValue == null ? "" : newValue.replaceAll("[\\r\\n\\t]", "");
		return s.length() > maxLength ? s.substring(0, maxLength) : s;
	}

	@Override
	public boolean isPersistent() {
		return persistent;
	}

	@Override
	public JsonElement toJson() {
		return persistent ? new JsonPrimitive(value) : JsonNull.INSTANCE;
	}

	@Override
	public void fromJson(JsonElement json) {
		if (persistent && json != null && json.isJsonPrimitive()) {
			set(json.getAsString());
		}
	}
}
