package com.hazeline.setting;

import org.lwjgl.glfw.GLFW;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.util.InputUtil;

import net.minecraft.client.MinecraftClient;

/**
 * A hold-to-use input: a keyboard key <em>or</em> a mouse button (unlike {@link KeybindSetting},
 * which is keyboard only). Stored as one int so it saves like every other setting:
 * <ul>
 *   <li>{@code >= 0}: GLFW key code;</li>
 *   <li>{@value #NONE}: unbound;</li>
 *   <li>{@code <= -100}: mouse button {@code -100 - value} (0 = left, 1 = right, 2 = middle, 3..7 = side buttons).</li>
 * </ul>
 */
public class InputBindSetting extends Setting<Integer> {
	public static final int NONE = -1;
	private static final int MOUSE_BASE = -100;
	private static final int MAX_MOUSE_BUTTON = 7;

	public InputBindSetting(String name, String description, int defaultValue) {
		super(name, description, defaultValue);
	}

	/** Encoded value of mouse button {@code button} (GLFW numbering). */
	public static int mouse(int button) {
		return MOUSE_BASE - button;
	}

	public boolean isBound() {
		return value != NONE;
	}

	public boolean isMouse() {
		return value <= MOUSE_BASE;
	}

	private int mouseButton() {
		return MOUSE_BASE - value;
	}

	/** The vanilla key object (used for the display name and to compare with vanilla key mappings). */
	public InputUtil.Key toKey() {
		if (!isBound()) {
			return InputUtil.UNKNOWN_KEY;
		}
		return isMouse() ? InputUtil.Type.MOUSE.createFromCode(mouseButton()) : InputUtil.Type.KEYSYM.createFromCode(value);
	}

	/** Human readable name, e.g. "Right Button", "R" or "None". */
	public String getKeyName() {
		return isBound() ? toKey().getLocalizedText().getString() : "None";
	}

	/** True while the key or mouse button is physically held down (render thread only). */
	public boolean isHeld(MinecraftClient mc) {
		if (!isBound()) {
			return false;
		}
		if (isMouse()) {
			return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), mouseButton()) == GLFW.GLFW_PRESS;
		}
		return InputUtil.isKeyPressed(mc.getWindow(), value);
	}

	@Override
	protected Integer sanitize(Integer newValue) {
		int v = newValue == null ? NONE : newValue;
		boolean key = v >= 0 && v <= GLFW.GLFW_KEY_LAST;
		boolean mouse = v <= MOUSE_BASE && v >= MOUSE_BASE - MAX_MOUSE_BUTTON;
		return key || mouse ? v : NONE;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsInt());
		}
	}
}
