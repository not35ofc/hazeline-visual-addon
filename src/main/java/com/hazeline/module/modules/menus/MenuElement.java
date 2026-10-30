package com.hazeline.module.modules.menus;

import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;

/**
 * One batch of quads for the menu background (gradient strips, star glows or star trails).
 *
 * <p>The quads are collected into a {@link Quads} buffer when the screen is drawn (so the later
 * frame step that uploads the vertices never reads the live star simulation) and are emitted with
 * the same corner order as vanilla's {@code BlitRenderState}. Coordinates are GUI (scaled) pixels,
 * {@code pose} is a copy of the draw context's current 2D transform.
 */
final class MenuElement implements SimpleGuiElementRenderState {
	/** Growable list of quads: 4 vertices each, with position, texture coordinate and colour. */
	static final class Quads {
		float[] data = new float[4 * 4 * 16]; // x, y, u, v per vertex
		int[] colors = new int[4 * 16];
		int vertices;

		void vertex(float x, float y, float u, float v, int color) {
			if (vertices == colors.length) {
				colors = java.util.Arrays.copyOf(colors, vertices * 2);
				data = java.util.Arrays.copyOf(data, vertices * 8);
			}
			int o = vertices * 4;
			data[o] = x;
			data[o + 1] = y;
			data[o + 2] = u;
			data[o + 3] = v;
			colors[vertices++] = color;
		}

		/** Axis-aligned rectangle, top edge colour {@code c0}, bottom edge colour {@code c1}. */
		void rect(float x0, float y0, float x1, float y1, int c0, int c1) {
			vertex(x0, y0, 0f, 0f, c0);
			vertex(x0, y1, 0f, 1f, c1);
			vertex(x1, y1, 1f, 1f, c1);
			vertex(x1, y0, 1f, 0f, c0);
		}

		boolean isEmpty() {
			return vertices == 0;
		}
	}

	private final RenderPipeline pipeline;
	private final TextureSetup textureSetup;
	private final Matrix3x2f pose;
	private final Quads quads;
	private final boolean textured;
	private final @Nullable ScreenRect scissorArea;
	private final @Nullable ScreenRect bounds;

	MenuElement(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, Quads quads, boolean textured,
			@Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds) {
		this.pipeline = pipeline;
		this.textureSetup = textureSetup;
		this.pose = pose;
		this.quads = quads;
		this.textured = textured;
		this.scissorArea = scissorArea;
		this.bounds = bounds;
	}

	@Override
	public void setupVertices(VertexConsumer consumer) {
		for (int i = 0; i < quads.vertices; i++) {
			int o = i * 4;
			VertexConsumer v = consumer.vertex(pose, quads.data[o], quads.data[o + 1]);
			if (textured) {
				v.texture(quads.data[o + 2], quads.data[o + 3]);
			}
			v.color(quads.colors[i]);
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
