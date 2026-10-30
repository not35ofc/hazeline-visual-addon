package com.hazeline.gui.component;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

import com.hazeline.appearance.SoundAction;
import com.hazeline.gui.Icons;
import com.hazeline.gui.Shapes;
import com.hazeline.gui.Colors;
import com.hazeline.gui.Theme;
import com.hazeline.setting.ColorSetting;

/**
 * Colour picker for a {@link ColorSetting}, used in module panels and in the Appearance editor.
 *
 * <p>Collapsed: name + swatch. Left click expands to:
 * <ul>
 *   <li>a saturation/value square for the current hue (drawn as 1-px vertical gradients, which
 *       is exact because RGB is linear in V for a fixed hue and saturation),</li>
 *   <li>a hue slider and an alpha slider,</li>
 *   <li>a hex field accepting #AARRGGBB or #RRGGBB (Enter / click elsewhere applies, Esc cancels, Ctrl+V pastes),</li>
 *   <li>a quick palette of {@link #PALETTE} swatches (click = take that colour, keeping the current alpha).</li>
 * </ul>
 * Right click on the header resets the colour. Hue is remembered separately so it survives
 * dragging to black or grey.
 */
public class ColorComponent extends SettingComponent<ColorSetting> {
	private enum Drag { NONE, SV, HUE, ALPHA }

	/** Quick-pick palette shown under the hex field (2 rows of 8). */
	static final int[] PALETTE = {
			0xFFFFFFFF, 0xFFBFC5CE, 0xFF6B7280, 0xFF111111, 0xFFEF4444, 0xFFF97316, 0xFFFACC15, 0xFF84CC16,
			0xFF22C55E, 0xFF14B8A6, 0xFF22D3EE, 0xFF38BDF8, 0xFF3B82F6, 0xFF6366F1, 0xFFA855F7, 0xFFEC4899};
	private static final int PALETTE_COLUMNS = 8;

	private boolean expanded;
	private Drag drag = Drag.NONE;
	private float hue;
	private float saturation;
	private float value;
	private int syncedColor;
	private final TextField hexField;

	public ColorComponent(ColorSetting setting) {
		super(setting);
		syncFromSetting();
		this.hexField = new TextField(9,
				cp -> (cp >= '0' && cp <= '9') || (cp >= 'a' && cp <= 'f') || (cp >= 'A' && cp <= 'F') || cp == '#',
				text -> {
					if (setting.setFromHex(text)) {
						syncFromSetting();
						changed();
					}
				});
	}

	private void syncFromSetting() {
		int c = setting.get();
		float[] hsv = Colors.toHsv(c);
		if (hsv[1] > 0 && hsv[2] > 0) {
			hue = hsv[0];
		}
		saturation = hsv[1];
		value = hsv[2];
		syncedColor = c;
	}

	// ------------------------------------------------------------------ layout

	private int areaX() {
		return x + indent();
	}

	/** Width of the picker block. */
	private int areaW() {
		return Math.max(Theme.px(60), Math.min(width - indent() * 2, Theme.px(170)));
	}

	private int hueW() {
		return Theme.px(8);
	}

	private int gap() {
		return Theme.px(5);
	}

	private int squareW() {
		return areaW() - hueW() - gap();
	}

	private int squareY() {
		return y + labelHeight() + Theme.px(2);
	}

	private int squareH() {
		return Theme.px(70);
	}

	private int hueX() {
		return areaX() + squareW() + gap();
	}

	private int barH() {
		return Theme.px(7);
	}

	private int alphaY() {
		return squareY() + squareH() + gap();
	}

	private int hexY() {
		return alphaY() + barH() + gap();
	}

	private int hexH() {
		return Theme.px(14);
	}

	private int paletteY() {
		return hexY() + hexH() + gap();
	}

	private int paletteGap() {
		return Math.max(1, Theme.px(3));
	}

	/** Width of one palette swatch (the row fills the picker width exactly). */
	private int paletteCell() {
		return Math.max(2, (areaW() - paletteGap() * (PALETTE_COLUMNS - 1)) / PALETTE_COLUMNS);
	}

	private int paletteRowH() {
		return Theme.px(10);
	}

	private int paletteH() {
		int rows = (PALETTE.length + PALETTE_COLUMNS - 1) / PALETTE_COLUMNS;
		return rows * paletteRowH() + (rows - 1) * paletteGap();
	}

