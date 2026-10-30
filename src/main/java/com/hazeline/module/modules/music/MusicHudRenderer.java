package com.hazeline.module.modules.music;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3x2fStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import com.hazeline.Hazeline;
import com.hazeline.appearance.FontManager;
import com.hazeline.gui.Colors;
import com.hazeline.gui.Icons;
import com.hazeline.gui.Shapes;
import com.hazeline.gui.Theme;
import com.hazeline.gui.text.TextRenderer;
import com.hazeline.gui.text.TextRenderer.TextStyle;
import com.hazeline.music.CoverArt;
import com.hazeline.music.MediaService;
import com.hazeline.music.MediaService.State;

/**
 * Draws the Music HUD card and handles its mouse input. Layout in card pixels (200 x 68 at Size 100 %):
 * <pre>
 *  +-----------------------------------------------+
 *  | [cover]  Title (bold)              |&lt;  (||)  &gt;| |
 *  | [ 40  ]  Artist                                |
 *  | [ px  ]  Album                                 |
 *  |          ===========o-----------------------   |
 *  | spk ---  1:23                            3:45  |
 *  +-----------------------------------------------+
 * </pre>
 * Shapes are drawn with the pose scaled to the card; text is drawn unscaled at the scaled font size,
 * so it stays sharp at any Size.
 */
final class MusicHudRenderer {
	static final int CARD_W = 200, CARD_H = 68;
	private static final int RADIUS = 6;
	private static final int MARGIN = 6;
	private static final int COVER_X = 8, COVER_Y = 8, COVER_S = 40;
	private static final int TEXT_X = 56, RIGHT = 192;
	private static final float BAR_Y = 44.5f;
	private static final int BTN_Y = 12; // centre line of the buttons
	private static final float[] BTN_X = {157f, 172f, 187f}; // prev, play/pause, next centres
	private static final float VOL_X1 = 20f, VOL_X2 = 48f, VOL_Y = 60f;

	private static final Identifier COVER_ID = Hazeline.id("music_hud/cover");
	private static final Identifier BG_ID = Hazeline.id("music_hud/background");
	private static int uploadedVersion = -1;
	private static boolean texturesFailed;

	private enum Drag {
		NONE, CARD, SEEK, VOLUME
	}

	private static Drag drag = Drag.NONE;
	private static double dragOffX, dragOffY;
	private static float seekPreview = -1f;
	private static final Map<String, Float> HOVER = new HashMap<>();
	private static long lastFrame;
	private static float dt;

	private MusicHudRenderer() {
	}

	// ======================================================================== geometry

	/** Top-left of the card in GUI pixels. */
	private static float[] origin(MusicHud m, float s) {
		Window win = Window.of();
		float w = CARD_W * s, h = CARD_H * s;
		float[] p = m.getPosition();
		float x = MARGIN + p[0] * Math.max(0f, win.w - w - 2 * MARGIN);
		float y = MARGIN + p[1] * Math.max(0f, win.h - h - 2 * MARGIN);
		return new float[] {x, y};
	}

	private record Window(int w, int h) {
		static Window of() {
			var win = MinecraftClient.getInstance().getWindow();
			return new Window(win.getScaledWidth(), win.getScaledHeight());
		}
	}

	private static boolean in(double cx, double cy, float x1, float y1, float x2, float y2) {
		return cx >= x1 && cx < x2 && cy >= y1 && cy < y2;
	}

	// ======================================================================== render

	static void render(DrawContext g, MusicHud m, double mouseX, double mouseY, boolean interactive) {
		State st = MediaService.get().state();
		boolean inGui = MusicHud.clickGuiOpen(MinecraftClient.getInstance());
		if (!st.active() && !inGui) {
			return; // nothing playing: no popup (in the click GUI a placeholder shows, so it can be moved)
		}
		long nowNs = System.nanoTime();
		dt = lastFrame == 0 ? 0f : Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
		lastFrame = nowNs;
		try {
			TextRenderer.beginFrame();
			draw(g, m, st, mouseX, mouseY, interactive, inGui);
		} catch (RuntimeException | LinkageError e) {
			Hazeline.LOGGER.error("Music HUD: drawing failed, module turned off", e);
			m.setEnabled(false);
		}
	}

	private static float hover(String key, boolean on) {
		float v = HOVER.getOrDefault(key, 0f);
		float k = 1f - (float) Math.exp(-14f * dt);
		v += ((on ? 1f : 0f) - v) * k;
		HOVER.put(key, v);
		return v;
	}

