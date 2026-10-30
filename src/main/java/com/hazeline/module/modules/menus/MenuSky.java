package com.hazeline.module.modules.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Matrix3x2f;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureSetup;

import com.hazeline.Hazeline;
import com.hazeline.gui.Colors;
import com.hazeline.module.modules.sky.CustomSky;
import com.hazeline.module.modules.sky.CustomSkyRenderer;
import com.hazeline.module.modules.sky.FallingStars;

/**
 * The Custom Sky look as a flat 2D background for menus: the sky gradient (sky colour at the top,
 * horizon colour at the bottom, same smoothstep curve as the in-game dome) plus falling stars with
 * glowing heads and trails.
 *
 * <p>All colours and star settings come from the {@link CustomSky} module (shared), so changing
 * the sky or loading a preset changes the menus as well. Day/night dimming is not applied.
 *
 * <h2>How it is drawn (1.21.11)</h2>
 * Three {@link MenuElement}s are queued in the GUI render state: the gradient (strips with
 * per-vertex colours), the star trails and the star heads. The gradient uses the dithering shader
 * {@code hazeline:core/custom_sky} (no banding) and the stars use vanilla's
 * {@code position_tex_color} shaders with glow blending ({@code SRC_ALPHA, ONE}). None of the pipelines is
 * registered with {@code RenderPipelines.register}; each is compiled on demand and checked with
 * {@code isValid()}, so a missing or broken shader never crashes the game:
 * <ul>
 *   <li>the dither pipeline falls back to vanilla's plain {@code GUI} pipeline (same colours, banding);</li>
 *   <li>the glow pipeline falls back to vanilla's {@code GUI_TEXTURED} (normal alpha blending);</li>
 *   <li>if even those are not valid yet (the shader programs only exist after the game's first
 *       resource reload has applied them), {@link #draw} returns false and the caller leaves the
 *       vanilla background in place.</li>
 * </ul>
 */
public final class MenuSky {
	private static final int STRIPS = 48;

	/** Gradient strips with the sky dome's dithering fragment shader. */
	private static final RenderPipeline DITHER = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(Hazeline.id("pipeline/menu_gradient"))
			.withVertexShader(Hazeline.id("core/custom_sky"))
			.withFragmentShader(Hazeline.id("core/custom_sky"))
			.build();

	/** Star glow: vanilla GUI textured shaders, additive-style glow blending. */
	private static final RenderPipeline GLOW = RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
			.withLocation(Hazeline.id("pipeline/menu_stars"))
			.withBlend(BlendFunction.LIGHTNING)
			.build();

	private static final java.util.Set<RenderPipeline> WARNED = new java.util.HashSet<>();

	private static final List<Star> STARS = new ArrayList<>();
	private static long lastNanos;
	private static float clock;
	private static float spawnBudget;
	private static boolean warmedUp;

	private MenuSky() {
	}

	/** One falling star in GUI pixels. */
	private static final class Star {
		float x, y; // current head position
		float dx, dy; // unit direction
		float speed; // GUI px per second
		float age, ttl;
		float size, phase, brightness;
	}

	// ============================================================================ drawing

	/**
	 * Queues the background for a full-screen menu.
	 *
	 * @param alpha overall opacity 0..1 (the loading screen fades out with it)
	 * @return false if nothing could be drawn (the caller then keeps the vanilla background)
	 */
	public static boolean draw(DrawContext g, float alpha) {
		CustomSky sky = CustomSky.get();
		CustomMenus menus = CustomMenus.get();
		if (sky == null || menus == null) {
			return false;
		}
		if (alpha <= 0f) {
			return true; // fully faded out: nothing to draw, nothing to fall back to
		}
		RenderPipeline gradientPipeline = valid(DITHER) ? DITHER : RenderPipelines.GUI;
		if (!valid(gradientPipeline)) {
			return false;
		}
		int width = g.getScaledWindowWidth();
		int height = g.getScaledWindowHeight();
		if (width <= 0 || height <= 0) {
			return false;
		}
		Matrix3x2f pose = new Matrix3x2f(g.getMatrices());
		ScreenRect scissor = g.scissorStack.peekLast();
		ScreenRect bounds = new ScreenRect(0, 0, width, height);
		if (scissor != null) {
			bounds = scissor.intersection(bounds);
		}
		int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);

		MenuElement.Quads gradient = new MenuElement.Quads();
		buildGradient(gradient, sky, width, height, a);
		g.state.addSimpleElement(new MenuElement(gradientPipeline, TextureSetup.empty(), pose, gradient, false, scissor, bounds));

