package com.hazeline.gui.component;

import com.hazeline.appearance.SoundAction;
import com.hazeline.appearance.SoundManager;
import net.minecraft.client.gui.DrawContext;

import com.hazeline.gui.Anim;
import com.hazeline.gui.Colors;
import com.hazeline.gui.Theme;
import com.hazeline.module.ModuleManager;
import com.hazeline.setting.ActionSetting;
import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.ChoiceSetting;
import com.hazeline.setting.ColorSetting;
import com.hazeline.setting.EntityListSetting;
import com.hazeline.setting.EnumSetting;
import com.hazeline.setting.InfoSetting;
import com.hazeline.setting.InputBindSetting;
import com.hazeline.setting.KeybindSetting;
import com.hazeline.setting.NamePreviewSetting;
import com.hazeline.setting.NumberSetting;
import com.hazeline.setting.FontPickerSetting;
import com.hazeline.setting.Setting;
import com.hazeline.setting.TextSetting;
import com.hazeline.setting.WeatherPreviewSetting;

/**
 * Base for widgets bound to a {@link Setting}. Hidden automatically when the setting's
 * visibility condition is false. {@link #create(Setting)} maps setting types to widgets -
 * add a branch there when introducing a new setting type (subclasses before their parents).
 */
public abstract class SettingComponent<S extends Setting<?>> extends Component {
	protected final S setting;

	protected SettingComponent(S setting) {
		this.setting = setting;
	}

	@Override
	public boolean isVisible() {
		return setting.isVisible();
	}

	/** Left/right inner padding of setting rows. */
	protected static int indent() {
		return Theme.px(8);
	}

	/** Height of the label part of a row: name, plus the description underneath when there is one. */
	protected int labelHeight() {
		String desc = labelDescription();
		return desc == null || desc.isEmpty() ? Theme.px(18) : Theme.px(26);
	}

	/** Name shown on the row (the setting's name unless a subclass shows something else). */
	protected String labelName() {
		return setting.getName();
	}

	/** Small line under the name (the setting's description by default). */
	protected String labelDescription() {
		return setting.getDescription();
	}

	/** Right edge for controls. */
	protected int controlRight() {
		return x + width - indent();
	}

	/**
	 * Draws the row label: the setting name and its description in small text underneath, both cut
	 * to end before {@code right}. A soft hover highlight covers {@code rowHeight}.
	 */
	protected void drawLabel(DrawContext g, int right, int rowHeight, boolean hovered) {
		float hover = Anim.hover(this, hovered);
		if (hover > 0.01f) {
			int r = Math.min(Theme.cornerRadius(), rowHeight / 3);
			Theme.roundedRect(g, x + 2, y + 1, x + width - 2, y + rowHeight - 1, r,
					Colors.withAlpha(Theme.hover(), Math.round(Colors.alpha(Theme.hover()) * hover)));
		}
		int maxW = Math.max(Theme.px(10), right - x - indent() - Theme.px(6));
		String desc = labelDescription();
		int lh = labelHeight();
		if (desc == null || desc.isEmpty()) {
			Theme.boxTextOf(g, Theme.fitOf(labelName(), maxW, 1.0, true), x + indent(), y, lh, Theme.text(), 1.0, true);
			return;
		}
		float nameH = Theme.heightOf(1.0);
		float descH = Theme.heightOf(Theme.SMALL);
		float gap = Theme.px(1.5f);
		float ty = y + (lh - nameH - gap - descH) / 2f;
		Theme.textOf(g, Theme.fitOf(labelName(), maxW, 1.0, true), x + indent(), ty, Theme.text(), 1.0, true);
		Theme.textOf(g, Theme.fitOf(desc, maxW, Theme.SMALL, false), x + indent(), ty + nameH + gap, Theme.textSecondary(),
				Theme.SMALL, false);
	}

	/** Call after any user edit so the config autosaves. */
	protected void changed() {
		ModuleManager.get().markDirty();
	}

	protected static void sound(SoundAction action) {
		SoundManager.get().play(action);
	}

	public static SettingComponent<?> create(Setting<?> setting) {
		if (setting instanceof BooleanSetting s) return new BooleanComponent(s);
		if (setting instanceof NumberSetting s) return new SliderComponent(s);
		if (setting instanceof ColorSetting s) return new ColorComponent(s);
		if (setting instanceof EnumSetting<?> s) return new EnumComponent(s);
		if (setting instanceof FontPickerSetting s) return new FontPickerComponent(s);
		if (setting instanceof ChoiceSetting s) return new ChoiceComponent(s);
		if (setting instanceof EntityListSetting s) return new EntityListComponent(s);
		if (setting instanceof KeybindSetting s) return new KeybindComponent(s);
		if (setting instanceof InputBindSetting s) return new InputBindComponent(s);
		if (setting instanceof TextSetting s) return new TextFieldComponent(s);
		if (setting instanceof InfoSetting s) return new InfoComponent(s);
		if (setting instanceof NamePreviewSetting s) return new NamePreviewComponent(s);
		if (setting instanceof WeatherPreviewSetting s) return new WeatherPreviewComponent(s);
		if (setting instanceof ActionSetting s) return new ActionComponent(s);
		throw new IllegalArgumentException("No GUI component for " + setting.getClass());
	}
}