	/** Index of the palette swatch under the mouse, or -1. */
	private int paletteIndexAt(double mouseX, double mouseY) {
		int cell = paletteCell();
		for (int i = 0; i < PALETTE.length; i++) {
			int px = areaX() + (i % PALETTE_COLUMNS) * (cell + paletteGap());
			int py = paletteY() + (i / PALETTE_COLUMNS) * (paletteRowH() + paletteGap());
			if (Theme.isHovered(mouseX, mouseY, px, py, cell, paletteRowH())) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public int getHeight() {
		return expanded ? paletteY() + paletteH() + Theme.px(6) - y : labelHeight();
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void render(DrawContext g, int mouseX, int mouseY, float delta) {
		if (setting.get() != syncedColor && drag == Drag.NONE) {
			syncFromSetting(); // changed elsewhere (preset, import, reset)
		}
		int lh = labelHeight();
		boolean hover = hovered(mouseX, mouseY, lh);
		int icon = Theme.px(7);
		int sw = Theme.px(22);
		int sh = Theme.px(12);
		int ix = controlRight() - icon;
		int sx = ix - Theme.px(5) - sw;
		int sy = y + (lh - sh) / 2;
		drawLabel(g, sx, lh, hover);
		swatch(g, sx, sy, sw, sh);
		Icons.draw(g, expanded ? Icons.CHEVRON_UP : Icons.CHEVRON_DOWN, ix, y + (lh - icon) / 2f, icon, Theme.textSecondary());

		if (!expanded) {
			return;
		}
		drawSvSquare(g);
		drawHueBar(g);
		drawAlphaBar(g);
		int hs = hexH();
		swatch(g, areaX(), hexY(), hs * 2, hs);
		hexField.render(g, areaX() + hs * 2 + gap(), hexY(), areaW() - hs * 2 - gap(), hs, setting.toHex());
		drawPalette(g, mouseX, mouseY);
	}

	private void drawPalette(DrawContext g, int mouseX, int mouseY) {
		int cell = paletteCell();
		int rh = paletteRowH();
		int hoverIndex = paletteIndexAt(mouseX, mouseY);
		int current = setting.get() & 0x00FFFFFF;
		for (int i = 0; i < PALETTE.length; i++) {
			int px = areaX() + (i % PALETTE_COLUMNS) * (cell + paletteGap());
			int py = paletteY() + (i / PALETTE_COLUMNS) * (rh + paletteGap());
			int r = Math.min(Theme.cornerRadius(), rh / 3);
			Theme.roundedRect(g, px, py, px + cell, py + rh, r, Theme.fade(PALETTE[i]));
			boolean selected = (PALETTE[i] & 0x00FFFFFF) == current;
			Theme.roundedOutline(g, px, py, cell, rh, r, selected || i == hoverIndex ? Theme.text() : Theme.border());
		}
	}

	private void swatch(DrawContext g, int sx, int sy, int sw, int sh) {
		Theme.checkerboard(g, sx + 1, sy + 1, sx + sw - 1, sy + sh - 1);
		int r = Math.min(Theme.cornerRadius(), sh / 3);
		Theme.roundedRect(g, sx, sy, sx + sw, sy + sh, r, Theme.fade(setting.get()));
		Theme.roundedOutline(g, sx, sy, sw, sh, r, Theme.border());
	}

	private void drawSvSquare(DrawContext g) {
		int x0 = areaX();
		int y0 = squareY();
		int w = squareW();
		int h = squareH();
		for (int i = 0; i < w; i++) {
			float s = w <= 1 ? 1 : i / (float) (w - 1);
			g.fillGradient(x0 + i, y0, x0 + i + 1, y0 + h, Theme.fade(Colors.fromHsv(hue, s, 1f, 255)), Theme.fade(0xFF000000));
		}
		Theme.outline(g, x0, y0, w, h, Theme.border());
		float mx = x0 + saturation * (w - 1);
		float my = y0 + (1 - value) * (h - 1);
		Shapes.ring(g, mx, my, Theme.px(3), Theme.fade(value > 0.5f ? 0xFF000000 : 0xFFFFFFFF));
	}

	private void drawHueBar(DrawContext g) {
		int x0 = hueX();
		int y0 = squareY();
		int h = squareH();
		for (int i = 0; i < h; i++) {
			g.fill(x0, y0 + i, x0 + hueW(), y0 + i + 1, Theme.fade(Colors.fromHsv(i / (float) h, 1f, 1f, 255)));
		}
		Theme.outline(g, x0, y0, hueW(), h, Theme.border());
		int my = y0 + Math.round(hue * (h - 1));
		int k = Math.max(1, Theme.px(1.5f));
		Theme.roundedRect(g, x0 - k, my - k, x0 + hueW() + k, my + k + 1, k, Theme.text());
	}

	private void drawAlphaBar(DrawContext g) {
		int x0 = areaX();
		int y0 = alphaY();
		int w = areaW();
		Theme.checkerboard(g, x0, y0, x0 + w, y0 + barH());
		int rgb = setting.get() & 0x00FFFFFF;
		for (int i = 0; i < w; i++) {
			g.fill(x0 + i, y0, x0 + i + 1, y0 + barH(), Theme.fade(Colors.withAlpha(rgb, Math.round(255f * i / Math.max(1, w - 1)))));
		}
		Theme.outline(g, x0, y0, w, barH(), Theme.border());
		int mx = x0 + Math.round(setting.alpha() / 255f * (w - 1));
		int k = Math.max(1, Theme.px(1.5f));
		Theme.roundedRect(g, mx - k, y0 - k, mx + k + 1, y0 + barH() + k, k, Theme.text());
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int row = labelHeight();
		if (hovered(mouseX, mouseY, row)) {
			if (button == 0) {
				expanded = !expanded;
				if (!expanded) {
					hexField.submit();
				}
			} else if (button == 1) {
				setting.reset();
				syncFromSetting();
				changed();
			}
			sound(SoundAction.CLICK);
			return true;
		}
		if (!expanded || !hovered(mouseX, mouseY, getHeight())) {
			return false;
		}
		if (button != 0) {
			return true;
		}
		int x0 = areaX();
		int w = areaW();
		int slack = Theme.px(2);
		if (Theme.isHovered(mouseX, mouseY, x0, hexY(), w, hexH())) {
			if (!hexField.isFocused()) {
				hexField.focus(setting.toHex());
			}
			return true;
		}
		int swatchIndex = paletteIndexAt(mouseX, mouseY);
		if (swatchIndex >= 0) {
			setting.set(Colors.withAlpha(PALETTE[swatchIndex], setting.alpha()));
			syncFromSetting();
			changed();
			sound(SoundAction.CLICK);
			return true;
		}
		if (Theme.isHovered(mouseX, mouseY, x0, squareY(), squareW(), squareH())) {
			drag = Drag.SV;
		} else if (Theme.isHovered(mouseX, mouseY, hueX() - slack, squareY(), hueW() + slack * 2, squareH())) {
			drag = Drag.HUE;
		} else if (Theme.isHovered(mouseX, mouseY, x0, alphaY() - slack, w, barH() + slack * 2)) {
			drag = Drag.ALPHA;
		}
		update(mouseX, mouseY);
		return true;
	}

	private void update(double mouseX, double mouseY) {
		float fx = clamp01((float) ((mouseX - areaX()) / Math.max(1, areaW() - 1)));
		switch (drag) {
			case SV -> {
				saturation = clamp01((float) ((mouseX - areaX()) / Math.max(1, squareW() - 1)));
				value = 1 - clamp01((float) ((mouseY - squareY()) / Math.max(1, squareH() - 1)));
				setting.set(Colors.fromHsv(hue, saturation, value, setting.alpha()));
			}
			case HUE -> {
				hue = Math.min(clamp01((float) ((mouseY - squareY()) / Math.max(1, squareH() - 1))), 0.9999f);
				setting.set(Colors.fromHsv(hue, saturation, value, setting.alpha()));
			}
			case ALPHA -> setting.set(Colors.withAlpha(setting.get(), Math.round(fx * 255)));
			default -> {
				return;
			}
		}
		syncedColor = setting.get();
		changed();
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	@Override
	public void mouseDragged(double mouseX, double mouseY) {
		if (drag != Drag.NONE) {
			update(mouseX, mouseY);
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		drag = Drag.NONE;
	}

	@Override
	public void onGlobalClick(double mouseX, double mouseY) {
		if (hexField.isFocused() && !(expanded && Theme.isHovered(mouseX, mouseY, areaX(), hexY(), areaW(), hexH()))) {
			hexField.submit();
		}
	}

	@Override
	public boolean keyPressed(KeyInput event) {
		return hexField.keyPressed(event);
	}

	@Override
	public boolean charTyped(int codepoint) {
		return hexField.charTyped(codepoint);
	}

	@Override
	public boolean isCapturingKeyboard() {
		return hexField.isFocused();
	}
}
