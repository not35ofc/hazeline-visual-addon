package com.hazeline.gui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

import com.hazeline.appearance.SoundAction;
import com.hazeline.gui.Anim;
import com.hazeline.gui.Colors;
import com.hazeline.gui.Icons;
import com.hazeline.gui.Theme;
import com.hazeline.gui.Widgets;
import com.hazeline.setting.EntityListSetting;
import com.hazeline.setting.EntityListSetting.Entry;
import com.hazeline.setting.EntityListSetting.Group;

/**
 * Picker for an {@link EntityListSetting}: the row shows what is selected; clicking it opens a
 * panel with group tabs (All / Players / Hostile / Animals / Water / Other), a search field,
 * "All on" / "All off" for the shown entries and a two-column list of check boxes.
 * Right click on the row resets to the default.
 */
public class EntityListComponent extends SettingComponent<EntityListSetting> {
	private static final String ALL_ON = "All on";
	private static final String ALL_OFF = "All off";

	private final TextField search = new TextField(32, cp -> cp >= 32 && cp != 127, text -> { });
	private boolean open;
	private Group group = Group.ALL;

	public EntityListComponent(EntityListSetting setting) {
		super(setting);
	}

	// ------------------------------------------------------------------ layout

	private static int gap() {
		return Theme.px(4);
	}

	private static int ctlH() {
		return Theme.px(14);
	}

	private static int rowH() {
		return Theme.px(14);
	}

	private int innerX() {
		return x + indent();
	}

	private int innerW() {
		return Math.max(Theme.px(40), width - 2 * indent());
	}

	private int columns() {
		return innerW() >= Theme.px(190) ? 2 : 1;
	}

	private static int chipW(Group g) {
		return Theme.widthOf(g.toString(), Theme.SMALL, false) + Theme.px(12);
	}

	/** x, y (relative to the panel top) of every group chip, wrapping onto new lines. */
	private int[][] chipPositions() {
		Group[] groups = Group.values();
		int[][] out = new int[groups.length][2];
		int cx = 0, cy = 0;
		for (int i = 0; i < groups.length; i++) {
			int w = chipW(groups[i]);
			if (cx > 0 && cx + w > innerW()) {
				cx = 0;
				cy += ctlH() + gap();
			}
			out[i][0] = cx;
			out[i][1] = cy;
			cx += w + gap();
		}
		return out;
	}

	private int chipsHeight() {
		int[][] p = chipPositions();
		return p[p.length - 1][1] + ctlH();
	}

	private List<Entry> shown() {
		String q = search.getText().trim().toLowerCase(Locale.ROOT);
		List<Entry> out = new ArrayList<>();
		for (Entry e : EntityListSetting.entries()) {
			if (group != Group.ALL && e.group() != group) {
				continue;
			}
			if (!q.isEmpty() && !e.label().toLowerCase(Locale.ROOT).contains(q) && !e.id().contains(q)) {
				continue;
			}
			out.add(e);
		}
		return out;
	}

	private int listTop() {
		return chipsHeight() + gap() + ctlH() + gap();
	}

	private int panelHeight(List<Entry> list) {
		int rows = list.isEmpty() ? 1 : (list.size() + columns() - 1) / columns();
		return gap() + listTop() + rows * rowH() + gap();
	}

	@Override
	public int getHeight() {
		return labelHeight() + (open ? panelHeight(shown()) : 0);
	}