	private static void draw(DrawContext g, MusicHud m, State st, double mouseX, double mouseY, boolean interactive, boolean inGui) {
		float s = m.scale();
		float[] o = origin(m, s);
		float ox = o[0], oy = o[1];
		double cx = (mouseX - ox) / s, cy = (mouseY - oy) / s; // mouse in card pixels
		boolean overCard = interactive && in(cx, cy, 0, 0, CARD_W, CARD_H);
		CoverArt.Cover cover = st.active() ? MediaService.get().cover() : null;
		boolean textures = cover != null && upload(cover);
		float alpha = (float) (m.opacity.get() / 100.0);

		Matrix3x2fStack pose = g.getMatrices();
		pose.pushMatrix();
		pose.translate(ox, oy);
		pose.scale(s, s);
		try {
			// background
			if (textures) {
				if (alpha < 1f) {
					Shapes.roundedRect(g, 0, 0, CARD_W, CARD_H, RADIUS, 0xE0101014, 0xE0101014, true, true);
				}
				blit(g, BG_ID, CoverArt.BG_W, CoverArt.BG_H, 0, 0, CARD_W, CARD_H, Colors.withAlpha(0xFFFFFFFF, Math.round(255 * alpha)));
			} else {
				int bg = Colors.withAlpha(0xFF15151B, Math.round(235 * alpha));
				Shapes.roundedRect(g, 0, 0, CARD_W, CARD_H, RADIUS, bg, bg, true, true);
			}
			int border = inGui && overCard ? Colors.withAlpha(Theme.accent(), 200) : 0x30FFFFFF;
			Shapes.roundedOutline(g, 0, 0, CARD_W, CARD_H, RADIUS, border);

			// cover
			if (textures) {
				blit(g, COVER_ID, CoverArt.COVER, CoverArt.COVER, COVER_X, COVER_Y, COVER_S, COVER_S, 0xFFFFFFFF);
			} else {
				Shapes.roundedRect(g, COVER_X, COVER_Y, COVER_X + COVER_S, COVER_Y + COVER_S, 5, 0xFF2A2A33, 0xFF1E1E25, true, true);
				noteIcon(g, COVER_X + COVER_S / 2f, COVER_Y + COVER_S / 2f, Colors.withAlpha(Theme.accent(), 230));
			}

			if (st.active()) {
				drawButtons(g, st, cx, cy, interactive);
				drawProgress(g, st, cx, cy, interactive);
				drawVolume(g, st, cx, cy, interactive);
			}
		} finally {
			pose.popMatrix();
		}

		// text (unscaled pose, scaled font size -> sharp)
		if (st.active()) {
			String title = st.title().isEmpty() ? "Unknown title" : st.title();
			text(g, title, ox, oy, s, TEXT_X, 7.5f, 9.5f, true, 0xFFFFFFFF, BTN_X[0] - 9 - TEXT_X);
			String artist = st.artist().isEmpty() ? appName(st.app()) : st.artist();
			text(g, artist, ox, oy, s, TEXT_X, 19.5f, 8f, false, 0xD8FFFFFF, RIGHT - TEXT_X);
			if (m.showAlbum.get() && !st.album().isEmpty()) {
				text(g, st.album(), ox, oy, s, TEXT_X, 29.5f, 7f, false, 0x99FFFFFF, RIGHT - TEXT_X);
			}
			if (m.showTimes.get()) {
				long dur = st.durationMs();
				long pos = seekPreview >= 0f && dur > 0 ? (long) (seekPreview * dur) : st.positionMs(System.currentTimeMillis());
				text(g, time(pos), ox, oy, s, TEXT_X, 50f, 6.5f, false, 0xB0FFFFFF, 60);
				if (dur > 0) {
					String d = time(dur);
					float w = TextRenderer.getWidth(d, style(s, 6.5f, false)) / s;
					text(g, d, ox, oy, s, RIGHT - w, 50f, 6.5f, false, 0xB0FFFFFF, 60);
				}
			}
		} else {
			text(g, "Music HUD", ox, oy, s, TEXT_X, 12f, 9.5f, true, 0xFFFFFFFF, RIGHT - TEXT_X);
			text(g, "Nothing playing - drag to move", ox, oy, s, TEXT_X, 25f, 7.5f, false, 0xB0FFFFFF, RIGHT - TEXT_X);
			String status = MediaService.get().status();
			text(g, status, ox, oy, s, TEXT_X, 36f, 6.5f, false, 0x80FFFFFF, RIGHT - TEXT_X);
		}
	}

