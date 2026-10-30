package com.hazeline.sound;

import org.lwjgl.glfw.GLFW;

/** Which recording a key or mouse button uses: Space, Enter and Backspace have their own. */
public enum KeySound {
	GENERIC("press", "release"),
	SPACE("space", "space_release"),
	ENTER("enter", "enter_release"),
	BACKSPACE("backspace", "backspace_release");

	private final String press;
	private final String release;

	KeySound(String press, String release) {
		this.press = press;
		this.release = release;
	}

	/** Sound event name for this key in {@code pack}, e.g. {@code kb.alpaca.space_release}. */
	public String event(SoundPack pack, boolean released) {
		return "kb." + pack.id() + "." + (released ? release : press);
	}

	public static KeySound ofKey(int glfwKey) {
		return switch (glfwKey) {
			case GLFW.GLFW_KEY_SPACE -> SPACE;
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> ENTER;
			case GLFW.GLFW_KEY_BACKSPACE -> BACKSPACE;
			default -> GENERIC;
		};
	}
}
