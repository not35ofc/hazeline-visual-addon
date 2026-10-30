package com.hazeline.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.client.MinecraftClient;

import com.hazeline.setting.KeybindSetting;
import com.hazeline.setting.Setting;

/**
 * Base class for every feature.
 *
 * <p>To add a module: extend this class, register settings in the constructor with
 * {@link #add(Setting)}, override {@link #onEnable()}/{@link #onDisable()}/{@link #onTick()}
 * as needed, and register an instance in {@link ModuleManager#init()}. Persistence,
 * GUI widgets, keybind handling and "reset to defaults" are all handled generically.
 */
public abstract class Module {

	private final String name;
	private final String description;
	private final Category category;
	private final boolean defaultEnabled;
	private boolean enabled;
	private final List<Setting<?>> settings = new ArrayList<>();
	/** Every module gets a toggle keybind (unbound by default). */
	private final KeybindSetting keybind;

	protected Module(String name, String description, Category category) {
		this(name, description, category, false);
	}

	protected Module(String name, String description, Category category, boolean defaultEnabled) {
		this.name = name;
		this.description = description;
		this.category = category;
		this.defaultEnabled = defaultEnabled;
		this.enabled = defaultEnabled;
		this.keybind = new KeybindSetting("Keybind", "Key that toggles this module", KeybindSetting.NONE);
	}

	/** Registers a setting; returns it so it can be stored in a field. */
	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	/** Registers a setting that is only shown in the GUI while {@code visibleWhen} is true. */
	protected <S extends Setting<?>> S add(S setting, BooleanSupplier visibleWhen) {
		setting.visibleWhen(visibleWhen);
		return add(setting);
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Whether the user can switch this module on and off. Modules that only hold settings
	 * (like the GUI appearance module) return false and are always "enabled".
	 */
	public boolean isToggleable() {
		return true;
	}

	/** Settings in display order, followed by the keybind (if toggleable). */
	public List<Setting<?>> getSettings() {
		if (!isToggleable()) {
			return Collections.unmodifiableList(settings);
		}
		List<Setting<?>> all = new ArrayList<>(settings);
		all.add(keybind);
		return all;
	}

	public KeybindSetting getKeybind() {
		return keybind;
	}

	public void setEnabled(boolean enabled) {
		if (!isToggleable() || this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
		ModuleManager.get().markDirty();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	/** Restores every setting (including the keybind) and the on/off state to defaults. */
	public void resetToDefaults() {
		for (Setting<?> setting : getSettings()) {
			setting.reset();
		}
		setEnabled(defaultEnabled);
		ModuleManager.get().markDirty();
	}

	/** Shortcut for the client instance (looked up lazily, never cached during init). */
	protected static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	/** Called when the module is switched on. */
	protected void onEnable() {
	}

	/** Called when the module is switched off. */
	protected void onDisable() {
	}

	/** Called every client tick (20/s) while enabled. */
	public void onTick() {
	}

	/** Called every frame while this module's settings are expanded in the click GUI. */
	public void onSettingsVisible() {
	}
}
