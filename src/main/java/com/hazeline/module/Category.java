package com.hazeline.module;

/**
 * Module categories. Each category (except SETTINGS) is one sidebar entry in the click GUI,
 * listed in declaration order.
 */
public enum Category {
	RENDER("Render"),
	SKY("Sky"),
	WEATHER("Weather"),
	MENUS("Menus"),
	SOUNDS("Sounds"),
	MUSIC("Music"),
	SETTINGS("Settings");

	private final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
