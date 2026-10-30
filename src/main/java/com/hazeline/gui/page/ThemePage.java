package com.hazeline.gui.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.DrawContext;

import com.hazeline.appearance.Appearance;
import com.hazeline.appearance.SoundAction;
import com.hazeline.appearance.SoundManager;
import com.hazeline.appearance.ThemePreset;
import com.hazeline.gui.Anim;
import com.hazeline.gui.Colors;
import com.hazeline.gui.Icons;
import com.hazeline.gui.Shapes;
import com.hazeline.gui.Theme;
import com.hazeline.module.ModuleManager;
import com.hazeline.setting.ActionSetting;
import com.hazeline.setting.Setting;

/**
 * Colour themes as a grid of cards (click to use, right click a custom one to delete it), and
 * below it every colour and effect for fine tuning, saving presets and theme codes.
 */
public class ThemePage extends CardsPage {
	private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
			Map.entry("Indigo", "Dark navy + indigo"), Map.entry("Vault", "Glass + violet"), Map.entry("Nova", "Dark indigo + cyan"),
			Map.entry("Rose", "Black plum + pink"), Map.entry("Ember", "Charcoal + orange"), Map.entry("Forest", "Deep moss + green"),
			Map.entry("Magenta", "Dark plum + magenta"), Map.entry("Crimson", "Near-black + red"), Map.entry("Lime", "Dark olive + lime"),
			Map.entry("Gold", "Dark brown + gold"), Map.entry("Aurora", "Deep teal + mint"), Map.entry("Hazeline", "Cream + warm yellow"),
			Map.entry("Dark", "Charcoal + amber"), Map.entry("Light", "Light grey + blue"), Map.entry("Ocean", "Deep sea + aqua"),
			Map.entry("Sakura", "Blossom pink + rose"));

	private record Tile(ThemePreset preset, int x, int y, int w, int h) {
	}

	private List<Tile> tiles = List.of();

	public ThemePage() {
		Appearance appearance = Appearance.get();
		List<Setting<?>> colours = new ArrayList<>(appearance.getSettings(Appearance.Tab.COLORS));
		colours.add(new ActionSetting("Reset colours", "All colours and effects back to the default theme",
				() -> appearance.resetTab(Appearance.Tab.COLORS)));
		cards.add(new SettingsCard("Custom colours", "Fine-tune the current theme, save it or share it as a code", colours));
	}

	@Override
	public String titleAccent() {
		return "";
	}

	@Override
	public String title() {
		return "Themes";
	}

	@Override
	public String subtitle() {
		String status = Appearance.get().getStatus();
		return status.isEmpty() ? "Menu accent and colours" : status;
	}

	private static int tileGap() {
		return Theme.px(6);
	}

	private static int columns(int width) {
		return width >= Theme.px(330) ? 3 : width >= Theme.px(200) ? 2 : 1;
	}

	private static int tileH() {
		return Theme.px(46);
	}

	@Override
	protected int topHeight(int width) {
		int n = Appearance.get().getAllPresets().size();
		int rows = (n + columns(width) - 1) / columns(width);
		return rows * tileH() + (rows - 1) * tileGap();
	}

	@Override
	protected void renderTop(DrawContext g, int x, int top, int width, int mouseX, int mouseY) {
		List<ThemePreset> presets = Appearance.get().getAllPresets();
		int cols = columns(width);
		int tw = (width - tileGap() * (cols - 1)) / cols;
		List<Tile> layout = new ArrayList<>();
		for (int i = 0; i < presets.size(); i++) {
			layout.add(new Tile(presets.get(i), x + (i % cols) * (tw + tileGap()), top + (i / cols) * (tileH() + tileGap()), tw, tileH()));
		}
		tiles = layout;
		ThemePreset current = Appearance.get().currentAsPreset("");
		for (Tile t : layout) {
			renderTile(g, t, matches(t.preset(), current), mouseX, mouseY);
		}
	}

	private static boolean matches(ThemePreset p, ThemePreset c) {
		return p.accent() == c.accent() && p.panelBackground() == c.panelBackground() && p.panelHeader() == c.panelHeader()
				&& p.text() == c.text() && p.textSecondary() == c.textSecondary() && p.border() == c.border();
	}

	private void renderTile(DrawContext g, Tile t, boolean selected, int mouseX, int mouseY) {
		ThemePreset p = t.preset();
		float hover = Anim.hover(p, Theme.isHovered(mouseX, mouseY, t.x(), t.y(), t.w(), t.h()));
		int r = Theme.cornerRadius();
		// The tile is drawn in the preset's own colours.
		int bg = Theme.fade(Colors.withAlpha(Colors.mix(p.panelBackground(), p.panelHeader(), 0.5f), 0xF0));
		Theme.roundedRect(g, t.x(), t.y(), t.x() + t.w(), t.y() + t.h(), r, bg);
		int outline = selected ? Theme.fade(p.accent()) : Colors.lerp(Theme.border(), Theme.fade(Colors.withAlpha(p.accent(), 0xB0)), hover);
		Theme.roundedOutline(g, t.x(), t.y(), t.w(), t.h(), r, outline);
		if (hover > 0.01f) {
			Shapes.glow(g, t.x() + t.w() * 0.8f, t.y() + t.h() * 0.2f, t.w() * 0.5f,
					Theme.fade(Colors.withAlpha(p.accent(), Math.round(40 * hover))));
		}

		// Swatches: background, header, accent.
		int sw = Theme.px(14);
		int sh = Theme.px(10);
		int sx = t.x() + Theme.px(7);
		int sy = t.y() + Theme.px(7);
		int sr = Math.max(1, Theme.px(2));
		int[] colours = {Colors.withAlpha(p.panelBackground(), 0xFF), p.panelHeader(), p.accent()};
		for (int i = 0; i < colours.length; i++) {
			int cx = sx + i * (sw + Theme.px(3));
			Theme.roundedRect(g, cx, sy, cx + sw, sy + sh, sr, Theme.fade(colours[i]));
			Theme.roundedOutline(g, cx, sy, sw, sh, sr, Theme.fade(Colors.withAlpha(p.border(), 0xC0)));
		}
		if (Colors.alpha(p.panelBackground()) < 0xFF) {
			String tag = "GLASS";
			int tw = Theme.widthOf(tag, 0.65, true) + Theme.px(6);
			int th = Theme.px(9);
			int tx = sx + 3 * (sw + Theme.px(3)) + Theme.px(2);
			Theme.roundedRect(g, tx, sy + (sh - th) / 2, tx + tw, sy + (sh - th) / 2 + th, th / 2,
					Theme.fade(Colors.withAlpha(p.accent(), 0x40)));
			Theme.boxTextOf(g, tag, tx + Theme.px(3), sy + (sh - th) / 2f, th, Theme.fade(p.accent()), 0.65, true);
		}
		if (selected) {
			int b = Theme.px(11);
			float bx = t.x() + t.w() - Theme.px(6) - b;
			float by = t.y() + Theme.px(6);
			Shapes.disc(g, bx + b / 2f, by + b / 2f, b / 2f, Theme.fade(p.accent()));
			Icons.draw(g, Icons.CHECK, bx + b * 0.18f, by + b * 0.18f, b * 0.64f, Theme.fade(0xFFFFFFFF));
		}

		int textX = t.x() + Theme.px(7);
		int maxW = t.w() - Theme.px(14);
		float nameY = sy + sh + Theme.px(5);
		Theme.textOf(g, Theme.fitOf(p.name(), maxW, 1.0, true), textX, nameY, Theme.fade(p.text()), 1.0, true);
		boolean custom = Appearance.get().isCustomPreset(p);
		String desc = custom ? "Your preset  ·  right click to delete" : DESCRIPTIONS.getOrDefault(p.name(), "");
		Theme.textOf(g, Theme.fitOf(desc, maxW, Theme.SMALL, false), textX, nameY + Theme.heightOf(1.0) + Theme.px(1),
				Theme.fade(p.textSecondary()), Theme.SMALL, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Tile t : tiles) {
			if (Theme.isHovered(mouseX, mouseY, t.x(), t.y(), t.w(), t.h())) {
				Appearance appearance = Appearance.get();
				if (button == 0) {
					appearance.applyPreset(t.preset());
				} else if (button == 1 && appearance.isCustomPreset(t.preset())) {
					appearance.deleteCustomPreset(t.preset().name());
				}
				ModuleManager.get().markDirty();
				SoundManager.get().play(SoundAction.CLICK);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
}
