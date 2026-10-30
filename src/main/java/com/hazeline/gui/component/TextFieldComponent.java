package com.hazeline.gui.component;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

import com.hazeline.appearance.SoundAction;
import com.hazeline.gui.Theme;
import com.hazeline.setting.TextSetting;

/**
 * Label + single-line text field for a {@link TextSetting}. Click to edit; Enter or clicking
 * elsewhere keeps the text, Escape cancels. Ctrl+V pastes (e.g. a theme code).
 */
public class TextFieldComponent extends SettingComponent<TextSetting> {
	private final TextField field;

	public TextFieldComponent(TextSetting setting) {
		super(setting);
		this.field = new TextField(setting.getMaxLength(), cp -> cp >= 32 && cp != 127, text -> {
			setting.set(text);
			changed();
		});
	}

	@Override
	public int getHeight() {
		return labelHeight();
	}

	private int fieldW() {
		return Math.min(Theme.px(150), Math.max(Theme.px(60), width * 2 / 5));
	}

	private int fieldH() {
		return Theme.px(14);
	}

	private int fieldX() {
		return controlRight() - fieldW();
	}

	private int fieldY() {
		return y + (getHeight() - fieldH()) / 2;
	}

	@Override
	public void render(DrawContext g, int mouseX, int mouseY, float delta) {
		int h = getHeight();
		drawLabel(g, fieldX(), h, hovered(mouseX, mouseY, h));
		String placeholder = setting.get().isEmpty() ? "Click to type" : setting.get();
		field.render(g, fieldX(), fieldY(), fieldW(), fieldH(), placeholder);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && hovered(mouseX, mouseY, getHeight())) {
			if (!field.isFocused()) {
				field.focus(setting.get());
				sound(SoundAction.CLICK);
			}
			return true;
		}
		return false;
	}

	@Override
	public void onGlobalClick(double mouseX, double mouseY) {
		if (field.isFocused() && !hovered(mouseX, mouseY, getHeight())) {
			field.submit();
		}
	}

	@Override
	public boolean keyPressed(KeyInput event) {
		return field.keyPressed(event);
	}

	@Override
	public boolean charTyped(int codepoint) {
		return field.charTyped(codepoint);
	}

	@Override
	public boolean isCapturingKeyboard() {
		return field.isFocused();
	}
}
