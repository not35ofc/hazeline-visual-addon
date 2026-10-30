package com.hazeline.module.modules.weather;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.attribute.EnvironmentAttributes;

import com.hazeline.Hazeline;

/**
 * The state behind the Weather, Glossy Rain, Mist and Smooth Thunder modules: what weather is being
 * shown right now, smoothed over time. Everything here is visual and client-side; the world's real
 * weather (and everything gameplay reads from it) is never changed.
 *
 * <p>Takeover: while the Weather module is on, Hazeline's own rain/snow replaces vanilla's. When it is
 * switched off the shown weather first fades to the server's, and only then does vanilla's drawing come
 * back, so there is no pop. Safe mode: if anything in the weather code throws, {@link #fail} logs it
 * once, hands all weather drawing back to vanilla and keeps it there for the rest of the session.
 */
public final class WeatherController {
	/** Which kind of precipitation is shown. */
	public enum Mode {
		/** Rain everywhere. */
		RAIN,
		/** Snow everywhere. */
		SNOW,
		/** Rain or snow by the biome of each column (vanilla's rule). */
		BIOME
	}

	/** Eases a value to a new target over a number of ticks (ease in-out), so fades are smooth. */
	private static final class Smoother {
		float from, to, cur, p = 1f;

		void target(float t, float ticks) {
			if (Math.abs(t - to) > 0.004f) {
				from = cur;
				to = t;
				p = 0f;
			}
			step(ticks);
		}

		void step(float ticks) {
			if (p < 1f) {
				p = Math.min(1f, p + 1f / Math.max(1f, ticks));
				cur = MathHelper.lerp(WeatherMath.smooth(p), from, to);
			} else {
				cur = to;
			}
		}

		void set(float v) {
			from = to = cur = v;
			p = 1f;
		}
	}

	private static final Smoother PRECIP = new Smoother();
	private static final Smoother THUNDER = new Smoother();
	private static float prevPrecip, prevThunder;
	private static boolean takeover;
	private static Mode mode = Mode.BIOME;

	private static float wet, prevWet;
	private static float mistFade, prevMistFade;
	private static float sheltered;
	private static ClientWorld lastWorld;
	private static volatile boolean failed;
	private static String failure = "";
	/** Last fog colour vanilla used this frame (RGB 0..1), set by the fog hook; drives "match sky". */
	private static float fogR = 0.66f, fogG = 0.75f, fogB = 0.87f;

	private WeatherController() {
	}

	public static void registerEvents() {
		ClientTickEvents.END_CLIENT_TICK.register(WeatherController::tick);
	}

	// ------------------------------------------------------------------ safe mode

	/** Logs the problem once, switches all weather drawing back to vanilla for this session. */
	public static void fail(String where, Throwable t) {
		if (!failed) {
			failed = true;
			failure = where + ": " + t.getClass().getSimpleName();
			Hazeline.LOGGER.error("Weather: {} failed, falling back to vanilla weather for this session", where, t);
		}
		takeover = false;
	}

	public static boolean isFailed() {
		return failed;
	}

	public static String failure() {
		return failure;
	}

	// ------------------------------------------------------------------ per tick

	private static void tick(MinecraftClient mc) {
		ClientWorld w = mc.world;
		if (w != lastWorld) {
			lastWorld = w;
			reset();
		}
		prevPrecip = PRECIP.cur;
		prevThunder = THUNDER.cur;
		prevWet = wet;
		prevMistFade = mistFade;
		if (failed || w == null || w.getRegistryKey() != World.OVERWORLD) {
			takeover = false;
			wet = 0f;
			mistFade = 0f;
			WeatherAmbience.tick(mc, null);
			ThunderStrikes.tick(mc, null);
			return;
		}
		try {
			Columns.frame(w);
			update(mc, w);
			WeatherAmbience.tick(mc, w);
			ThunderStrikes.tick(mc, w);
		} catch (Throwable t) {
			fail("tick", t);
		}
	}

	private static void reset() {
		PRECIP.set(0f);
		THUNDER.set(0f);
		prevPrecip = prevThunder = 0f;
		takeover = false;
		wet = prevWet = 0f;
		mistFade = prevMistFade = 0f;
		sheltered = 0f;
		mode = Mode.BIOME;
		ThunderStrikes.clear();
	}

