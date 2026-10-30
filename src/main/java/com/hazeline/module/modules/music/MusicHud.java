package com.hazeline.module.modules.music;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import com.hazeline.Hazeline;
import com.hazeline.gui.ClickGuiScreen;
import com.hazeline.gui.GuiOverlay;
import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.module.ModuleManager;
import com.hazeline.music.MediaService;
import com.hazeline.setting.ActionSetting;
import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.DataSetting;
import com.hazeline.setting.InfoSetting;
import com.hazeline.setting.KeybindSetting;
import com.hazeline.setting.NumberSetting;

/**
 * Music HUD (1.36.0): a "now playing" popup for whatever Windows reports as playing - YouTube Music
 * in Chrome, Spotify, ... No account to link: it reads the Windows media session ({@link MediaService}).
 *
 * <p>User's choices (interview 2026-10-24): card like their screenshot (cover, title, artist, progress
 * bar, previous / play-pause / next, volume slider) on a blurred copy of the cover, theme accent colour,
 * album line + time stamps, always visible while something plays or is paused, draggable while the
 * click GUI is open, buttons clickable while the click GUI or chat is open, rebindable keys (unbound by
 * default), click the bar to seek, volume = only the playing app (Chrome) in the Windows mixer.
 */
public class MusicHud extends Module {
	private static volatile MusicHud instance;

	public final InfoSetting statusInfo = add(new InfoSetting("Status", "What Windows reports right now",
			() -> MediaService.get().status()));
	public final NumberSetting size = add(new NumberSetting("Size", "Size of the popup in %", 100, 50, 200, 5));
	public final NumberSetting opacity = add(new NumberSetting("Background opacity", "How solid the blurred cover background is, in %",
			100, 30, 100, 5));
	public final BooleanSetting showAlbum = add(new BooleanSetting("Show album", "Album name under the artist", true));
	public final BooleanSetting showTimes = add(new BooleanSetting("Show time stamps", "Position and length under the progress bar", true));
	public final NumberSetting volumeStep = add(new NumberSetting("Volume step", "How much the volume keys change the volume, in %",
			5, 1, 20, 1));
	public final KeybindSetting keyPlay = add(new KeybindSetting("Play / pause key", "Pauses or resumes the music (in game)", KeybindSetting.NONE));
	public final KeybindSetting keyNext = add(new KeybindSetting("Next track key", "Skips to the next song (in game)", KeybindSetting.NONE));
	public final KeybindSetting keyPrev = add(new KeybindSetting("Previous track key", "Goes back a song (in game)", KeybindSetting.NONE));
	public final KeybindSetting keyVolUp = add(new KeybindSetting("Volume up key", "Louder (hold to repeat, in game)", KeybindSetting.NONE));
	public final KeybindSetting keyVolDown = add(new KeybindSetting("Volume down key", "Quieter (hold to repeat, in game)", KeybindSetting.NONE));
	/** Position as fractions "x,y" of the free space (0 = left/top, 1 = right/bottom). */
	private final DataSetting position = add(new DataSetting("Position", "0,0"));
	public final ActionSetting resetPosition = add(new ActionSetting("Reset position", "Moves the popup back to the top-left corner",
			() -> setPosition(0f, 0f)));

	private final Map<KeybindSetting, Long> keyDownSince = new HashMap<>();
	private final Map<KeybindSetting, Long> keyLastRepeat = new HashMap<>();

	public MusicHud() {
		super("Music HUD", "Shows the song that is playing (YouTube Music, Spotify...) with controls - no account needed",
				Category.MUSIC);
		instance = this;
	}

	public static MusicHud instance() {
		return instance;
	}

