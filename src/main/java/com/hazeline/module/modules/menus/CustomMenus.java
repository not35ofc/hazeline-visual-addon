package com.hazeline.module.modules.menus;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;

import com.hazeline.Hazeline;
import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.EnumSetting;
import com.hazeline.setting.NumberSetting;
import com.hazeline.setting.TextSetting;

/**
 * Custom Menus: the loading screen, the main menu and the other menu screens can use the
 * Custom Sky look (gradient + falling stars, see {@link MenuSky}) instead of vanilla's backgrounds.
 *
 * <ul>
 *   <li><b>Loading screen</b> ({@code SplashOverlay}): the Mojang logo and red background are replaced by the
 *       sky, a "Hazeline" wordmark and a thin rounded progress bar with a glow in the theme accent colour.</li>
 *   <li><b>Menus</b> ({@code Screen#renderPanoramaBackground}): the rotating panorama behind the main menu, the
 *       multiplayer list, the world list and every other menu opened outside a world.</li>
 *   <li><b>Main menu logo</b> ({@code LogoDrawer}): the Minecraft logo becomes the wordmark. Buttons stay vanilla.</li>
 * </ul>
 * Each background can be chosen per screen. Whenever the custom drawing is not possible (a shader is
 * not available yet, anything throws) vanilla draws as usual.
 */
public class CustomMenus extends Module {
	public enum Background {
		VANILLA("Vanilla"), CUSTOM_SKY("Custom Sky");

		private final String label;

