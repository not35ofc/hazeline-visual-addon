package com.hazeline.module.modules.weather;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.module.ModuleManager;
import com.hazeline.setting.ActionSetting;
import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.ChoiceSetting;
import com.hazeline.setting.ColorSetting;
import com.hazeline.setting.EnumSetting;
import com.hazeline.setting.InfoSetting;
import com.hazeline.setting.NumberSetting;
import com.hazeline.setting.WeatherPreviewSetting;

/**
 * Weather: forces the weather you SEE, on your screen only (nothing is sent to the server and the
 * server's weather and gameplay are untouched). While it is on, vanilla rain and snow are not drawn
 * at all: Hazeline draws its own, with adjustable amount, speed, wind, size, colour and opacity, a
 * smooth fade between states, optional sky darkening / fog tint, and its own rain and wind ambience.
 * Overworld only. How it is drawn is described in {@link WeatherDraw}; the smoothing and the choice of
 * what to show is {@link WeatherController}.
 */
public class Weather extends Module {
	/** What to show. */
	public enum Kind {
		FOLLOW("Follow server"), CLEAR("Clear"), RAIN("Rain"), THUNDERSTORM("Thunderstorm"), SNOW("Snow");

		private final String label;

		Kind(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** Hard safety caps on drawn particles, whatever the settings say. */
	public static final int MAX_RAIN = 6000;
	public static final int MAX_SNOW = 4500;

	// ------------------------------------------------------------------ presets (registered first, like Custom Sky)
	public final ChoiceSetting preset = add(new ChoiceSetting("Preset", "Load a ready-made weather (every value stays editable)",
			WeatherPresets.DEFAULT_ID, WeatherPresets::choices));
	public final ActionSetting applyPreset = add(new ActionSetting("Apply preset again", "Restore the selected preset's values",
			() -> WeatherPresets.apply(this, preset.get())));
	public final WeatherPreviewSetting livePreview = add(new WeatherPreviewSetting("Preview", "Live look of the current weather settings"));

	// ------------------------------------------------------------------ what to show
	public final EnumSetting<Kind> kind = add(new EnumSetting<>("Weather",
			"What you see while this module is on (the server's own weather is ignored, except in Follow server)", Kind.RAIN));
	public final NumberSetting intensity = add(new NumberSetting("Intensity",
			"How strong the weather is (in Follow server: 100 = exactly what the server has)", 70, 0, 100, 1), () -> kind.get() != Kind.CLEAR);
	public final NumberSetting fadeTime = add(new NumberSetting("Fade time",
			"Seconds to blend smoothly between weathers", 4.0, 0.2, 30.0, 0.1));
	public final NumberSetting density = add(new NumberSetting("Amount",
			"How many drops or flakes at full intensity (100 = default)", 100, 20, 250, 1), () -> kind.get() != Kind.CLEAR);
	public final NumberSetting speed = add(new NumberSetting("Fall speed", "How fast drops and flakes fall", 1.0, 0.3, 3.0, 0.05),
			() -> kind.get() != Kind.CLEAR);

	// ------------------------------------------------------------------ wind
	public final NumberSetting windStrength = add(new NumberSetting("Wind strength",
			"How far the weather leans with the wind (0 = straight down)", 25, 0, 100, 1), () -> kind.get() != Kind.CLEAR);
	public final NumberSetting windAngle = add(new NumberSetting("Wind direction",
			"Compass direction the wind blows towards in degrees (0 = north, 90 = east, 180 = south, 270 = west)", 90, 0, 359, 1),
			() -> kind.get() != Kind.CLEAR && windStrength.get() > 0);

	// ------------------------------------------------------------------ rain look
	public final NumberSetting rainSize = add(new NumberSetting("Rain thickness", "Width of each rain streak", 1.0, 0.3, 3.0, 0.05), this::showsRain);
	public final NumberSetting rainLength = add(new NumberSetting("Rain length", "Length of each rain streak", 1.0, 0.3, 3.0, 0.05), this::showsRain);
	public final ColorSetting rainColor = add(new ColorSetting("Rain colour", "Colour of the rain", 0xFFB9D3F5), this::showsRain);
	public final NumberSetting rainOpacity = add(new NumberSetting("Rain opacity", "How see-through the rain is", 60, 5, 100, 1), this::showsRain);

	// ------------------------------------------------------------------ snow look
	public final NumberSetting snowSize = add(new NumberSetting("Snowflake size", "Size of each flake", 1.0, 0.3, 3.0, 0.05), this::showsSnow);
	public final NumberSetting snowDrift = add(new NumberSetting("Flake sway", "How much the flakes wobble on the way down", 55, 0, 100, 1), this::showsSnow);
	public final ColorSetting snowColor = add(new ColorSetting("Snow colour", "Colour of the snow", 0xFFFFFFFF), this::showsSnow);
	public final NumberSetting snowOpacity = add(new NumberSetting("Snow opacity", "How see-through the snow is", 85, 5, 100, 1), this::showsSnow);

	// ------------------------------------------------------------------ sky + fog
	public final NumberSetting skyDarkness = add(new NumberSetting("Sky darkness",
			"How much the weather darkens the sky, clouds and light (100 = vanilla storm)", 100, 0, 150, 1));
	public final NumberSetting fogTint = add(new NumberSetting("Fog tint", "How much the distance fog turns to the fog colour in bad weather", 45, 0, 100, 1));
	public final ColorSetting fogColor = add(new ColorSetting("Fog colour", "Colour the fog moves towards", 0xFF8C98AA), () -> fogTint.get() > 0 || this.haze.get() > 0);
	public final NumberSetting fogDistance = add(new NumberSetting("Fog closeness",
			"How much closer the fog comes in bad weather", 20, 0, 100, 1));
	public final NumberSetting haze = add(new NumberSetting("Constant haze",
			"Fog tint and closeness that stay even in clear weather (for hazy-morning looks)", 0, 0, 100, 1));

	// ------------------------------------------------------------------ sound
	public final BooleanSetting ambience = add(new BooleanSetting("Rain ambience", "Soft rain sound that follows the weather (replaces vanilla's)", true), this::showsRain);
	public final NumberSetting ambienceVolume = add(new NumberSetting("Rain volume", "Loudness of the rain sound", 55, 0, 100, 1),
			() -> showsRain() && ambience.get());
	public final BooleanSetting windSound = add(new BooleanSetting("Wind ambience", "Gentle wind that grows with the wind strength and the storm", true));
	public final NumberSetting windVolume = add(new NumberSetting("Wind volume", "Loudness of the wind sound", 45, 0, 100, 1), windSound::get);
	public final BooleanSetting splashes = add(new BooleanSetting("Ground splashes", "Little splashes where the rain lands", true), this::showsRain);

	// ------------------------------------------------------------------ performance
	public final EnumSetting<WeatherQuality> quality = add(new EnumSetting<>("Quality",
			"Scales how many drops and flakes are drawn (hard caps: " + MAX_RAIN + " streaks, " + MAX_SNOW + " flakes)", WeatherQuality.HIGH));
	public final NumberSetting range = add(new NumberSetting("Render range", "How far around you the weather is drawn, in blocks", 24, 8, 48, 1));

	public final InfoSetting safeMode = add(new InfoSetting("Safe mode",
			"Hazeline's weather hit a problem and handed weather back to vanilla for this session (details are in the log)",
			WeatherController::failure), WeatherController::isFailed);

	private static Weather instance;

	public Weather() {
		super("Weather", "Show the weather you want: rain, thunderstorm, snow or clear, drawn by Hazeline instead of vanilla", Category.WEATHER);
		instance = this;
		preset.onChange(() -> {
			if (WeatherPresets.apply(this, preset.get())) {
				ModuleManager.get().markDirty();
			}
		});
	}

	public static Weather get() {
		return instance;
	}

	boolean showsRain() {
		return kind.get() != Kind.SNOW && kind.get() != Kind.CLEAR;
	}

	boolean showsSnow() {
		return kind.get() == Kind.SNOW || kind.get() == Kind.FOLLOW;
	}

	/** True while this module is on and the world is the Overworld. */
	public static boolean activeIn(ClientWorld world) {
		Weather w = instance;
		return w != null && w.isEnabled() && world != null && world.getRegistryKey() == World.OVERWORLD;
	}

	public static boolean active() {
		return activeIn(MinecraftClient.getInstance().world);
	}

	/** Wind direction as a unit vector in the XZ plane (x, z): compass 0 = north (-Z), 90 = east (+X). */
	public float windX() {
		return (float) Math.sin(Math.toRadians(windAngle.getFloat()));
	}

	public float windZ() {
		return (float) -Math.cos(Math.toRadians(windAngle.getFloat()));
	}
}
