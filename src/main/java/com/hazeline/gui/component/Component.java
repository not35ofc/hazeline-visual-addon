package com.hazeline.gui.component;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

/**
 * Base class for every row-based widget (module panels and the Appearance editor).
 *
 * <p>The owner lays components out top-to-bottom each frame by calling
 * {@link #setBounds(int, int, int)} and then asking for {@link #getHeight()}. All sizes come
 * from {@link com.hazeline.gui.Theme}, so heights scale with the font size. Input
 * coordinates are GUI-scaled screen coordinates (same space as rendering).
 */
public abstract class Component {
	protected int x;
	protected int y;
	protected int width;

	public void setBounds(int x, int y, int width) {
		this.x = x;
		this.y = y;
		this.width = width;
	}

	/** Changes only the width (for measuring heights without moving the component). */
	public void setWidth(int width) {
		this.width = width;
	}

	/** Total height including any expanded children. */
	public abstract int getHeight();

	public abstract void render(DrawContext g, int mouseX, int mouseY, float delta);

	/** Whether this component is currently shown (e.g. setting visibility conditions). */
	public boolean isVisible() {
		return true;
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return false;
	}

	/**
	 * Called on every component for every click before normal dispatch, so focused widgets can
	 * lose focus when the user clicks somewhere else.
	 */
	public void onGlobalClick(double mouseX, double mouseY) {
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
	}

	public void mouseDragged(double mouseX, double mouseY) {
	}

	/** @return true if the key was consumed (e.g. by a focused text field). */
	public boolean keyPressed(KeyInput event) {
		return false;
	}

	public boolean charTyped(int codepoint) {
		return false;
	}

	/** True while this component wants all keyboard input (text field focus, keybind listening). */
	public boolean isCapturingKeyboard() {
		return false;
	}

	protected boolean hovered(double mouseX, double mouseY, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