		Background(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	// ------------------------------------------------------------------ which screen gets which background
	public final EnumSetting<Background> loadingScreen = add(new EnumSetting<>("Loading screen",
			"Background while the game starts or reloads resources", Background.CUSTOM_SKY));
	public final EnumSetting<Background> mainMenu = add(new EnumSetting<>("Main menu",
			"Background of the title screen (Singleplayer / Multiplayer buttons)", Background.CUSTOM_SKY));
	public final EnumSetting<Background> multiplayer = add(new EnumSetting<>("Multiplayer",
			"Background of the server list", Background.CUSTOM_SKY));
	public final EnumSetting<Background> singleplayer = add(new EnumSetting<>("Singleplayer",
			"Background of the world list", Background.CUSTOM_SKY));
	public final EnumSetting<Background> otherMenus = add(new EnumSetting<>("Other menus",
			"Background of every other menu screen opened outside a world (options, add server, ...)", Background.CUSTOM_SKY));

	// ------------------------------------------------------------------ stars (the look itself is the Custom Sky module's)
	public final BooleanSetting stars = add(new BooleanSetting("Falling stars",
			"Show falling stars in the menus (their look is set in the Custom Sky module)", true));
	public final NumberSetting starAmount = add(new NumberSetting("Star amount",
			"Percent of the Custom Sky star count used in the menus", 60, 0, 100, 1), stars::get);

	// ------------------------------------------------------------------ loading screen
	public final BooleanSetting progressBar = add(new BooleanSetting("Progress bar",
			"Thin rounded loading bar with a glow in the theme accent colour", true));
	public final NumberSetting barWidth = add(new NumberSetting("Bar width",
			"Width of the progress bar in percent of the screen width", 34, 15, 70, 1), progressBar::get);

	// ------------------------------------------------------------------ wordmark
	public final TextSetting wordmarkText = add(new TextSetting("Wordmark text",
			"The text of the wordmark (first word white, the rest in the accent colour). Empty = \"Hazeline\"",
			MenuWordmark.DEFAULT_TEXT, 32, true));
	public final BooleanSetting titleWordmark = add(new BooleanSetting("Title screen wordmark",
			"Replace the Minecraft logo on the main menu with the wordmark", true));
	public final BooleanSetting loadingWordmark = add(new BooleanSetting("Loading screen wordmark",
			"Show the wordmark above the progress bar instead of the Mojang logo", true));
	public final NumberSetting wordmarkSize = add(new NumberSetting("Wordmark size", "Size of the wordmark in percent", 100, 50, 180, 1));
	public final BooleanSetting wordmarkGlow = add(new BooleanSetting("Wordmark glow", "Soft accent coloured glow behind the wordmark", true));

	private static CustomMenus instance;
	private static boolean failed;
	/** Set by {@link #drawLoadingBackground} for the current loading-screen frame; the logo and bar follow it. */
	private static boolean loadingDrawn;

	public CustomMenus() {
		super("Custom Menus", "Custom Sky background, falling stars, progress bar and wordmark for the loading screen and menus",
				Category.MENUS, true);
		instance = this;
	}

	public static CustomMenus get() {
		return instance;
	}

	@Override
	protected void onDisable() {
		MenuSky.reset();
	}

	private static CustomMenus active() {
		CustomMenus m = instance;
		return m != null && m.isEnabled() && !failed ? m : null;
	}

	/** Which background setting applies to {@code screen}. */
	private Background backgroundFor(Screen screen) {
		if (screen instanceof TitleScreen) {
			return mainMenu.get();
		}
		if (screen instanceof MultiplayerScreen) {
			return multiplayer.get();
		}
		if (screen instanceof SelectWorldScreen) {
			return singleplayer.get();
		}
		return otherMenus.get();
	}

	// ============================================================================ hooks (called from the mixins)

	/** {@code Screen#renderPanoramaBackground}: true if the custom background was drawn (vanilla's is then skipped). */
	public static boolean drawScreenBackground(Screen screen, DrawContext g) {
		CustomMenus m = active();
		if (m == null || MinecraftClient.getInstance().world != null || m.backgroundFor(screen) != Background.CUSTOM_SKY) {
			return false;
		}
		try {
			return MenuSky.draw(g, 1f);
		} catch (RuntimeException | LinkageError e) {
			return fail("menu background", e);
		}
	}

	/** {@code LogoDrawer#draw}: true if the wordmark was drawn instead of the Minecraft logo. */
	public static boolean drawTitleLogo(DrawContext g, int screenWidth, float alpha, int y) {
		CustomMenus m = active();
		if (m == null || !m.titleWordmark.get()) {
			return false;
		}
		try {
			return MenuWordmark.draw(g, screenWidth / 2f, y, alpha, m);
		} catch (RuntimeException | LinkageError e) {
			return fail("title wordmark", e);
		}
	}

	/** Start of a loading-screen frame. */
	public static void beginLoadingFrame() {
		loadingDrawn = false;
	}

	/** {@code SplashOverlay}: draws the loading background; false = vanilla draws its own. {@code alpha} is 0..255. */
	public static boolean drawLoadingBackground(DrawContext g, int alpha) {
		CustomMenus m = active();
		if (m == null || m.loadingScreen.get() != Background.CUSTOM_SKY) {
			return false;
		}
		try {
			loadingDrawn = MenuSky.draw(g, alpha / 255f);
		} catch (RuntimeException | LinkageError e) {
			loadingDrawn = false;
			return fail("loading background", e);
		}
		return loadingDrawn;
	}

	/** True if this frame's loading background is the custom one (so vanilla's Mojang logo and bar are skipped). */
	public static boolean loadingCustom() {
		return loadingDrawn && active() != null;
	}

	/** Loading screen: the wordmark that replaces the Mojang logo. {@code alpha} is 0..255. */
	public static void drawLoadingWordmark(DrawContext g, int alpha) {
		CustomMenus m = active();
		if (m == null || !loadingDrawn || !m.loadingWordmark.get()) {
			return;
		}
		try {
			float cy = g.getScaledWindowHeight() * 0.42f;
			MenuWordmark.drawCentered(g, g.getScaledWindowWidth() / 2f, cy, alpha / 255f, m);
		} catch (RuntimeException | LinkageError e) {
			fail("loading wordmark", e);
		}
	}

	/** Loading screen: the progress bar; false = vanilla draws its own. {@code opacity} is 0..1. */
	public static boolean drawLoadingBar(DrawContext g, float progress, float opacity) {
		CustomMenus m = active();
		if (m == null || !loadingDrawn) {
			return false;
		}
		if (!m.progressBar.get()) {
			return true; // custom look, bar switched off: draw nothing
		}
		try {
			MenuLoadingBar.draw(g, progress, opacity, m);
			return true;
		} catch (RuntimeException | LinkageError e) {
			return fail("progress bar", e);
		}
	}

	/** Never let a menu drawing problem break the game: log once, switch the custom drawing off for this session. */
	private static boolean fail(String what, Throwable e) {
		failed = true;
		Hazeline.LOGGER.error("Custom Menus: {} failed, falling back to the vanilla menus for this session", what, e);
		return false;
	}
}
