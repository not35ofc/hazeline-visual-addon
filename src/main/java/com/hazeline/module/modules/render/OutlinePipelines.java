package com.hazeline.module.modules.render;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;

import com.hazeline.Hazeline;

/**
 * Hazeline's own render layers for Custom Block Outlines.
 *
 * <p>They mirror vanilla 1.21.11 (verified against the bytecode): {@code RenderLayers.LINES} =
 * {@code RENDERTYPE_LINES_SNIPPET} (TRANSFORMS_PROJECTION_FOG + GLOBALS, translucent blend, no cull,
 * POSITION_COLOR_NORMAL_LINE_WIDTH / LINES) with {@code VIEW_OFFSET_Z_LAYERING} and
 * {@code ITEM_ENTITY_TARGET}; {@code RenderLayers.debugQuads()} = POSITION_COLOR / QUADS, translucent,
 * no depth write, no cull. Differences: the shaders are {@code hazeline:core/outline_lines} and
 * {@code hazeline:core/outline_fill} (self-contained copies of the vanilla ones), so resource packs that
 * replace {@code minecraft:core/rendertype_lines} or its includes can't change our outline; and each layer
 * has an "on top" twin without depth test (drawn over the block and its textures).
 *
 * <p>If our shaders ever fail to compile, {@link #lines(boolean)} / {@link #fill(boolean)} fall back to the
 * vanilla layers (the outline still shows, just without "on top").
 */
public final class OutlinePipelines {
	private static final RenderPipeline LINES = linesPipeline("pipeline/outline_lines", false);
	private static final RenderPipeline LINES_ON_TOP = linesPipeline("pipeline/outline_lines_on_top", true);
	private static final RenderPipeline FILL = fillPipeline("pipeline/outline_fill", false);
	private static final RenderPipeline FILL_ON_TOP = fillPipeline("pipeline/outline_fill_on_top", true);

	private static final RenderLayer LINES_LAYER = linesLayer("hazeline_outline_lines", LINES);
	private static final RenderLayer LINES_ON_TOP_LAYER = linesLayer("hazeline_outline_lines_on_top", LINES_ON_TOP);
	private static final RenderLayer FILL_LAYER = fillLayer("hazeline_outline_fill", FILL);
	private static final RenderLayer FILL_ON_TOP_LAYER = fillLayer("hazeline_outline_fill_on_top", FILL_ON_TOP);

	private static final Set<RenderPipeline> WARNED = new HashSet<>();

	private OutlinePipelines() {
	}

	private static RenderPipeline linesPipeline(String location, boolean onTop) {
		RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Hazeline.id(location))
				.withVertexShader(Hazeline.id("core/outline_lines"))
				.withFragmentShader(Hazeline.id("core/outline_lines"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withCull(false)
				.withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.DrawMode.LINES)
				.withDepthWrite(false);
		if (onTop) {
			b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
		}
		return b.build();
	}

	private static RenderPipeline fillPipeline(String location, boolean onTop) {
		RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
				.withLocation(Hazeline.id(location))
				.withVertexShader(Hazeline.id("core/outline_fill"))
				.withFragmentShader(Hazeline.id("core/outline_fill"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withCull(false)
				.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
				.withDepthWrite(false);
		if (onTop) {
			b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
		}
		return b.build();
	}

	private static RenderLayer linesLayer(String name, RenderPipeline pipeline) {
		return RenderLayer.of(name, RenderSetup.builder(pipeline)
				.layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
				.outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
				.build());
	}

	private static RenderLayer fillLayer(String name, RenderPipeline pipeline) {
		return RenderLayer.of(name, RenderSetup.builder(pipeline)
				.translucent()
				.layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
				.build());
	}

	/** Outline layer: ours (on top or depth-tested), or vanilla lines if our shader is not available. */
	static RenderLayer lines(boolean onTop) {
		RenderPipeline p = onTop ? LINES_ON_TOP : LINES;
		return valid(p) ? (onTop ? LINES_ON_TOP_LAYER : LINES_LAYER) : RenderLayers.lines();
	}

	/** Fill layer: ours (on top or depth-tested), or vanilla debug quads if our shader is not available. */
	public static RenderLayer fill(boolean onTop) {
		RenderPipeline p = onTop ? FILL_ON_TOP : FILL;
		return valid(p) ? (onTop ? FILL_ON_TOP_LAYER : FILL_LAYER) : RenderLayers.debugQuads();
	}

	/** Compiles on demand (cached by the device, cleared on resource reload); never throws. */
	private static boolean valid(RenderPipeline pipeline) {
		try {
			CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline);
			if (compiled.isValid()) {
				return true;
			}
		} catch (RuntimeException e) {
			if (WARNED.add(pipeline)) {
				Hazeline.LOGGER.warn("Custom Block Outlines: pipeline {} could not be compiled, using vanilla", pipeline.getLocation(), e);
			}
			return false;
		}
		if (WARNED.add(pipeline)) {
			Hazeline.LOGGER.warn("Custom Block Outlines: pipeline {} is not valid, using vanilla", pipeline.getLocation());
		}
		return false;
	}
}
