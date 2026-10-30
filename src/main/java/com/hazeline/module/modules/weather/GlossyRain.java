package com.hazeline.module.modules.weather;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.ColorSetting;
import com.hazeline.setting.EnumSetting;
import com.hazeline.setting.NumberSetting;

/**
 * Glossy Rain: restyles how the ground looks in rain. The falling rain is not touched; the ground gets
 * a soft wet sheen (stronger in puddle spots and at grazing angles, never a mirror) and ripple rings
 * where drops land. It follows whatever rain is showing: the server's, or Hazeline's Weather module.
 * The sheen builds up while it rains and dries out slowly afterwards. Overworld only.
 */
public class GlossyRain extends Module {
	public final NumberSetting glossiness = add(new NumberSetting("Glossiness", "How shiny the wet ground looks (kept soft, never a mirror)", 55, 0, 100, 1));
	public final NumberSetting wetness = add(new NumberSetting("Wetness", "How wet the ground gets overall", 70, 0, 100, 1));
	public final NumberSetting puddles = add(new NumberSetting("Puddles", "How much of the ground collects extra-wet puddle spots", 45, 0, 100, 1));
	public final NumberSetting natural = add(new NumberSetting("Grass, dirt and sand gloss", "How much of the sheen natural ground gets (stone and wood get all of it)", 35, 0, 100, 1));
	public final ColorSetting sheenColor = add(new ColorSetting("Sheen colour", "Tint of the reflected light", 0xFFC4D6EC));
	public final NumberSetting skyTint = add(new NumberSetting("Sky tint", "How much the sheen picks up the colour of the sky", 40, 0, 100, 1));

	public final BooleanSetting ripples = add(new BooleanSetting("Ripple rings", "Little rings spreading where drops land on puddles", true));
	public final NumberSetting rippleAmount = add(new NumberSetting("Ripple amount", "How many rings at once", 55, 5, 100, 1), ripples::get);
	public final NumberSetting rippleSize = add(new NumberSetting("Ripple size", "How wide the rings grow", 1.0, 0.4, 2.5, 0.05), ripples::get);
	public final NumberSetting rippleSpeed = add(new NumberSetting("Ripple speed", "How fast the rings spread", 1.0, 0.4, 3.0, 0.05), ripples::get);

	public final NumberSetting range = add(new NumberSetting("Range", "How far around you the ground is made glossy, in blocks", 16, 6, 40, 1));
	public final NumberSetting soakTime = add(new NumberSetting("Soak time", "Seconds of rain until the ground is fully wet", 6.0, 1.0, 60.0, 0.5));
	public final NumberSetting dryTime = add(new NumberSetting("Dry-out time", "Seconds until the ground is dry again after the rain stops", 45, 3, 600, 1));
	public final EnumSetting<WeatherQuality> quality = add(new EnumSetting<>("Quality", "Scales how many ripples are drawn", WeatherQuality.HIGH));

	private static GlossyRain instance;

	public GlossyRain() {
		super("Glossy Rain", "A soft wet sheen and ripple rings on the ground while it rains", Category.WEATHER);
		instance = this;
	}

	public static GlossyRain get() {
		return instance;
	}

	public static boolean activeIn(ClientWorld world) {
		GlossyRain g = instance;
		return g != null && g.isEnabled() && world != null && world.getRegistryKey() == World.OVERWORLD;
	}

	public static boolean active() {
		return activeIn(MinecraftClient.getInstance().world);
	}
}
