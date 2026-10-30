package com.hazeline.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.util.InputUtil;

import net.minecraft.client.MinecraftClient;

import com.hazeline.Hazeline;
import com.hazeline.appearance.SoundAction;
import com.hazeline.appearance.SoundManager;
import com.hazeline.module.modules.menus.CustomMenus;
import com.hazeline.module.modules.music.MusicHud;
import com.hazeline.module.modules.render.BlockOutlines;
import com.hazeline.module.modules.render.HitBoxes;
import com.hazeline.module.modules.render.HandView;
import com.hazeline.module.modules.render.NameTag;
import com.hazeline.module.modules.render.SwingSpeed;
import com.hazeline.module.modules.settings.ClickGuiModule;
import com.hazeline.module.modules.sounds.SoundsAndClicks;
import com.hazeline.module.modules.sky.CustomSky;
import com.hazeline.module.modules.weather.GlossyRain;
import com.hazeline.module.modules.weather.Mist;
import com.hazeline.module.modules.weather.SmoothThunder;
import com.hazeline.module.modules.weather.Weather;

/**
 * Owns all module instances, ticks enabled modules and handles module keybinds.
 *
 * <p>Register new modules in {@link #init()}.
 */
public final class ModuleManager {
	private static final ModuleManager INSTANCE = new ModuleManager();

	private final List<Module> modules = new ArrayList<>();
	private final Map<Module, Boolean> keyWasDown = new HashMap<>();
	private boolean dirty;

	private ModuleManager() {
	}

	public static ModuleManager get() {
		return INSTANCE;
	}

	/** Creates every module. Order here = order inside each GUI panel. */
	public void init() {
		register(new HandView());
		register(new SwingSpeed());
		register(new BlockOutlines());
		register(new HitBoxes());
		register(new NameTag());
		register(new CustomSky());
		register(new Weather());
		register(new GlossyRain());
		register(new Mist());
		register(new SmoothThunder());
		register(new CustomMenus());
		register(new SoundsAndClicks());
		register(new MusicHud());
		register(new ClickGuiModule());
	}

	private void register(Module module) {
		modules.add(module);
	}

	public List<Module> getModules() {
		return Collections.unmodifiableList(modules);
	}

	public List<Module> getModules(Category category) {
		List<Module> result = new ArrayList<>();
		for (Module module : modules) {
			if (module.getCategory() == category) {
				result.add(module);
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public <T extends Module> T get(Class<T> type) {
		for (Module module : modules) {
			if (type.isInstance(module)) {
				return (T) module;
			}
		}
		throw new IllegalArgumentException("Module not registered: " + type.getName());
	}

	public Module getByName(String name) {
		for (Module module : modules) {
			if (module.getName().equalsIgnoreCase(name)) {
				return module;
			}
		}
		return null;
	}

	/** Called at the end of every client tick. */
	public void tick(MinecraftClient mc) {
		// Module keybinds: only while no screen is open, edge-triggered (press, not hold).
		boolean canUseKeys = mc.currentScreen == null && mc.player != null;
		for (Module module : modules) {
			if (!module.isToggleable() || !module.getKeybind().isBound()) {
				continue;
			}
			boolean down = canUseKeys && InputUtil.isKeyPressed(mc.getWindow(), module.getKeybind().get());
			boolean wasDown = keyWasDown.getOrDefault(module, false);
			if (down && !wasDown) {
				module.toggle();
				SoundManager.get().play(module.isEnabled() ? SoundAction.ENABLE : SoundAction.DISABLE);
			}
			keyWasDown.put(module, down);
		}

		for (Module module : modules) {
			if (module.isEnabled()) {
				// Safety net: a failing module is switched off and logged instead of crashing
				// the whole game (e.g. a class that can no longer be loaded because the mod jar
				// was replaced while the game was running).
				try {
					module.onTick();
				} catch (RuntimeException | LinkageError e) {
					Hazeline.LOGGER.error("Module '{}' failed during tick and was turned off", module.getName(), e);
					try {
						module.setEnabled(false);
					} catch (RuntimeException | LinkageError e2) {
						Hazeline.LOGGER.error("Could not turn off module '{}'", module.getName(), e2);
					}
				}
			}
		}
	}

	/** Marks the config as changed so it is written on the next autosave. */
	public void markDirty() {
		dirty = true;
	}

	public boolean consumeDirty() {
		boolean wasDirty = dirty;
		dirty = false;
		return wasDirty;
	}

	public void resetAll() {
		for (Module module : modules) {
			module.resetToDefaults();
		}
	}
}
