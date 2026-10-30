package com.hazeline.module.modules.sky;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import com.hazeline.Hazeline;

/**
 * Draws the custom Overworld sky (render thread, inside vanilla's sky pass).
 *
 * <h2>Where (1.21.11, Yarn, checked against the game's bytecode)</h2>
 * {@code WorldRenderer#renderSky} adds a "sky" frame pass that, for an Overworld-type skybox, calls
 * {@code SkyRendering#renderTopSky(skyColor)}, {@code renderGlowingSky} (sunrise/sunset),
 * {@code renderCelestialBodies} (sun, moon, stars) and {@code renderSkyDark}. The model-view matrix
 * at that point is the camera rotation only, so everything here is drawn around the origin, like
 * vanilla's sky. {@code SkyRenderingMixin} replaces {@code renderTopSky} with {@link #render}.
 *
 * <h2>How</h2>
 * <ul>
 *   <li><b>Gradient dome</b>: a sphere of {@value #RINGS} latitude rings x {@value #SEGMENTS}
 *       segments with per-vertex colours (horizon colour at the horizon and below, sky colour at the
 *       top, smoothstep in between). The fragment shader {@code hazeline:core/custom_sky} adds
 *       ±0.5/255 of interleaved-gradient-noise dither, so even slow gradients show no banding.</li>
 *   <li><b>Falling stars</b>: a soft radial glow quad for the head and a ribbon of up to 14
 *       segments for the trail (a texture with a Gaussian profile across, vertex alpha fading
 *       along it), with additive-style "lightning" blending ({@code SRC_ALPHA, ONE}) for a glow.</li>
 *   <li>Both use vanilla's immediate upload ({@code VertexFormat#uploadImmediateVertexBuffer}, one
 *       shared buffer per vertex format) and one reusable {@link BufferAllocator}, so no GPU buffer
 *       is created per frame. The two textures are registered once with the TextureManager, which
 *       owns and closes them. {@link #close()} frees the allocator when the game stops.</li>
 *   <li>Neither pipeline is registered with {@code RenderPipelines.register}: they are compiled on
 *       demand and checked with {@code isValid()}. If a resource pack breaks the shader, the
 *       vanilla sky is drawn instead (never a crash, never a failed resource reload).</li>
 * </ul>
 */
public final class CustomSkyRenderer {
	private static final int RINGS = 48;
	private static final int SEGMENTS = 48;
	private static final float DOME_RADIUS = 50f;
	private static final float STAR_RADIUS = 45f;

	private static final Identifier GLOW = Hazeline.id("sky/star_glow");
	private static final Identifier TRAIL = Hazeline.id("sky/star_trail");

	/** Sky dome: vertex colours + dithering fragment shader, no depth, translucent (sky colour alpha). */
	private static final RenderPipeline DOME = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
			.withLocation(Hazeline.id("pipeline/custom_sky"))
			.withVertexShader(Hazeline.id("core/custom_sky"))
			.withFragmentShader(Hazeline.id("core/custom_sky"))
			.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
			.withBlend(BlendFunction.TRANSLUCENT)
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withCull(false)
			.build();

	/** Falling stars: vanilla position_tex_color shaders, glow blending, no depth. */
	private static final RenderPipeline STARS = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
			.withLocation(Hazeline.id("pipeline/custom_sky_stars"))
			.withVertexShader("core/position_tex_color")
			.withFragmentShader("core/position_tex_color")
			.withSampler("Sampler0")
			.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
			.withBlend(BlendFunction.LIGHTNING)
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withCull(false)
			.build();

	private static BufferAllocator allocator;
	private static boolean texturesCreated;
	private static boolean warned;
	/** Day/night factor of the last frame (also used by the fog mixin). */
	private static float lastLight = 1f;

	private CustomSkyRenderer() {
	}