	private int boxW() {
		return Math.min(Theme.px(130), Math.max(Theme.px(60), width / 2));
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void render(DrawContext g, int mouseX, int mouseY, float delta) {
		int lh = labelHeight();
		boolean hover = hovered(mouseX, mouseY, lh);
		int bw = boxW();
		int bh = ctlH();
		int bx = controlRight() - bw;
		int by = y + (lh - bh) / 2;
		drawLabel(g, bx, lh, hover);
		float h = Anim.approach(this, 3, hover || open ? 1f : 0f, 14f);
		int r = Math.min(Theme.cornerRadius(), bh / 2);
		Theme.roundedRect(g, bx, by, bx + bw, by + bh, r, Colors.lerp(Theme.inset(), Colors.mix(Theme.inset(), Theme.text(), 0.06f), h));
		Theme.roundedOutline(g, bx, by, bw, bh, r, open ? Theme.accent() : Theme.border());
		int icon = Theme.px(7);
		Theme.boxTextOf(g, Theme.fitOf(setting.summary(), bw - icon - Theme.px(12), Theme.SMALL, false), bx + Theme.px(6), by, bh,
				Theme.text(), Theme.SMALL, false);
		Icons.draw(g, open ? Icons.CHEVRON_UP : Icons.CHEVRON_DOWN, bx + bw - icon - Theme.px(5), by + (bh - icon) / 2, icon,
				Theme.textSecondary());
		if (!open) {
			return;
		}

		List<Entry> list = shown();
		int top = y + lh + gap();
		int ix = innerX();
		int iw = innerW();
		Theme.roundedRect(g, ix - Theme.px(3), y + lh, ix + iw + Theme.px(3), y + lh + panelHeight(list) - Theme.px(1),
				Theme.cornerRadius(), Theme.inset());

		// group chips
		Group[] groups = Group.values();
		int[][] chips = chipPositions();
		for (int i = 0; i < groups.length; i++) {
			int cx = ix + chips[i][0], cy = top + chips[i][1], cw = chipW(groups[i]);
			boolean over = Theme.isHovered(mouseX, mouseY, cx, cy, cw, ctlH());
			boolean sel = groups[i] == group;
			int cr = Math.min(Theme.cornerRadius(), ctlH() / 2);
			Theme.roundedRect(g, cx, cy, cx + cw, cy + ctlH(), cr, sel ? Theme.accent(200) : over ? Theme.hover() : Theme.card());
			Theme.boxTextOf(g, groups[i].toString(), cx + Theme.px(6), cy, ctlH(), sel ? Theme.text() : Theme.textSecondary(), Theme.SMALL,
					false);
		}

		// search + buttons
		int sy = top + chipsHeight() + gap();
		int onW = Widgets.buttonWidth(ALL_ON), offW = Widgets.buttonWidth(ALL_OFF);
		int fieldW = Math.max(Theme.px(40), iw - onW - offW - 2 * gap());
		int icon2 = Theme.px(7);
		search.render(g, ix, sy, fieldW, ctlH(), search.getText().isEmpty() ? "Search..." : search.getText(), icon2 + Theme.px(3));
		Icons.draw(g, Icons.SEARCH, ix + Theme.px(5), sy + (ctlH() - icon2) / 2, icon2, Theme.textSecondary());
		int onX = ix + fieldW + gap(), offX = onX + onW + gap();
		Widgets.button(g, onX, sy, onW, ctlH(), ALL_ON, false, Theme.isHovered(mouseX, mouseY, onX, sy, onW, ctlH()) ? 1f : 0f);
		Widgets.button(g, offX, sy, offW, ctlH(), ALL_OFF, false, Theme.isHovered(mouseX, mouseY, offX, sy, offW, ctlH()) ? 1f : 0f);

		// entries
		int ly = top + listTop();
		if (list.isEmpty()) {
			Theme.boxTextOf(g, "No matching entities", ix + Theme.px(2), ly, rowH(), Theme.textSecondary(), Theme.SMALL, false);
			return;
		}
		int cols = columns();
		int colW = (iw - (cols - 1) * gap()) / cols;
		int box = Theme.px(8);
		for (int i = 0; i < list.size(); i++) {
			Entry e = list.get(i);
			int ex = ix + (i % cols) * (colW + gap());
			int ey = ly + (i / cols) * rowH();
			boolean on = setting.contains(e.id());
			if (Theme.isHovered(mouseX, mouseY, ex, ey, colW, rowH())) {
				Theme.roundedRect(g, ex, ey, ex + colW, ey + rowH(), Math.min(Theme.cornerRadius(), rowH() / 3), Theme.hover());
			}
			int bxx = ex + Theme.px(3), byy = ey + (rowH() - box) / 2;
			BooleanComponent.drawCheckbox(g, bxx, byy, box, on);
			if (on) {
				Icons.draw(g, Icons.CHECK, bxx + Theme.px(1), byy + Theme.px(1), box - Theme.px(2), Theme.text());
			}
			Theme.boxTextOf(g, Theme.fitOf(e.label(), colW - box - Theme.px(10), Theme.SMALL, false), bxx + box + Theme.px(4), ey, rowH(),
					on ? Theme.text() : Theme.textSecondary(), Theme.SMALL, false);
		}
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int lh = labelHeight();
		if (hovered(mouseX, mouseY, lh)) {
			if (button == 0) {
				open = !open;
				if (!open) {
					search.submit();
				}
			} else if (button == 1) {
				setting.reset();
				changed();
			}
			sound(SoundAction.CLICK);
			return true;
		}
		if (!open || !hovered(mouseX, mouseY, getHeight())) {
			return false;
		}
		if (button != 0) {
			return true;
		}
		int top = y + lh + gap();
		int ix = innerX();
		int iw = innerW();
		Group[] groups = Group.values();
		int[][] chips = chipPositions();
		for (int i = 0; i < groups.length; i++) {
			if (Theme.isHovered(mouseX, mouseY, ix + chips[i][0], top + chips[i][1], chipW(groups[i]), ctlH())) {
				group = groups[i];
				sound(SoundAction.CLICK);
				return true;
			}
		}
		int sy = top + chipsHeight() + gap();
		int onW = Widgets.buttonWidth(ALL_ON), offW = Widgets.buttonWidth(ALL_OFF);
		int fieldW = Math.max(Theme.px(40), iw - onW - offW - 2 * gap());
		if (Theme.isHovered(mouseX, mouseY, ix, sy, fieldW, ctlH())) {
			if (!search.isFocused()) {
				search.focus(search.getText());
			}
			sound(SoundAction.CLICK);
			return true;
		}
		int onX = ix + fieldW + gap(), offX = onX + onW + gap();
		boolean allOn = Theme.isHovered(mouseX, mouseY, onX, sy, onW, ctlH());
		boolean allOff = Theme.isHovered(mouseX, mouseY, offX, sy, offW, ctlH());
		if (allOn || allOff) {
			List<String> ids = new ArrayList<>();
			for (Entry e : shown()) {
				ids.add(e.id());
			}
			setting.setAll(ids, allOn);
			changed();
			sound(SoundAction.CLICK);
			return true;
		}
		List<Entry> list = shown();
		int ly = top + listTop();
		int cols = columns();
		int colW = (iw - (cols - 1) * gap()) / cols;
		for (int i = 0; i < list.size(); i++) {
			int ex = ix + (i % cols) * (colW + gap());
			int ey = ly + (i / cols) * rowH();
			if (Theme.isHovered(mouseX, mouseY, ex, ey, colW, rowH())) {
				setting.toggle(list.get(i).id());
				changed();
				sound(SoundAction.CLICK);
				return true;
			}
		}
		return true; // clicks on the panel background stay here
	}

	@Override
	public void onGlobalClick(double mouseX, double mouseY) {
		if (search.isFocused()) {
			int lh = labelHeight();
			int sy = y + lh + gap() + chipsHeight() + gap();
			int onW = Widgets.buttonWidth(ALL_ON), offW = Widgets.buttonWidth(ALL_OFF);
			int fieldW = Math.max(Theme.px(40), innerW() - onW - offW - 2 * gap());
			if (!Theme.isHovered(mouseX, mouseY, innerX(), sy, fieldW, ctlH())) {
				search.submit();
			}
		}
	}

	@Override
	public boolean keyPressed(KeyInput event) {
		return search.keyPressed(event);
	}

	@Override
	public boolean charTyped(int codepoint) {
		return search.charTyped(codepoint);
	}

	@Override
	public boolean isCapturingKeyboard() {
		return search.isFocused();
	}
}
