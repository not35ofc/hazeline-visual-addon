package com.hazeline.gui.component;

import java.util.ArrayList;
import java.util.List;

import com.hazeline.setting.ChoiceSetting;

/** Dropdown for a {@link ChoiceSetting} whose options can change at runtime (fonts, sound packs). */
public class ChoiceComponent extends DropdownComponent<ChoiceSetting> {
	public ChoiceComponent(ChoiceSetting setting) {
		super(setting);
	}

	@Override
	protected List<String> labels() {
		List<String> labels = new ArrayList<>();
		for (ChoiceSetting.Choice choice : setting.getOptions()) {
			labels.add(choice.label());
		}
		return labels;
	}

	@Override
	protected int selectedIndex() {
		List<ChoiceSetting.Choice> options = setting.getOptions();
		for (int i = 0; i < options.size(); i++) {
			if (options.get(i).id().equals(setting.get())) {
				return i;
			}
		}
		return -1;
	}

	@Override
	protected void select(int index) {
		List<ChoiceSetting.Choice> options = setting.getOptions();
		if (index >= 0 && index < options.size()) {
			setting.set(options.get(index).id());
		}
	}

	@Override
	protected String currentLabel() {
		return setting.getLabel();
	}
}
