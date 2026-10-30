package com.hazeline.module.modules.weather;

/** Performance preset shared by the weather modules: scales how many drops, flakes, puffs and ripples are drawn. */
public enum WeatherQuality {
	LOW("Low", 0.35f), MEDIUM("Medium", 0.7f), HIGH("High", 1.0f);

	private final String label;
	public final float scale;

	WeatherQuality(String label, float scale) {
		this.label = label;
		this.scale = scale;
	}

	@Override
	public String toString() {
		return label;
	}
}
