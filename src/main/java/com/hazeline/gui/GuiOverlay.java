package com.hazeline.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import com.hazeline.Hazeline;
import com.hazeline.appearance.SoundAction;
import com.hazeline.appearance.SoundManager;
import com.hazeline.config.ConfigManager;

/**
 * The click GUI as an <b>overlay</b> on top of any other screen (title screen, singleplayer /
 * multiplayer lists, options, inventory, crafting table, chests...).
 *
 * <p>The screen underneath ("host") stays open and keeps ticking, but while the overlay is open:
 * <ul>
 *   <li>it is drawn with the mouse far off-screen, so nothing under the menu is hovered and no
 *       item tooltips show ({@code ScreenOverlayMixin});</li>
 *   <li>the overlay is drawn after it in a new root layer ({@code ScreenOverlayMixin});</li>
 *   <li>every click / release / drag / scroll goes to the overlay only (Fabric API
 *       {@link ScreenMouseEvents} "allow" events, registered on every screen), and every key
 *       press / release / typed character goes to the overlay only ({@code KeyboardOverlayMixin}).
 *       So the host never sees the input: it cannot close, and items in a container cannot move.</li>
 * </ul>
 * Escape or the open-menu key closes only the overlay. When no screen is open (in game) the menu
 * is still opened as a normal screen, as before.
 */
public final class GuiOverlay {
	private static ClickGuiScreen overlay;
	private static Screen host;
	private static boolean renderFailed;

	private GuiOverlay() {
	}

	public static boolean isOpen() {
		return overlay != null;
	}

	/** True while the overlay is open on top of {@code screen}. */
	public static boolean isHost(Screen screen) {
		return overlay != null && screen != null && screen == host;
	}

