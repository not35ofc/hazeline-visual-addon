package com.hazeline.appearance;

/**
 * Click GUI events that can make a sound. {@link #fileName()} is the .ogg name of the Clicky/Creamy
 * menu sounds (assets/hazeline/sounds/&lt;pack&gt;/); the other packs map each action to a key sound
 * ({@link SoundManager#play}). {@link #vanillaPitch} belonged to the "Minecraft" pack (removed in 1.35.0).
 */
public enum SoundAction {
	OPEN("open", "GUI open", 1.10f, 1.0f),
	CLOSE("close", "GUI close", 0.90f, 1.0f),
	ENABLE("enable", "Module enable", 1.30f, 1.0f),
	DISABLE("disable", "Module disable", 0.80f, 1.0f),
	CLICK("click", "Button / setting click", 1.00f, 0.8f),
	SLIDER("slider", "Slider drag tick", 1.70f, 0.35f),
	KEYBIND("keybind", "Keybind set", 1.45f, 1.0f);

	private final String fileName;
	private final String label;
	private final float vanillaPitch;
	private final float volumeFactor;

	SoundAction(String fileName, String label, float vanillaPitch, float volumeFactor) {
		this.fileName = fileName;
		this.label = label;
		this.vanillaPitch = vanillaPitch;
		this.volumeFactor = volumeFactor;
	}

	public String fileName() {
		return fileName;
	}

	public String getLabel() {
		return label;
	}

	public float vanillaPitch() {
		return vanillaPitch;
	}

	/** Relative loudness so frequent sounds (slider ticks) stay subtle. */
	public float volumeFactor() {
		return volumeFactor;
	}
}