	/**
	 * Draws the custom sky instead of vanilla's top sky disc.
	 *
	 * @param vanillaSkyColor the colour vanilla would have used (already darkened by time and rain)
	 * @return false if the custom sky can't be drawn (shader unavailable) - vanilla then draws its own
	 */
	public static boolean render(CustomSky sky, int vanillaSkyColor) {
		if (!valid(DOME) || (sky.stars.get() && !valid(STARS))) {
			return false;
		}
		ensureResources();
		lastLight = lightFactor(sky, vanillaSkyColor);
		int top = scale(sky.skyColor.get(), lastLight);
		int horizon = scale(horizonColor(sky), lastLight);

		BufferBuilder dome = new BufferBuilder(allocator, VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
		buildDome(dome, top, horizon);
		try (BuiltBuffer mesh = dome.end()) {
			draw(DOME, mesh, null, null, "Hazeline sky");
		}

		FallingStars sim = FallingStars.get();
		sim.update(sky);
		if (sky.stars.get() && !sim.stars().isEmpty()) {
			drawStars(sky, sim);
		}
		return true;
	}

	/** Light factor for fog matching: the day/night dimming of the last drawn frame. */
	public static float lastLight() {
		return lastLight;
	}

	/** Horizon colour before day/night dimming (the sky colour itself if the gradient is off). */
	public static int horizonColor(CustomSky sky) {
		if (!sky.gradient.get()) {
			return sky.skyColor.get();
		}
		float s = (float) (sky.gradientStrength.get() / 100.0);
		return lerpArgb(s, sky.skyColor.get(), sky.horizonColor.get());
	}

	/**
	 * 0..1 dimming. Vanilla's own sky colour already follows the time of day and rain, so its
	 * brightness (largest channel) is used as the light level, eased with smoothstep and never
	 * below 6 %, so the sky dims smoothly instead of vanishing. 0 % influence keeps the set colour.
	 */
	static float lightFactor(CustomSky sky, int vanillaSkyColor) {
		if (!sky.dayNight.get()) {
			return 1f;
		}
		int max = Math.max(ColorHelper.getRed(vanillaSkyColor), Math.max(ColorHelper.getGreen(vanillaSkyColor), ColorHelper.getBlue(vanillaSkyColor)));
		float light = Math.min(1f, max / 230f);
		light = light * light * (3f - 2f * light);
		light = Math.max(0.06f, light);
		float influence = (float) (sky.timeInfluence.get() / 100.0);
		return 1f - influence * (1f - light);
	}

	// ============================================================================ dome

	static void buildDome(VertexConsumer b, int top, int horizon) {
		// Latitude from -90 (straight down) to +90 (straight up).
		for (int ring = 0; ring < RINGS; ring++) {
			double lat0 = -Math.PI / 2 + Math.PI * ring / RINGS;
			double lat1 = -Math.PI / 2 + Math.PI * (ring + 1) / RINGS;
			int c0 = domeColor(Math.sin(lat0), top, horizon);
			int c1 = domeColor(Math.sin(lat1), top, horizon);
			for (int seg = 0; seg < SEGMENTS; seg++) {
				double lon0 = Math.PI * 2 * seg / SEGMENTS;
				double lon1 = Math.PI * 2 * (seg + 1) / SEGMENTS;
				float[] p00 = point(lat0, lon0), p01 = point(lat0, lon1), p10 = point(lat1, lon0), p11 = point(lat1, lon1);
				b.vertex(p00[0], p00[1], p00[2]).color(c0);
				b.vertex(p10[0], p10[1], p10[2]).color(c1);
				b.vertex(p11[0], p11[1], p11[2]).color(c1);
				b.vertex(p00[0], p00[1], p00[2]).color(c0);
				b.vertex(p11[0], p11[1], p11[2]).color(c1);
				b.vertex(p01[0], p01[1], p01[2]).color(c0);
			}
		}
	}

	private static float[] point(double lat, double lon) {
		double c = Math.cos(lat);
		return new float[] {(float) (c * Math.cos(lon) * DOME_RADIUS), (float) (Math.sin(lat) * DOME_RADIUS), (float) (c * Math.sin(lon) * DOME_RADIUS)};
	}

	/** Horizon colour at and below the horizon, smoothstep to the top colour at the zenith. */
	private static int domeColor(double y, int top, int horizon) {
		if (y <= 0) {
			return horizon;
		}
		float t = (float) Math.min(1.0, y / 0.85);
		t = t * t * (3f - 2f * t);
		return lerpArgb(t, horizon, top);
	}

	// ============================================================================ stars

	private static void drawStars(CustomSky sky, FallingStars sim) {
		BufferBuilder trails = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		if (buildTrails(trails, sky, sim)) {
			try (BuiltBuffer mesh = trails.end()) {
				drawTextured(mesh, TRAIL, "Hazeline star trails");
			}
		} else {
			discard(trails);
		}
		BufferBuilder heads = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		if (buildHeads(heads, sky, sim)) {
			try (BuiltBuffer mesh = heads.end()) {
				drawTextured(mesh, GLOW, "Hazeline falling stars");
			}
		} else {
			discard(heads);
		}
	}

	private static int starColor(CustomSky sky) {
		return sky.starColorMode.get() == CustomSky.StarColorMode.SKY_ACCENT ? accent(sky) : sky.starColor.get();
	}

	private static float starAlpha(CustomSky sky, FallingStars sim, FallingStars.Star s) {
		float baseAlpha = ColorHelper.getAlpha(starColor(sky)) / 255f;
		return baseAlpha * s.brightness * FallingStars.fade(s)
				* twinkle(sky.twinkle.get(), (float) (sky.twinkleStrength.get() / 100.0), sky.twinkleSpeed.getFloat(), sim.clock(), s);
	}

	/** Trail ribbons (quads, u along the trail, v across). Returns true if anything was added. */
	static boolean buildTrails(VertexConsumer out, CustomSky sky, FallingStars sim) {
		int segments = sky.quality.get().trailSegments;
		float trail = sky.trailLength.getFloat();
		if (trail <= 0.01f) {
			return false;
		}
		float smooth = (float) (sky.trailSmoothness.get() / 100.0);
		// Fade curve along the trail: exponent 3 (short, sharp) .. 0.6 (long, gentle)
		float exponent = 3f - 2.4f * smooth;
		float size = sky.starSize.getFloat();
		int color = starColor(sky);
		boolean any = false;
		Vector3f a = new Vector3f(), b = new Vector3f(), side = new Vector3f();
		for (FallingStars.Star s : sim.stars()) {
			float alpha = starAlpha(sky, sim, s);
			if (alpha <= 0.004f) {
				continue;
			}
			// The trail spans trail * 0.22 rad of the path behind the head.
			float span = trail * 0.22f / s.angularSpeed;
			float width = 0.32f * size * s.size;
			for (int i = 0; i < segments; i++) {
				float f0 = i / (float) segments, f1 = (i + 1) / (float) segments;
				FallingStars.position(s, span * f0, a).mul(STAR_RADIUS);
				FallingStars.position(s, span * f1, b).mul(STAR_RADIUS);
				if (a.distanceSquared(b) < 1e-6f) {
					break; // reached the spawn point
				}
				float w0 = width * (1f - 0.7f * f0), w1 = width * (1f - 0.7f * f1);
				int c0 = withAlpha(color, alpha * (float) Math.pow(1f - f0, exponent));
				int c1 = withAlpha(color, alpha * (float) Math.pow(1f - f1, exponent));
				// Ribbon side vector: perpendicular to the path and to the view direction.
				side.set(b).sub(a).cross(a).normalize();
				out.vertex(a.x + side.x * w0, a.y + side.y * w0, a.z + side.z * w0).texture(f0, 0f).color(c0);
				out.vertex(a.x - side.x * w0, a.y - side.y * w0, a.z - side.z * w0).texture(f0, 1f).color(c0);
				out.vertex(b.x - side.x * w1, b.y - side.y * w1, b.z - side.z * w1).texture(f1, 1f).color(c1);
				out.vertex(b.x + side.x * w1, b.y + side.y * w1, b.z + side.z * w1).texture(f1, 0f).color(c1);
				any = true;
			}
		}
		return any;
	}

	/** Glowing heads: one camera-facing quad per star. Returns true if anything was added. */
	static boolean buildHeads(VertexConsumer out, CustomSky sky, FallingStars sim) {
		float size = sky.starSize.getFloat();
		int color = starColor(sky);
		boolean any = false;
		Vector3f p = new Vector3f(), u = new Vector3f(), v = new Vector3f();
		for (FallingStars.Star s : sim.stars()) {
			float alpha = starAlpha(sky, sim, s);
			if (alpha <= 0.004f) {
				continue;
			}
			FallingStars.position(s, 0f, p).mul(STAR_RADIUS);
			float r = 0.9f * size * s.size;
			// Billboard axes perpendicular to the view direction (the camera is at the origin).
			boolean vertical = Math.abs(p.y) > 0.99f * STAR_RADIUS;
			u.set(p).cross(vertical ? 1f : 0f, vertical ? 0f : 1f, 0f).normalize(r);
			v.set(p).cross(u).normalize(r);
			int c = withAlpha(lerpArgb(0.45f, color, 0xFFFFFFFF), alpha); // whiter core
			out.vertex(p.x - u.x - v.x, p.y - u.y - v.y, p.z - u.z - v.z).texture(0f, 0f).color(c);
			out.vertex(p.x - u.x + v.x, p.y - u.y + v.y, p.z - u.z + v.z).texture(0f, 1f).color(c);
			out.vertex(p.x + u.x + v.x, p.y + u.y + v.y, p.z + u.z + v.z).texture(1f, 1f).color(c);
			out.vertex(p.x + u.x - v.x, p.y + u.y - v.y, p.z + u.z - v.z).texture(1f, 0f).color(c);
			any = true;
		}
		return any;
	}

	private static float twinkle(boolean on, float strength, float speed, float clock, FallingStars.Star s) {
		if (!on || strength <= 0f) {
			return 1f;
		}
		float wave = 0.5f + 0.5f * (float) Math.sin(s.phase + clock * speed * Math.PI * 2);
		return 1f - strength * 0.6f * wave;
	}

	/** Light version of the sky's accent (the horizon colour when the gradient is on). */
	private static int accent(CustomSky sky) {
		int base = sky.gradient.get() ? sky.horizonColor.get() : sky.skyColor.get();
		return lerpArgb(0.6f, base | 0xFF000000, 0xFFFFFFFF);
	}

	/** A builder that received no vertices still has to be ended so the allocator is reset. */
	private static void discard(BufferBuilder builder) {
		BuiltBuffer empty = builder.endNullable();
		if (empty != null) {
			empty.close();
		}
	}

	// ============================================================================ GPU

	private static void drawTextured(BuiltBuffer mesh, Identifier texture, String label) {
		AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(texture);
		draw(STARS, mesh, tex.getGlTextureView(), tex.getSampler(), label);
	}

	/** Same steps as vanilla's {@code RenderLayer#draw} / {@code SkyRendering#renderTopSky}. */
	private static void draw(RenderPipeline pipeline, BuiltBuffer mesh, GpuTextureView texture, GpuSampler sampler, String label) {
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(RenderSystem.getModelViewMatrix(),
				new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f());
		GpuBuffer vertices = pipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.getBuffer());
		BuiltBuffer.DrawParameters params = mesh.getDrawParameters();
		RenderSystem.ShapeIndexBuffer indices = RenderSystem.getSequentialBuffer(params.mode());
		GpuBuffer indexBuffer = indices.getIndexBuffer(params.indexCount());
		var target = MinecraftClient.getInstance().getFramebuffer();
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label,
				target.getColorAttachmentView(), OptionalInt.empty(), target.getDepthAttachmentView(), OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			if (texture != null) {
				pass.bindTexture("Sampler0", texture, sampler);
			}
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indexBuffer, indices.getIndexType());
			pass.drawIndexed(0, 0, params.indexCount(), 1);
		}
	}

	/** Compiles the pipeline on demand (cached by the device, cleared on resource reload). */
	private static boolean valid(RenderPipeline pipeline) {
		try {
			CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline);
			if (compiled.isValid()) {
				return true;
			}
		} catch (RuntimeException e) {
			if (!warned) {
				Hazeline.LOGGER.warn("Custom Sky shader could not be compiled; drawing the vanilla sky", e);
			}
		}
		if (!warned) {
			warned = true;
			Hazeline.LOGGER.warn("Custom Sky pipeline {} is not valid; drawing the vanilla sky", pipeline.getLocation());
		}
		return false;
	}

	/**
	 * The shared star textures (soft glow head, trail strip), created on first use. Also used by
	 * the menu backgrounds ({@code CustomMenus}) so both skies look the same.
	 */
	public static AbstractTexture glowTexture() {
		ensureTextures();
		return MinecraftClient.getInstance().getTextureManager().getTexture(GLOW);
	}

	public static AbstractTexture trailTexture() {
		ensureTextures();
		return MinecraftClient.getInstance().getTextureManager().getTexture(TRAIL);
	}

	private static void ensureResources() {
		if (allocator == null) {
			allocator = new BufferAllocator(256 * 1024);
		}
		ensureTextures();
	}

	private static void ensureTextures() {
		if (!texturesCreated) {
			texturesCreated = true;
			MinecraftClient.getInstance().getTextureManager().registerTexture(GLOW, smooth("hazeline star glow", glowImage(64)));
			MinecraftClient.getInstance().getTextureManager().registerTexture(TRAIL, smooth("hazeline star trail", trailImage(16, 32)));
		}
	}

	/** Frees the vertex allocator (game shutdown). Textures belong to the TextureManager. */
	public static void close() {
		if (allocator != null) {
			allocator.close();
			allocator = null;
		}
	}

	/** Linear filtering, clamped edges (vanilla's NativeImageBackedTexture defaults to nearest). */
	private static final class SmoothTexture extends NativeImageBackedTexture {
		SmoothTexture(String label, NativeImage image) {
			super(() -> label, image);
			this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
		}
	}

	private static NativeImageBackedTexture smooth(String label, NativeImage image) {
		return new SmoothTexture(label, image);
	}

	/** White radial glow: bright core with a soft Gaussian-like falloff to zero at the edge. */
	static NativeImage glowImage(int size) {
		NativeImage image = new NativeImage(size, size, false);
		float c = size / 2f;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float d = (float) Math.hypot(x + 0.5f - c, y + 0.5f - c) / c;
				float a = d >= 1f ? 0f : (float) (Math.exp(-d * d * 5.0) - Math.exp(-5.0)) / (float) (1 - Math.exp(-5.0));
				a = Math.max(a, d < 0.18f ? 1f - d / 0.18f * 0.35f : 0f); // small solid core
				image.setColorArgb(x, y, (Math.round(Math.min(1f, a) * 255f) << 24) | 0xFFFFFF);
			}
		}
		return image;
	}

	/** White trail strip: Gaussian across (v), uniform along (u - the vertex alpha fades it). */
	static NativeImage trailImage(int width, int height) {
		NativeImage image = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			float d = Math.abs((y + 0.5f) / height * 2f - 1f);
			float a = d >= 1f ? 0f : (float) (Math.exp(-d * d * 4.0) - Math.exp(-4.0)) / (float) (1 - Math.exp(-4.0));
			for (int x = 0; x < width; x++) {
				image.setColorArgb(x, y, (Math.round(a * 255f) << 24) | 0xFFFFFF);
			}
		}
		return image;
	}

	// ============================================================================ colour helpers

	static int scale(int argb, float factor) {
		return ColorHelper.withAlpha(ColorHelper.getAlpha(argb), ColorHelper.scaleRgb(argb, factor));
	}

	private static int withAlpha(int argb, float alpha) {
		return ColorHelper.withAlpha(Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f), argb);
	}

	static int lerpArgb(float t, int from, int to) {
		return ColorHelper.lerp(t, from, to);
	}
}
