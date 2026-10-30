package com.hazeline.gui.component;

import java.util.ArrayList;
import java.util.List;

import com.hazeline.setting.EnumSetting;

/** Dropdown for an {@link EnumSetting} (constants' {@code toString()} are the labels). */
public class EnumComponent extends DropdownComponent<EnumSetting<?>> {
	public EnumComponent(EnumSetting<?> setting) {
		super(setting);
	}

	@Override
	protected List<String> labels() {
		List<String> labels = new ArrayList<>();
		for (Object constant : setting.getConstants()) {
			labels.add(constant.toString());
		}
		return labels;
	}

	@Override
	protected int selectedIndex() {
		return setting.get().ordinal();
	}

	@Override
	protected void select(int index) {
		setting.setIndex(index);
	}

	@Override
	protected String currentLabel() {
		return setting.get().toString();
	}
}
