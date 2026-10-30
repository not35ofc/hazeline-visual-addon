package com.hazeline.gui.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.client.gui.DrawContext;

import com.hazeline.appearance.SoundAction;
import com.hazeline.appearance.SoundManager;
import com.hazeline.gui.Anim;
import com.hazeline.gui.Colors;
import com.hazeline.gui.Icons;
import com.hazeline.gui.Theme;
import com.hazeline.gui.Widgets;
import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.module.ModuleManager;

/**
 * The modules of one category (or the search results across all categories) as cards, in a list
 * or a grid. The switch on a card turns the module on/off; clicking anywhere else on the card
 * (or right clicking it) opens its settings.
 */
public class ModulesPage extends Page {
	private final Category category;
	private final Supplier<String> query;
	private final Supplier<Boolean> grid;
	private final java.util.function.Consumer<Module> open;

	/** {@code category} null = search results across every category. */
	public ModulesPage(Category category, Supplier<String> query, Supplier<Boolean> grid, java.util.function.Consumer<Module> open) {
		this.category = category;
		this.query = query;
		this.grid = grid;
		this.open = open;
	}

	public Category category() {
		return category;
	}

	private static boolean listed(Module module) {
		return module.getCategory() != Category.SETTINGS;
	}

	List<Module> modules() {
		List<Module> list = new ArrayList<>();
		String q = query.get() == null ? "" : query.get().trim().toLowerCase(Locale.ROOT);
		List<Module> source = category == null ? ModuleManager.get().getModules() : ModuleManager.get().getModules(category);
		for (Module m : source) {
			if (!listed(m)) {
				continue;
			}
			if (!q.isEmpty() && !m.getName().toLowerCase(Locale.ROOT).contains(q)
					&& !m.getDescription().toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			list.add(m);
		}
		return list;
	}

	@Override
	public String titleAccent() {
		return category == null ? "Search" : category.getDisplayName();
	}

	@Override
	public String title() {
		return category == null ? "Results" : "Modules";
	}

	@Override
	public String subtitle() {
		List<Module> list = modules();
		int enabled = 0;
		for (Module m : list) {
			if (m.isToggleable() && m.isEnabled()) {
				enabled++;
			}
		}
		return list.size() + (list.size() == 1 ? " module" : " modules") + "  ·  " + enabled + " enabled";
	}

	@Override
	public boolean showsSearch() {
		return true;
	}

	// ------------------------------------------------------------------ layout

	private static int gap() {
		return Theme.px(5);
	}

	private static int listCardH() {
		return Theme.px(30);
	}

	private static int gridCardH() {
		return Theme.px(52);
	}

	private static int columns(int width) {
		return width >= Theme.px(360) ? 3 : width >= Theme.px(220) ? 2 : 1;
	}

	@Override
	public int contentHeight(int width) {
		int n = modules().size();
		if (n == 0) {
			return Theme.px(40);
		}
		if (grid.get()) {
			int rows = (n + columns(width) - 1) / columns(width);
			return rows * gridCardH() + (rows - 1) * gap();
		}
		return n * listCardH() + (n - 1) * gap();
	}

	private record Card(Module module, int x, int y, int w, int h) {
	}

	private List<Card> layout(int x, int top, int width) {
		List<Card> cards = new ArrayList<>();
		List<Module> list = modules();
		if (grid.get()) {
			int cols = columns(width);
			int cw = (width - gap() * (cols - 1)) / cols;
			for (int i = 0; i < list.size(); i++) {
				cards.add(new Card(list.get(i), x + (i % cols) * (cw + gap()), top + (i / cols) * (gridCardH() + gap()), cw, gridCardH()));
			}
		} else {
			for (int i = 0; i < list.size(); i++) {
				cards.add(new Card(list.get(i), x, top + i * (listCardH() + gap()), width, listCardH()));
			}
		}
		return cards;
	}

	private List<Card> lastLayout = List.of();

	/** Switch position inside a card. */
	private int[] switchRect(Card c) {
		int sw = Widgets.switchW();
		int sh = Widgets.switchH();
		if (grid.get()) {
			return new int[] {c.x() + c.w() - Theme.px(8) - sw, c.y() + Theme.px(8), sw, sh};
		}
		return new int[] {c.x() + c.w() - Theme.px(10) - sw, c.y() + (c.h() - sh) / 2, sw, sh};
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void render(DrawContext g, int x, int top, int width, int mouseX, int mouseY, float delta) {
		lastLayout = layout(x, top, width);
		if (lastLayout.isEmpty()) {
			String msg = category == null ? "No modules match your search" : "No modules here yet";
			Theme.boxTextOf(g, msg, x + (width - Theme.widthOf(msg, 1.0, false)) / 2f, top, Theme.px(40), Theme.textSecondary(), 1.0, false);
			return;
		}
		for (Card c : lastLayout) {
			renderCard(g, c, mouseX, mouseY);
		}
	}

	private void renderCard(DrawContext g, Card c, int mouseX, int mouseY) {
		Module m = c.module();
		boolean hoverCard = Theme.isHovered(mouseX, mouseY, c.x(), c.y(), c.w(), c.h());
		int[] sr = switchRect(c);
		boolean hoverSwitch = Theme.isHovered(mouseX, mouseY, sr[0] - 3, sr[1] - 3, sr[2] + 6, sr[3] + 6);
		float hover = Anim.hover(m, hoverCard);
		boolean on = m.isToggleable() && m.isEnabled();
		float onP = Anim.toggle(m, on);
		int r = Theme.cornerRadius();

		int bg = Colors.lerp(Theme.card(), Theme.cardHover(), hover);
		Theme.roundedRect(g, c.x(), c.y(), c.x() + c.w(), c.y() + c.h(), r, bg);
		int outline = Colors.lerp(Theme.border(), Theme.accent(150), Math.max(hover * 0.8f, onP * 0.55f));
		Theme.roundedOutline(g, c.x(), c.y(), c.w(), c.h(), r, outline);
		if (onP > 0.01f) {
			// Accent bar on the left edge of enabled modules.
			int bh = Math.round((c.h() - Theme.px(12)) * onP);
			int by = c.y() + (c.h() - bh) / 2;
			Theme.roundedRect(g, c.x() + Theme.px(3), by, c.x() + Theme.px(3) + Math.max(2, Theme.px(2)), by + bh, Theme.px(1),
					Theme.accent());
		}

		int tx = c.x() + Theme.px(10);
		float nameH = Theme.heightOf(1.0);
		float descH = Theme.heightOf(Theme.SMALL);
		boolean bold = true;
		if (grid.get()) {
			int nameMax = sr[0] - tx - Theme.px(4);
			Theme.textOf(g, Theme.fitOf(m.getName(), nameMax, 1.0, bold), tx, c.y() + Theme.px(7), Theme.text(), 1.0, bold);
			List<String> lines = Theme.wrapOf(m.getDescription(), c.w() - Theme.px(20), Theme.SMALL);
			float ly = c.y() + Theme.px(7) + nameH + Theme.px(3);
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				String line = lines.get(i);
				if (i == 1 && lines.size() > 2) {
					line = Theme.fitOf(line + " ...", c.w() - Theme.px(20), Theme.SMALL, false);
				}
				Theme.textOf(g, line, tx, ly, Theme.textSecondary(), Theme.SMALL, false);
				ly += descH + Theme.px(1);
			}
			int icon = Theme.px(7);
			Icons.draw(g, Icons.CHEVRON_RIGHT, c.x() + c.w() - Theme.px(8) - icon, c.y() + c.h() - Theme.px(6) - icon, icon,
					Colors.lerp(Theme.textSecondary(), Theme.text(), hover));
		} else {
			int icon = Theme.px(7);
			int chevronX = sr[0] - Theme.px(10) - icon;
			int textMax = chevronX - tx - Theme.px(6);
			float ty = c.y() + (c.h() - nameH - Theme.px(1.5f) - descH) / 2f;
			Theme.textOf(g, Theme.fitOf(m.getName(), textMax, 1.0, bold), tx, ty, Theme.text(), 1.0, bold);
			Theme.textOf(g, Theme.fitOf(m.getDescription(), textMax, Theme.SMALL, false), tx, ty + nameH + Theme.px(1.5f),
					Theme.textSecondary(), Theme.SMALL, false);
			Icons.draw(g, Icons.CHEVRON_RIGHT, chevronX + hover * Theme.px(1.5f), c.y() + (c.h() - icon) / 2f, icon,
					Colors.lerp(Theme.textSecondary(), Theme.text(), hover));
		}
		if (m.isToggleable()) {
			Widgets.toggleSwitch(g, sr[0], sr[1], onP, hoverSwitch);
		}
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Card c : lastLayout) {
			if (!Theme.isHovered(mouseX, mouseY, c.x(), c.y(), c.w(), c.h())) {
				continue;
			}
			Module m = c.module();
			int[] sr = switchRect(c);
			if (button == 0 && m.isToggleable() && Theme.isHovered(mouseX, mouseY, sr[0] - 3, sr[1] - 3, sr[2] + 6, sr[3] + 6)) {
				m.toggle();
				SoundManager.get().play(m.isEnabled() ? SoundAction.ENABLE : SoundAction.DISABLE);
				return true;
			}
			if (button == 0 || button == 1) {
				SoundManager.get().play(SoundAction.CLICK);
				open.accept(m);
				return true;
			}
		}
		return false;
	}
}
