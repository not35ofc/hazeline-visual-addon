package com.hazeline.module.modules.sky;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import com.hazeline.Hazeline;
import com.hazeline.setting.ChoiceSetting;
import com.hazeline.setting.Setting;

/**
 * Built-in and user-made Custom Sky presets.
 *
 * <p>A preset is just a map "setting name -> value" (the same JSON values the config uses), so a
 * preset can hold any of {@link CustomSky#presetSettings()}. Values a preset does not list keep
 * their current value. Built-in ids are {@code builtin:<key>}, saved ones {@code custom:<name>};
 * saved presets live in the hidden "Custom presets" setting, i.e. in {@code config.json} under
 * {@code modules > Custom Sky > settings > Custom presets}.
 *
 * <p>To add another built-in preset, add an entry to {@link #BUILT_IN} below.
 */
public final class SkyPresets {
	public static final String DEFAULT_ID = "builtin:dusk_rose";
	private static final String BUILTIN = "builtin:";
	private static final String CUSTOM = "custom:";

	/**
	 * key -> (label, values). Colours are 0xAARRGGBB, numbers in the settings' own units. Stars on
	 * screen ~= spawn rate x lifetime (a star lives for about 40 degrees of travel at 14 deg/s x speed),
	 * capped by Star count - the spawn rates below are chosen so each preset reaches its count.
	 */
	private static final Map<String, Preset> BUILT_IN = new LinkedHashMap<>();

	private record Preset(String label, JsonObject values) {
	}

	static {
		// 1. soft pink/purple gradient, slow soft stars, medium count
		builtIn("dusk_rose", "Dusk Rose", 0xFF5B3A7A, 0xFFF2A0C0, 85,
				0xFFFFE0F0, 60, 0.6, 1.0, 12.0, 1.0, 70, 25, 15, 30, 1.0);
		// 2. near-black navy, bright white fast stars, high count, stronger twinkle
		builtIn("deep_space", "Deep Space", 0xFF02030A, 0xFF0B1630, 60,
				0xFFFFFFFF, 160, 2.0, 1.1, 60.0, 1.4, 60, 30, 20, 70, 2.0);
		// 3. teal/cyan gradient, gentle soft cyan-white stars, low count, slow and smooth
		builtIn("ocean_teal", "Ocean Teal", 0xFF0E5C6B, 0xFF6FE3E0, 80,
				0xFFD9FFFF, 25, 0.5, 0.9, 4.0, 1.2, 85, 15, 10, 20, 0.8);
		// 4. warm amber/orange gradient, warm slow stars, low count, long soft trails
		builtIn("golden_hour", "Golden Hour", 0xFFC9662B, 0xFFFFC46B, 90,
				0xFFFFE2A8, 22, 0.45, 1.0, 3.0, 2.4, 90, 20, 12, 20, 0.6);
		// 5. deep violet/indigo gradient, violet-white stars, medium-high count, faster twinkle
		builtIn("aurora_violet", "Aurora Violet", 0xFF1B0F45, 0xFF6A3FC0, 75,
				0xFFE6D9FF, 110, 1.2, 1.0, 30.0, 1.2, 70, 30, 25, 50, 2.6);
	}

	private SkyPresets() {
	}

	private static void builtIn(String key, String label, int sky, int horizon, double gradientStrength, int starColor, double count,
			double speed, double size, double spawnRate, double trail, double smoothness, double angle, double range, double twinkleStrength,
			double twinkleSpeed) {
		JsonObject o = new JsonObject();
		o.add("Sky colour", color(sky));
		o.addProperty("Vertical gradient", true);
		o.add("Horizon colour", color(horizon));
		o.addProperty("Gradient strength", gradientStrength);
		o.addProperty("Day/night blending", true);
		o.addProperty("Time-of-day influence", 70.0);
		o.addProperty("Falling stars", true);
		o.addProperty("Star count", count);
		o.addProperty("Star speed", speed);
		o.addProperty("Star size", size);
		o.addProperty("Star colour mode", CustomSky.StarColorMode.CUSTOM.name());
		o.add("Star colour", color(starColor));
		o.addProperty("Trail length", trail);
		o.addProperty("Trail fade smoothness", smoothness);
		o.addProperty("Spawn rate", spawnRate);
		o.addProperty("Direction", CustomSky.DirectionMode.RANDOM.name());
		o.addProperty("Fall angle", angle);
		o.addProperty("Angle range", range);
		o.addProperty("Twinkle", true);
		o.addProperty("Twinkle strength", twinkleStrength);
		o.addProperty("Twinkle speed", twinkleSpeed);
		BUILT_IN.put(key, new Preset(label, o));
	}

