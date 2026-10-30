package com.hazeline.gui.component;

import net.minecraft.client.gui.DrawContext;

import com.hazeline.appearance.SoundAction;
import com.hazeline.gui.Anim;
import com.hazeline.gui.Widgets;
import com.hazeline.gui.Theme;
import com.hazeline.setting.ActionSetting;

/** Button row for an {@link ActionSetting}: a framed label that runs the action on left click. */
public class ActionComponent extends SettingComponent<ActionSetting> {
	public ActionComponent(ActionSetting setting) {
		super(setting);
	}

	@Override
	public int getHeight() {
		return labelHeight();
	}

	private int buttonW() {
		return Widgets.buttonWidth("Run");
	}

	@Override
	public void render(DrawContext g, int mouseX, int mouseY, float delta) {
		int h = getHeight();
		boolean hover = hovered(mouseX, mouseY, h);
		int bw = buttonW();
		int bh = Widgets.buttonHeight();
		int bx = controlRight() - bw;
		drawLabel(g, bx, h, hover);
		Widgets.button(g, bx, y + (h - bh) / 2, bw, bh, "Run", true, Anim.approach(this, 3, hover ? 1f : 0f, 14f));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && hovered(mouseX, mouseY, getHeight())) {
			sound(SoundAction.CLICK);
			setting.run();
			return true;
		}
		return false;
	}
}
