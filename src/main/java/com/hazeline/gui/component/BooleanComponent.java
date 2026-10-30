package com.hazeline.gui.component;

import net.minecraft.client.gui.DrawContext;

import com.hazeline.appearance.SoundAction;
import com.hazeline.gui.Anim;
import com.hazeline.gui.Theme;
import com.hazeline.gui.Widgets;
import com.hazeline.setting.BooleanSetting;

/** On/off setting: label on the left, an animated switch on the right. Right click resets. */
public class BooleanComponent extends SettingComponent<BooleanSetting> {
	public BooleanComponent(BooleanSetting setting) {
		super(setting);
	}

	@Override
	public int getHeight() {
		return labelHeight();
	}

	@Override
	public void render(DrawContext g, int mouseX, int mouseY, float delta) {
		int h = getHeight();
		boolean hover = hovered(mouseX, mouseY, h);
		int sx = controlRight() - Widgets.switchW();
		drawLabel(g, sx, h, hover);
		Widgets.toggleSwitch(g, sx, y + (h - Widgets.switchH()) / 2, Anim.toggle(this, setting.get()), hover);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!hovered(mouseX, mouseY, getHeight())) {
			return false;
		}
		if (button == 0) {
			setting.toggle();
		} else if (button == 1) {
			setting.reset();
		}
		sound(SoundAction.CLICK);
		changed();
		return true;
	}

	/** Theme-independent helper kept for the sound rows: a small checkbox. */
	static void drawCheckbox(DrawContext g, int bx, int by, int box, boolean checked) {
		int r = Math.max(1, box / 4);
		if (checked) {
			Theme.roundedRect(g, bx, by, bx + box, by + box, r, Theme.accent());
		} else {
			Theme.roundedOutline(g, bx, by, box, box, r, Theme.textSecondary());
		}
	}
}
