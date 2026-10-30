package com.hazeline.module.modules.render;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.Vec3d;

import com.hazeline.appearance.FontManager;
import com.hazeline.gui.Colors;
import com.hazeline.gui.Shapes;
import com.hazeline.gui.Theme;
import com.hazeline.gui.text.TextRenderer;
import com.hazeline.module.modules.render.NameStyler.StyledChar;
import com.hazeline.render.ScreenProjector;

/**
 * Draws the Name Tag module's own tag: above your head in F5 and in the click GUI preview.
 *
 * <p>Position and size follow vanilla's label (verified in 1.21.11): the anchor is the entity's
 * {@code NAME_TAG} attachment point ({@code getAttachments().getPointNullable(NAME_TAG, 0, lerpedYaw)})
 * plus 0.5 blocks up, the text's top edge sits on the anchor and one text pixel is 0.025 blocks
 * ({@code LabelCommandRenderer$Commands#add}). Here it is drawn as a HUD layer at that projected
 * point, so the Hazeline fonts and the rounded box can be used.
 */
public final class NameTagRenderer {
	/** Text pixels per line (vanilla font height 9, incl. the gap). */
	private static final float LINE = 9f;

	private NameTagRenderer() {
	}

	// ============================================================================ F5

	static void renderF5(NameTag m, DrawContext g, RenderTickCounter tick) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null || mc.world == null || mc.options.hudHidden || mc.options.getPerspective().isFirstPerson()
				|| mc.getCameraEntity() != player || player.isInvisible()) {
			return;
		}
		if (m.hideSneaking.get() && player.isSneaky()) {
			return;
		}
		ScreenProjector projector = ScreenProjector.create(g);
		if (projector == null) {
			return;
		}
		float partial = tick.getTickProgress(true);
		Vec3d attach = player.getAttachments().getPointNullable(EntityAttachmentType.NAME_TAG, 0, player.getLerpedYaw(partial));
		if (attach == null) {
			attach = new Vec3d(0, player.getHeight(), 0);
		}
		Vec3d anchor = player.getLerpedPos(partial).add(attach).add(0, 0.5 + m.tagHeight.get(), 0);
		float[] screen = projector.project(anchor.x, anchor.y, anchor.z);
		if (screen == null || Math.abs(screen[2]) > 1.5f || Math.abs(screen[3]) > 1.5f) {
			return; // behind the camera or far off screen
		}
		float k = projector.pixelsPerBlock(screen[4]) * 0.025f * m.tagSize.get().floatValue();
		if (k <= 0.05f || k > 40f) {
			return;
		}
		List<StyledChar> chars = f5Chars(m, player);
		TextRenderer.beginFrame();
		drawTag(g, m, chars, screen[0], screen[1], k, 1f);
	}

	/** Your display name (team prefix / colour / suffix like vanilla's tag) with the name swapped. */
	private static List<StyledChar> f5Chars(NameTag m, ClientPlayerEntity player) {
		String real = player.getGameProfile().name();
		Text display = player.getDisplayName();
		List<StyledChar> flat = display == null ? new ArrayList<>() : NameStyler.flatten(display);
		List<StyledChar> replaced = NameStyler.replace(m, flat, real, false);
		if (replaced != null) {
			return replaced;
		}
		// The server shows a different name (a nick): show just the styled name.
		return NameStyler.build(m, real, Style.EMPTY, false);
	}

	// ============================================================================ click GUI preview

	/** Height the preview needs at the current GUI size (for {@code tagSize}, capped at 2). */
	public static int previewHeight(NameTag m) {
		float k = previewScale(m);
		return Math.round(Theme.px(8) + 9 + Theme.px(8) + LINE * k + Theme.px(10));
	}

	private static float previewScale(NameTag m) {
		return (float) Math.min(2.0, m.tagSize.get().doubleValue()) * 1.25f;
	}

	/** Draws the live preview (a chat line and the tag) inside the box x..x+w, y..y+h. */
	public static void drawPreview(DrawContext g, NameTag m, int x, int y, int w, int h) {
		String real = NameTag.realName();
		if (real == null || real.isEmpty()) {
			real = "Steve";
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		// Chat line exactly as chat would show it (also when Chat is switched off, so the style can be seen).
		List<StyledChar> line = NameStyler.flatten(Text.literal("<" + real + "> Hello!"));
		List<StyledChar> swapped = NameStyler.replace(m, line, real, false);
		var ordered = NameStyler.toOrdered(swapped != null ? swapped : line);
		int lineY = y + Theme.px(8);
		g.drawText(mc.textRenderer, ordered, x + Theme.px(8), lineY, 0xFFFFFFFF, true);
		// The tag above your head.
		List<StyledChar> tag = NameStyler.build(m, real, Style.EMPTY, false);
		float top = lineY + 9 + Theme.px(8);
		drawTag(g, m, tag, x + w / 2f, top, previewScale(m), 1f);
	}

	// ============================================================================ shared drawing

	/**
	 * Draws a tag whose text is horizontally centred on {@code cx} with its top edge at {@code top}.
	 *
	 * @param k GUI pixels per text pixel (1 = vanilla GUI text size)
	 */
	static void drawTag(DrawContext g, NameTag m, List<StyledChar> chars, float cx, float top, float k, float alpha) {
		if (chars.isEmpty()) {
			return;
		}
		String fontId = m.tagFont.get();
		boolean hazeline = !NameTag.MINECRAFT_FONT.equals(fontId) && FontManager.isBundled(fontId) && !TextRenderer.isVanillaFallback();
		NameTag.Background bg = m.background.get();
		if (hazeline) {
			drawHazeline(g, m, chars, cx, top, k, bg, fontId, alpha);
		} else {
			drawVanilla(g, m, chars, cx, top, k, bg, alpha);
		}
	}

	private static void drawVanilla(DrawContext g, NameTag m, List<StyledChar> chars, float cx, float top, float k, NameTag.Background bg, float alpha) {
		MinecraftClient mc = MinecraftClient.getInstance();
		var tr = mc.textRenderer;
		var ordered = NameStyler.toOrdered(chars);
		int w = tr.getWidth(ordered);
		Matrix3x2fStack pose = g.getMatrices();
		pose.pushMatrix();
		pose.translate(cx, top);
		pose.scale(k, k);
		int left = -w / 2;
		boolean shadow = m.tagShadow.get();
		if (bg != NameTag.Background.NONE) {
			box(g, bg, left - 1, -1, left + w + 1, (int) LINE - 1, 1f / k, alpha * opacity(m));
		}
		g.drawText(tr, ordered, left, 0, Colors.withAlpha(0xFFFFFF, Math.round(255 * alpha)), shadow);
		pose.popMatrix();
	}

	/** Hazeline fonts: drawn letter by letter at real GUI coordinates (the Hazeline text renderer is not pose-scaled). */
	private static void drawHazeline(DrawContext g, NameTag m, List<StyledChar> chars, float cx, float top, float k, NameTag.Background bg,
			String fontId, float alpha) {
		double h1 = TextRenderer.getHeight(new TextRenderer.TextStyle(fontId, 1.0, false, false));
		double rel = h1 <= 0 ? 1.0 : (LINE * k * 0.95) / h1;
		rel = Math.max(0.2, Math.round(rel * 20) / 20.0); // steps of 0.05: fewer glyph sizes while zooming
		boolean shadow = m.tagShadow.get();

		// Measure: x offset of every letter (advance of the text before it, in its own boldness).
		int n = chars.size();
		float[] xs = new float[n + 1];
		StringBuilder before = new StringBuilder();
		for (int i = 0; i < n; i++) {
			xs[i] = i == 0 ? 0f : TextRenderer.getAdvance(before.toString(), new TextRenderer.TextStyle(fontId, rel, isBold(chars.get(i)), false));
			before.appendCodePoint(chars.get(i).codepoint());
		}
		xs[n] = TextRenderer.getAdvance(before.toString(), new TextRenderer.TextStyle(fontId, rel, isBold(chars.get(n - 1)), false));
		float width = xs[n];
		float height = TextRenderer.getHeight(new TextRenderer.TextStyle(fontId, rel, false, false));
		float left = cx - width / 2f;

		if (bg != NameTag.Background.NONE) {
			float pad = Math.max(1f, k);
			box(g, bg, Math.round(left - pad * 1.5f), Math.round(top - pad), Math.round(left + width + pad * 1.5f), Math.round(top + height + pad * 0.5f),
					1f, alpha * opacity(m));
		}
		for (int i = 0; i < n; i++) {
			StyledChar c = chars.get(i);
			int color = argb(c.style(), alpha);
			TextRenderer.TextStyle style = new TextRenderer.TextStyle(fontId, rel, isBold(c), shadow);
			String s = new String(Character.toChars(c.codepoint()));
			float x = left + xs[i];
			TextRenderer.drawString(g, s, x, top, color, style);
			float x2 = left + xs[i + 1];
			float t = Math.max(1f, k * 0.9f);
			if (Boolean.TRUE.equals(c.style().isUnderlined())) {
				Shapes.line(g, x, top + height - t * 0.5f, x2, top + height - t * 0.5f, t, color);
			}
			if (Boolean.TRUE.equals(c.style().isStrikethrough())) {
				Shapes.line(g, x, top + height * 0.52f, x2, top + height * 0.52f, t, color);
			}
		}
	}

	/** Tag background opacity setting as 0..1. */
	private static float opacity(NameTag m) {
		return (float) Math.max(0, Math.min(1, m.tagOpacity.get() / 100.0));
	}

	private static boolean isBold(StyledChar c) {
		return c.style().isBold();
	}

	private static int argb(Style style, float alpha) {
		TextColor color = style.getColor();
		int rgb = color == null ? 0xFFFFFF : color.getRgb();
		return Colors.withAlpha(rgb, Math.round(255 * alpha));
	}

	/**
	 * Tag background in the current pose. {@code unit} = size of one GUI pixel in pose units (so the
	 * Hazeline outline stays 1 pixel thick when the pose is scaled).
	 */
	private static void box(DrawContext g, NameTag.Background bg, int x1, int y1, int x2, int y2, float unit, float alpha) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (bg == NameTag.Background.VANILLA) {
			g.fill(x1, y1, x2, y2, Colors.withAlpha(0x000000, Math.round(255 * alpha)));
			return;
		}
		if (bg != NameTag.Background.HAZELINE) {
			return;
		}
		Matrix3x2fStack pose = g.getMatrices();
		pose.pushMatrix();
		// Draw the rounded box in GUI pixels so corners and outline stay crisp at any tag size.
		float scale = 1f / unit;
		pose.scale(unit, unit);
		int gx1 = Math.round(x1 * scale), gy1 = Math.round(y1 * scale), gx2 = Math.round(x2 * scale), gy2 = Math.round(y2 * scale);
		int r = Math.max(2, Math.min((gy2 - gy1) / 2, Math.round(4 * Theme.scale())));
		int accent = Theme.accent();
		// Soft glow: a few wider rounded boxes in the accent colour, fading out.
		int[] glowAlpha = {0x0C, 0x16, 0x24};
		for (int i = 0; i < glowAlpha.length; i++) {
			int grow = glowAlpha.length - i;
			Shapes.roundedRect(g, gx1 - grow, gy1 - grow, gx2 + grow, gy2 + grow, r + grow,
					Colors.withAlpha(accent, Math.round(glowAlpha[i] * alpha)), Colors.withAlpha(accent, Math.round(glowAlpha[i] * alpha)), true, true);
		}
		int panel = Colors.withAlpha(Theme.panelBackground(), Math.round(0xFF * alpha));
		Shapes.roundedRect(g, gx1, gy1, gx2, gy2, r, panel, panel, true, true);
		Shapes.roundedOutline(g, gx1, gy1, gx2, gy2, r, Colors.withAlpha(accent, Math.round(0xB0 * alpha)));
		pose.popMatrix();
	}
}
