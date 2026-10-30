package com.hazeline.gui;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.texture.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import com.hazeline.Hazeline;

/**
 * Smooth (anti-aliased) shapes for the menu, drawn with small generated textures instead of
 * pixel strips:
 * <ul>
 *   <li>a filled disc - rounded rectangle corners, toggle knobs, dots;</li>
 *   <li>rings of a few thicknesses - rounded outlines;</li>
 *   <li>a soft radial glow - particles and the light that follows the mouse.</li>
 * </ul>
 * The textures are white with an alpha mask and use linear filtering, so they are tinted by the
 * blit colour and stay smooth at any size and GUI scale. Everything is created lazily on the
 * render thread the first time it is drawn.
 */
public final class Shapes {
	private static final int DISC_SIZE = 128;
	private static final int GLOW_SIZE = 64;
	private static final Identifier DISC = Hazeline.id("gui/disc");
	private static final Identifier GLOW = Hazeline.id("gui/glow");
	/** Ring textures by "thickness in 1/64 of the radius" (outline width / corner radius). */
	private static final Map<Integer, Identifier> RINGS = new HashMap<>();
	private static boolean created;

	private Shapes() {
	}

	/** A texture with linear filtering and clamped edges (vanilla's NativeImageBackedTexture uses nearest + repeat). */
	private static final class SmoothTexture extends NativeImageBackedTexture {
		SmoothTexture(String label, NativeImage image) {
			super(() -> label, image);
			this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
		}
	}

	private static void ensure() {
		if (created) {
			return;
		}
		created = true;
		register(DISC, "hazeline disc", image(DISC_SIZE, (dx, dy) -> coverage(DISC_SIZE / 2f - (float) Math.hypot(dx, dy))));
		register(GLOW, "hazeline glow", image(GLOW_SIZE, (dx, dy) -> {
			float d = (float) Math.hypot(dx, dy) / (GLOW_SIZE / 2f);
			if (d >= 1f) {
				return 0f;
			}
			float f = 1f - d;
			return f * f * (3f - 2f * f) * f; // smooth, bright centre fading to nothing
		}));
	}

	private interface Mask {
		float alpha(float dx, float dy);
	}

	/** Anti-aliased edge: 1.5 texels wide. */
	private static float coverage(float distanceInside) {
		return Math.max(0f, Math.min(1f, distanceInside / 1.5f + 0.5f));
	}

