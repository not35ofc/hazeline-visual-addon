package com.hazeline.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import com.hazeline.module.ModuleManager;
import com.hazeline.module.modules.settings.ClickGuiModule;

/**
 * Smooth floating particles behind the menu window.
 *
 * <p>Each particle is a small accent-tinted dot with a soft glow that drifts slowly upward with a
 * gentle sideways sway, fades in when it appears and fades out before it leaves. Particles close
 * to each other (and to the mouse) are joined by thin lines that fade with distance.
 *
 * <p>Motion uses real elapsed time (not frames), so it is equally smooth at any FPS, and every
 * particle is drawn through a sub-pixel translation of the GUI pose, so nothing snaps to the GUI
 * pixel grid. Lines are thin rotated quads.
 */
final class Particles {
	private static final class Particle {
		float x;
		float y;
		float vx;
		float vy;
		float size;
		float phase;
		float swayAmp;
		float swaySpeed;
		float life;
		float maxLife;
		float brightness;
	}

	private final List<Particle> particles = new ArrayList<>();
	private long lastNanos;
	private int width;
	private int height;

	private static ClickGuiModule settings() {
		return ModuleManager.get().get(ClickGuiModule.class);
	}

	void render(DrawContext g, int screenWidth, int screenHeight, int mouseX, int mouseY, float openProgress) {
		ClickGuiModule s = settings();
		if (s == null || !s.particles.get()) {
			particles.clear();
			lastNanos = 0;
			return;
		}
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		boolean resized = screenWidth != width || screenHeight != height;
		width = screenWidth;
		height = screenHeight;

		int target = MathHelper.clamp(s.particleCount.getInt(), 10, 200);
		if (resized) {
			particles.clear();
		}
		while (particles.size() < target) {
			particles.add(spawn(particles.isEmpty() || resized, true));
		}
		while (particles.size() > target) {
			particles.remove(particles.size() - 1);
		}

		float speed = (float) MathHelper.clamp(s.particleSpeed.get(), 0.2, 3.0);
		boolean follow = s.mouseInteraction.get();
		for (int i = 0; i < particles.size(); i++) {
			Particle p = particles.get(i);
			p.life += dt;
			if (follow) {
				float dx = mouseX - p.x;
				float dy = mouseY - p.y;
				float d2 = dx * dx + dy * dy;
				float reach = 90f;
				if (d2 < reach * reach && d2 > 1f) {
					float d = (float) Math.sqrt(d2);
					float pull = (1f - d / reach) * 18f * dt; // gentle attraction, strongest close by
					p.vx += dx / d * pull;
					p.vy += dy / d * pull;
				}
			}
			// Ease velocities back to their natural drift.
			p.vx += (0f - p.vx) * Math.min(1f, dt * 0.6f);
			p.vy += (-6f - p.vy) * Math.min(1f, dt * 0.15f);
			float sway = (float) Math.sin(p.phase + p.life * p.swaySpeed) * p.swayAmp;
			p.x += (p.vx + sway) * dt * speed;
			p.y += p.vy * dt * speed;
			if (p.life >= p.maxLife || p.y < -20 || p.x < -40 || p.x > width + 40) {
				particles.set(i, spawn(false, false));
			}
		}

		int accent = Colors.withAlpha(Theme.accent(), 0xFF);
		float global = MathHelper.clamp(openProgress, 0f, 1f);
		Matrix3x2fStack pose = g.getMatrices();

		// Lines first, so the dots sit on top of them.
		if (s.particleLines.get()) {
			float maxDist = 70f;
			for (int i = 0; i < particles.size(); i++) {
				Particle a = particles.get(i);
				float aa = alpha(a);
				if (aa <= 0.02f) {
					continue;
				}
				for (int j = i + 1; j < particles.size(); j++) {
					Particle b = particles.get(j);
					float dx = b.x - a.x;
					float dy = b.y - a.y;
					if (Math.abs(dx) > maxDist || Math.abs(dy) > maxDist) {
						continue;
					}
					float d = (float) Math.sqrt(dx * dx + dy * dy);
					if (d >= maxDist) {
						continue;
					}
					float strength = (1f - d / maxDist) * Math.min(aa, alpha(b)) * global;
					line(g, pose, a.x, a.y, b.x, b.y, Colors.withAlpha(accent, Math.round(strength * 70)));
				}
				if (follow) {
					float dx = mouseX - a.x;
					float dy = mouseY - a.y;
					float d = (float) Math.sqrt(dx * dx + dy * dy);
					if (d < 100f) {
						float strength = (1f - d / 100f) * aa * global;
						line(g, pose, a.x, a.y, mouseX, mouseY, Colors.withAlpha(accent, Math.round(strength * 90)));
					}
				}
			}
		}

		for (Particle p : particles) {
			float a = alpha(p) * global;
			if (a <= 0.01f) {
				continue;
			}
			int core = Colors.mix(accent, 0xFFFFFFFF, 0.35f * p.brightness);
			// Soft glow around a small bright core (both anti-aliased, sub-pixel positioned).
			Shapes.glow(g, p.x, p.y, p.size * 5f, Colors.withAlpha(accent, Math.round(a * 70)));
			Shapes.disc(g, p.x, p.y, p.size, Colors.withAlpha(core, Math.round(a * 225)));
		}
	}

	/** Fade in over the first second, out over the last 1.5 s. */
	private static float alpha(Particle p) {
		float in = Math.min(1f, p.life / 1.0f);
		float out = Math.min(1f, (p.maxLife - p.life) / 1.5f);
		return Math.max(0f, Math.min(in, out)) * (0.45f + 0.55f * p.brightness);
	}

	private Particle spawn(boolean anywhere, boolean randomAge) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		Particle p = new Particle();
		p.x = r.nextFloat() * Math.max(1, width);
		p.y = anywhere ? r.nextFloat() * Math.max(1, height) : height + r.nextFloat() * 20f;
		p.vx = (r.nextFloat() - 0.5f) * 4f;
		p.vy = -(3f + r.nextFloat() * 9f);
		p.size = 0.6f + r.nextFloat() * r.nextFloat() * 1.6f;
		p.phase = r.nextFloat() * MathHelper.TAU;
		p.swayAmp = 2f + r.nextFloat() * 6f;
		p.swaySpeed = 0.3f + r.nextFloat() * 0.9f;
		p.maxLife = 8f + r.nextFloat() * 14f;
		p.life = randomAge ? r.nextFloat() * p.maxLife * 0.7f : 0f;
		p.brightness = r.nextFloat();
		return p;
	}

	/** Thin line from (x1, y1) to (x2, y2): a rotated quad half a GUI pixel thick. */
	private static void line(DrawContext g, Matrix3x2fStack pose, float x1, float y1, float x2, float y2, int color) {
		if ((color >>> 24) == 0) {
			return;
		}
		float dx = x2 - x1;
		float dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1f) {
			return;
		}
		pose.pushMatrix();
		pose.translate(x1, y1);
		pose.rotate((float) Math.atan2(dy, dx));
		pose.scale(len / 64f, 0.35f);
		g.fill(0, -1, 64, 1, color);
		pose.popMatrix();
	}
}
