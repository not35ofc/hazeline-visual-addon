package com.hazeline.module.modules.settings;

import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.NumberSetting;

/**
 * Layout settings of the click GUI window (size, corner rounding, screen dim, background
 * particles, animations). It cannot be toggled. Colours and font live in
 * {@link com.hazeline.appearance.Appearance} (Theme page and the appearance editor).
 */
public class ClickGuiModule extends Module {
	public final NumberSetting screenDim = add(new NumberSetting("Screen dim", "How much the world behind the menu is darkened", 0.35, 0.0, 1.0, 0.05));
	public final NumberSetting windowSize = add(new NumberSetting("Window size", "Size of the menu window in % of the screen", 72, 50, 95, 1).applyOnRelease());
	public final NumberSetting cornerRadius = add(new NumberSetting("Corner rounding", "Radius of the window and card corners in pixels at font size 9", 5, 0, 10, 1));
	public final BooleanSetting animations = add(new BooleanSetting("Animations", "Smooth open, hover and switch animations", true));
	public final BooleanSetting particles = add(new BooleanSetting("Particles", "Floating glowing particles behind the menu", true));
	public final NumberSetting particleCount = add(new NumberSetting("Particle count", "How many particles float around", 70, 10, 200, 5), particles::get);
	public final NumberSetting particleSpeed = add(new NumberSetting("Particle speed", "How fast they drift", 1.0, 0.2, 3.0, 0.1), particles::get);
	public final BooleanSetting particleLines = add(new BooleanSetting("Particle lines", "Thin lines between particles that are close together", true), particles::get);
	public final BooleanSetting mouseInteraction = add(new BooleanSetting("Particles follow mouse", "Particles near the mouse are gently pulled toward it and linked to it", true), particles::get);

	public ClickGuiModule() {
		super("Click GUI", "Layout of the Hazeline menu (colours: Theme page, fonts: Settings page, sounds: Sounds and Clicks module)", Category.SETTINGS, true);
	}

	@Override
	public boolean isToggleable() {
		return false;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
