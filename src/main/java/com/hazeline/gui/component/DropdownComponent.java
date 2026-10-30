package com.hazeline.gui.component;

import java.util.List;

import net.minecraft.client.gui.DrawContext;

import com.hazeline.appearance.SoundAction;
import com.hazeline.gui.Anim;
import com.hazeline.gui.Colors;
import com.hazeline.gui.Icons;
import com.hazeline.gui.Theme;
import com.hazeline.setting.Setting;

/**
 * Base for "pick one" settings: a value box with an arrow on the right that opens a list below
 * the row. Left click opens/closes, right click steps to the next option.
 */
public abstract class DropdownComponent<S extends Setting<?>> extends SettingComponent<S> {
	private boolean open;

	protected DropdownComponent(S setting) {
		super(setting);
	}

	/** Labels of all entries in display order. */
	protected abstract List<String> labels();

	protected abstract int selectedIndex();

	protected abstract void select(int index);

	protected abstract String currentLabel();

	private int optionH() {
		return Theme.px(15);
	}

	private int boxW() {
		int widest = Theme.px(40);
		for (String label : labels()) {
			widest = Math.max(widest, Theme.widthOf(label, Theme.SMALL, false));
		}
		return Math.min(Math.max(Theme.px(40), widest + Theme.px(22)), Math.max(Theme.px(40), width / 2));
	}

	private int boxH() {
		return Theme.px(14);
	}

	private int listHeight() {
		return labels().size() * optionH() + Theme.px(4);
	}

	@Override
	public int getHeight() {
		return labelHeight() + (open ? listHeight() + Theme.px(4) : 0);
	}

	@Override
	public void render(DrawContext g, int mouseX, int mouseY, float delta) {
		int lh = labelHeight();
		boolean hover = hovered(mouseX, mouseY, lh);
		int bw = boxW();
		int bh = boxH();
		int bx = controlRight() - bw;
		int by = y + (lh - bh) / 2;
		drawLabel(g, bx, lh, hover);

		float h = Anim.approach(this, 3, hover || open ? 1f : 0f, 14f);
		int r = Math.min(Theme.cornerRadius(), bh / 2);
		Theme.roundedRect(g, bx, by, bx + bw, by + bh, r, Colors.lerp(Theme.inset(), Colors.mix(Theme.inset(), Theme.text(), 0.06f), h));
		Theme.roundedOutline(g, bx, by, bw, bh, r, open ? Theme.accent() : Theme.border());
		int icon = Theme.px(7);
		String value = Theme.fitOf(currentLabel(), bw - icon - Theme.px(12), Theme.SMALL, false);
		Theme.boxTextOf(g, value, bx + Theme.px(6), by, bh, Theme.text(), Theme.SMALL, false);
		Icons.draw(g, open ? Icons.CHEVRON_UP : Icons.CHEVRON_DOWN, bx + bw - icon - Theme.px(5), by + (bh - icon) / 2, icon,
				Theme.textSecondary());

		if (!open) {
			return;
		}
		List<String> labels = labels();
		int selected = selectedIndex();
		int ly = y + lh + Theme.px(2);
		Theme.roundedRect(g, bx, ly, bx + bw, ly + listHeight(), r, Theme.inset());
		Theme.roundedOutline(g, bx, ly, bw, listHeight(), r, Theme.border());
		int oy = ly + Theme.px(2);
		for (int i = 0; i < labels.size(); i++) {
			boolean over = Theme.isHovered(mouseX, mouseY, bx, oy, bw, optionH());
			if (over || i == selected) {
				Theme.roundedRect(g, bx + Theme.px(2), oy, bx + bw - Theme.px(2), oy + optionH(), r,
						i == selected ? Theme.accent(60) : Theme.hover());
			}
			Theme.boxTextOf(g, Theme.fitOf(labels.get(i), bw - Theme.px(12), Theme.SMALL, false), bx + Theme.px(6), oy, optionH(),
					i == selected ? Theme.text() : Theme.textSecondary(), Theme.SMALL, false);
			oy += optionH();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int lh = labelHeight();
		if (hovered(mouseX, mouseY, lh)) {
			if (button == 0) {
				open = !open;
			} else if (button == 1) {
				int size = labels().size();
				if (size > 0) {
					select((selectedIndex() + 1 + size) % size);
					changed();
				}
			}
			sound(SoundAction.CLICK);
			return true;
		}
		if (open && hovered(mouseX, mouseY, getHeight())) {
			int bx = controlRight() - boxW();
			int oy = y + lh + Theme.px(4);
			if (mouseX >= bx && mouseX < bx + boxW()) {
				int index = (int) Math.floor((mouseY - oy) / optionH());
				if (index >= 0 && index < labels().size()) {
					select(index);
					changed();
				}
			}
			open = false;
			sound(SoundAction.CLICK);
			return true;
		}
		return false;
	}

	@Override
	public void onGlobalClick(double mouseX, double mouseY) {
		if (open && !hovered(mouseX, mouseY, getHeight())) {
			open = false;
		}
	}
}
