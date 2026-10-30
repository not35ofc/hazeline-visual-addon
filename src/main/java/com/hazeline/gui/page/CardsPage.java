package com.hazeline.gui.page;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

import com.hazeline.gui.Theme;

/** A page made of stacked {@link SettingsCard}s (Settings, Keybinds, the colours part of Theme). */
public abstract class CardsPage extends Page {
	protected final List<SettingsCard> cards = new ArrayList<>();

	protected static int gap() {
		return Theme.px(8);
	}

	/** Height of anything the subclass draws above the cards. */
	protected int topHeight(int width) {
		return 0;
	}

	/** Draws the part above the cards (nothing by default). */
	protected void renderTop(DrawContext g, int x, int top, int width, int mouseX, int mouseY) {
	}

	@Override
	public int contentHeight(int width) {
		int h = topHeight(width);
		for (SettingsCard card : cards) {
			if (h > 0) {
				h += gap();
			}
			h += card.height(width);
		}
		return h;
	}

	@Override
	public void render(DrawContext g, int x, int top, int width, int mouseX, int mouseY, float delta) {
		int top0 = topHeight(width);
		renderTop(g, x, top, width, mouseX, mouseY);
		int y = top + top0;
		for (SettingsCard card : cards) {
			if (y > top) {
				y += gap();
			}
			card.render(g, x, y, width, mouseX, mouseY, delta);
			y += card.height(width);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (SettingsCard card : cards) {
			if (card.mouseClicked(mouseX, mouseY, button)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onGlobalClick(double mouseX, double mouseY) {
		cards.forEach(c -> c.onGlobalClick(mouseX, mouseY));
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		cards.forEach(c -> c.mouseReleased(mouseX, mouseY, button));
	}

	@Override
	public void mouseDragged(double mouseX, double mouseY) {
		cards.forEach(c -> c.mouseDragged(mouseX, mouseY));
	}

	@Override
	public boolean keyPressed(KeyInput event) {
		for (SettingsCard card : cards) {
			if (card.keyPressed(event)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean charTyped(int codepoint) {
		for (SettingsCard card : cards) {
			if (card.charTyped(codepoint)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isCapturingKeyboard() {
		for (SettingsCard card : cards) {
			if (card.isCapturingKeyboard()) {
				return true;
			}
		}
		return false;
	}
}
