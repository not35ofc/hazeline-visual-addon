package com.hazeline.module.modules.render;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import com.hazeline.Hazeline;
import com.hazeline.appearance.FontManager;
import com.hazeline.gui.Colors;
import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.ChoiceSetting;
import com.hazeline.setting.ColorSetting;
import com.hazeline.setting.EnumSetting;
import com.hazeline.setting.NamePreviewSetting;
import com.hazeline.setting.NumberSetting;
import com.hazeline.setting.Setting;
import com.hazeline.setting.TextSetting;

/**
 * Name Tag: shows your own name the way you like it - only on your screen.
 *
 * <h2>Client side only</h2>
 * Nothing here sends anything to the server or touches the Microsoft / Minecraft account. The real
 * name is swapped for the custom one at the moment this client draws text, so other players, the
 * server and the account keep the real name.
 *
 * <h2>Where (1.21.11, Yarn, verified against the bytecode)</h2>
 * <ul>
 *   <li>F5: vanilla never draws your own tag ({@code LivingEntityRenderer#hasLabel} is false for the
 *       camera entity), so {@link NameTagRenderer} draws it as a HUD layer at your name tag
 *       attachment point, projected with this frame's world matrices ({@code ScreenProjector}).</li>
 *   <li>Tab list: return value of {@code PlayerListHud#getPlayerName} for your own entry (rebuilt every
 *       frame, so animations move).</li>
 *   <li>Scoreboard: the decorated line text in the sidebar entry lambda ({@code InGameHud.method_55439},
 *       {@code Team.decorateName}) and the sidebar title, rebuilt every frame.</li>
 *   <li>Chat: {@code ChatHud#addVisibleMessage} wraps a copy of each message with the name swapped
 *       (the stored message, the log and copy keep the real text); animated colours are applied while
 *       drawing ({@code ChatHud$1#accept}) to characters marked with {@link NameStyler#MARKER_FONT}.</li>
 * </ul>
 * Server ranks around your name ("[VIP] Name") stay: only the name itself is swapped.
 */
public class NameTag extends Module {

	public enum Colours {
		ORIGINAL("Server colour", null),
		CUSTOM("Custom", null),
		RAINBOW("Rainbow", null),
		SUNSET("Sunset", OutlinePalette.SUNSET),
		OCEAN("Ocean", OutlinePalette.OCEAN),
		AURORA("Aurora", OutlinePalette.AURORA),
		FIRE("Fire", OutlinePalette.FIRE),
		CANDY("Candy", OutlinePalette.CANDY),
		NEON("Neon", OutlinePalette.NEON),
		ICE("Ice", OutlinePalette.ICE),
		FOREST("Forest", OutlinePalette.FOREST),
		GALAXY("Galaxy", OutlinePalette.GALAXY);

		private final String label;
		private final OutlinePalette preset;