	public static void registerEvents() {
		HudElementRegistry.addLast(Hazeline.id("music_hud"), (g, tick) -> {
			MusicHud m = instance;
			if (m == null || !m.isEnabled()) {
				return;
			}
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.options.hudHidden || clickGuiOpen(mc)) {
				return; // the click GUI draws it on top of itself instead
			}
			boolean chat = mc.currentScreen instanceof ChatScreen;
			double[] mouse = chat ? scaledMouse(mc) : null;
			MusicHudRenderer.render(g, m, mouse == null ? -1e5 : mouse[0], mouse == null ? -1e5 : mouse[1], chat);
		});
		// Chat: the buttons, seek bar and volume slider can be clicked (no dragging there).
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof ChatScreen)) {
				return;
			}
			ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
				MusicHud m = instance;
				if (m == null || !m.isEnabled() || GuiOverlay.isOpen()) {
					return true;
				}
				return !MusicHudRenderer.mouseClicked(m, click.x(), click.y(), click.button(), false);
			});
			ScreenMouseEvents.allowMouseDrag(screen).register((s, click, dx, dy) -> {
				MusicHud m = instance;
				if (m == null || !m.isEnabled() || GuiOverlay.isOpen()) {
					return true;
				}
				return !MusicHudRenderer.mouseDragged(m, click.x(), click.y());
			});
			ScreenMouseEvents.allowMouseRelease(screen).register((s, click) -> {
				MusicHud m = instance;
				if (m == null || GuiOverlay.isOpen()) {
					return true;
				}
				return !MusicHudRenderer.mouseReleased(m, click.x(), click.y());
			});
		});
	}

	static boolean clickGuiOpen(MinecraftClient mc) {
		return mc.currentScreen instanceof ClickGuiScreen || GuiOverlay.isOpen();
	}

	private static double[] scaledMouse(MinecraftClient mc) {
		Window w = mc.getWindow();
		return new double[] {mc.mouse.getScaledX(w), mc.mouse.getScaledY(w)};
	}

	// ------------------------------------------------------------------ click GUI hooks (ClickGuiScreen)

	/** Drawn on top of the click GUI (in a world), where it can also be dragged. */
	public static void renderInClickGui(DrawContext g, int mouseX, int mouseY) {
		MusicHud m = instance;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (m == null || !m.isEnabled() || mc.world == null) {
			return;
		}
		MusicHudRenderer.render(g, m, mouseX, mouseY, true);
	}

	/** True if the click was on the popup (then the click GUI ignores it). */
	public static boolean clickGuiMouseClicked(double mx, double my, int button) {
		MusicHud m = instance;
		if (m == null || !m.isEnabled() || MinecraftClient.getInstance().world == null) {
			return false;
		}
		return MusicHudRenderer.mouseClicked(m, mx, my, button, true);
	}

	public static boolean clickGuiMouseDragged(double mx, double my) {
		MusicHud m = instance;
		return m != null && m.isEnabled() && MusicHudRenderer.mouseDragged(m, mx, my);
	}

	public static boolean clickGuiMouseReleased(double mx, double my) {
		MusicHud m = instance;
		return m != null && MusicHudRenderer.mouseReleased(m, mx, my);
	}

	// ------------------------------------------------------------------ position

	float[] getPosition() {
		String[] p = position.get().split(",");
		try {
			return new float[] {clamp01(Float.parseFloat(p[0])), clamp01(Float.parseFloat(p[1]))};
		} catch (RuntimeException e) {
			return new float[] {0f, 0f};
		}
	}

	void setPosition(float fx, float fy) {
		position.set(String.format(java.util.Locale.ROOT, "%.4f,%.4f", clamp01(fx), clamp01(fy)));
		ModuleManager.get().markDirty();
	}

	private static float clamp01(float v) {
		return Float.isNaN(v) ? 0f : Math.max(0f, Math.min(1f, v));
	}

	float scale() {
		return (float) (size.get() / 100.0);
	}

	// ------------------------------------------------------------------ lifecycle

	@Override
	protected void onEnable() {
		MediaService.get().start();
	}

	@Override
	protected void onDisable() {
		MediaService.get().stop();
		MusicHudRenderer.releaseTextures();
	}

	@Override
	public void onTick() {
		MinecraftClient mc = mc();
		if (mc.currentScreen != null || mc.player == null) {
			keyDownSince.clear();
			return;
		}
		MediaService media = MediaService.get();
		if (pressed(mc, keyPlay, false)) {
			media.togglePlay();
		}
		if (pressed(mc, keyNext, false)) {
			media.next();
		}
		if (pressed(mc, keyPrev, false)) {
			media.previous();
		}
		float step = (float) (volumeStep.get() / 100.0);
		if (pressed(mc, keyVolUp, true)) {
			changeVolume(media, step);
		}
		if (pressed(mc, keyVolDown, true)) {
			changeVolume(media, -step);
		}
	}

	private static void changeVolume(MediaService media, float delta) {
		float v = media.state().volume();
		if (v >= 0f) {
			media.setVolume(Math.round((v + delta) * 100f) / 100f);
		}
	}

	/** Edge-triggered key press; with {@code repeat}, held keys repeat after 0.4 s every 0.1 s. */
	private boolean pressed(MinecraftClient mc, KeybindSetting key, boolean repeat) {
		if (!key.isBound()) {
			return false;
		}
		boolean down = InputUtil.isKeyPressed(mc.getWindow(), key.get());
		long now = System.currentTimeMillis();
		Long since = keyDownSince.get(key);
		if (!down) {
			keyDownSince.remove(key);
			return false;
		}
		if (since == null) {
			keyDownSince.put(key, now);
			keyLastRepeat.put(key, now);
			return true;
		}
		if (repeat && now - since >= 400 && now - keyLastRepeat.getOrDefault(key, now) >= 100) {
			keyLastRepeat.put(key, now);
			return true;
		}
		return false;
	}
}
