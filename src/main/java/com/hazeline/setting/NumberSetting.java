package com.hazeline.setting;

import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A numeric value constrained to [min, max] and snapped to multiples of {@code step}.
 * Rendered as a draggable slider.
 */
public class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private boolean applyOnRelease;

	public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
		super(name, description, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	public double getStep() {
		return step;
	}

	/**
	 * Marks a setting whose value changes the click GUI layout itself (font size, window size). Its
	 * slider then only shows the new value while it is dragged and applies it when the mouse is
	 * released - applying it live moves the slider under the mouse every frame, so the value jumps.
	 */
	public NumberSetting applyOnRelease() {
		applyOnRelease = true;
		return this;
	}

	public boolean isApplyOnRelease() {
		return applyOnRelease;
	}

	/** The value a 0..1 slider fraction stands for (clamped and snapped like {@link #set}), without setting it. */
	public double valueAt(double fraction) {
		return sanitize(min + Math.max(0, Math.min(1, fraction)) * (max - min));
	}

	/** 0..1 slider fraction of any value of this setting's range. */
	public double fractionOf(double number) {
		return max == min ? 0 : Math.max(0, Math.min(1, (number - min) / (max - min)));
	}

	public int getInt() {
		return (int) Math.round(value);
	}

	public float getFloat() {
		return value.floatValue();
	}

	/** Sets the value from a 0..1 slider fraction. */
	public void setFraction(double fraction) {
		set(min + Math.max(0, Math.min(1, fraction)) * (max - min));
	}

	/** Current position as a 0..1 slider fraction. */
	public double getFraction() {
		return max == min ? 0 : (value - min) / (max - min);
	}

	@Override
	protected Double sanitize(Double newValue) {
		double v = Math.max(min, Math.min(max, newValue));
		if (step > 0) {
			v = min + Math.round((v - min) / step) * step;
			v = Math.max(min, Math.min(max, v));
		}
		// Avoid floating point noise like 0.30000000000000004.
		return Math.round(v * 10000.0) / 10000.0;
	}

	/** Formats the value with as many decimals as the step needs. */
	public String format() {
		return format(value);
	}

	/** Formats any number of this setting's range the same way (used for min/max hints). */
	public String format(double number) {
		if (step >= 1 && step == Math.floor(step)) {
			return Long.toString(Math.round(number));
		}
		int decimals = step >= 0.1 ? 1 : step >= 0.01 ? 2 : 3;
		return String.format(Locale.ROOT, "%." + decimals + "f", number);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsDouble());
		}
	}
}
