package com.hazeline.setting;

import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A colour stored as packed ARGB ({@code 0xAARRGGBB}), matching Minecraft's
 * {@link net.minecraft.util.math.ColorHelper} convention. Persisted as a "#AARRGGBB" hex string.
 */
public class ColorSetting extends Setting<Integer> {
	public ColorSetting(String name, String description, int defaultArgb) {
		super(name, description, defaultArgb);
	}

	public int alpha() {
		return (value >>> 24) & 0xFF;
	}

	public int red() {
		return (value >> 16) & 0xFF;
	}

	public int green() {
		return (value >> 8) & 0xFF;
	}

	public int blue() {
		return value & 0xFF;
	}

	public void setChannels(int a, int r, int g, int b) {
		set(((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF));
	}

	/** Returns the colour as "#AARRGGBB". */
	public String toHex() {
		return String.format(Locale.ROOT, "#%08X", value);
	}

	/**
	 * Parses "#RRGGBB", "#AARRGGBB", "RRGGBB" or "AARRGGBB". A 6-digit value keeps the current alpha.
	 *
	 * @return true if the text was a valid colour
	 */
	public boolean setFromHex(String text) {
		String s = text.trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		try {
			if (s.length() == 6) {
				int rgb = (int) Long.parseLong(s, 16);
				set((alpha() << 24) | rgb);
				return true;
			} else if (s.length() == 8) {
				set((int) Long.parseLong(s, 16));
				return true;
			}
		} catch (NumberFormatException ignored) {
			// fall through
		}
		return false;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(toHex());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive()) {
			setFromHex(json.getAsString());
		}
	}
}