		Colours(String label, OutlinePalette preset) {
			this.label = label;
			this.preset = preset;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum Animation {
		STATIC("Static"), WAVE("Wave"), FADE("Fade"), SHINE("Shine"), PULSE("Pulse");

		private final String label;

		Animation(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum Background {
		VANILLA("Vanilla box"), HAZELINE("Hazeline style"), NONE("None");

		private final String label;

		Background(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public static final int MAX_COLORS = 6;
	public static final String MINECRAFT_FONT = "minecraft";
	private static final int[] DEFAULT_COLORS = {0xFF60A5FA, 0xFFC084FC, 0xFFF472B6, 0xFFFBBF24, 0xFF34D399, 0xFFF87171};

	// ------------------------------------------------------------------ name
	public final NamePreviewSetting preview = add(new NamePreviewSetting("Preview", "How your name looks right now", this));
	public final TextSetting name = add(new TextSetting("Name",
			"Your name on your screen. Codes: &0-&f colours, &#RRGGBB any colour, &l bold, &o italic, &n underline, &m strikethrough, &k magic, &r reset. Empty = your real name with the style below",
			"", 48, true));

	// ------------------------------------------------------------------ where
	public final BooleanSetting inF5 = add(new BooleanSetting("Show above head (F5)",
			"Draw your own name tag above your head in third person", true));
	public final BooleanSetting inTab = add(new BooleanSetting("Tab list", "Your name in the player list (Tab)", true));
	public final BooleanSetting inScoreboard = add(new BooleanSetting("Scoreboards", "Your name in the scoreboard on the side", true));
	public final BooleanSetting inChat = add(new BooleanSetting("Chat",
			"Your name in every chat line: messages, join / leave, deaths, /msg", true));

	// ------------------------------------------------------------------ colours
	public final EnumSetting<Colours> colours = add(new EnumSetting<>("Colours",
			"Server colour = keep the colour the server gives your name, Custom = your own colours below, Rainbow, or a ready-made set",
			Colours.CUSTOM));
	public final NumberSetting colorCount = add(new NumberSetting("Colour count",
			"1 = one colour, 2 or more = a gradient across the letters", 2, 1, MAX_COLORS, 1),
			() -> colours.get() == Colours.CUSTOM);
	public final ColorSetting[] colors = createColorSettings();
	public final NumberSetting rainbowSaturation = add(new NumberSetting("Rainbow saturation",
			"0 = white, 100 = full colour (%)", 75, 0, 100, 1), () -> colours.get() == Colours.RAINBOW);
	public final NumberSetting rainbowBrightness = add(new NumberSetting("Rainbow brightness", "(%)", 100, 10, 100, 1),
			() -> colours.get() == Colours.RAINBOW);
	public final EnumSetting<Animation> animation = add(new EnumSetting<>("Animation",
			"Static = colours stay, Wave = colours flow through the letters, Fade = the whole name changes colour, Shine = a light sweeps over the name, Pulse = the name glows brighter and dimmer",
			Animation.STATIC), this::paletteOn);
	public final NumberSetting speed = add(new NumberSetting("Speed",
			"How fast it moves (1 = one cycle every 4 seconds)", 1.0, 0.1, 5.0, 0.05), this::animated);
	public final NumberSetting spread = add(new NumberSetting("Colour spread",
			"How many times the colours repeat across the name", 1.0, 0.25, 4.0, 0.05),
			() -> paletteOn() && (animation.get() == Animation.WAVE || colours.get() == Colours.RAINBOW));
	public final BooleanSetting reverse = add(new BooleanSetting("Reverse direction", "Move the other way", false),
			() -> animated() && (animation.get() == Animation.WAVE || animation.get() == Animation.SHINE));

	// ------------------------------------------------------------------ formatting
	public final BooleanSetting bold = add(new BooleanSetting("Bold", "Bold letters", false));
	public final BooleanSetting italic = add(new BooleanSetting("Italic", "Slanted letters (Minecraft font only)", false));
	public final BooleanSetting underline = add(new BooleanSetting("Underline", "Line under the name", false));
	public final BooleanSetting strikethrough = add(new BooleanSetting("Strikethrough", "Line through the name", false));

	// ------------------------------------------------------------------ F5 tag
	public final EnumSetting<Background> background = add(new EnumSetting<>("Tag background",
			"Vanilla box = Minecraft's dark box, Hazeline style = rounded box in your theme colours with a soft glow, None = text only",
			Background.VANILLA), inF5::get);
	public final ChoiceSetting tagFont = add(new ChoiceSetting("Tag font",
			"Font of the tag above your head (chat, tab and scoreboard always use Minecraft's font)", MINECRAFT_FONT,
			NameTag::fontChoices), inF5::get);
	public final NumberSetting tagSize = add(new NumberSetting("Tag size", "1 = vanilla size", 1.0, 0.5, 3.0, 0.05), inF5::get);
	public final NumberSetting tagOpacity = add(new NumberSetting("Background opacity",
			"How see-through the tag background is (%), 0 = no box", 50, 0, 100, 1),
			() -> inF5.get() && background.get() != Background.NONE);
	public final BooleanSetting tagShadow = add(new BooleanSetting("Text shadow", "Dark shadow behind the tag's letters", false),
			inF5::get);
	public final NumberSetting tagHeight = add(new NumberSetting("Tag height", "Move the tag up or down (blocks)", 0.0, -0.5, 1.5, 0.05),
			inF5::get);
	public final BooleanSetting hideSneaking = add(new BooleanSetting("Hide when sneaking",
			"Hide the tag while you sneak (like vanilla hides names)", false), inF5::get);

	private static NameTag instance;

	public NameTag() {
		super("Name Tag", "Customise how your own name looks - only on your screen, your account keeps its name", Category.RENDER);
		instance = this;
		// Chat lines are wrapped once, so rebuild them whenever something that changes them changes.
		for (Setting<?> s : chatAffecting()) {
			s.onChange(NameTag::refreshChat);
		}
	}

	public static NameTag get() {
		return instance;
	}

	private List<Setting<?>> chatAffecting() {
		List<Setting<?>> list = new ArrayList<>(List.of(name, inChat, colours, colorCount, rainbowSaturation, rainbowBrightness, animation,
				speed, spread, reverse, bold, italic, underline, strikethrough));
		list.addAll(List.of(colors));
		return list;
	}

	private ColorSetting[] createColorSettings() {
		ColorSetting[] out = new ColorSetting[MAX_COLORS];
		for (int i = 0; i < MAX_COLORS; i++) {
			final int index = i;
			out[i] = add(new ColorSetting("Colour " + (i + 1), "Name colour " + (i + 1), DEFAULT_COLORS[i]),
					() -> colours.get() == Colours.CUSTOM && index < colorCount.getInt());
		}
		return out;
	}

	private static List<ChoiceSetting.Choice> fontChoices() {
		List<ChoiceSetting.Choice> list = new ArrayList<>();
		list.add(new ChoiceSetting.Choice(MINECRAFT_FONT, "Minecraft"));
		list.addAll(FontManager.get().choices());
		return list;
	}

	@Override
	protected void onEnable() {
		refreshChat();
	}

	@Override
	protected void onDisable() {
		refreshChat();
	}

	// ============================================================================ colours

	public boolean paletteOn() {
		return colours.get() != Colours.ORIGINAL;
	}

	/** Whether the colours move (so chat lines need the per-frame recolour). */
	public boolean animated() {
		return paletteOn() && animation.get() != Animation.STATIC;
	}

	int[] colourSet() {
		Colours c = colours.get();
		if (c.preset != null) {
			return c.preset.colors();
		}
		int n = Math.max(1, Math.min(MAX_COLORS, colorCount.getInt()));
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			out[i] = colors[i].get() | 0xFF000000;
		}
		return out;
	}

	/** ARGB colour of letter {@code i} of {@code n} at time {@code t} (seconds). */
	public int colourAt(int i, int n, double t) {
		double cycles = speed.get() / 4.0 * t; // 1 = one cycle every 4 s
		double dir = reverse.get() ? -1 : 1;
		double x = n <= 1 ? 0 : i / (double) n; // position along the name, 0..1
		Animation a = animation.get();
		if (colours.get() == Colours.RAINBOW) {
			float sat = (float) (rainbowSaturation.get() / 100.0);
			float val = (float) (rainbowBrightness.get() / 100.0);
			double hue = switch (a) {
				case WAVE -> x * spread.get() - dir * cycles;
				case FADE -> cycles;
				default -> x * spread.get();
			};
			int c = Colors.fromHsv((float) (hue - Math.floor(hue)), sat, val, 255);
			return effect(c, a, i, n, cycles, dir);
		}
		int[] set = colourSet();
		int c = switch (a) {
			case WAVE -> NameStyler.cyclic(set, x * spread.get() - dir * cycles);
			case FADE -> NameStyler.cyclic(set, cycles);
			default -> NameStyler.linear(set, n <= 1 ? 0 : i / (double) (n - 1));
		};
		return effect(c, a, i, n, cycles, dir);
	}

	/** Shine and Pulse are drawn on top of the static colours. */
	private static int effect(int c, Animation a, int i, int n, double cycles, double dir) {
		if (a == Animation.SHINE) {
			// A bright band crosses the name once per cycle, with a short pause off the ends.
			double p = cycles - Math.floor(cycles);
			double centre = p * (n + 6) - 3;
			if (dir < 0) {
				centre = n - 1 - centre;
			}
			double d = Math.abs(i - centre);
			float k = (float) Math.max(0, 1 - d / 2.0) * 0.75f;
			return Colors.lerp(c, 0xFFFFFFFF, k);
		}
		if (a == Animation.PULSE) {
			float k = (float) (0.5 - 0.5 * Math.cos(cycles * 2 * Math.PI));
			return Colors.lerp(Colors.darken(c, 0.55f), c, 0.35f + 0.65f * k);
		}
		return c;
	}

	// ============================================================================ names

	/** The account name (never changed). */
	public static String realName() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			return mc.player.getGameProfile().name();
		}
		return mc.getSession() != null ? mc.getSession().getUsername() : null;
	}

	private static UUID realId() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			return mc.player.getUuid();
		}
		return mc.getSession() != null ? mc.getSession().getUuidOrNull() : null;
	}

