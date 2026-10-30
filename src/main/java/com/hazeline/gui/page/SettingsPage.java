package com.hazeline.gui.page;

import java.util.List;

import com.hazeline.appearance.Appearance;
import com.hazeline.config.ConfigManager;
import com.hazeline.module.ModuleManager;
import com.hazeline.module.modules.settings.ClickGuiModule;
import com.hazeline.setting.ActionSetting;

/** Menu layout (Click GUI module), font settings, and resetting every module. (Sounds: Sounds and Clicks module.) */
public class SettingsPage extends CardsPage {
	private long resetConfirmUntil;

	public SettingsPage() {
		Appearance appearance = Appearance.get();
		cards.add(new SettingsCard("Menu", "Window size, rounding, animations and particles",
				ModuleManager.get().get(ClickGuiModule.class).getSettings()));
		cards.add(new SettingsCard("Text", "Font and text size of the whole menu", appearance.getSettings(Appearance.Tab.FONT)));
		ActionSetting resetAll = new ActionSetting("Reset all modules",
				"Every module back to its default settings and off (click Run twice)", this::resetAll);
		cards.add(new SettingsCard("Reset", null, List.of(resetAll)));
	}

	private void resetAll() {
		if (System.currentTimeMillis() < resetConfirmUntil) {
			ModuleManager.get().resetAll();
			ConfigManager.get().save();
			resetConfirmUntil = 0;
			Appearance.get().setStatus("Every module was reset");
		} else {
			resetConfirmUntil = System.currentTimeMillis() + 3000;
			Appearance.get().setStatus("Click Run again within 3 seconds to reset every module");
		}
	}

	@Override
	public String titleAccent() {
		return "";
	}

	@Override
	public String title() {
		return "Settings";
	}

	@Override
	public String subtitle() {
		String status = Appearance.get().getStatus();
		return status.isEmpty() ? "Menu layout and font" : status;
	}
}
