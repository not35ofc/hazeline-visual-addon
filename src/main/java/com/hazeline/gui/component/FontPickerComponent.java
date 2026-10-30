package com.hazeline.gui.component;

import java.util.List;

import net.minecraft.client.gui.DrawContext;

import com.hazeline.appearance.Appearance;
import com.hazeline.appearance.FontManager;
import com.hazeline.appearance.SoundAction;
import com.hazeline.config.ConfigManager;
import com.hazeline.gui.Anim;
import com.hazeline.gui.Colors;
import com.hazeline.gui.Icons;
import com.hazeline.gui.Shapes;
import com.hazeline.gui.Theme;
import com.hazeline.setting.FontPickerSetting;

/**
 * Font chooser: one card per bundled font in a grid, each previewing its name, description and
 * a sample line in that font. Click a card to use the font (saved right away).
 */
public class FontPickerComponent extends SettingComponent<FontPickerSetting> {
	private static final String SAMPLE = "AaBbCc 0123456789";
	private static final double NAME_SCALE = 1.1;
	private static final double DESC_SCALE = 0.8;
	private static final double SAMPLE_SCALE = 0.95;

	public FontPickerComponent(FontPickerSetting setting) {
		super(setting);
	}

	private int columns() {
		return width >= Theme.px(300) ? 3 : width >= Theme.px(170) ? 2 : 1;
	}

	private int gap() {
		return Theme.px(6);
	}

	private int cardW() {
		int inner = width - indent() * 2;
		return (inner - gap() * (columns() - 1)) / columns();
	}

	private static int cardH() {
		return Theme.px(8) * 2 + Theme.glyphHeightIn(NAME_SCALE) + Theme.px(3) + Theme.glyphHeightIn(DESC_SCALE) + Theme.px(5)
				+ Theme.glyphHeightIn(SAMPLE_SCALE);
	}

	private int headerH() {
		return labelHeight();
	}

	@Override
	public int getHeight() {
		int rows = (FontManager.FONTS.size() + columns() - 1) / columns();
		return headerH() + rows * cardH() + (rows - 1) * gap() + Theme.px(6);
	}

	private int cardX(int index) {
		return x + indent() + (index % columns()) * (cardW() + gap());
	}

	private int cardY(int index) {
		return y + headerH() + (index / columns()) * (cardH() + gap());
	}

	@Override
	public void render(DrawContext g, int mouseX, int mouseY, float delta) {
		drawLabel(g, x + width - indent(), headerH(), false);
		List<FontManager.FontEntry> fonts = FontManager.FONTS;
		String selected = setting.get();
		for (int i = 0; i < fonts.size(); i++) {
			renderCard(g, fonts.get(i), cardX(i), cardY(i), fonts.get(i).id().equals(selected), mouseX, mouseY);
		}
	}

	private void renderCard(DrawContext g, FontManager.FontEntry font, int cx, int cy, boolean selected, int mouseX, int mouseY) {
		int w = cardW();
		int h = cardH();
		int r = Theme.cornerRadius();
		float hover = Anim.hover(font, Theme.isHovered(mouseX, mouseY, cx, cy, w, h));
		int bg = selected ? Colors.mix(Theme.card(), Theme.accent(), 0.12f) : Colors.lerp(Theme.inset(), Theme.cardHover(), hover);
		Theme.roundedRect(g, cx, cy, cx + w, cy + h, r, bg);
		Theme.roundedOutline(g, cx, cy, w, h, r, selected ? Theme.accent() : Colors.lerp(Theme.border(), Theme.accent(120), hover));

		String id = font.id();
		int tx = cx + Theme.px(8);
		int ty = cy + Theme.px(8);
		int maxW = w - Theme.px(16);
		int nameMax = selected ? maxW - Theme.px(12) : maxW;
		Theme.textIn(g, id, Theme.fitIn(id, font.label(), nameMax, NAME_SCALE, true), tx, ty, Theme.text(), NAME_SCALE, true);
		if (selected) {
			int b = Theme.px(10);
			float bx = cx + w - Theme.px(6) - b;
			Shapes.disc(g, bx + b / 2f, cy + Theme.px(6) + b / 2f, b / 2f, Theme.accent());
			Icons.draw(g, Icons.CHECK, bx + b * 0.18f, cy + Theme.px(6) + b * 0.18f, b * 0.64f, Theme.text());
		}
		ty += Theme.glyphHeightIn(NAME_SCALE) + Theme.px(3);
		Theme.textIn(g, id, Theme.fitIn(id, font.description(), maxW, DESC_SCALE, false), tx, ty, Theme.textSecondary(), DESC_SCALE,
				false);
		ty += Theme.glyphHeightIn(DESC_SCALE) + Theme.px(5);
		Theme.textIn(g, id, Theme.fitIn(id, SAMPLE, maxW, SAMPLE_SCALE, false), tx, ty, Colors.lerp(Theme.textSecondary(), Theme.text(), 0.6f),
				SAMPLE_SCALE, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		List<FontManager.FontEntry> fonts = FontManager.FONTS;
		for (int i = 0; i < fonts.size(); i++) {
			if (Theme.isHovered(mouseX, mouseY, cardX(i), cardY(i), cardW(), cardH())) {
				setting.set(fonts.get(i).id());
				Appearance.get().commitFont();
				ConfigManager.get().save();
				sound(SoundAction.CLICK);
				changed();
				return true;
			}
		}
		return false;
	}
}