	/** Number of visible letters of the shown name (custom or real). */
	int shownLength(String realName) {
		String custom = NameStyler.plain(name.get());
		return custom.isEmpty() ? (realName == null ? 0 : realName.length()) : custom.codePointCount(0, custom.length());
	}

	private static boolean active(BooleanSetting place) {
		NameTag m = instance;
		return m != null && m.isEnabled() && place.get();
	}

	// ============================================================================ hooks (mixins)

	/**
	 * NameTagOwnLabelMixin: true when the tag another mod (e.g. Essential "Show own nametag") makes Minecraft
	 * draw above {@code entity} must be removed because our F5 tag replaces it.
	 */
	public static boolean replacesOwnLabel(Object entity) {
		NameTag m = instance;
		MinecraftClient mc = MinecraftClient.getInstance();
		return m != null && m.isEnabled() && m.inF5.get() && entity != null && entity == mc.player;
	}

	/** NameTagTabMixin: tab list name of {@code entry}. */
	public static Text tabName(PlayerListEntry entry, Text original) {
		NameTag m = instance;
		if (m == null || !active(m.inTab) || entry == null || original == null) {
			return original;
		}
		try {
			UUID me = realId();
			if (me == null || !me.equals(entry.getProfile().id())) {
				return original;
			}
			return NameStyler.replaceIn(m, original, entry.getProfile().name(), false);
		} catch (RuntimeException e) {
			return fail(e, original);
		}
	}

