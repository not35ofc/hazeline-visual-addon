package com.hazeline.module.modules.weather;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.setting.ActionSetting;
import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.ColorSetting;
import com.hazeline.setting.EnumSetting;
import com.hazeline.setting.NumberSetting;

/**
 * Smooth Thunder: your own gentle client-side thunderstorm. While a thunderstorm is showing (the
 * server's, or Hazeline's Weather module) it draws lightning bolts in the distance, a soft flash that
 * brightens the sky and fades slowly, a glow in the clouds, optional screen shake and vignette, and a
 * low rolling thunder sound that arrives after the light. Overworld only; nothing is sent to the server.
 */
public class SmoothThunder extends Module {
	public enum Direction {
		RANDOM("Anywhere around you"), AHEAD("In front of you"), FIXED("Fixed direction");

		private final String label;

		Direction(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	// ------------------------------------------------------------------ when and where
	public final NumberSetting gap = add(new NumberSetting("Strike gap", "Average seconds between strikes while a thunderstorm is showing", 14.0, 2.0, 120.0, 0.5));
	public final EnumSetting<Direction> direction = add(new EnumSetting<>("Direction", "Where the lightning strikes around you", Direction.RANDOM));
	public final NumberSetting fixedAngle = add(new NumberSetting("Fixed direction", "Compass direction of the strikes in degrees (0 = north, 90 = east)", 0, 0, 359, 1),
			() -> direction.get() == Direction.FIXED);
	public final NumberSetting spread = add(new NumberSetting("Direction spread", "How wide the strikes scatter around that direction, in degrees", 70, 5, 180, 1),
			() -> direction.get() != Direction.RANDOM);
	public final NumberSetting minDistance = add(new NumberSetting("Closest strike", "Nearest distance of a strike, in blocks", 60, 20, 250, 1));
	public final NumberSetting maxDistance = add(new NumberSetting("Farthest strike", "Farthest distance of a strike, in blocks", 230, 60, 450, 1));

	// ------------------------------------------------------------------ the bolt
	public final ColorSetting boltColor = add(new ColorSetting("Bolt colour", "Colour of the lightning", 0xFFD9E5FF));
	public final NumberSetting boltBrightness = add(new NumberSetting("Bolt brightness", "How bright the bolt is", 100, 20, 200, 1));
	public final NumberSetting boltThickness = add(new NumberSetting("Bolt thickness", "How thick the bolt looks", 1.0, 0.3, 3.0, 0.05));
	public final NumberSetting branching = add(new NumberSetting("Branching", "How many side branches the bolt grows (0 = a single strand)", 50, 0, 100, 1));

	// ------------------------------------------------------------------ flash
	public final NumberSetting flashStrength = add(new NumberSetting("Sky flash", "How much the sky brightens in a strike", 45, 0, 100, 1));
	public final ColorSetting flashColor = add(new ColorSetting("Flash colour", "Colour of the sky flash", 0xFFE6EEFF), () -> flashStrength.get() > 0);
	public final NumberSetting flashFade = add(new NumberSetting("Flash fade", "Seconds the flash takes to fade away (slower = smoother)", 1.3, 0.3, 4.0, 0.05),
			() -> flashStrength.get() > 0);
	public final NumberSetting cloudGlow = add(new NumberSetting("Cloud glow", "Soft flickering glow where the bolt lights the clouds", 60, 0, 100, 1));
	public final NumberSetting vignette = add(new NumberSetting("Screen-edge flash", "A brief bright vignette at the screen edges for close strikes", 30, 0, 100, 1));
	public final NumberSetting shake = add(new NumberSetting("Screen shake", "How much the camera trembles for close strikes", 25, 0, 100, 1));
	public final NumberSetting shakeRange = add(new NumberSetting("Shake range", "Strikes closer than this shake the screen, in blocks", 110, 40, 300, 1), () -> shake.get() > 0);

	// ------------------------------------------------------------------ sound
	public final BooleanSetting thunderSound = add(new BooleanSetting("Thunder sound", "A low rolling thunder that follows each strike", true));
	public final NumberSetting thunderVolume = add(new NumberSetting("Thunder volume", "Loudness of the thunder", 70, 0, 100, 1), thunderSound::get);
	public final BooleanSetting soundDelay = add(new BooleanSetting("Delay by distance", "Thunder arrives after the light, later for farther strikes", true), thunderSound::get);
	public final BooleanSetting muteServer = add(new BooleanSetting("Replace server thunder", "Swap the server's loud thunder sound for this gentle one", true));

	public final ActionSetting test = add(new ActionSetting("Test strike", "Make one strike happen now (in a world)", () -> ThunderStrikes.testStrike()));

	private static SmoothThunder instance;

	public SmoothThunder() {
		super("Smooth Thunder", "Gentle client-side lightning: soft flashes, cloud glow, rolling thunder", Category.WEATHER);
		instance = this;
	}

	public static SmoothThunder get() {
		return instance;
	}

	public static boolean activeIn(ClientWorld world) {
		SmoothThunder t = instance;
		return t != null && t.isEnabled() && world != null && world.getRegistryKey() == World.OVERWORLD;
	}

	public static boolean active() {
		return activeIn(MinecraftClient.getInstance().world);
	}
}
