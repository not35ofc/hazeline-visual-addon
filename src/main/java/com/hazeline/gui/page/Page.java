package com.hazeline.gui.page;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

/**
 * One page of the menu's content area (module list, module settings, Settings, Theme, Keybinds).
 * The screen draws the title, scrolls and clips the content; the page lays out its content at
 * (x, top) - {@code top} already includes the scroll offset - for a given width.
 */
public abstract class Page {
	/** Highlighted first word of the title (drawn in the accent colour), may be empty. */
	public abstract String titleAccent();

	/** Rest of the title. */
	public abstract String title();

	/** Small line under the title. */
	public abstract String subtitle();

	/** Whether the search box and the list/grid switch are shown (module lists only). */
	public boolean showsSearch() {
		return false;
	}

	/** Total content height for {@code width}. */
	public abstract int contentHeight(int width);

	public abstract void render(DrawContext g, int x, int top, int width, int mouseX, int mouseY, float delta);

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return false;
	}

	/** Every click, before normal dispatch (so focused fields can lose focus). */
	public void onGlobalClick(double mouseX, double mouseY) {
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
	}

	public void mouseDragged(double mouseX, double mouseY) {
	}

	public boolean keyPressed(KeyInput event) {
		return false;
	}

	public boolean charTyped(int codepoint) {
		return false;
	}

	public boolean isCapturingKeyboard() {
		return false;
	}

	/** Called every frame while the page is shown. */
	public void tick() {
	}
}