	/** NameTagScoreboardMixin: one sidebar line (or the title). */
	public static Text scoreboardText(Text original) {
		NameTag m = instance;
		if (m == null || !active(m.inScoreboard) || original == null) {
			return original;
		}
		try {
			return NameStyler.replaceIn(m, original, realName(), false);
		} catch (RuntimeException e) {
			return fail(e, original);
		}
	}

	/** Same as {@link #scoreboardText} for the sidebar line, which must stay a MutableText. */
	public static MutableText scoreboardLine(MutableText original) {
		Text t = scoreboardText(original);
		return t instanceof MutableText mt ? mt : original;
	}

	/** NameTagChatMixin: the line that is wrapped for display (the stored message is not changed). */
	public static ChatHudLine chatLine(ChatHudLine line) {
		NameTag m = instance;
		if (m == null || !active(m.inChat) || line == null) {
			return line;
		}
		try {
			Text replaced = NameStyler.replaceIn(m, line.content(), realName(), true);
			if (replaced == line.content()) {
				return line;
			}
			return new ChatHudLine(line.creationTick(), replaced, line.signature(), line.indicator());
		} catch (RuntimeException e) {
			fail(e, null);
			return line;
		}
	}

	/** NameTagChatLineMixin: a chat line about to be drawn (animated colours). */
	public static OrderedText chatDraw(OrderedText line) {
		NameTag m = instance;
		if (m == null || line == null || !active(m.inChat) || !m.animated()) {
			return line;
		}
		try {
			if (!NameStyler.hasMarker(line)) {
				return line;
			}
			return NameStyler.animateLine(m, line, m.shownLength(realName()));
		} catch (RuntimeException e) {
			return fail(e, line);
		}
	}

	private static boolean chatRefreshPending;
	private static String lastChatKey = "";

	/**
	 * Asks for the chat lines to be re-wrapped with the current settings (the message history itself is
	 * unchanged). Done at most once per tick, and only when something that changes the lines changed,
	 * so dragging a colour or slider doesn't re-wrap a long chat history every frame.
	 */
	public static void refreshChat() {
		chatRefreshPending = true;
	}

	/** Everything that changes how chat lines are built (animation speed is applied while drawing). */
	private String chatKey() {
		if (!isEnabled() || !inChat.get()) {
			return "off";
		}
		StringBuilder sb = new StringBuilder();
		for (Setting<?> s : chatAffecting()) {
			if (s != speed) {
				sb.append(s.toJson()).append('|');
			}
		}
		return sb.toString();
	}

	private static void applyChatRefresh(MinecraftClient mc) {
		NameTag m = instance;
		if (!chatRefreshPending || m == null || mc.inGameHud == null) {
			return;
		}
		chatRefreshPending = false;
		String key = m.chatKey();
		if (key.equals(lastChatKey)) {
			return;
		}
		lastChatKey = key;
		try {
			mc.inGameHud.getChatHud().reset();
		} catch (RuntimeException e) {
			Hazeline.LOGGER.warn("Name Tag: could not refresh the chat lines", e);
		}
	}

	private static boolean loggedFailure;

	private static <T> T fail(RuntimeException e, T fallback) {
		if (!loggedFailure) {
			loggedFailure = true;
			Hazeline.LOGGER.error("Name Tag failed while changing a name; showing the original text where it fails", e);
		}
		return fallback;
	}

	// ============================================================================ F5 tag

	public static void registerEvents() {
		ClientTickEvents.END_CLIENT_TICK.register(NameTag::applyChatRefresh);
		// addFirst: every vanilla HUD element (hotbar, chat, ...) stays on top of the tag.
		HudElementRegistry.addFirst(Hazeline.id("name_tag"), (g, tick) -> {
			NameTag m = instance;
			if (m != null && m.isEnabled() && m.inF5.get()) {
				NameTagRenderer.renderF5(m, g, tick);
			}
		});
	}
}
