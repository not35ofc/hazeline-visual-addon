package com.hazeline.module.modules.weather;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.ColorSetting;
import com.hazeline.setting.NumberSetting;
import com.hazeline.setting.EnumSetting;

/**
 * Mist: soft low fog that hugs the ground around you, drifting wisps, a gentle distance haze and
 * (optionally) light shafts and a glow around the sun. Always around the player, independent of the
 * weather. Overworld only.
 */
public class Mist extends Module {
	public final NumberSetting density = add(new NumberSetting("Density", "How thick the mist is", 50, 0, 100, 1));
	public final NumberSetting height = add(new NumberSetting("Height", "How high the low mist reaches above the ground, in blocks", 4.0, 1.0, 20.0, 0.5));
	public final NumberSetting wisps = add(new NumberSetting("Wisps", "How many drifting mist clouds you see", 60, 0, 100, 1));
	public final NumberSetting drift = add(new NumberSetting("Drift speed", "How fast the mist moves", 0.6, 0.0, 3.0, 0.05));
	public final NumberSetting driftAngle = add(new NumberSetting("Drift direction", "Compass direction the mist moves towards (0 = north, 90 = east)", 90, 0, 359, 1),
			() -> drift.get() > 0);
	public final NumberSetting range = add(new NumberSetting("Range", "How far around you the mist is drawn, in blocks", 28, 10, 48, 1));

	public final ColorSetting color = add(new ColorSetting("Colour", "Mist colour", 0xFFDCE6F0));
	public final BooleanSetting matchSky = add(new BooleanSetting("Match sky", "Blend the mist towards the sky and fog colour so it sits naturally in the scene", true));
	public final BooleanSetting timeTint = add(new BooleanSetting("Time-of-day tint", "Darker at night, warmer at sunrise and sunset", true));

	public final BooleanSetting haze = add(new BooleanSetting("Distance haze", "Pull the distance fog in and tint it with the mist colour", true));
	public final NumberSetting hazeAmount = add(new NumberSetting("Haze amount", "How strong the distance haze is", 40, 0, 100, 1), haze::get);

	public final BooleanSetting shafts = add(new BooleanSetting("Light shafts", "A soft glow and light rays around the sun while mist hangs in the air", true));
	public final NumberSetting shaftStrength = add(new NumberSetting("Shaft strength", "How bright the glow and rays are", 50, 0, 100, 1), shafts::get);

	public final EnumSetting<WeatherQuality> quality = add(new EnumSetting<>("Quality", "Scales how many mist clouds are drawn (hard cap " + MAX_PUFFS + ")",
			WeatherQuality.HIGH));

	public static final int MAX_PUFFS = 420;

	private static Mist instance;

	public Mist() {
		super("Mist", "Soft low mist around you, drifting wisps, distance haze and light shafts", Category.WEATHER);
		instance = this;
	}

	public static Mist get() {
		return instance;
	}

	public static boolean activeIn(ClientWorld world) {
		Mist m = instance;
		return m != null && m.isEnabled() && world != null && world.getRegistryKey() == World.OVERWORLD;
	}

	public static boolean active() {
		return activeIn(MinecraftClient.getInstance().world);
	}
}