	private static void update(MinecraftClient mc, ClientWorld w) {
		float vRain = w.getRainGradient(1f);
		float vThunder = w.getThunderGradient(1f);
		Weather wx = Weather.get();
		if (wx != null && wx.isEnabled() && WeatherTextures.ensure()) {
			if (!takeover) {
				takeover = true;
				PRECIP.set(vRain);
				THUNDER.set(vThunder);
				prevPrecip = vRain;
				prevThunder = vThunder;
			}
			float ticks = wx.fadeTime.getFloat() * 20f;
			float k = wx.intensity.getFloat() / 100f;
			switch (wx.kind.get()) {
				case FOLLOW -> {
					mode = Mode.BIOME;
					PRECIP.set(vRain * k);
					THUNDER.set(vThunder);
				}
				case CLEAR -> {
					mode = Mode.BIOME;
					PRECIP.target(0f, ticks);
					THUNDER.target(0f, ticks);
				}
				case RAIN -> {
					mode = Mode.RAIN;
					PRECIP.target(k, ticks);
					THUNDER.target(0f, ticks);
				}
				case THUNDERSTORM -> {
					mode = Mode.RAIN;
					PRECIP.target(k, ticks);
					THUNDER.target(k, ticks);
				}
				case SNOW -> {
					mode = Mode.SNOW;
					PRECIP.target(k, ticks);
					THUNDER.target(0f, ticks);
				}
			}
		} else if (takeover) {
			// Switched off: fade to what the server has, then hand drawing back to vanilla.
			float ticks = (wx != null ? wx.fadeTime.getFloat() : 3f) * 20f;
			PRECIP.target(vRain, ticks);
			THUNDER.target(vThunder, ticks);
			if (Math.abs(PRECIP.cur - vRain) < 0.03f && Math.abs(THUNDER.cur - vThunder) < 0.03f) {
				takeover = false;
				mode = Mode.BIOME;
			}
		}

		// Glossy Rain: wetness builds while rain falls and dries out afterwards.
		GlossyRain g = GlossyRain.get();
		if (g != null && g.isEnabled()) {
			float rain = rainAmount(1f);
			float cap = g.wetness.getFloat() / 100f * (0.35f + 0.65f * rain);
			if (rain > 0.05f && wet < cap) {
				wet = Math.min(cap, wet + 1f / (g.soakTime.getFloat() * 20f));
			} else if (rain < 0.05f || wet > cap) {
				wet = Math.max(0f, wet - 1f / (g.dryTime.getFloat() * 20f));
			}
		} else {
			wet = Math.max(0f, wet - 0.02f);
		}

		// Mist fades in and out in 1.5 s instead of popping.
		Mist m = Mist.get();
		float mistTarget = m != null && m.isEnabled() ? 1f : 0f;
		mistFade += MathHelper.clamp(mistTarget - mistFade, -1f / 30f, 1f / 30f);

		// How much the player is under cover (muffles the ambience).
		Camera camera = mc.gameRenderer.getCamera();
		if (camera.isReady()) {
			int top = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, camera.getBlockPos().getX(), camera.getBlockPos().getZ());
			float target = camera.getCameraPos().y < top - 0.5 ? 1f : 0f;
			sheltered += MathHelper.clamp(target - sheltered, -0.1f, 0.1f);
		}
	}

	// ------------------------------------------------------------------ queries (client thread)

	/** True while Hazeline's precipitation replaces vanilla's (vanilla rain/snow is not drawn). */
	public static boolean takeover() {
		return takeover && !failed;
	}

	public static Mode mode() {
		return takeover ? mode : Mode.BIOME;
	}

	/** Amount of precipitation being shown (0..1), whether rain or snow. */
	public static float precip(float tp) {
		ClientWorld w = MinecraftClient.getInstance().world;
		if (takeover) {
			return MathHelper.lerp(tp, prevPrecip, PRECIP.cur);
		}
		return w == null ? 0f : w.getRainGradient(tp);
	}

	/** Thunder strength being shown (0..1). */
	public static float thunder(float tp) {
		ClientWorld w = MinecraftClient.getInstance().world;
		if (takeover) {
			return MathHelper.lerp(tp, prevThunder, THUNDER.cur);
		}
		return w == null ? 0f : w.getThunderGradient(tp);
	}

	/** Amount of precipitation that is RAIN at the player (0 in snow mode / snowy biome). */
	public static float rainAmount(float tp) {
		float p = precip(tp);
		return switch (mode()) {
			case SNOW -> 0f;
			case RAIN -> p;
			case BIOME -> {
				ClientWorld w = MinecraftClient.getInstance().world;
				Camera c = MinecraftClient.getInstance().gameRenderer.getCamera();
				if (w == null || !c.isReady()) {
					yield p;
				}
				var pos = c.getBlockPos();
				var biome = w.getBiome(pos).value();
				yield biome.getPrecipitation(pos, w.getSeaLevel()) == net.minecraft.world.biome.Biome.Precipitation.RAIN ? p : 0f;
			}
		};
	}

	/** 0..1 how wet the ground is (Glossy Rain). */
	public static float wetness(float tp) {
		return MathHelper.lerp(tp, prevWet, wet);
	}

	/** 0..1 fade of the Mist module. */
	public static float mistFade(float tp) {
		return MathHelper.lerp(tp, prevMistFade, mistFade);
	}

	/** 0 = standing in the open, 1 = under cover. */
	public static float sheltered() {
		return sheltered;
	}

	/** Gradient the sky / fog / cloud / light systems should see instead of vanilla's (client worlds only). */
	public static float visualRain(float vanilla) {
		if (!takeover()) {
			return vanilla;
		}
		Weather wx = Weather.get();
		float dark = wx == null ? 1f : wx.skyDarkness.getFloat() / 100f;
		return MathHelper.clamp(precip(1f) * dark, 0f, 1f);
	}

	public static float visualThunder(float vanilla) {
		if (!takeover()) {
			return vanilla;
		}
		Weather wx = Weather.get();
		float dark = wx == null ? 1f : wx.skyDarkness.getFloat() / 100f;
		return MathHelper.clamp(thunder(1f) * dark, 0f, 1f);
	}

	/** Weather-driven fog strength 0..1 (tint amount), including the constant haze setting. */
	public static float fogStrength() {
		Weather wx = Weather.get();
		if (wx == null || !wx.isEnabled() || failed) {
			return 0f;
		}
		float haze = wx.haze.getFloat() / 100f;
		return MathHelper.clamp(Math.max(precip(1f), haze), 0f, 1f);
	}

	// ------------------------------------------------------------------ sky helpers

	/** Records vanilla's fog colour this frame (RGB 0..1). */
	public static void noteFog(float r, float g, float b) {
		fogR = r;
		fogG = g;
		fogB = b;
	}

	/** The fog / sky colour as 0xRRGGBB. */
	public static int skyRgb() {
		return (Math.round(fogR * 255f) << 16) | (Math.round(fogG * 255f) << 8) | Math.round(fogB * 255f);
	}

	/** Unit vector towards the sun (x, y, z); y = height above the horizon. Null if unknown. */
	public static float[] sunDirection(float tp) {
		try {
			Camera c = MinecraftClient.getInstance().gameRenderer.getCamera();
			if (!c.isReady()) {
				return null;
			}
			Float deg = c.getEnvironmentAttributeInterpolator().get(EnvironmentAttributes.SUN_ANGLE_VISUAL, tp);
			if (deg == null) {
				return null;
			}
			double a = Math.toRadians(deg);
			// Vanilla rotates the sky about Y by -90 and about X by the sun angle: direction (-sin a, cos a, 0).
			return new float[] {(float) -Math.sin(a), (float) Math.cos(a), 0f};
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Daylight 0..1 from the sun's height (smooth through dawn and dusk). */
	public static float daylight(float tp) {
		float[] s = sunDirection(tp);
		return s == null ? 1f : WeatherMath.smoothstep(-0.18f, 0.22f, s[1]);
	}

	/** 0..1, peaks at sunrise / sunset. */
	public static float twilight(float tp) {
		float[] s = sunDirection(tp);
		return s == null ? 0f : Math.max(0f, 1f - Math.abs(s[1]) / 0.32f);
	}

	// ------------------------------------------------------------------ hooks used by the mixins

	/** Camera shake {yaw, pitch} in degrees from close lightning, or null. */
	public static float[] cameraShake(float tp) {
		if (failed || !SmoothThunder.active()) {
			return null;
		}
		return ThunderStrikes.shake(tp);
	}

	/** True if the Smooth Thunder module should swap the server's thunder sounds for its own. */
	public static boolean replacesServerThunder() {
		SmoothThunder st = SmoothThunder.get();
		return !failed && st != null && st.muteServer.get() && SmoothThunder.active();
	}

	/** The server's thunder was swapped; play ours. */
	public static void onServerThunder() {
		try {
			ThunderStrikes.serverThunder();
		} catch (Throwable t) {
			fail("server thunder", t);
		}
	}
}
