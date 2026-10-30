package com.hazeline.module.modules.sky;

import java.util.ArrayList;
import java.util.List;

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
import com.hazeline.setting.DataSetting;
import com.hazeline.setting.EnumSetting;
import com.hazeline.setting.InfoSetting;
import com.hazeline.setting.NumberSetting;
import com.hazeline.setting.Setting;
import com.hazeline.setting.TextSetting;

/**
 * Custom Sky: replaces the Overworld sky with a smooth colour gradient and falling stars.
 *
 * <p>Only the Overworld is changed. The Nether has no sky pass at all and the End uses
 * {@code SkyRendering#renderEndSky}, which is never touched, so their skies (and any resource-pack
 * skyboxes drawn there) stay vanilla. How it is drawn is described in {@link CustomSkyRenderer}.
 *
 * <p>Presets: {@link SkyPresets} holds the five built-in presets and the ones you save. Choosing a
 * preset copies its values into the settings below, which all stay editable.
 */
public class CustomSky extends Module {
	public enum StarColorMode {
		CUSTOM("Custom colour"), SKY_ACCENT("Match sky accent");

		private final String label;

		StarColorMode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum DirectionMode {
		FIXED("Fixed angle"), RANDOM("Random within range");

		private final String label;

		DirectionMode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** Performance preset: scales star count and trail smoothness. */
	public enum Quality {
		LOW("Low", 0.4, 4), MEDIUM("Medium", 0.7, 8), HIGH("High", 1.0, 14);

		private final String label;
		public final double countScale;
		public final int trailSegments;

		Quality(String label, double countScale, int trailSegments) {
			this.label = label;
			this.countScale = countScale;
			this.trailSegments = trailSegments;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** Hard safety cap on active falling stars, whatever the settings say. */
	public static final int MAX_ACTIVE_STARS = 200;

	private String status = "";
	/** Set while the preset id changes without loading it (after deleting a preset). */
	boolean suppressPresetApply;

	// ------------------------------------------------------------------ presets (registered first, see SkyPresets)
	public final ChoiceSetting preset = add(new ChoiceSetting("Preset", "Load a saved combination of the settings below (still editable)",
			SkyPresets.DEFAULT_ID, () -> SkyPresets.choices(this)));
	public final ActionSetting applyPreset = add(new ActionSetting("Apply preset again", "Restore the selected preset's values",
			() -> setStatus(SkyPresets.apply(this, preset.get()) ? "Applied " + preset.getLabel() : "Preset not found")));

	// ------------------------------------------------------------------ sky colours
	public final ColorSetting skyColor = add(new ColorSetting("Sky colour", "Main sky colour (top of the sky), with transparency", 0xFF5B3A7A));
	public final BooleanSetting gradient = add(new BooleanSetting("Vertical gradient", "Blend to a second colour towards the horizon", true));
	public final ColorSetting horizonColor = add(new ColorSetting("Horizon colour", "Colour at the horizon", 0xFFF2A0C0), gradient::get);
	public final NumberSetting gradientStrength = add(new NumberSetting("Gradient strength",
			"How much the horizon colour shows (0 = one colour, 100 = full horizon colour)", 85, 0, 100, 1), gradient::get);
	public final BooleanSetting dayNight = add(new BooleanSetting("Day/night blending",
			"Let the sky dim smoothly at night and in rain instead of staying the same", true));
	public final NumberSetting timeInfluence = add(new NumberSetting("Time-of-day influence",
			"0 = always the set colour, 100 = follows vanilla's light cycle fully", 70, 0, 100, 1), dayNight::get);
	public final BooleanSetting showSun = add(new BooleanSetting("Show sun", "Keep the vanilla sun", true));
	public final BooleanSetting showMoon = add(new BooleanSetting("Show moon", "Keep the vanilla moon", true));
	public final BooleanSetting showClouds = add(new BooleanSetting("Show clouds", "Keep the vanilla clouds", true));
	public final BooleanSetting matchFog = add(new BooleanSetting("Match fog to sky",
			"Blend the distance fog into the horizon colour (only in open air)", true));

	// ------------------------------------------------------------------ falling stars
	public final BooleanSetting stars = add(new BooleanSetting("Falling stars", "Soft glowing shooting stars with trails", true));
	public final NumberSetting starCount = add(new NumberSetting("Star count", "Most stars on screen at once", 60, 0, 200, 1), stars::get);
	public final NumberSetting starSpeed = add(new NumberSetting("Star speed", "How fast they fall", 0.6, 0.1, 4.0, 0.05), stars::get);
	public final NumberSetting starSize = add(new NumberSetting("Star size", "Size of each star", 1.0, 0.2, 3.0, 0.05), stars::get);
	public final EnumSetting<StarColorMode> starColorMode = add(new EnumSetting<>("Star colour mode",
			"Use a custom colour or a light version of the sky's accent colour", StarColorMode.CUSTOM), stars::get);
	public final ColorSetting starColor = add(new ColorSetting("Star colour", "Colour of the stars and trails", 0xFFFFE0F0),
			() -> stars.get() && starColorMode.get() == StarColorMode.CUSTOM);
	public final NumberSetting trailLength = add(new NumberSetting("Trail length", "Length of the glowing trail (0 = no trail)", 1.0, 0, 3.0, 0.05), stars::get);
	public final NumberSetting trailSmoothness = add(new NumberSetting("Trail fade smoothness",
			"How gently the trail fades out (higher = longer, softer fade)", 70, 0, 100, 1), stars::get);
	public final NumberSetting spawnRate = add(new NumberSetting("Spawn rate", "New stars per second", 12.0, 0.1, 60.0, 0.1), stars::get);
	public final EnumSetting<DirectionMode> directionMode = add(new EnumSetting<>("Direction", "One fixed fall angle, or random angles in a range",
			DirectionMode.RANDOM), stars::get);
	public final NumberSetting fallAngle = add(new NumberSetting("Fall angle", "Degrees from straight down (negative = to the left)", 25, -80, 80, 1), stars::get);
	public final NumberSetting angleRange = add(new NumberSetting("Angle range", "Random spread around the fall angle, in degrees", 15, 0, 90, 1),
			() -> stars.get() && directionMode.get() == DirectionMode.RANDOM);
	public final BooleanSetting twinkle = add(new BooleanSetting("Twinkle", "Stars gently vary in brightness", true), stars::get);
	public final NumberSetting twinkleStrength = add(new NumberSetting("Twinkle strength", "How much the brightness varies", 30, 0, 100, 1),
			() -> stars.get() && twinkle.get());
	public final NumberSetting twinkleSpeed = add(new NumberSetting("Twinkle speed", "Brightness changes per second", 1.0, 0.1, 5.0, 0.1),
			() -> stars.get() && twinkle.get());

	// ------------------------------------------------------------------ performance (not part of presets)
	public final EnumSetting<Quality> quality = add(new EnumSetting<>("Quality",
			"Scales star count and trail smoothness (" + MAX_ACTIVE_STARS + " stars is the hard cap)", Quality.HIGH));

	// ------------------------------------------------------------------ your own presets
	public final TextSetting presetName = add(new TextSetting("New preset name", "Name for \"Save current as preset\"", "", 24, false));
	public final ActionSetting savePreset = add(new ActionSetting("Save current as preset", "Store the current sky settings under the name above",
			() -> setStatus(SkyPresets.saveCurrent(this, presetName.get()))));
	public final ActionSetting deletePreset = add(new ActionSetting("Delete selected preset", "Remove the selected custom preset",
			() -> setStatus(SkyPresets.deleteSelected(this))));
	public final InfoSetting presetStatus = add(new InfoSetting("Status", "Result of the last preset action", () -> status),
			() -> !status.isEmpty());
	/** Saved custom presets as JSON ({"name": {setting: value, ...}, ...}); hidden, see {@link DataSetting}. */
	public final DataSetting customPresets = add(new DataSetting("Custom presets", "{}"));

	private static CustomSky instance;

	public CustomSky() {
		super("Custom Sky", "Replace the Overworld sky with a smooth gradient and falling stars", Category.SKY);
		instance = this;
		preset.onChange(() -> {
			if (!suppressPresetApply && SkyPresets.apply(this, preset.get())) {
				ModuleManager.get().markDirty();
			}
		});
	}

	public static CustomSky get() {
		return instance;
	}

	void setStatus(String text) {
		status = text == null ? "" : text;
		ModuleManager.get().markDirty();
	}

	/** Every setting a preset stores, in order (everything except preset handling and Quality). */
	List<Setting<?>> presetSettings() {
		List<Setting<?>> list = new ArrayList<>();
		for (Setting<?> s : List.of(skyColor, gradient, horizonColor, gradientStrength, dayNight, timeInfluence, showSun, showMoon,
				showClouds, matchFog, stars, starCount, starSpeed, starSize, starColorMode, starColor, trailLength, trailSmoothness,
				spawnRate, directionMode, fallAngle, angleRange, twinkle, twinkleStrength, twinkleSpeed)) {
			list.add(s);
		}
		return list;
	}

	/** True while the custom sky replaces this world's sky (module on and the world is the Overworld). */
	public static boolean activeIn(ClientWorld world) {
		CustomSky sky = instance;
		return sky != null && sky.isEnabled() && world != null && world.getRegistryKey() == World.OVERWORLD;
	}

	/** {@link #activeIn} for the client's current world. */
	public static boolean active() {
		return activeIn(MinecraftClient.getInstance().world);
	}

	@Override
	protected void onDisable() {
		FallingStars.get().clear();
	}
}
