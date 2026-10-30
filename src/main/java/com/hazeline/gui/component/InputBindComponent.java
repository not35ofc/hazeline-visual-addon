package com.hazeline.gui.component;

import net.minecraft.client.util.InputUtil;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

import com.hazeline.appearance.SoundAction;
import com.hazeline.gui.ClickGuiScreen;
import com.hazeline.gui.Theme;
import com.hazeline.gui.Widgets;
import com.hazeline.setting.InputBindSetting;

/**
 * Row for an {@link InputBindSetting} (key or mouse button). Left click starts listening; then
 * press a key, or click any mouse button on this row, to bind it. Escape cancels,
 * Backspace/Delete unbinds, clicking elsewhere cancels. Right click (while not listening) unbinds.
 */
public class InputBindComponent extends SettingComponent<InputBindSetting> {
	private boolean listening;

	public InputBindComponent(InputBindSetting setting) {
		super(setting);
	}

	@Override
	public int getHeight() {
		return labelHeight();
	}

	private int boxW() {
		return Math.max(Theme.px(54), Theme.widthOf(setting.getKeyName(), Theme.SMALL, false) + Theme.px(14));
	}

	@Override
	public void render(DrawContext g, int mouseX, int mouseY, float delta) {
		int h = getHeight();
		boolean hover = hovered(mouseX, mouseY, h);
		int bw = boxW();
		int bh = Theme.px(14);
		int bx = controlRight() - bw;
		drawLabel(g, bx, h, hover);
		Widgets.valueBox(g, bx, y + (h - bh) / 2, bw, bh, listening ? "Key or mouse..." : setting.getKeyName(), listening, hover);
		if (hover) {
			ClickGuiScreen.setTooltip(listening ? "Press a key, or click a mouse button on this row (Esc = cancel, Backspace = none)"
					: "Click, then press a key or click a mouse button on this row");
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!hovered(mouseX, mouseY, getHeight())) {
			return false;
		}
		if (listening) {
			listening = false;
			setting.set(InputBindSetting.mouse(button));
			sound(SoundAction.KEYBIND);
			changed();
			return true;
		}
		if (button == 0) {
			listening = true;
		} else if (button == 1) {
			setting.set(InputBindSetting.NONE);
			changed();
		}
		sound(SoundAction.CLICK);
		return true;
	}

	@Override
	public void onGlobalClick(double mouseX, double mouseY) {
		if (!hovered(mouseX, mouseY, getHeight())) {
			listening = false;
		}
	}

	@Override
	public boolean keyPressed(KeyInput event) {
		if (!listening) {
			return false;
		}
		listening = false;
		int key = event.key();
		if (key == InputUtil.GLFW_KEY_ESCAPE) {
			return true;
		}
		if (key == InputUtil.GLFW_KEY_BACKSPACE || key == InputUtil.GLFW_KEY_DELETE) {
			setting.set(InputBindSetting.NONE);
		} else {
			setting.set(key);
		}
		sound(SoundAction.KEYBIND);
		changed();
		return true;
	}

	@Override
	public boolean isCapturingKeyboard() {
		return listening;
	}
}
