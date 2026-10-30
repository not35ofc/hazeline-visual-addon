package com.hazeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.util.InputUtil;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import com.hazeline.appearance.SoundAction;
import com.hazeline.appearance.SoundManager;
import com.hazeline.config.ConfigManager;
import com.hazeline.gui.ClickGuiScreen;
import com.hazeline.gui.GuiOverlay;
import com.hazeline.gui.text.TextRenderer;
import com.hazeline.module.ModuleManager;
import com.hazeline.module.modules.music.MusicHud;
import com.hazeline.module.modules.render.NameTag;
import com.hazeline.module.modules.sky.CustomSkyRenderer;
import com.hazeline.module.modules.weather.WeatherController;
import com.hazeline.module.modules.weather.WeatherHud;

/**
 * Client entrypoint (declared in fabric.mod.json).
 *
 * <p>Wires everything together using Fabric API events only:
 * <ul>
 *   <li>registers the rebindable "Open Hazeline menu" key (Right Shift) via
 *       {@link KeyBindingHelper} - it appears under "Hazeline" in Options &gt; Controls;</li>
 *   <li>creates modules and loads {@code config/hazeline/config.json} (incl. appearance);</li>
 *   <li>frees the click GUI text renderer's textures and FreeType objects on shutdown;</li>
 *   <li>ticks modules and autosaves the config at the end of each client tick.</li>
 * </ul>
 */
public class Hazeline implements ClientModInitializer {
	public static final String MOD_ID = "hazeline";
	public static final Logger LOGGER = LoggerFactory.getLogger("Hazeline");

	/** 1.21.11 key binding categories are records created from an Identifier (lang key: key.category.hazeline.main). */
	public static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.create(id("main"));

	private static KeyBinding openGuiKey;
	private int autosaveTimer;

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	public static KeyBinding getOpenGuiKey() {
		return openGuiKey;
	}

	@Override
	public void onInitializeClient() {
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("pastaclient")) {
			LOGGER.warn("The old \"Pasta Client\" jar (pastaclient*.jar) is still in the mods folder next to Hazeline - remove it, both would run at once");
		}
		openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.hazeline.open_gui",
				InputUtil.Type.KEYSYM,
				InputUtil.GLFW_KEY_RIGHT_SHIFT,
				KEY_CATEGORY));

		ModuleManager.get().init();
		ConfigManager.get().load();

		NameTag.registerEvents();
		MusicHud.registerEvents();
		WeatherController.registerEvents();
		WeatherHud.registerEvents();
		// Click GUI on top of any other screen (menus, inventory, containers...).
		GuiOverlay.register();

		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ConfigManager.get().save();
			TextRenderer.shutdown();
			CustomSkyRenderer.close();
		});

		LOGGER.info("Hazeline initialised");
	}

	private void onEndTick(MinecraftClient client) {
		// Free glyph atlas textures that have not been used for a while (also when the menu is closed).
		TextRenderer.tick();
		// Open the menu in game. (While the menu is open, the screen itself handles closing. On top of
		// another screen the key is read by GuiOverlay through KeyboardOverlayMixin instead.)
		while (openGuiKey.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new ClickGuiScreen());
				SoundManager.get().play(SoundAction.OPEN);
			}
		}

		GuiOverlay.tick(client);
		ModuleManager.get().tick(client);

		// Autosave at most once per second when something changed.
		if (++autosaveTimer >= 20) {
			autosaveTimer = 0;
			if (ModuleManager.get().consumeDirty()) {
				ConfigManager.get().save();
			}
		}
	}
}
