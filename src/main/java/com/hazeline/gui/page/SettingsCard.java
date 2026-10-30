package com.hazeline.gui.page;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

import com.hazeline.gui.Colors;
import com.hazeline.gui.Theme;
import com.hazeline.gui.component.Component;
import com.hazeline.gui.component.SettingComponent;
import com.hazeline.setting.PresetsSetting;
import com.hazeline.setting.Setting;

/**
 * A card holding a list of setting rows (optionally with a title), separated by thin lines.
 * Used by the module settings page and the Settings / Theme pages.
 */
public class SettingsCard {
	private final String title;
	private final String subtitle;
	private final List<Component> components = new ArrayList<>();

	public SettingsCard(String title, String subtitle, List<? extends Setting<?>> settings) {
		this.title = title;
		this.subtitle = subtitle;
		for (Setting<?> setting : settings) {
			if (setting instanceof PresetsSetting) {
				continue; // the Theme page draws presets as a grid
			}
			components.add(SettingComponent.create(setting));
		}
	}

	public SettingsCard(String title, String subtitle) {
		this.title = title;
		this.subtitle = subtitle;
	}

	public SettingsCard add(Component component) {
		components.add(component);
		return this;
	}

	private List<Component> visible() {
		List<Component> list = new ArrayList<>();
		for (Component c : components) {
			if (c.isVisible()) {
				list.add(c);
			}
		}
		return list;
	}

	private int headerHeight() {
		if (title == null) {
			return Theme.px(3);
		}
		return subtitle == null ? Theme.px(20) : Theme.px(28);
	}

	public int height(int width) {
		int h = headerHeight() + Theme.px(3);
		for (Component c : visible()) {
			c.setWidth(width);
			h += c.getHeight();
		}
		return h;
	}

	public void render(DrawContext g, int x, int y, int width, int mouseX, int mouseY, float delta) {
		int h = height(width);
		int r = Theme.cornerRadius();
		Theme.roundedRect(g, x, y, x + width, y + h, r, Theme.card());
		Theme.roundedOutline(g, x, y, width, h, r, Theme.border());
		if (title != null) {
			int tx = x + Theme.px(8);
			if (subtitle == null) {
				Theme.boxTextOf(g, title, tx, y + Theme.px(3), Theme.px(16), Theme.text(), 1.05, true);
			} else {
				float nameH = Theme.heightOf(1.05);
				Theme.textOf(g, title, tx, y + Theme.px(6), Theme.text(), 1.05, true);
				Theme.textOf(g, Theme.fitOf(subtitle, width - Theme.px(16), Theme.SMALL, false), tx, y + Theme.px(6) + nameH + Theme.px(1.5f),
						Theme.textSecondary(), Theme.SMALL, false);
			}
		}
		int cy = y + headerHeight();
		List<Component> visible = visible();
		int line = Colors.withAlpha(Theme.border(), Colors.alpha(Theme.border()) / 2);
		for (int i = 0; i < visible.size(); i++) {
			Component c = visible.get(i);
			if (i > 0 || title != null) {
				g.fill(x + Theme.px(8), cy, x + width - Theme.px(8), cy + 1, line);
			}
			c.setBounds(x, cy, width);
			c.render(g, mouseX, mouseY, delta);
			cy += c.getHeight();
		}
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Component c : visible()) {
			if (c.mouseClicked(mouseX, mouseY, button)) {
				return true;
			}
		}
		return false;
	}

	public void onGlobalClick(double mouseX, double mouseY) {
		for (Component c : visible()) {
			c.onGlobalClick(mouseX, mouseY);
		}
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
		for (Component c : components) {
			c.mouseReleased(mouseX, mouseY, button);
		}
	}

	public void mouseDragged(double mouseX, double mouseY) {
		for (Component c : visible()) {
			c.mouseDragged(mouseX, mouseY);
		}
	}

	public boolean keyPressed(KeyInput event) {
		for (Component c : visible()) {
			if (c.keyPressed(event)) {
				return true;
			}
		}
		return false;
	}

	public boolean charTyped(int codepoint) {
		for (Component c : visible()) {
			if (c.charTyped(codepoint)) {
				return true;
			}
		}
		return false;
	}

	public boolean isCapturingKeyboard() {
		for (Component c : visible()) {
			if (c.isCapturingKeyboard()) {
				return true;
			}
		}
		return false;
	}
}
