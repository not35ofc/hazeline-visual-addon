package com.hazeline.gui.component;

import net.minecraft.client.gui.DrawContext;

import com.hazeline.gui.Shapes;
import com.hazeline.gui.Theme;
import com.hazeline.module.modules.weather.WeatherPreview;
import com.hazeline.setting.WeatherPreviewSetting;

/** Live preview of the Weather settings: a small animated sky redrawn every frame. */
public class WeatherPreviewComponent extends SettingComponent<WeatherPreviewSetting> {
	public WeatherPreviewComponent(WeatherPreviewSetting setting) {
		super(setting);
	}

	private int boxHeight() {
		return Theme.px(WeatherPreview.HEIGHT);
	}

	@Override
	public int getHeight() {
		return Theme.px(16) + boxHeight() + Theme.px(6);
	}

	@Override
	public void render(DrawContext g, int mouseX, int mouseY, float delta) {
		Theme.boxTextOf(g, Theme.fitOf(setting.getName(), width - indent() * 2, 1.0, true), x + indent(), y + Theme.px(2), Theme.px(14),
				Theme.text(), 1.0, true);
		int bx = x + indent();
		int by = y + Theme.px(16);
		int bw = width - indent() * 2;
		int bh = boxHeight();
		Shapes.roundedRect(g, bx, by, bx + bw, by + bh, Theme.cornerRadius(), Theme.inset(), Theme.inset(), true, true);
		g.enableScissor(bx, by, bx + bw, by + bh);
		try {
			WeatherPreview.draw(g, bx, by, bw, bh);
		} finally {
			g.disableScissor();
		}
	}
}
