package com.hazeline.module.modules.sounds;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.hazeline.Hazeline;
import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.setting.ActionSetting;
import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.EnumSetting;
import com.hazeline.setting.NumberSetting;
import com.hazeline.sound.InputSounds;
import com.hazeline.sound.SoundPack;
import com.hazeline.sound.SoundRow;

/**
 * Sounds and Clicks (1.35.0): keyboard sounds for three places, one sound pack per row.
 * <ul>
 *   <li><b>Click GUI</b> - the Hazeline menu, also as overlay on another screen (replaces the old
 *       Settings &gt; Sounds card; that choice is migrated once by ConfigManager);</li>
 *   <li><b>In game</b> - every key press and mouse click while playing (no screen open), and typing in chat;</li>
 *   <li><b>Game menus</b> - every other screen: title screen, singleplayer, multiplayer, options, pause menu,
 *       inventory, containers... with a choice what happens to the vanilla button click.</li>
 * </ul>
 * Holding a key plays one sound per press (key repeat is ignored). Input hooks: {@code InputSoundsMixin}
 * (Keyboard#onKey, Mouse#onMouseButton) and {@code VanillaClickMixin} (SoundManager#play); logic in
 * {@link InputSounds}. Switching the module off silences every row and leaves vanilla clicks alone.
 */
public class SoundsAndClicks extends Module {
	public enum VanillaClick {
		REPLACE("Replace"), BOTH("Play both"), MUTE("Mute vanilla");

		private final String label;

		VanillaClick(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private static volatile SoundsAndClicks instance;

	public final SoundRow gui = row("Click GUI", "the Hazeline menu (also when it is open on top of another screen)", true);
	public final SoundRow inGame = row("In game", "key presses and mouse clicks while playing, and typing in chat", false);
	public final SoundRow menus = row("Game menus", "main menu, singleplayer, multiplayer, options, pause menu, inventory and other screens", false);
	public final EnumSetting<VanillaClick> vanillaClick = add(new EnumSetting<>("Vanilla button click",
			"Game menus: Replace = vanilla click only muted while the Game menus row makes a sound, Play both = keep it, "
					+ "Mute vanilla = always muted", VanillaClick.REPLACE));

	public SoundsAndClicks() {
		super("Sounds and Clicks", "Keyboard click sounds for the click GUI, in game and in the game menus", Category.SOUNDS, true);
		instance = this;
	}

	/** The registered module, or null before ModuleManager.init(). */
	public static SoundsAndClicks get() {
		return instance;
	}

	private SoundRow row(String label, String where, boolean on) {
		BooleanSetting enabled = add(new BooleanSetting(label + " sounds", "Play sounds for " + where, on));
		EnumSetting<SoundPack> pack = add(new EnumSetting<>(label + " pack", "Sound pack of this row", SoundPack.CLICKY),
				enabled::get);
		NumberSetting volume = add(new NumberSetting(label + " volume",
				"Volume in percent (multiplied by Minecraft's Master volume)", on ? 60 : 50, 0, 100, 1), enabled::get);
		BooleanSetting pitch = add(new BooleanSetting(label + " pitch variation",
				"Vary the pitch slightly so repeated clicks don't sound identical", true), enabled::get);
		BooleanSetting release = add(new BooleanSetting(label + " release sound",
				"Also play the key's release sound when you let go of the key or mouse button "
						+ "(the Clicky/Creamy menu sounds already contain it)", true), enabled::get);
		SoundRow row = new SoundRow(label, enabled, pack, volume, pitch, release);
		add(new ActionSetting("Test " + label + " sound", "Plays a key press and release with this row's settings",
				() -> InputSounds.test(row)), enabled::get);
		return row;
	}

	/**
	 * One-time migration of the old Settings &gt; Sounds card (Appearance "sounds" block, before 1.35.0)
	 * into the Click GUI row. Called by ConfigManager when the config has no entry for this module yet.
	 */
	public void migrateFromAppearance(JsonObject sounds) {
		try {
			JsonElement pack = sounds.get("Sound pack");
			SoundPack migrated = SoundPack.fromLegacy(pack != null && pack.isJsonPrimitive() ? pack.getAsString() : null);
			if (migrated == null) {
				gui.enabled().set(false);
			} else {
				gui.pack().set(migrated);
			}
			gui.volume().fromJson(sounds.get("Volume"));
			gui.pitchVariation().fromJson(sounds.get("Random pitch variation"));
			Hazeline.LOGGER.info("Sounds and Clicks: took over the old menu sound settings (pack {}, volume {})",
					migrated == null ? "none" : migrated.id(), gui.volume().get());
		} catch (RuntimeException e) {
			Hazeline.LOGGER.warn("Sounds and Clicks: could not migrate the old menu sound settings: {}", e.toString());
		}
	}
}
