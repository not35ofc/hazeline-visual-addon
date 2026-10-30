package com.hazeline.gui.text;

import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.render.VertexConsumer;

import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;

/**
 * One GUI element holding all glyph quads of one string that live on the same atlas page.
 *
 * <p>Coordinates are physical (screen) pixels: the cached, origin-relative quads of a
 * {@link TextQuads.Run}, times {@code scale} (1 except while a font size change is debounced), plus
 * the string origin. {@link #pose} is {@code scale(1/guiScale)}, which undoes the GUI projection's
 * scale (1.21.11 projects GUI space with {@code framebuffer / guiScale}), so atlas texels map 1:1 to
 * screen pixels. Elements with the same pipeline, page and scissor are merged into a single draw
 * call by vanilla's {@code GuiRenderer}.
 */
final class TextRenderState implements SimpleGuiElementRenderState {
	private final RenderPipeline pipeline;
	private final TextureSetup textureSetup;
	private final Matrix3x2f pose;
	private final TextQuads.Run run;
	private final float originX;
	private final float originY;
	private final float scale;
	private final int color;
	private final @Nullable ScreenRect scissorArea;
	private final @Nullable ScreenRect bounds;

	TextRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, TextQuads.Run run, float originX, float originY,
			float scale, int color, @Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds) {
		this.pipeline = pipeline;
		this.textureSetup = textureSetup;
		this.pose = pose;
		this.run = run;
		this.originX = originX;
		this.originY = originY;
		this.scale = scale;
		this.color = color;
		this.scissorArea = scissorArea;
		this.bounds = bounds;
	}

	@Override
	public void setupVertices(VertexConsumer consumer) {
		float[] q = run.quads();
		for (int i = 0; i < run.count(); i++) {
			int o = i * TextQuads.STRIDE;
			float x0 = originX + q[o] * scale, y0 = originY + q[o + 1] * scale;
			float x1 = originX + q[o + 2] * scale, y1 = originY + q[o + 3] * scale;
			float u0 = q[o + 4], v0 = q[o + 5], u1 = q[o + 6], v1 = q[o + 7];
			// Same corner order as vanilla's BlitRenderState.
			consumer.vertex(pose, x0, y0).texture(u0, v0).color(color);
			consumer.vertex(pose, x0, y1).texture(u0, v1).color(color);
			consumer.vertex(pose, x1, y1).texture(u1, v1).color(color);
			consumer.vertex(pose, x1, y0).texture(u1, v0).color(color);
		}
	}

	@Override
	public RenderPipeline pipeline() {
		return pipeline;
	}

	@Override
	public TextureSetup textureSetup() {
		return textureSetup;
	}

	@Override
	public @Nullable ScreenRect scissorArea() {
		return scissorArea;
	}

	@Override
	public @Nullable ScreenRect bounds() {
		return bounds;
	}
}