		boolean wantStars = sky.stars.get() && menus.stars.get();
		int maxStars = wantStars ? (int) Math.round(FallingStars.maxActive(sky) * menus.starAmount.get() / 100.0) : 0;
		advance(sky, width, height, maxStars);
		if (maxStars > 0 && !STARS.isEmpty()) {
			drawStars(g, sky, pose, scissor, bounds, a / 255f);
		}
		return true;
	}

	/** Sky colour at the top, horizon colour at the bottom, smoothstep between (like the dome). */
	static void buildGradient(MenuElement.Quads out, CustomSky sky, int width, int height, int alpha) {
		int top = Colors.withAlpha(sky.skyColor.get(), 255);
		int horizon = Colors.withAlpha(CustomSkyRenderer.horizonColor(sky), 255);
		int previous = colorAt(0f, top, horizon, alpha);
		for (int i = 0; i < STRIPS; i++) {
			float y0 = height * i / (float) STRIPS;
			float y1 = height * (i + 1) / (float) STRIPS;
			int next = colorAt((i + 1) / (float) STRIPS, top, horizon, alpha);
			out.rect(0f, y0, width, y1, previous, next);
			previous = next;
		}
	}

	/** {@code f}: 0 at the top of the screen, 1 at the bottom. */
	private static int colorAt(float f, int top, int horizon, int alpha) {
		float t = 1f - f;
		t = t * t * (3f - 2f * t);
		return Colors.withAlpha(Colors.lerp(horizon, top, t), alpha);
	}

	// ============================================================================ stars

	static void advance(CustomSky sky, int width, int height, int maxStars) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		if (maxStars <= 0) {
			STARS.clear();
			spawnBudget = 0;
			warmedUp = false;
			return;
		}
		if (!warmedUp) {
			warmedUp = true;
			for (int i = 0; i < 50; i++) { // let a few seconds of stars exist already
				step(sky, width, height, maxStars, 0.1f);
			}
		}
		step(sky, width, height, maxStars, dt);
	}

	private static void step(CustomSky sky, int width, int height, int maxStars, float dt) {
		clock += dt;
		for (int i = STARS.size() - 1; i >= 0; i--) {
			Star s = STARS.get(i);
			s.age += dt;
			s.x += s.dx * s.speed * dt;
			s.y += s.dy * s.speed * dt;
			boolean gone = s.age >= s.ttl || s.y > height + 40 || s.x < -60 || s.x > width + 60;
			if (gone || i >= maxStars) {
				STARS.remove(i);
			}
		}
		spawnBudget = Math.min(spawnBudget + dt * sky.spawnRate.getFloat(), 4f);
		while (spawnBudget >= 1f) {
			spawnBudget -= 1f;
			if (STARS.size() < maxStars) {
				STARS.add(spawn(sky, width, height));
			}
		}
	}

	private static Star spawn(CustomSky sky, int width, int height) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		Star s = new Star();
		double angle = sky.fallAngle.get();
		if (sky.directionMode.get() == CustomSky.DirectionMode.RANDOM) {
			angle += (r.nextDouble() * 2 - 1) * sky.angleRange.get();
		}
		double a = Math.toRadians(angle); // 0 = straight down, negative = to the left
		s.dx = (float) Math.sin(a);
		s.dy = (float) Math.cos(a);
		s.x = (float) (-0.1 * width + r.nextDouble() * 1.2 * width);
		s.y = (float) (-0.05 * height + r.nextDouble() * 0.55 * height);
		s.speed = (float) (height * 0.20 * sky.starSpeed.get() * (0.8 + r.nextDouble() * 0.4));
		s.ttl = (float) (3.5 + r.nextDouble() * 2.0);
		s.size = (float) (0.8 + r.nextDouble() * 0.4);
		s.phase = (float) (r.nextDouble() * Math.PI * 2);
		s.brightness = (float) (0.75 + r.nextDouble() * 0.25);
		return s;
	}

	private static int starColor(CustomSky sky) {
		if (sky.starColorMode.get() == CustomSky.StarColorMode.SKY_ACCENT) {
			int base = sky.gradient.get() ? sky.horizonColor.get() : sky.skyColor.get();
			return Colors.lerp(Colors.withAlpha(base, 255), 0xFFFFFFFF, 0.6f);
		}
		return sky.starColor.get();
	}

	private static float starAlpha(CustomSky sky, Star s, float masterAlpha) {
		float fadeIn = Math.min(1f, s.age / 0.4f);
		float progress = s.age / s.ttl;
		float fadeOut = progress < 0.65f ? 1f : Math.max(0f, 1f - (progress - 0.65f) / 0.35f);
		float twinkle = 1f;
		if (sky.twinkle.get() && sky.twinkleStrength.get() > 0) {
			float wave = 0.5f + 0.5f * (float) Math.sin(s.phase + clock * sky.twinkleSpeed.getFloat() * Math.PI * 2);
			twinkle = 1f - (float) (sky.twinkleStrength.get() / 100.0) * 0.6f * wave;
		}
		float base = Colors.alpha(starColor(sky)) / 255f;
		return base * s.brightness * fadeIn * fadeOut * twinkle * masterAlpha;
	}

	private static void drawStars(DrawContext g, CustomSky sky, Matrix3x2f pose, ScreenRect scissor, ScreenRect bounds, float masterAlpha) {
		RenderPipeline pipeline = valid(GLOW) ? GLOW : RenderPipelines.GUI_TEXTURED;
		if (!valid(pipeline)) {
			return;
		}
		MenuElement.Quads trails = new MenuElement.Quads();
		MenuElement.Quads heads = new MenuElement.Quads();
		collectStars(sky, g.getScaledWindowHeight(), masterAlpha, trails, heads);
		queue(g, pipeline, CustomSkyRenderer.trailTexture(), trails, pose, scissor, bounds);
		queue(g, pipeline, CustomSkyRenderer.glowTexture(), heads, pose, scissor, bounds);
	}

	/** Builds the quads of all live stars: trail ribbons (u along, v across) and glow head squares. */
	static void collectStars(CustomSky sky, int height, float masterAlpha, MenuElement.Quads trails, MenuElement.Quads heads) {
		int color = starColor(sky);
		float size = sky.starSize.getFloat();
		int segments = sky.quality.get().trailSegments;
		float trail = sky.trailLength.getFloat();
		float smooth = (float) (sky.trailSmoothness.get() / 100.0);
		float exponent = 3f - 2.4f * smooth;
		for (Star s : STARS) {
			float alpha = starAlpha(sky, s, masterAlpha);
			if (alpha <= 0.004f) {
				continue;
			}
			float headR = 4.2f * size * s.size;
			// Head: a camera-facing square with the soft glow texture and a whiter core colour.
			int headColor = withAlpha(Colors.lerp(color | 0xFF000000, 0xFFFFFFFF, 0.45f), alpha);
			heads.vertex(s.x - headR, s.y - headR, 0f, 0f, headColor);
			heads.vertex(s.x - headR, s.y + headR, 0f, 1f, headColor);
			heads.vertex(s.x + headR, s.y + headR, 1f, 1f, headColor);
			heads.vertex(s.x + headR, s.y - headR, 1f, 0f, headColor);

			if (trail > 0.01f) {
				float length = Math.min(trail * 0.15f * height, s.age * s.speed); // never behind the spawn point
				float width = 1.6f * size * s.size;
				float px = -s.dy, py = s.dx; // perpendicular to the path
				for (int i = 0; i < segments && length > 1f; i++) {
					float f0 = i / (float) segments, f1 = (i + 1) / (float) segments;
					float ax = s.x - s.dx * length * f0, ay = s.y - s.dy * length * f0;
					float bx = s.x - s.dx * length * f1, by = s.y - s.dy * length * f1;
					float w0 = width * (1f - 0.7f * f0), w1 = width * (1f - 0.7f * f1);
					int c0 = withAlpha(color | 0xFF000000, alpha * (float) Math.pow(1f - f0, exponent));
					int c1 = withAlpha(color | 0xFF000000, alpha * (float) Math.pow(1f - f1, exponent));
					trails.vertex(ax + px * w0, ay + py * w0, f0, 0f, c0);
					trails.vertex(ax - px * w0, ay - py * w0, f0, 1f, c0);
					trails.vertex(bx - px * w1, by - py * w1, f1, 1f, c1);
					trails.vertex(bx + px * w1, by + py * w1, f1, 0f, c1);
				}
			}
		}
	}

	private static void queue(DrawContext g, RenderPipeline pipeline, AbstractTexture texture, MenuElement.Quads quads, Matrix3x2f pose,
			ScreenRect scissor, ScreenRect bounds) {
		if (quads.isEmpty()) {
			return;
		}
		g.state.addSimpleElement(new MenuElement(pipeline, TextureSetup.of(texture.getGlTextureView(), texture.getSampler()), pose, quads, true,
				scissor, bounds));
	}

	private static int withAlpha(int rgb, float alpha) {
		return Colors.withAlpha(rgb, Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f));
	}

	/** Forgets all stars (menus closed / module off). */
	public static void reset() {
		STARS.clear();
		spawnBudget = 0;
		lastNanos = 0;
		warmedUp = false;
	}

	// ============================================================================ pipelines

	/** Compiles {@code pipeline} on demand (cached by the device, cleared on resource reload). */
	private static boolean valid(RenderPipeline pipeline) {
		try {
			CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline);
			if (compiled.isValid()) {
				return true;
			}
		} catch (RuntimeException e) {
			if (WARNED.add(pipeline)) {
				Hazeline.LOGGER.warn("Menu background pipeline {} could not be compiled", pipeline.getLocation(), e);
			}
			return false;
		}
		return false;
	}
}
