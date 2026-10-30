package com.hazeline.music;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import com.hazeline.Hazeline;

/**
 * Turns the cover image bytes into the two pictures the HUD draws (worker thread, plain pixel maths -
 * no AWT drawing, Minecraft runs with java.awt.headless=true):
 * <ul>
 *   <li>the cover, square (centre crop), {@value #COVER} px, rounded corners baked into the alpha;</li>
 *   <li>the card background: the cover cropped to the card's aspect, heavily blurred and darkened,
 *       rounded corners baked in.</li>
 * </ul>
 */
public final class CoverArt {
	public static final int COVER = 128;
	/** Background size; aspect = card aspect (see MusicHud.CARD_W / CARD_H). */
	public static final int BG_W = 400, BG_H = 136;
	/** Corner radius in background pixels (= 6 card px at BG_W / 200 = 2 px per card px). */
	public static final int BG_RADIUS = 14;
	public static final int COVER_RADIUS = 16;

	public record Cover(int version, int[] cover, int[] background, int accent) {
	}

	private static boolean warned;

	private CoverArt() {
	}

	/** Decodes and prepares the pictures; null if the image cannot be decoded. */
	static Cover process(byte[] bytes, int version) {
		BufferedImage img;
		try {
			img = ImageIO.read(new ByteArrayInputStream(bytes));
		} catch (Throwable t) {
			if (!warned) {
				warned = true;
				Hazeline.LOGGER.warn("Music HUD: could not decode the cover image: {}", t.toString());
			}
			return null;
		}
		if (img == null || img.getWidth() < 2 || img.getHeight() < 2) {
			MediaService.debug("cover image format not supported (" + bytes.length + " bytes)");
			return null;
		}
		int w = img.getWidth();
		int h = img.getHeight();
		int[] src = img.getRGB(0, 0, w, h, null, 0, w);

		// Cover: centre square, area-averaged down to COVER x COVER.
		int side = Math.min(w, h);
		int[] cover = resample(src, w, (w - side) / 2, (h - side) / 2, side, side, COVER, COVER);
		roundCorners(cover, COVER, COVER, COVER_RADIUS);

		// Background: crop to the card aspect, shrink (cheap blur), box blur 3x, darken, round corners.
		float aspect = BG_W / (float) BG_H;
		int cw = w, ch = Math.round(w / aspect);
		if (ch > h) {
			ch = h;
			cw = Math.round(h * aspect);
		}
		int sw = BG_W / 8, sh = BG_H / 8;
		int[] small = resample(src, w, (w - cw) / 2, (h - ch) / 2, cw, ch, sw, sh);
		for (int i = 0; i < 3; i++) {
			small = boxBlur(small, sw, sh, 2);
		}
		int[] bg = upscale(small, sw, sh, BG_W, BG_H);
		for (int i = 0; i < bg.length; i++) {
			int c = bg[i];
			int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
			// darken to ~45 % and slightly desaturate so white text always reads well
			int l = (r * 3 + g * 6 + b) / 10;
			r = (int) ((r * 0.8f + l * 0.2f) * 0.45f);
			g = (int) ((g * 0.8f + l * 0.2f) * 0.45f);
			b = (int) ((b * 0.8f + l * 0.2f) * 0.45f);
			bg[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
		}
		roundCorners(bg, BG_W, BG_H, BG_RADIUS);
		return new Cover(version, cover, bg, average(small));
	}

	/** Area-average resample of the rectangle (sx, sy, sw, sh) of {@code src} to dw x dh. */
	private static int[] resample(int[] src, int stride, int sx, int sy, int sw, int sh, int dw, int dh) {
		int[] out = new int[dw * dh];
		for (int y = 0; y < dh; y++) {
			int y0 = sy + y * sh / dh, y1 = Math.max(y0 + 1, sy + (y + 1) * sh / dh);
			for (int x = 0; x < dw; x++) {
				int x0 = sx + x * sw / dw, x1 = Math.max(x0 + 1, sx + (x + 1) * sw / dw);
				long r = 0, g = 0, b = 0, n = 0;
				for (int yy = y0; yy < y1; yy++) {
					for (int xx = x0; xx < x1; xx++) {
						int c = src[yy * stride + xx];
						int a = (c >>> 24);
						// transparent pixels count as black
						r += ((c >> 16) & 255) * a / 255;
						g += ((c >> 8) & 255) * a / 255;
						b += (c & 255) * a / 255;
						n++;
					}
				}
				out[y * dw + x] = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
			}
		}
		return out;
	}

	private static int[] boxBlur(int[] in, int w, int h, int radius) {
		int[] tmp = new int[in.length];
		int[] out = new int[in.length];
		pass(in, tmp, w, h, radius, true);
		pass(tmp, out, w, h, radius, false);
		return out;
	}

	private static void pass(int[] in, int[] out, int w, int h, int radius, boolean horizontal) {
		int len = horizontal ? w : h;
		int lines = horizontal ? h : w;
		for (int line = 0; line < lines; line++) {
			for (int i = 0; i < len; i++) {
				int r = 0, g = 0, b = 0, n = 0;
				for (int k = -radius; k <= radius; k++) {
					int j = Math.max(0, Math.min(len - 1, i + k)); // clamp edges
					int c = horizontal ? in[line * w + j] : in[j * w + line];
					r += (c >> 16) & 255;
					g += (c >> 8) & 255;
					b += c & 255;
					n++;
				}
				int idx = horizontal ? line * w + i : i * w + line;
				out[idx] = 0xFF000000 | (r / n) << 16 | (g / n) << 8 | (b / n);
			}
		}
	}

	/** Bilinear upscale (smooth, the source is already blurred). */
	private static int[] upscale(int[] in, int w, int h, int dw, int dh) {
		int[] out = new int[dw * dh];
		for (int y = 0; y < dh; y++) {
			float fy = Math.max(0f, (y + 0.5f) * h / dh - 0.5f);
			int y0 = Math.min(h - 1, (int) fy), y1 = Math.min(h - 1, y0 + 1);
			float ty = fy - y0;
			for (int x = 0; x < dw; x++) {
				float fx = Math.max(0f, (x + 0.5f) * w / dw - 0.5f);
				int x0 = Math.min(w - 1, (int) fx), x1 = Math.min(w - 1, x0 + 1);
				float tx = fx - x0;
				int c00 = in[y0 * w + x0], c10 = in[y0 * w + x1], c01 = in[y1 * w + x0], c11 = in[y1 * w + x1];
				int rgb = 0xFF000000;
				for (int shift = 0; shift <= 16; shift += 8) {
					float top = ((c00 >> shift) & 255) * (1 - tx) + ((c10 >> shift) & 255) * tx;
					float bot = ((c01 >> shift) & 255) * (1 - tx) + ((c11 >> shift) & 255) * tx;
					rgb |= Math.round(top * (1 - ty) + bot * ty) << shift;
				}
				out[y * dw + x] = rgb;
			}
		}
		return out;
	}

	/** Anti-aliased rounded-corner alpha mask. */
	static void roundCorners(int[] px, int w, int h, int r) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float cx = x < r ? r : x >= w - r ? w - r : -1;
				float cy = y < r ? r : y >= h - r ? h - r : -1;
				if (cx < 0 || cy < 0) {
					continue;
				}
				float d = (float) Math.hypot(x + 0.5f - cx, y + 0.5f - cy);
				float a = Math.max(0f, Math.min(1f, r - d + 0.5f));
				int i = y * w + x;
				int alpha = Math.round(((px[i] >>> 24) & 255) * a);
				px[i] = (alpha << 24) | (px[i] & 0xFFFFFF);
			}
		}
	}

	private static int average(int[] px) {
		long r = 0, g = 0, b = 0;
		for (int c : px) {
			r += (c >> 16) & 255;
			g += (c >> 8) & 255;
			b += c & 255;
		}
		int n = Math.max(1, px.length);
		return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
	}
}