	/** Same format as {@link com.hazeline.setting.ColorSetting#toJson()} ("#AARRGGBB"). */
	private static JsonElement color(int argb) {
		return new JsonPrimitive(String.format(Locale.ROOT, "#%08X", argb));
	}

	/** Dropdown entries: built-ins first, then saved presets. */
	static List<ChoiceSetting.Choice> choices(CustomSky sky) {
		List<ChoiceSetting.Choice> list = new ArrayList<>();
		BUILT_IN.forEach((key, p) -> list.add(new ChoiceSetting.Choice(BUILTIN + key, p.label())));
		for (String name : customs(sky).keySet()) {
			list.add(new ChoiceSetting.Choice(CUSTOM + name, name + " (custom)"));
		}
		return list;
	}

	/** Copies the preset's values into the settings. Unknown or malformed values are skipped. */
	static boolean apply(CustomSky sky, String id) {
		JsonObject values = null;
		if (id.startsWith(BUILTIN)) {
			Preset p = BUILT_IN.get(id.substring(BUILTIN.length()));
			values = p == null ? null : p.values();
		} else if (id.startsWith(CUSTOM)) {
			JsonElement e = customs(sky).get(id.substring(CUSTOM.length()));
			values = e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
		}
		if (values == null) {
			return false;
		}
		for (Setting<?> setting : sky.presetSettings()) {
			JsonElement v = values.get(setting.getName());
			if (v == null) {
				continue;
			}
			try {
				setting.fromJson(v);
			} catch (RuntimeException ex) {
				Hazeline.LOGGER.warn("Custom Sky preset {}: ignoring bad value for {}", id, setting.getName());
			}
		}
		return true;
	}

	/** Saves the current settings as a custom preset and selects it. Returns a status line. */
	static String saveCurrent(CustomSky sky, String rawName) {
		String name = rawName == null ? "" : rawName.trim();
		if (name.isEmpty()) {
			return "Type a name first";
		}
		JsonObject values = new JsonObject();
		for (Setting<?> setting : sky.presetSettings()) {
			values.add(setting.getName(), setting.toJson());
		}
		JsonObject all = customs(sky);
		boolean replaced = all.has(name);
		all.add(name, values);
		sky.customPresets.set(all.toString());
		sky.preset.set(CUSTOM + name);
		sky.presetName.set("");
		return (replaced ? "Updated preset " : "Saved preset ") + name;
	}

	/** Deletes the selected preset if it is a custom one; falls back to the default preset id. */
	static String deleteSelected(CustomSky sky) {
		String id = sky.preset.get();
		if (!id.startsWith(CUSTOM)) {
			return "Built-in presets can't be deleted";
		}
		String name = id.substring(CUSTOM.length());
		JsonObject all = customs(sky);
		if (all.remove(name) == null) {
			return "Preset not found";
		}
		sky.customPresets.set(all.toString());
		// Select the default without overwriting the current values (set() would apply it).
		sky.suppressPresetApply = true;
		try {
			sky.preset.set(DEFAULT_ID);
		} finally {
			sky.suppressPresetApply = false;
		}
		return "Deleted preset " + name;
	}

	/** Saved presets, parsed from the hidden setting (an empty object if it is missing or broken). */
	private static JsonObject customs(CustomSky sky) {
		try {
			JsonElement e = JsonParser.parseString(sky.customPresets.get());
			if (e.isJsonObject()) {
				return e.getAsJsonObject();
			}
		} catch (RuntimeException e) {
			Hazeline.LOGGER.warn("Custom Sky: saved presets are not valid JSON, ignoring them");
		}
		return new JsonObject();
	}
}
