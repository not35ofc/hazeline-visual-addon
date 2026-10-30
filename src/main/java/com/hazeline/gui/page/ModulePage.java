package com.hazeline.gui.page;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

import com.hazeline.appearance.SoundAction;
import com.hazeline.appearance.SoundManager;
import com.hazeline.gui.Anim;
import com.hazeline.gui.Colors;
import com.hazeline.gui.Icons;
import com.hazeline.gui.Theme;
import com.hazeline.gui.Widgets;
import com.hazeline.module.Module;
import com.hazeline.module.ModuleManager;

/**
 * Settings of one module: a "Back" button, a header card (name, description, on/off switch,
 * reset) and every setting (including the keybind) as rows in a card.
 */
public class ModulePage extends Page {
	private static final String BACK = "Back";
	private static final String RESET = "Reset to defaults";

	private final Module module;
	private final Page parent;
	private final Runnable back;
	private final SettingsCard settings;
	private long resetConfirmUntil;

	public ModulePage(Module module, Page parent, Runnable back) {
		this.module = module;
		this.parent = parent;
		this.back = back;
		this.settings = new SettingsCard(null, null, module.getSettings());
	}

	public Page parent() {
		return parent;
	}

	@Override
	public String titleAccent() {
		return parent.titleAccent();
	}

	@Override
	public String title() {
		return parent.title();
	}

	@Override
	public String subtitle() {
		return parent.subtitle();
	}

	@Override
	public void tick() {
		module.onSettingsVisible(); // e.g. Hand View's live preview
	}

	// ------------------------------------------------------------------ layout

	private static int toolbarH() {
		return Widgets.buttonHeight();
	}

	private static int headerH() {
		return Theme.px(36);
	}

	private static int gap() {
		return Theme.px(6);
	}

	@Override
	public int contentHeight(int width) {
		return toolbarH() + gap() + headerH() + gap() + settings.height(width);
	}

	private int[] backRect;
	private int[] resetRect;
	private int[] switchRect;

	@Override
	public void render(DrawContext g, int x, int top, int width, int mouseX, int mouseY, float delta) {
		// Toolbar: Back (left), Reset to defaults (right).
		int bh = toolbarH();
		int icon = Theme.px(7);
		int bw = Widgets.buttonWidth(BACK) + icon;
		backRect = new int[] {x, top, bw, bh};
		boolean overBack = Theme.isHovered(mouseX, mouseY, x, top, bw, bh);
		Widgets.button(g, x, top, bw, bh, "", false, Anim.approach(BACK, 3, overBack ? 1f : 0f, 14f));
		Icons.draw(g, Icons.CHEVRON_LEFT, x + Theme.px(5), top + (bh - icon) / 2f, icon, Theme.text());
		Theme.boxTextOf(g, BACK, x + Theme.px(5) + icon + Theme.px(2), top, bh, Theme.text(), Theme.SMALL, false);

		boolean confirming = System.currentTimeMillis() < resetConfirmUntil;
		String resetLabel = confirming ? "Click again to reset" : RESET;
		int rw = Widgets.buttonWidth(resetLabel);
		resetRect = new int[] {x + width - rw, top, rw, bh};
		boolean overReset = Theme.isHovered(mouseX, mouseY, resetRect[0], top, rw, bh);
		Widgets.button(g, resetRect[0], top, rw, bh, resetLabel, confirming, Anim.approach(RESET, 3, overReset ? 1f : 0f, 14f));

		// Header card.
		int hy = top + bh + gap();
		int hh = headerH();
		int r = Theme.cornerRadius();
		boolean on = module.isToggleable() && module.isEnabled();
		float onP = Anim.toggle(module, on);
		Theme.roundedRect(g, x, hy, x + width, hy + hh, r, Colors.mix(Theme.card(), Theme.accent(), 0.08f * onP));
		Theme.roundedOutline(g, x, hy, width, hh, r, Colors.lerp(Theme.border(), Theme.accent(150), onP * 0.6f));
		int sw = Widgets.switchW();
		int sh = Widgets.switchH();
		int sx = x + width - Theme.px(10) - sw;
		int sy = hy + (hh - sh) / 2;
		switchRect = module.isToggleable() ? new int[] {sx, sy, sw, sh} : null;
		int textMax = (module.isToggleable() ? sx : x + width) - x - Theme.px(20);
		float nameH = Theme.heightOf(1.2);
		float descH = Theme.heightOf(Theme.SMALL);
		float ty = hy + (hh - nameH - Theme.px(2) - descH) / 2f;
		Theme.textOf(g, Theme.fitOf(module.getName(), textMax, 1.2, true), x + Theme.px(10), ty, Theme.text(), 1.2, true);
		Theme.textOf(g, Theme.fitOf(module.getDescription(), textMax, Theme.SMALL, false), x + Theme.px(10), ty + nameH + Theme.px(2),
				Theme.textSecondary(), Theme.SMALL, false);
		if (switchRect != null) {
			boolean overSwitch = Theme.isHovered(mouseX, mouseY, sx - 3, sy - 3, sw + 6, sh + 6);
			Widgets.toggleSwitch(g, sx, sy, onP, overSwitch);
		}

		settings.render(g, x, hy + hh + gap(), width, mouseX, mouseY, delta);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && backRect != null && Theme.isHovered(mouseX, mouseY, backRect[0], backRect[1], backRect[2], backRect[3])) {
			SoundManager.get().play(SoundAction.CLICK);
			back.run();
			return true;
		}
		if (button == 0 && resetRect != null && Theme.isHovered(mouseX, mouseY, resetRect[0], resetRect[1], resetRect[2], resetRect[3])) {
			SoundManager.get().play(SoundAction.CLICK);
			if (System.currentTimeMillis() < resetConfirmUntil) {
				module.resetToDefaults();
				ModuleManager.get().markDirty();
				resetConfirmUntil = 0;
			} else {
				resetConfirmUntil = System.currentTimeMillis() + 3000;
			}
			return true;
		}
		if (button == 0 && switchRect != null
				&& Theme.isHovered(mouseX, mouseY, switchRect[0] - 3, switchRect[1] - 3, switchRect[2] + 6, switchRect[3] + 6)) {
			module.toggle();
			SoundManager.get().play(module.isEnabled() ? SoundAction.ENABLE : SoundAction.DISABLE);
			return true;
		}
		return settings.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void onGlobalClick(double mouseX, double mouseY) {
		settings.onGlobalClick(mouseX, mouseY);
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		settings.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void mouseDragged(double mouseX, double mouseY) {
		settings.mouseDragged(mouseX, mouseY);
	}

	@Override
	public boolean keyPressed(KeyInput event) {
		return settings.keyPressed(event);
	}

	@Override
	public boolean charTyped(int codepoint) {
		return settings.charTyped(codepoint);
	}

	@Override
	public boolean isCapturingKeyboard() {
		return settings.isCapturingKeyboard();
	}
}
