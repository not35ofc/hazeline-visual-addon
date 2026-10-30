package com.hazeline.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * One choice out of a Java enum. Rendered as a dropdown. Enum constants can override
 * {@code toString()} to provide a friendlier display name.
 */
public class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] constants;

	public EnumSetting(String name, String description, E defaultValue) {
		super(name, description, defaultValue);
		this.constants = defaultValue.getDeclaringClass().getEnumConstants();
	}

	public E[] getConstants() {
		return constants;
	}

	/** Selects the constant at {@code index} (used by the dropdown, avoids generic casts). */
	public void setIndex(int index) {
		if (index >= 0 && index < constants.length) {
			set(constants[index]);
		}
	}

	/** Advances to the next constant (wrapping). */
	public void cycle(int direction) {
		int i = Math.floorMod(value.ordinal() + direction, constants.length);
		set(constants[i]);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value.name());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json == null || !json.isJsonPrimitive()) {
			return;
		}
		String name = json.getAsString();
		for (E constant : constants) {
			if (constant.name().equalsIgnoreCase(name)) {
				set(constant);
				return;
			}
		}
	}
}