	/** Registers the per-screen mouse hooks; call once from the client initialiser. */
	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ClickGuiScreen) {
				return;
			}
			// The host was re-initialised (window resized) or replaced by another screen
			// (e.g. the server opened a different container): keep the overlay on the current screen.
			if (overlay != null && client.currentScreen == screen) {
				host = screen;
				overlay.resize(width, height);
			}
			ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
				if (!isHost(s)) {
					tapScreen = null; // e.g. shift-click on an item while holding the menu key
					return true;
				}
				overlay.mouseClicked(click, false);
				return false;
			});
			ScreenMouseEvents.allowMouseRelease(screen).register((s, click) -> {
				if (!isHost(s)) {
					return true;
				}
				overlay.mouseReleased(click);
				return false;
			});
			ScreenMouseEvents.allowMouseDrag(screen).register((s, click, dx, dy) -> {
				if (!isHost(s)) {
					return true;
				}
				overlay.mouseDragged(click, dx, dy);
				return false;
			});
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, h, v) -> {
				if (!isHost(s)) {
					return true;
				}
				overlay.mouseScrolled(mx, my, h, v);
				return false;
			});
		});
	}

	// ------------------------------------------------------------------ open / close

	private static void open(Screen screen) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClickGuiScreen gui = new ClickGuiScreen();
		gui.setOverlayMode(true);
		overlay = gui;
		host = screen;
		tapScreen = null;
		renderFailed = false;
		gui.init(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
		SoundManager.get().play(SoundAction.OPEN);
	}

	/** Closes only the overlay; the screen underneath stays as it is. */
	public static void close() {
		if (overlay == null) {
			return;
		}
		overlay = null;
		host = null;
		SoundManager.get().play(SoundAction.CLOSE);
		ConfigManager.get().save();
	}

	/** Called every client tick. */
	public static void tick(MinecraftClient client) {
		if (overlay == null) {
			return;
		}
		Screen current = client.currentScreen;
		if (current == host) {
			return;
		}
		if (current == null) {
			// The screen underneath was closed by the game (e.g. the server closed the container):
			// keep the menu open as a normal screen.
			ClickGuiScreen gui = overlay;
			overlay = null;
			host = null;
			gui.setOverlayMode(false);
			client.setScreen(gui);
		} else if (current instanceof ClickGuiScreen) {
			overlay = null;
			host = null;
		} else {
			host = current;
			overlay.resize(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
		}
	}

	// ------------------------------------------------------------------ rendering

	/** Mouse position in GUI coordinates (the host is given a fake off-screen position). */
	private static int mouseX(MinecraftClient client) {
		Window w = client.getWindow();
		return (int) client.mouse.getScaledX(w);
	}

	private static int mouseY(MinecraftClient client) {
		Window w = client.getWindow();
		return (int) client.mouse.getScaledY(w);
	}

	/** Draws the overlay after {@code screen} (end of {@code Screen#renderWithTooltip}). */
	public static void render(Screen screen, DrawContext context, float delta) {
		if (!isHost(screen)) {
			return;
		}
		ClickGuiScreen gui = overlay;
		MinecraftClient client = MinecraftClient.getInstance();
		int mx = mouseX(client);
		int my = mouseY(client);
		try {
			// Same order as Screen#renderWithTooltip, each part in its own root layer so it is
			// drawn above everything of the screen underneath (text and items included).
			context.createNewRootLayer();
			gui.renderBackground(context, mx, my, delta);
			context.createNewRootLayer();
			gui.render(context, mx, my, delta);
			context.drawDeferredElements();
		} catch (Throwable t) {
			if (!renderFailed) {
				renderFailed = true;
				Hazeline.LOGGER.error("Hazeline: drawing the menu over {} failed; closing the overlay",
						screen.getClass().getName(), t);
			}
			overlay = null;
			host = null;
		}
	}

	// ------------------------------------------------------------------ keyboard

	/**
	 * Key press on {@code screen}. Returns null to let the screen handle it normally, otherwise the
	 * result to report (the screen does not see the key).
	 *
	 * <p>Opening: when the menu key is a modifier (Right Shift by default - also used to type
	 * capitals and for shift-clicking items) the menu opens when the key is <i>released</i> without any
	 * other key, typed character or click in between, so typing and shift-clicking still work. Any
	 * other menu key opens it on press, unless a text box has focus.
	 */
	public static Boolean keyPressed(Screen screen, KeyInput key) {
		if (screen instanceof ClickGuiScreen) {
			return null;
		}
		if (isHost(screen)) {
			overlay.keyPressed(key);
			return Boolean.TRUE;
		}
		if (overlay != null) {
			return null;
		}
		if (isOpenKey(key)) {
			if (isModifier(key.key())) {
				// Tap: wait for the release. Not while a vanilla key bind is being set or in pure
				// text screens (chat, signs, books).
				tapScreen = isTyping(screen, false) ? null : screen;
				return null;
			}
			if (!isTyping(screen, true)) {
				open(screen);
				return Boolean.TRUE;
			}
			return null;
		}
		tapScreen = null; // another key while the menu key is held (e.g. Shift + letter)
		return null;
	}

	public static Boolean keyReleased(Screen screen, KeyInput key) {
		if (screen instanceof ClickGuiScreen) {
			return null;
		}
		if (isHost(screen)) {
			overlay.keyReleased(key);
			return Boolean.TRUE;
		}
		if (overlay == null && tapScreen != null && tapScreen == screen && isOpenKey(key) && isModifier(key.key())) {
			tapScreen = null;
			open(screen);
			return Boolean.TRUE;
		}
		return null;
	}

	public static Boolean charTyped(Screen screen, CharInput input) {
		if (isHost(screen)) {
			overlay.charTyped(input);
			return Boolean.TRUE;
		}
		tapScreen = null;
		return null;
	}

	/** Screen on which the modifier menu key is held and was not used for anything else yet. */
	private static Screen tapScreen;

	private static boolean isOpenKey(KeyInput key) {
		return Hazeline.getOpenGuiKey() != null && Hazeline.getOpenGuiKey().matchesKey(key);
	}

	private static boolean isModifier(int key) {
		return key >= InputUtil.GLFW_KEY_LEFT_SHIFT && key <= InputUtil.GLFW_KEY_RIGHT_SUPER;
	}

	/**
	 * True when the screen is taking text (chat, signs, books, a focused text box such as the world
	 * search, a server address or the creative search), so the open key - Right Shift by default,
	 * also used to type capitals - must not open the menu there.
	 */
	static boolean isTyping(Screen screen, boolean textBoxes) {
		if (screen instanceof ChatScreen || screen instanceof AbstractSignEditScreen || screen instanceof BookEditScreen) {
			return true;
		}
		// Vanilla Controls screen waiting for a key to bind.
		if (screen instanceof KeybindsScreen keybinds && keybinds.selectedKeyBinding != null) {
			return true;
		}
		return textBoxes && hasFocusedTextBox(screen, 0);
	}

	private static boolean hasFocusedTextBox(ParentElement parent, int depth) {
		if (depth > 8) {
			return false;
		}
		for (Element e : parent.children()) {
			if (e instanceof TextFieldWidget field) {
				if (field.isFocused() && field.isVisible()) {
					return true;
				}
			} else if (e instanceof EditBoxWidget box) {
				if (box.isFocused() && box.visible) {
					return true;
				}
			} else if (e instanceof ParentElement child && hasFocusedTextBox(child, depth + 1)) {
				return true;
			}
		}
		return false;
	}
}
