package com.hazeline.gui.component;

import java.util.List;

import net.minecraft.client.gui.DrawContext;

import com.hazeline.gui.Theme;
import com.hazeline.setting.InfoSetting;

/**
 * Read-only row: the setting name (secondary colour) with its live text wrapped below it,
 * so long messages (folder paths, missing sound files) are fully readable.
 */
public class InfoComponent extends SettingComponent<InfoSetting> {
	public InfoComponent(InfoSetting setting) {
		super(setting);
	}

	private List<String> lines() {
		return Theme.wrapOf(setting.getText(), Math.max(Theme.px(20), width - indent() * 2), Theme.SMALL);
	}

	private int lineHeight() {
		return Math.round(Theme.heightOf(Theme.SMALL)) + Theme.px(2);
	}

	@Override
	public int getHeight() {
		return Theme.px(16) + lines().size() * lineHeight() + Theme.px(5);
	}

	@Override
	public void render(DrawContext g, int mouseX, int mouseY, float delta) {
		Theme.boxTextOf(g, Theme.fitOf(setting.getName(), width - indent() * 2, 1.0, true), x + indent(), y + Theme.px(2), Theme.px(14),
				Theme.text(), 1.0, true);
		float ly = y + Theme.px(16);
		for (String line : lines()) {
			Theme.textOf(g, line, x + indent(), ly, Theme.textSecondary(), Theme.SMALL, false);
			ly += lineHeight();
		}
	}
}
