package com.hazeline.module.modules.weather;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.hazeline.setting.ChoiceSetting;

/** The built-in weather presets. Choosing one copies its values into the Weather settings (all stay editable). */
final class WeatherPresets {
	static final String DEFAULT_ID = "rain";

	private record Preset(String label, Consumer<Weather> values) {
	}

	private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();

	static {
		PRESETS.put("drizzle", new Preset("Drizzle", w -> base(w, Weather.Kind.RAIN, 30, 0.8, 8, 0.7, 0.8, 38, 35, 20, 10, 0)));
		PRESETS.put("rain", new Preset("Rain", w -> base(w, Weather.Kind.RAIN, 70, 1.0, 25, 1.0, 1.0, 60, 100, 45, 20, 0)));
		PRESETS.put("heavy_rain", new Preset("Heavy rain", w -> base(w, Weather.Kind.RAIN, 100, 1.25, 35, 1.2, 1.1, 70, 120, 60, 30, 0)));
		PRESETS.put("storm", new Preset("Storm", w -> base(w, Weather.Kind.THUNDERSTORM, 100, 1.4, 70, 1.3, 1.25, 72, 135, 70, 38, 0)));
		PRESETS.put("light_snow", new Preset("Light snow", w -> base(w, Weather.Kind.SNOW, 40, 0.8, 10, 1.0, 1.0, 85, 60, 40, 12, 0)));
		PRESETS.put("snowfall", new Preset("Snowfall", w -> base(w, Weather.Kind.SNOW, 75, 1.0, 22, 1.2, 1.0, 88, 80, 50, 22, 0)));
		PRESETS.put("blizzard", new Preset("Blizzard", w -> base(w, Weather.Kind.SNOW, 100, 1.9, 85, 1.0, 1.9, 92, 110, 75, 55, 10)));
		PRESETS.put("foggy_morning", new Preset("Foggy morning", w -> {
			base(w, Weather.Kind.CLEAR, 0, 1.0, 0, 1.0, 1.0, 60, 40, 60, 40, 70);
		}));
		PRESETS.put("clear", new Preset("Clear sky", w -> base(w, Weather.Kind.CLEAR, 0, 1.0, 0, 1.0, 1.0, 60, 100, 0, 0, 0)));
	}

	private WeatherPresets() {
	}

	/** Sets the settings every preset touches; the rest keep whatever the player chose. */
	private static void base(Weather w, Weather.Kind kind, double intensity, double speed, double wind, double size, double length,
			double opacity, double skyDark, double fogTint, double fogClose, double haze) {
		w.kind.set(kind);
		w.intensity.set(intensity);
		w.speed.set(speed);
		w.windStrength.set(wind);
		w.rainSize.set(size);
		w.snowSize.set(size);
		w.rainLength.set(length);
		if (kind == Weather.Kind.SNOW) {
			w.snowOpacity.set(opacity);
		} else {
			w.rainOpacity.set(opacity);
		}
		w.skyDarkness.set(skyDark);
		w.fogTint.set(fogTint);
		w.fogDistance.set(fogClose);
		w.haze.set(haze);
	}

	static List<ChoiceSetting.Choice> choices() {
		List<ChoiceSetting.Choice> list = new ArrayList<>();
		PRESETS.forEach((id, p) -> list.add(new ChoiceSetting.Choice(id, p.label())));
		return list;
	}

	static boolean apply(Weather w, String id) {
		Preset p = PRESETS.get(id);
		if (p == null) {
			return false;
		}
		p.values().accept(w);
		return true;
	}
}
