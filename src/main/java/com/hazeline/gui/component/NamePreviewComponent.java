package com.hazeline.gui.component;

import net.minecraft.client.gui.DrawContext;

import com.hazeline.gui.Shapes;
import com.hazeline.gui.Theme;
import com.hazeline.module.modules.render.NameTagRenderer;
import com.hazeline.setting.NamePreviewSetting;

/** Live preview of the Name Tag module: a chat line and the F5 tag, redrawn every frame (animations move). */
public class NamePreviewComponent extends SettingComponent<NamePreviewSetting> {
	public NamePreviewComponent(NamePreviewSetting setting) {
		super(setting);
	}

	private int boxHeight() {
		return NameTagRenderer.previewHeight(setting.getModule());
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
			NameTagRenderer.drawPreview(g, setting.getModule(), bx, by, bw, bh);
		} finally {
			g.disableScissor();
		}
	}
}
