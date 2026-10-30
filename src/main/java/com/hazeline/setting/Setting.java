package com.hazeline.setting;

import java.util.function.BooleanSupplier;

import com.google.gson.JsonElement;

/**
 * Base class for every configurable value a module exposes.
 *
 * <p>A setting knows its display name, its default value and its current value, and can
 * serialise itself to / from JSON so the {@link com.hazeline.config.ConfigManager} can
 * persist it without knowing the concrete type. The GUI picks a widget based on the
 * concrete subclass.
 *
 * @param <T> the value type
 */
public abstract class Setting<T> {
	private final String name;
	private final String description;
	private final T defaultValue;
	protected T value;
	/** Optional condition controlling whether the setting is shown in the GUI. */
	private BooleanSupplier visibility = () -> true;
	/** Optional callback run whenever the value changes. */
	private Runnable onChange = () -> { };

	protected Setting(String name, String description, T defaultValue) {
		this.name = name;
		this.description = description;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public T get() {
		return value;
	}

	public T getDefault() {
		return defaultValue;
	}

	/** Sets the value (after validation by {@link #sanitize}) and fires the change callback. */
	public void set(T newValue) {
		T clean = sanitize(newValue);
		if (!clean.equals(value)) {
			value = clean;
			onChange.run();
		}
	}

	/** Restores the default value. */
	public void reset() {
		set(defaultValue);
	}

	/** Subclasses may clamp / round values here. */
	protected T sanitize(T newValue) {
		return newValue;
	}

	public boolean isVisible() {
		return visibility.getAsBoolean();
	}

	/** Fluent helper: only show this setting while {@code condition} is true. */
	public Setting<T> visibleWhen(BooleanSupplier condition) {
		this.visibility = condition;
		return this;
	}

	public Setting<T> onChange(Runnable callback) {
		this.onChange = callback;
		return this;
	}

	/**
	 * Whether the value is written to config.json. Display-only settings (labels, buttons)
	 * return false.
	 */
	public boolean isPersistent() {
		return true;
	}

	/** Serialises the current value. */
	public abstract JsonElement toJson();

	/** Loads a value previously produced by {@link #toJson()}. Must tolerate bad input. */
	public abstract void fromJson(JsonElement json);
}
