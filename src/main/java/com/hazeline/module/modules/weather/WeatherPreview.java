package com.hazeline.module.modules.weather;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import com.hazeline.gui.Shapes;

/**
 * The small animated sky shown in the Weather page of the click GUI: it draws the current Weather
 * settings (kind, intensity, wind, colours, darkness) plus whichever of Glossy Rain, Mist and Smooth
 * Thunder are switched on. A simplified picture drawn with 2D shapes, redrawn every frame.
 */
public final class WeatherPreview {
	public static final int HEIGHT = 96;

	private WeatherPreview() {
	}

	public static void draw(DrawContext g, int x, int y, int w, int h) {
		Weather wx = Weather.get();
		if (wx == null) {
			return;
		}
		double t = System.nanoTime() / 1e9;
		Weather.Kind kind = wx.kind.get();
		boolean snow = kind == Weather.Kind.SNOW;
		boolean clear = kind == Weather.Kind.CLEAR;
		boolean storm = kind == Weather.Kind.THUNDERSTORM;
		float amount = clear ? 0f : wx.intensity.getFloat() / 100f;
		if (kind == Weather.Kind.FOLLOW) {
			amount = 0.5f; // the server decides in game; show a middle amount
		}
		float dark = amount * wx.skyDarkness.getFloat() / 100f;
		float haze = Math.max(amount * wx.fogTint.getFloat() / 100f, wx.haze.getFloat() / 100f);

		// Sky: day blue towards grey with the darkness, then the fog colour near the horizon.
		int skyTop = WeatherMath.mix(0xFF6E9BD6, 0xFF2C3440, MathHelper.clamp(dark, 0f, 1f));
		int skyLow = WeatherMath.mix(0xFFB6D2F0, 0xFF58626F, MathHelper.clamp(dark, 0f, 1f));
		skyLow = WeatherMath.mix(skyLow, wx.fogColor.get() | 0xFF000000, MathHelper.clamp(haze * 0.7f, 0f, 1f));
		g.fillGradient(x, y, x + w, y + h, skyTop, skyLow);

		int groundTop = y + (int) (h * 0.78f);
		boolean mist = Mist.get() != null && Mist.get().isEnabled();
		if (mist) {
			float d = Mist.get().density.getFloat() / 100f;
			g.fillGradient(x, y + (int) (h * 0.42f), x + w, groundTop + 6, 0x00FFFFFF,
					WeatherMath.argb(Math.round(d * 0.8f * 255f), Mist.get().color.get() & 0xFFFFFF));
		}
		int ground = snow ? 0xFFDCE6EE : 0xFF3F4F3A;
		g.fill(x, groundTop, x + w, y + h, ground);

		boolean glossy = GlossyRain.get() != null && GlossyRain.get().isEnabled();
		if (glossy && !snow && !clear) {
			float gl = GlossyRain.get().glossiness.getFloat() / 100f;
			g.fillGradient(x, groundTop, x + w, y + h, WeatherMath.argb(Math.round(gl * 150f), GlossyRain.get().sheenColor.get() & 0xFFFFFF), 0x00FFFFFF);
			for (int i = 0; i < 4; i++) {
				double ph = (t * 0.7 + i * 0.27) % 1.0;
				float rx = x + w * (0.15f + 0.22f * i) + (float) Math.sin(i * 3.1) * 8f;
				float ry = groundTop + (y + h - groundTop) * (0.3f + 0.4f * ((i * 37) % 10) / 10f);
				float rad = (float) (4 + ph * 14);
				Shapes.ring(g, rx, ry, rad, WeatherMath.argb((int) ((1 - ph) * 150), 0xFFFFFF));
			}
		}

		// Falling drops / flakes.
		if (!clear) {
			int count = Math.round(8 + 46 * amount * wx.density.getFloat() / 100f);
			float tilt = wx.windStrength.getFloat() / 100f * 0.7f * (wx.windX() >= 0 ? 1f : -1f);
			int rgb = (snow ? wx.snowColor.get() : wx.rainColor.get()) & 0xFFFFFF;
			float op = (snow ? wx.snowOpacity.getFloat() : wx.rainOpacity.getFloat()) / 100f;
			for (int i = 0; i < count; i++) {
				float sj = 0.8f + 0.4f * WeatherMath.rand(i, 4);
				double speed = (snow ? 0.18 : 1.1) * wx.speed.getFloat() * sj;
				double fy = WeatherMath.mod(WeatherMath.rand(i, 3) + t * speed, 1.0);
				double fx = WeatherMath.mod(WeatherMath.rand(i, 1) + fy * tilt * 0.35, 1.0);
				float px = x + (float) fx * w;
				float py = y + (float) fy * (groundTop - y);
				if (snow) {
					Shapes.disc(g, px + (float) Math.sin(t * 1.5 + i) * 3f, py, 1.4f * wx.snowSize.getFloat(),
							WeatherMath.argb(Math.round(op * 230f), rgb));
				} else {
					float len = 6f * wx.rainLength.getFloat();
					Shapes.line(g, px - tilt * len, py - len, px, py, 1.0f * wx.rainSize.getFloat(), WeatherMath.argb(Math.round(op * 200f), rgb));
				}
			}
		}

		// Lightning.
		SmoothThunder th = SmoothThunder.get();
		if (th != null && th.isEnabled() && (storm || kind == Weather.Kind.FOLLOW)) {
			double cycle = t % 6.0;
			if (cycle < 1.3) {
				float flashA = (float) Math.exp(-cycle / 0.35) * th.flashStrength.getFloat() / 100f;
				g.fillGradient(x, y, x + w, y + h, WeatherMath.argb(Math.round(flashA * 0.9f * 255f), th.flashColor.get() & 0xFFFFFF),
						WeatherMath.argb(Math.round(flashA * 0.4f * 255f), th.flashColor.get() & 0xFFFFFF));
				if (cycle < 0.28) {
					float bx = x + w * 0.62f;
					float by = y;
					int c = WeatherMath.argb(Math.round(Math.min(1f, th.boltBrightness.getFloat() / 100f) * 255f), th.boltColor.get() & 0xFFFFFF);
					for (int s = 0; s < 7; s++) {
						float nx = bx + (WeatherMath.rand(s, 91) - 0.5f) * 16f;
						float ny = by + (groundTop - y) / 7f;
						Shapes.line(g, bx, by, nx, ny, 1.4f * th.boltThickness.getFloat(), c);
						bx = nx;
						by = ny;
					}
				}
			}
		}
	}
}