	private static String appName(String app) {
		String a = app.toLowerCase(java.util.Locale.ROOT);
		if (a.contains("chrome")) {
			return "Google Chrome";
		}
		if (a.contains("spotify")) {
			return "Spotify";
		}
		if (a.contains("msedge")) {
			return "Microsoft Edge";
		}
		return app;
	}

	static String time(long ms) {
		long sec = Math.max(0, ms / 1000);
		long h = sec / 3600, mnt = (sec / 60) % 60, sc = sec % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, mnt, sc) : String.format("%d:%02d", mnt, sc);
	}

	private static TextStyle style(float s, float px, boolean bold) {
		double relScale = px * s / (9.0 * Theme.scale());
		return new TextStyle(FontManager.currentId(), relScale, bold, true);
	}

	/** Text at card position (x, y) = its top, cut with "..." to {@code maxW} card px. */
	private static void text(DrawContext g, String text, float ox, float oy, float s, float x, float y, float px, boolean bold,
			int color, float maxW) {
		TextStyle style = style(s, px, bold);
		float max = maxW * s;
		String t = text;
		if (TextRenderer.getWidth(t, style) > max) {
			while (!t.isEmpty() && TextRenderer.getWidth(t + "...", style) > max) {
				t = t.substring(0, t.length() - 1);
			}
			t = t.stripTrailing() + "...";
		}
		TextRenderer.drawString(g, t, ox + x * s, oy + y * s, color, style);
	}

	// ------------------------------------------------------------------ parts (card pixels, pose scaled)

	private static void drawButtons(DrawContext g, State st, double cx, double cy, boolean interactive) {
		for (int i = 0; i < 3; i++) {
			float bx = BTN_X[i];
			boolean enabled = i == 0 ? st.canPrev() : i == 2 ? st.canNext() : (st.canToggle() || st.canPlay() || st.canPause());
			boolean hot = interactive && enabled && in(cx, cy, bx - 7, BTN_Y - 7, bx + 7, BTN_Y + 7);
			float h = hover("btn" + i, hot);
			if (h > 0.01f) {
				Shapes.disc(g, bx, BTN_Y, 7f, Colors.withAlpha(0xFFFFFFFF, Math.round(40 * h)));
			}
			int c = enabled ? Colors.lerp(0xFFE6E6E6, 0xFFFFFFFF, h) : 0xFF6E6E76;
			switch (i) {
				case 0 -> { // |<
					Shapes.line(g, bx - 3.6f, BTN_Y - 3.5f, bx - 3.6f, BTN_Y + 3.5f, 1.4f, c);
					triangle(g, bx + 3.4f, bx - 2.6f, BTN_Y, 3.6f, c);
				}
				case 2 -> { // >|
					triangle(g, bx - 3.4f, bx + 2.6f, BTN_Y, 3.6f, c);
					Shapes.line(g, bx + 3.6f, BTN_Y - 3.5f, bx + 3.6f, BTN_Y + 3.5f, 1.4f, c);
				}
				default -> { // play / pause in a ring
					Shapes.ring(g, bx, BTN_Y, 6.5f, Colors.withAlpha(c, 220));
					if (st.playing()) {
						Shapes.line(g, bx - 1.6f, BTN_Y - 2.8f, bx - 1.6f, BTN_Y + 2.8f, 1.5f, c);
						Shapes.line(g, bx + 1.6f, BTN_Y - 2.8f, bx + 1.6f, BTN_Y + 2.8f, 1.5f, c);
					} else {
						triangle(g, bx - 1.9f, bx + 3.1f, BTN_Y, 3.1f, c);
					}
				}
			}
		}
	}

	/**
	 * Filled triangle with its flat side at {@code baseX} (height 2 x {@code half}) and its tip at
	 * {@code tipX}, made of thin vertical strokes (opaque colours only, strokes overlap slightly).
	 */
	private static void triangle(DrawContext g, float baseX, float tipX, float midY, float half, int color) {
		float len = Math.abs(tipX - baseX);
		float dir = Math.signum(tipX - baseX);
		int steps = Math.max(2, Math.round(len / 0.3f));
		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			float x = baseX + dir * len * t;
			float hh = half * (1f - t);
			if (hh < 0.15f) {
				continue;
			}
			Shapes.line(g, x, midY - hh, x, midY + hh, 0.45f, color);
		}
	}

	private static void noteIcon(DrawContext g, float x, float y, int color) {
		Shapes.disc(g, x - 4.5f, y + 5f, 3.2f, color);
		Shapes.disc(g, x + 5f, y + 3f, 3.2f, color);
		Shapes.line(g, x - 1.8f, y + 5f, x - 1.8f, y - 7f, 1.3f, color);
		Shapes.line(g, x + 7.7f, y + 3f, x + 7.7f, y - 9f, 1.3f, color);
		Shapes.line(g, x - 1.8f, y - 7f, x + 7.7f, y - 9f, 2.2f, color);
	}

	private static void drawProgress(DrawContext g, State st, double cx, double cy, boolean interactive) {
		long dur = st.durationMs();
		float frac = dur > 0 ? st.positionMs(System.currentTimeMillis()) / (float) dur : 0f;
		if (seekPreview >= 0f) {
			frac = seekPreview;
		}
		frac = Math.max(0f, Math.min(1f, frac));
		boolean canSeek = st.canSeek() && dur > 0;
		boolean hot = interactive && canSeek && (drag == Drag.SEEK || in(cx, cy, TEXT_X - 2, BAR_Y - 4, RIGHT + 2, BAR_Y + 4));
		float h = hover("bar", hot);
		float thick = 2.4f + h * 0.8f;
		Shapes.capsule(g, TEXT_X, BAR_Y, RIGHT, BAR_Y, thick, 0x45FFFFFF);
		float fx = TEXT_X + (RIGHT - TEXT_X) * frac;
		if (fx > TEXT_X + 0.5f) {
			Shapes.capsule(g, TEXT_X, BAR_Y, fx, BAR_Y, thick, Theme.accent());
		}
		if (h > 0.01f) {
			Shapes.disc(g, fx, BAR_Y, 2.6f * h + 0.5f, 0xFFFFFFFF);
		}
	}

	private static void drawVolume(DrawContext g, State st, double cx, double cy, boolean interactive) {
		float v = st.volume();
		boolean has = v >= 0f;
		boolean hot = interactive && has && (drag == Drag.VOLUME || in(cx, cy, VOL_X1 - 3, VOL_Y - 4, VOL_X2 + 3, VOL_Y + 4));
		float h = hover("vol", hot);
		int iconColor = has ? 0xD8FFFFFF : 0x60FFFFFF;
		Icons.draw(g, Icons.Icon.SPEAKER, COVER_X - 1f, VOL_Y - 5f, 10f, iconColor);
		float thick = 2f + h * 0.6f;
		Shapes.capsule(g, VOL_X1, VOL_Y, VOL_X2, VOL_Y, thick, has ? 0x45FFFFFF : 0x22FFFFFF);
		if (has) {
			float fx = VOL_X1 + (VOL_X2 - VOL_X1) * Math.max(0f, Math.min(1f, v));
			if (fx > VOL_X1 + 0.4f) {
				Shapes.capsule(g, VOL_X1, VOL_Y, fx, VOL_Y, thick, 0xFFFFFFFF);
			}
			if (h > 0.01f) {
				Shapes.disc(g, fx, VOL_Y, 2.2f * h + 0.4f, Theme.accent());
			}
		}
	}

	// ------------------------------------------------------------------ textures

	private static final class SmoothTexture extends NativeImageBackedTexture {
		SmoothTexture(String label, NativeImage image) {
			super(() -> label, image);
			this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
		}
	}

	/** Uploads a new cover when its version changed. False if textures cannot be used. */
	private static boolean upload(CoverArt.Cover cover) {
		if (texturesFailed) {
			return false;
		}
		if (cover.version() == uploadedVersion) {
			return true;
		}
		try {
			var tm = MinecraftClient.getInstance().getTextureManager();
			tm.destroyTexture(COVER_ID);
			tm.destroyTexture(BG_ID);
			tm.registerTexture(COVER_ID, new SmoothTexture("hazeline music cover", image(cover.cover(), CoverArt.COVER, CoverArt.COVER)));
			tm.registerTexture(BG_ID, new SmoothTexture("hazeline music background", image(cover.background(), CoverArt.BG_W, CoverArt.BG_H)));
			uploadedVersion = cover.version();
			return true;
		} catch (RuntimeException e) {
			texturesFailed = true;
			Hazeline.LOGGER.error("Music HUD: cover upload failed, covers disabled for this session", e);
			return false;
		}
	}

	private static NativeImage image(int[] argb, int w, int h) {
		NativeImage img = new NativeImage(w, h, false);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setColorArgb(x, y, argb[y * w + x]);
			}
		}
		return img;
	}

	/** Draws a whole texture of size tw x th into the card rectangle (x, y, w, h). */
	private static void blit(DrawContext g, Identifier id, int tw, int th, float x, float y, float w, float h, int color) {
		Matrix3x2fStack pose = g.getMatrices();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(w / tw, h / th);
		g.drawTexture(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, tw, th, tw, th, color);
		pose.popMatrix();
	}

	static void releaseTextures() {
		if (uploadedVersion < 0) {
			return;
		}
		uploadedVersion = -1;
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(() -> {
			try {
				mc.getTextureManager().destroyTexture(COVER_ID);
				mc.getTextureManager().destroyTexture(BG_ID);
			} catch (RuntimeException ignored) {
			}
		});
	}

	// ======================================================================== input

	/**
	 * A mouse press at GUI position (mx, my). {@code canDrag}: in the click GUI the card can be moved
	 * by grabbing it anywhere but a control. Returns true if the popup took the click.
	 */
	static boolean mouseClicked(MusicHud m, double mx, double my, int button, boolean canDrag) {
		State st = MediaService.get().state();
		boolean inGui = MusicHud.clickGuiOpen(MinecraftClient.getInstance());
		if (!st.active() && !inGui) {
			return false;
		}
		float s = m.scale();
		float[] o = origin(m, s);
		double cx = (mx - o[0]) / s, cy = (my - o[1]) / s;
		if (!in(cx, cy, 0, 0, CARD_W, CARD_H)) {
			return false;
		}
		if (button != 0) {
			return true; // swallow other buttons on the card
		}
		MediaService media = MediaService.get();
		if (st.active()) {
			for (int i = 0; i < 3; i++) {
				if (in(cx, cy, BTN_X[i] - 7, BTN_Y - 7, BTN_X[i] + 7, BTN_Y + 7)) {
					switch (i) {
						case 0 -> media.previous();
						case 1 -> media.togglePlay();
						default -> media.next();
					}
					return true;
				}
			}
			if (st.canSeek() && st.durationMs() > 0 && in(cx, cy, TEXT_X - 2, BAR_Y - 4, RIGHT + 2, BAR_Y + 4)) {
				drag = Drag.SEEK;
				seekPreview = barFraction(cx);
				return true;
			}
			if (st.volume() >= 0f && in(cx, cy, VOL_X1 - 3, VOL_Y - 4, VOL_X2 + 3, VOL_Y + 4)) {
				drag = Drag.VOLUME;
				media.setVolume(volFraction(cx));
				return true;
			}
		}
		if (canDrag) {
			drag = Drag.CARD;
			dragOffX = mx - o[0];
			dragOffY = my - o[1];
		}
		return true;
	}

	static boolean mouseDragged(MusicHud m, double mx, double my) {
		if (drag == Drag.NONE) {
			return false;
		}
		float s = m.scale();
		float[] o = origin(m, s);
		double cx = (mx - o[0]) / s;
		switch (drag) {
			case SEEK -> seekPreview = barFraction(cx);
			case VOLUME -> MediaService.get().setVolume(volFraction(cx));
			case CARD -> {
				Window win = Window.of();
				float w = CARD_W * s, h = CARD_H * s;
				float freeX = Math.max(1f, win.w - w - 2 * MARGIN), freeY = Math.max(1f, win.h - h - 2 * MARGIN);
				m.setPosition((float) ((mx - dragOffX - MARGIN) / freeX), (float) ((my - dragOffY - MARGIN) / freeY));
			}
			default -> {
			}
		}
		return true;
	}

	static boolean mouseReleased(MusicHud m, double mx, double my) {
		if (drag == Drag.NONE) {
			return false;
		}
		if (drag == Drag.SEEK && seekPreview >= 0f) {
			State st = MediaService.get().state();
			if (st.durationMs() > 0) {
				MediaService.get().seek((long) (seekPreview * st.durationMs()));
			}
		}
		drag = Drag.NONE;
		seekPreview = -1f;
		return true;
	}

	private static float barFraction(double cx) {
		return (float) Math.max(0, Math.min(1, (cx - TEXT_X) / (RIGHT - TEXT_X)));
	}

	private static float volFraction(double cx) {
		return Math.round(Math.max(0, Math.min(1, (cx - VOL_X1) / (VOL_X2 - VOL_X1))) * 100f) / 100f;
	}
}
