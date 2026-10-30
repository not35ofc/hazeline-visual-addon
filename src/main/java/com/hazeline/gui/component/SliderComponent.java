package com.hazeline.gui.component;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import com.hazeline.appearance.SoundAction;
import com.hazeline.gui.Anim;
import com.hazeline.gui.Colors;
import com.hazeline.gui.Shapes;
import com.hazeline.gui.Theme;
import com.hazeline.gui.Widgets;
import com.hazeline.setting.NumberSetting;

/**
 * Number setting: label on the left, a slider and a value box on the right. Drag the slider (or
 * click on it) to change the value; right click the row to reset it.
 *
 * <p>Settings marked {@link NumberSetting#applyOnRelease()} (font size, window size) change the
 * menu layout, including this slider's own position. For those the dragged value is only shown
 * ({@link #pending}) and applied once on release, so the slider does not move under the mouse.
 */
public class SliderComponent extends SettingComponent<NumberSetting> {
	private boolean dragging;
	/** Value shown while an apply-on-release setting is dragged; NaN otherwise. */
	private double pending = Double.NaN;

	public SliderComponent(NumberSetting setting) {
		super(setting);
	}

	@Override
	public int getHeight() {
		return labelHeight();
	}

	private int boxW() {
		int widest = Math.max(Theme.widthOf(setting.format(setting.getMin()), Theme.SMALL, false),
				Theme.widthOf(setting.format(setting.getMax()), Theme.SMALL, false));
		return Math.max(Theme.px(44), widest + Theme.px(10));
	}

	private int trackW() {
		return MathHelper.clamp(width * 3 / 10, Theme.px(46), Theme.px(120));
	}

	private int trackX2() {
		return controlRight() - boxW() - Theme.px(8);
	}

	private int trackX1() {
		return trackX2() - trackW();
	}

	@Override
	public void render(DrawContext g, int mouseX, int mouseY, float delta) {
		int h = getHeight();
		boolean hover = hovered(mouseX, mouseY, h);
		int x1 = trackX1();
		int x2 = trackX2();
		drawLabel(g, x1 - Theme.px(4), h, hover);

		boolean deferred = !Double.isNaN(pending);
		double shown = deferred ? pending : setting.get();
		float fraction = Anim.approach(this, 2, (float) setting.fractionOf(shown), dragging ? 40f : 18f);
		int th = Math.max(2, Theme.px(3));
		int ty = y + (h - th) / 2;
		Theme.roundedRect(g, x1, ty, x2, ty + th, th / 2, Theme.track());
		float fx = x1 + (x2 - x1) * fraction;
		if (fx > x1 + 0.5f) {
			Theme.roundedRect(g, x1, ty, Math.max(x1 + th, Math.round(fx)), ty + th, th / 2, Theme.accent());
		}
		boolean overTrack = trackHovered(mouseX, mouseY);
		float knobR = Theme.px(3.5f) * (dragging || overTrack ? 1.15f : 1f);
		Shapes.glow(g, fx, y + h / 2f, knobR * 3f, Colors.withAlpha(Theme.accent(), Math.round(Colors.alpha(Theme.accent()) * 0.35f)));
		Shapes.disc(g, fx, y + h / 2f, knobR, Theme.text());

		int bw = boxW();
		int bh = Theme.px(13);
		Widgets.valueBox(g, controlRight() - bw, y + (h - bh) / 2, bw, bh, setting.format(shown), dragging, hover);
	}

	private boolean trackHovered(double mouseX, double mouseY) {
		int slack = Theme.px(5);
		return mouseX >= trackX1() - slack && mouseX <= trackX2() + slack && mouseY >= y && mouseY < y + getHeight();
	}

	private void update(double mouseX) {
		double fraction = (mouseX - trackX1()) / (double) Math.max(1, trackX2() - trackX1());
		if (setting.isApplyOnRelease()) {
			double before = Double.isNaN(pending) ? setting.get() : pending;
			pending = setting.valueAt(fraction);
			if (pending != before) {
				sound(SoundAction.SLIDER);
			}
			return;
		}
		double before = setting.get();
		setting.setFraction(fraction);
		if (setting.get() != before) {
			sound(SoundAction.SLIDER);
			changed();
		}
	}

	/** Applies the value of an apply-on-release drag (once, on release). */
	private void applyPending() {
		if (Double.isNaN(pending)) {
			return;
		}
		double value = pending;
		pending = Double.NaN;
		if (value != setting.get()) {
			setting.set(value);
			changed();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!hovered(mouseX, mouseY, getHeight())) {
			return false;
		}
		if (button == 0 && trackHovered(mouseX, mouseY)) {
			dragging = true;
			pending = Double.NaN;
			update(mouseX);
		} else if (button == 1) {
			dragging = false;
			pending = Double.NaN;
			setting.reset();
			sound(SoundAction.CLICK);
			changed();
		}
		return true;
	}

	@Override
	public void mouseDragged(double mouseX, double mouseY) {
		if (dragging) {
			update(mouseX);
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		if (dragging) {
			dragging = false;
			applyPending();
		}
	}
}
