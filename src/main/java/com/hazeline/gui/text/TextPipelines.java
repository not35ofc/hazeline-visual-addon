package com.hazeline.gui.text;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gl.RenderPipelines;

import com.hazeline.Hazeline;

/**
 * Render pipelines for GUI text.
 *
 * <p>{@link #COVERAGE}: vanilla's GUI textured snippet (vertex shader
 * {@code core/position_tex_color}, POSITION_TEX_COLOR quads, translucent blending, Sampler0) with
 * the fragment shader {@code hazeline:core/gui_text_coverage}, which reads coverage from a
 * single-channel atlas.
 *
 * <p>The pipeline is deliberately <em>not</em> registered with {@code RenderPipelines.register}:
 * registered pipelines are precompiled on every resource reload, and if one fails (for example a
 * resource pack replaces the shader with something broken) the game aborts the reload with
 * "Failed to load required shader programs". Instead {@link #coverageAvailable()} compiles it on
 * demand ({@code GpuDevice#precompilePipeline}, cached by the device and cleared on reload) and
 * checks {@code isValid()}; if it is not valid, text falls back to RGBA8 atlases drawn with the
 * vanilla {@link RenderPipelines#GUI_TEXTURED} pipeline, which looks the same.
 */
public final class TextPipelines {
	public static final RenderPipeline COVERAGE = RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
			.withLocation(Hazeline.id("pipeline/gui_text_coverage"))
			.withFragmentShader(Hazeline.id("core/gui_text_coverage"))
			.build();

	private TextPipelines() {
	}

	/** True if the single-channel coverage pipeline compiled and linked. */
	public static boolean coverageAvailable() {
		try {
			CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(COVERAGE);
			return compiled.isValid();
		} catch (RuntimeException e) {
			Hazeline.LOGGER.warn("Hazeline text shader could not be compiled", e);
			return false;
		}
	}

	public static RenderPipeline pipeline(boolean singleChannel) {
		return singleChannel ? COVERAGE : RenderPipelines.GUI_TEXTURED;
	}
}