	private static NativeImage image(int size, Mask mask) {
		NativeImage image = new NativeImage(size, size, false);
		float c = size / 2f;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float a = mask.alpha(x + 0.5f - c, y + 0.5f - c);
				image.setColorArgb(x, y, (Math.round(a * 255f) << 24) | 0xFFFFFF);
			}
		}
		return image;
	}

	private static void register(Identifier id, String label, NativeImage image) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.getTextureManager() == null) {
			image.close(); // no game (offline preview tools): nothing to upload to
			return;
		}
		mc.getTextureManager().registerTexture(id, new SmoothTexture(label, image));
	}

	private static Identifier ring(int thickness64) {
		ensure();
		return RINGS.computeIfAbsent(thickness64, t -> {
			Identifier id = Hazeline.id("gui/ring_" + t);
			float outer = DISC_SIZE / 2f;
			float inner = outer - outer * t / 64f;
			register(id, "hazeline ring " + t, image(DISC_SIZE, (dx, dy) -> {
				float d = (float) Math.hypot(dx, dy);
				return Math.min(coverage(outer - d), coverage(d - inner));
			}));
			return id;
		});
	}

	// ------------------------------------------------------------------------------ drawing

	/**
	 * Rounded rectangle with smooth corners and an optional vertical gradient. Parts never
	 * overlap, so translucent colours stay even.
	 */
	public static void roundedRect(DrawContext g, int x1, int y1, int x2, int y2, int radius,
			int top, int bottom, boolean roundTop, boolean roundBottom) {
		int w = x2 - x1;
		int h = y2 - y1;
		if (w <= 0 || h <= 0) {
			return;
		}
		int r = Math.max(0, Math.min(radius, Math.min(w / 2, h / 2)));
		if (r <= 0 || (!roundTop && !roundBottom)) {
			if (top == bottom) {
				g.fill(x1, y1, x2, y2, top);
			} else {
				g.fillGradient(x1, y1, x2, y2, top, bottom);
			}
			return;
		}
		ensure();
		int rt = roundTop ? r : 0;
		int rb = roundBottom ? r : 0;
		int midTop = Colors.lerp(top, bottom, rt / (float) h);
		int midBottom = Colors.lerp(top, bottom, (h - rb) / (float) h);
		if (y2 - rb > y1 + rt) {
			if (midTop == midBottom) {
				g.fill(x1, y1 + rt, x2, y2 - rb, midTop);
			} else {
				g.fillGradient(x1, y1 + rt, x2, y2 - rb, midTop, midBottom);
			}
		}
		int half = DISC_SIZE / 2;
		if (rt > 0) {
			int c = Colors.lerp(top, bottom, rt / (2f * h));
			g.fill(x1 + rt, y1, x2 - rt, y1 + rt, c);
			corner(g, DISC, x1, y1, rt, 0, 0, c);
			corner(g, DISC, x2 - rt, y1, rt, half, 0, c);
		}
		if (rb > 0) {
			int c = Colors.lerp(top, bottom, 1f - rb / (2f * h));
			g.fill(x1 + rb, y2 - rb, x2 - rb, y2, c);
			corner(g, DISC, x1, y2 - rb, rb, 0, half, c);
			corner(g, DISC, x2 - rb, y2 - rb, rb, half, half, c);
		}
	}

	/** One quarter of a disc/ring texture, drawn r x r at (x, y). */
	private static void corner(DrawContext g, Identifier texture, int x, int y, int r, int u, int v, int color) {
		int half = DISC_SIZE / 2;
		g.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, r, r, half, half, DISC_SIZE, DISC_SIZE, color);
	}

	/** 1 GUI pixel outline with rounded corners matching {@link #roundedRect}. */
	public static void roundedOutline(DrawContext g, int x1, int y1, int x2, int y2, int radius, int color) {
		int w = x2 - x1;
		int h = y2 - y1;
		if (w <= 1 || h <= 1 || (color >>> 24) == 0) {
			return;
		}
		int r = Math.max(0, Math.min(radius, Math.min(w / 2, h / 2)));
		if (r <= 1) {
			g.drawStrokedRectangle(x1, y1, w, h, color);
			return;
		}
		g.fill(x1 + r, y1, x2 - r, y1 + 1, color);
		g.fill(x1 + r, y2 - 1, x2 - r, y2, color);
		g.fill(x1, y1 + r, x1 + 1, y2 - r, color);
		g.fill(x2 - 1, y1 + r, x2, y2 - r, color);
		Identifier ring = ring(Math.max(1, Math.min(64, Math.round(64f / r))));
		int half = DISC_SIZE / 2;
		corner(g, ring, x1, y1, r, 0, 0, color);
		corner(g, ring, x2 - r, y1, r, half, 0, color);
		corner(g, ring, x1, y2 - r, r, 0, half, color);
		corner(g, ring, x2 - r, y2 - r, r, half, half, color);
	}

	/** Smooth filled circle; centre and radius may be fractional. */
	public static void disc(DrawContext g, float cx, float cy, float radius, int color) {
		sprite(g, DISC, DISC_SIZE, cx, cy, radius, color);
	}

	/** Soft radial glow (bright centre fading to nothing at {@code radius}). */
	public static void glow(DrawContext g, float cx, float cy, float radius, int color) {
		sprite(g, GLOW, GLOW_SIZE, cx, cy, radius, color);
	}

	private static void sprite(DrawContext g, Identifier texture, int size, float cx, float cy, float radius, int color) {
		if (radius <= 0f || (color >>> 24) == 0) {
			return;
		}
		ensure();
		Matrix3x2fStack pose = g.getMatrices();
		pose.pushMatrix();
		pose.translate(cx, cy);
		float s = radius * 2f / size;
		pose.scale(s, s);
		int half = size / 2;
		g.drawTexture(RenderPipelines.GUI_TEXTURED, texture, -half, -half, 0, 0, size, size, size, size, color);
		pose.popMatrix();
	}

	/** Thin line between two points (a rotated quad {@code thickness} GUI px wide). */
	public static void line(DrawContext g, float x1, float y1, float x2, float y2, float thickness, int color) {
		if ((color >>> 24) == 0) {
			return;
		}
		float dx = x2 - x1;
		float dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.5f) {
			return;
		}
		Matrix3x2fStack pose = g.getMatrices();
		pose.pushMatrix();
		pose.translate(x1, y1);
		pose.rotate((float) Math.atan2(dy, dx));
		pose.scale(len / 64f, thickness / 2f);
		g.fill(0, -1, 64, 1, color);
		pose.popMatrix();
	}

	/** Anti-aliased thick line with round caps (a row of overlapping discs); for small icons. */
	public static void capsule(DrawContext g, float x1, float y1, float x2, float y2, float thickness, int color) {
		float r = thickness / 2f;
		float dx = x2 - x1;
		float dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		int steps = Math.max(1, (int) Math.ceil(len / Math.max(0.35f, r * 0.6f)));
		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			disc(g, x1 + dx * t, y1 + dy * t, r, color);
		}
	}

	/** Circle outline ({@code thickness} 1 GUI px). */
	public static void ring(DrawContext g, float cx, float cy, float radius, int color) {
		int r = Math.max(1, Math.round(radius));
		roundedOutline(g, Math.round(cx) - r, Math.round(cy) - r, Math.round(cx) + r, Math.round(cy) + r, r, color);
	}
}
