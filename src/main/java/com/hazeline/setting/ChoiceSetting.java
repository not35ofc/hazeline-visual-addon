package com.hazeline.setting;

import java.util.List;
import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * One choice out of a list that can change at runtime (fonts, sound packs). The value is a
 * stable id string; the GUI shows the matching label. Rendered as a dropdown.
 */
public class ChoiceSetting extends Setting<String> {
	/** One entry of the dropdown. */
	public record Choice(String id, String label) {
	}

	private final Supplier<List<Choice>> options;

	public ChoiceSetting(String name, String description, String defaultId, Supplier<List<Choice>> options) {
		super(name, description, defaultId);
		this.options = options;
	}

	public List<Choice> getOptions() {
		return options.get();
	}

	/** Label of the current value, or the raw id marked "(missing)" if it is not available now. */
	public String getLabel() {
		for (Choice choice : getOptions()) {
			if (choice.id().equals(value)) {
				return choice.label();
			}
		}
		return value + " (missing)";
	}

	public boolean isAvailable() {
		for (Choice choice : getOptions()) {
			if (choice.id().equals(value)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive()) {
			set(json.getAsString());
		}
	}
}
