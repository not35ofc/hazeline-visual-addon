package com.hazeline.gui.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.hazeline.Hazeline;
import com.hazeline.gui.component.KeybindComponent;
import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.module.ModuleManager;
import com.hazeline.setting.InfoSetting;

/**
 * Every module's toggle key in one list (modules that have a key first). Click a key box, then
 * press a key (Backspace = none, Esc = cancel); right click a row to unbind.
 */
public class KeybindsPage extends CardsPage {
	/** A keybind row labelled with the module instead of "Keybind". */
	private static final class ModuleKeyRow extends KeybindComponent {
		private final Module module;

		ModuleKeyRow(Module module) {
			super(module.getKeybind());
			this.module = module;
		}

		@Override
		protected String labelName() {
			return module.getName();
		}

		@Override
		protected String labelDescription() {
			return module.getCategory().getDisplayName();
		}
	}

	public KeybindsPage() {
		InfoSetting menuKey = new InfoSetting("Open / close this menu",
				"Change it in Options > Controls > Hazeline",
				() -> (Hazeline.getOpenGuiKey() == null ? "Right Shift" : Hazeline.getOpenGuiKey().getBoundKeyLocalizedText().getString())
						+ "  (change it in Options > Controls > Key Binds > Hazeline)");
		cards.add(new SettingsCard("Menu", null, List.of(menuKey)));

		List<Module> modules = new ArrayList<>();
		for (Module m : ModuleManager.get().getModules()) {
			if (m.isToggleable() && m.getCategory() != Category.SETTINGS) {
				modules.add(m);
			}
		}
		modules.sort(Comparator.comparing((Module m) -> !m.getKeybind().isBound()).thenComparing(Module::getName));
		SettingsCard keys = new SettingsCard("Module keys", "Click a key box, then press a key. Right click = unbind");
		for (Module m : modules) {
			keys.add(new ModuleKeyRow(m));
		}
		cards.add(keys);
	}

	@Override
	public String titleAccent() {
		return "";
	}

	@Override
	public String title() {
		return "Keybinds";
	}

	@Override
	public String subtitle() {
		int bound = 0;
		for (Module m : ModuleManager.get().getModules()) {
			if (m.isToggleable() && m.getKeybind().isBound()) {
				bound++;
			}
		}
		return "Rebind any module  ·  " + bound + " bound";
	}
}
