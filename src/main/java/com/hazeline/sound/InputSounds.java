package com.hazeline.sound;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;

import com.hazeline.Hazeline;
import com.hazeline.appearance.SoundManager;
import com.hazeline.gui.ClickGuiScreen;
import com.hazeline.gui.GuiOverlay;
import com.hazeline.module.modules.sounds.SoundsAndClicks;

/**
 * Key and mouse sounds of the Sounds and Clicks module. {@code InputSoundsMixin} calls {@link #before}
 * at HEAD and {@link #after} at every RETURN of {@code Keyboard#onKey} and {@code Mouse#onMouseButton}
 * (1.21.11: {@code onKey(long window, int action, KeyInput)}, {@code onMouseButton(long window,
 * MouseInput, int action)}; action 1 = press, 0 = release, 2 = key repeat - ignored, so holding a key is
 * one sound).
 *
 * <p>The row is chosen when the input arrives: Click GUI row while the Hazeline menu (or its overlay)
 * is open, In game row with no screen or the chat screen, Game menus row for every other screen. In the
 * click GUI the menu's own action sounds ({@link SoundManager#play}) come first: a key press only clicks
 * when it did not already trigger an action sound (so closing with Esc plays the close sound once), a
 * mouse click only sounds when it hit something, and a release sound follows only a press that sounded.
 */
public final class InputSounds {
	private static final Identifier VANILLA_CLICK = Identifier.ofVanilla("ui.button.click");
	/** Mouse buttons are stored next to key codes (which are >= -1) under negative ids. */
	private static final int MOUSE_ID_BASE = -1000;

	private enum Place { GUI, IN_GAME, MENUS }

	/** A press that sounded and is waiting for its release. */
	private record Held(Place place, KeySound key) {
	}

	private static final Map<Integer, Held> held = new HashMap<>();
	private static Place pendingPlace;
	private static int pendingId;
	private static KeySound pendingKey;
	private static boolean pendingMouse;
	private static int countBefore;
	private static boolean failed;

	private InputSounds() {
	}

	public static void beforeKey(long window, int action, int key) {
		before(window, action, key, KeySound.ofKey(key), false);
	}

	public static void beforeMouse(long window, int button, int action) {
		before(window, action, MOUSE_ID_BASE - button, KeySound.GENERIC, true);
	}

	private static void before(long window, int action, int id, KeySound key, boolean mouse) {
		pendingPlace = null;
		if (failed || action == GLFW.GLFW_REPEAT) {
			return;
		}
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.getWindow() == null || window != mc.getWindow().getHandle()) {
				return;
			}
			if (action == GLFW.GLFW_RELEASE) {
				Held h = held.remove(id);
				SoundRow row = h == null ? null : row(h.place());
				if (row != null && row.audible() && row.releaseSound().get()) {
					SoundManager.get().playEvent(row, h.key().event(row.pack().get(), true), 1.0f, 0.9f, 0);
				}
				return;
			}
			if (action != GLFW.GLFW_PRESS || mc.getOverlay() != null) {
				return; // nothing while the loading overlay is shown
			}
			Place place = placeOf(mc.currentScreen);
			SoundRow row = row(place);
			if (row == null || !row.enabled().get()) {
				return;
			}
			if (place == Place.GUI) {
				// decided after the menu handled the input (see after())
				pendingPlace = place;
				pendingId = id;
				pendingKey = key;
				pendingMouse = mouse;
				countBefore = SoundManager.get().actionCount();
				return;
			}
			if (row.audible()) {
				SoundManager.get().playEvent(row, key.event(row.pack().get(), false), 1.0f, 1.0f, 0);
				held.put(id, new Held(place, key));
			}
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
	}

	/** RETURN of onKey / onMouseButton: finishes a click GUI press started in {@link #before}. */
	public static void after() {
		Place place = pendingPlace;
		pendingPlace = null;
		if (place == null || failed) {
			return;
		}
		try {
			SoundRow row = row(place);
			if (row == null) {
				return;
			}
			SoundManager sm = SoundManager.get();
			if (sm.actionCount() != countBefore) {
				if (sm.lastActionWasPress()) {
					held.put(pendingId, new Held(place, pendingKey));
				}
			} else if (!pendingMouse && row.audible()) {
				sm.playEvent(row, pendingKey.event(row.pack().get(), false), 1.0f, 1.0f, 0);
				held.put(pendingId, new Held(place, pendingKey));
			}
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
	}

	/** The row's "Test" button: a press, then the release 2 ticks later. */
	public static void test(SoundRow row) {
		SoundManager sm = SoundManager.get();
		SoundPack pack = row.pack().get();
		sm.playEvent(row, KeySound.GENERIC.event(pack, false), 1.0f, 1.0f, 0);
		if (row.releaseSound().get()) {
			sm.playEvent(row, KeySound.GENERIC.event(pack, true), 1.0f, 0.9f, 2);
		}
	}

	/**
	 * {@code VanillaClickMixin}: whether a vanilla {@code ui.button.click} should be dropped. Only on a
	 * screen (Game menus) and only with the module on: Mute vanilla always, Replace when the Game menus
	 * row actually makes a sound.
	 */
	public static boolean muteVanillaClick(Identifier id) {
		if (failed || !VANILLA_CLICK.equals(id)) {
			return false;
		}
		SoundsAndClicks module = SoundsAndClicks.get();
		if (module == null || !module.isEnabled() || MinecraftClient.getInstance().currentScreen == null) {
			return false;
		}
		return switch (module.vanillaClick.get()) {
			case MUTE -> true;
			case REPLACE -> module.menus.audible();
			case BOTH -> false;
		};
	}

	private static Place placeOf(Screen screen) {
		if (GuiOverlay.isOpen() || screen instanceof ClickGuiScreen) {
			return Place.GUI;
		}
		return screen == null || screen instanceof ChatScreen ? Place.IN_GAME : Place.MENUS;
	}

	/** The row for a place, or null while the module is off. */
	private static SoundRow row(Place place) {
		SoundsAndClicks module = SoundsAndClicks.get();
		if (module == null || !module.isEnabled()) {
			return null;
		}
		return switch (place) {
			case GUI -> module.gui;
			case IN_GAME -> module.inGame;
			case MENUS -> module.menus;
		};
	}

	private static void fail(Throwable e) {
		failed = true;
		held.clear();
		Hazeline.LOGGER.error("Sounds and Clicks: key/mouse sounds failed and are off for this session", e);
	}
}
