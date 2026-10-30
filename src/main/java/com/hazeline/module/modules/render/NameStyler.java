package com.hazeline.module.modules.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import com.hazeline.Hazeline;
import com.hazeline.gui.Colors;

/**
 * Text engine of the Name Tag module: parses the custom name (with {@code &} codes), colours it
 * and swaps the real account name for it inside any {@link Text} or {@link OrderedText}.
 *
 * <p>Everything here only changes what this client draws. Nothing is sent anywhere; the account
 * name, the server and other players are untouched.
 */
public final class NameStyler {
	/**
	 * Font id that marks the coloured name characters in chat lines (chat lines are built once, so
	 * the animation is applied while drawing). {@code assets/hazeline/font/name_tag.json} is a
	 * plain reference to {@code minecraft:default}, so these characters look and measure exactly
	 * like normal text (resource packs that change the default font change it too).
	 */
	public static final Identifier MARKER_FONT_ID = Hazeline.id("name_tag");
	public static final StyleSpriteSource.Font MARKER_FONT = new StyleSpriteSource.Font(MARKER_FONT_ID);

	private NameStyler() {
	}

	/** One character with its style. */
	public record StyledChar(int codepoint, Style style) {
	}

	/** One character of the custom name after the {@code &} codes are read. */
	record NameChar(int codepoint, Integer codeColor, boolean bold, boolean italic, boolean underline, boolean strike,
			boolean obfuscated) {
	}

	// ============================================================================ parsing

	/**
	 * Reads {@code &0-&f} / {@code §} colours, {@code &#RRGGBB} hex colours, {@code &l &o &n &m &k}
	 * formatting and {@code &r} reset. Unknown codes are kept as normal text. "&&" is a literal "&".
	 */
	static List<NameChar> parse(String text) {
		List<NameChar> out = new ArrayList<>();
		Integer color = null;
		boolean bold = false, italic = false, underline = false, strike = false, obf = false;
		int[] cps = text.codePoints().toArray();
		for (int i = 0; i < cps.length; i++) {
			int c = cps[i];
			if ((c == '&' || c == '§') && i + 1 < cps.length) {
				int n = cps[i + 1];
				if (c == '&' && n == '&') {
					out.add(new NameChar('&', color, bold, italic, underline, strike, obf));
					i++;
					continue;
				}
				if (n == '#' && i + 7 < cps.length) {
					Integer hex = parseHex(cps, i + 2);
					if (hex != null) {
						color = hex;
						bold = italic = underline = strike = obf = false;
						i += 7;
						continue;
					}
				}
				Formatting f = Formatting.byCode(Character.toLowerCase((char) n));
				if (f != null && n < 0x10000) {
					if (f == Formatting.RESET) {
						color = null;
						bold = italic = underline = strike = obf = false;
					} else if (f.isColor()) {
						color = f.getColorValue();
						bold = italic = underline = strike = obf = false; // like vanilla: a colour code resets formatting
					} else {
						switch (f) {
							case BOLD -> bold = true;
							case ITALIC -> italic = true;
							case UNDERLINE -> underline = true;
							case STRIKETHROUGH -> strike = true;
							case OBFUSCATED -> obf = true;
							default -> {
							}
						}
					}
					i++;
					continue;
				}
			}
			out.add(new NameChar(c, color, bold, italic, underline, strike, obf));
		}
		return out;
	}

	private static Integer parseHex(int[] cps, int start) {
		int v = 0;
		for (int k = 0; k < 6; k++) {
			int d = Character.digit(cps[start + k], 16);
			if (d < 0) {
				return null;
			}
			v = v * 16 + d;
		}
		return v;
	}

	/** The custom name without its codes (what is actually shown). */
	public static String plain(String text) {
		StringBuilder sb = new StringBuilder();
		for (NameChar c : parse(text)) {
			sb.appendCodePoint(c.codepoint());
		}
		return sb.toString();
	}

	// ============================================================================ building the name

	/**
	 * The styled characters of the name that replaces the real name.
	 *
	 * @param base   style of the real name where it was found (keeps click / hover / insertion / font)
	 * @param marker true for chat lines: palette-coloured characters get {@link #MARKER_FONT} so they can
	 *               be animated while drawing (only when the base font is the default font)
	 */
	public static List<StyledChar> build(NameTag m, String realName, Style base, boolean marker) {
		String custom = m.name.get();
		List<NameChar> chars = custom.isEmpty() ? parse(realName) : parse(custom);
		if (chars.isEmpty()) {
			chars = parse(realName);
		}
		int n = chars.size();
		boolean palette = m.colours.get() != NameTag.Colours.ORIGINAL;
		boolean useMarker = marker && palette && m.animated() && isDefaultFont(base);
		double time = time();
		List<StyledChar> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			NameChar c = chars.get(i);
			Style s = base;
			if (c.codeColor() != null) {
				s = s.withColor(TextColor.fromRgb(c.codeColor()));
			} else if (palette) {
				s = s.withColor(TextColor.fromRgb(m.colourAt(i, n, time) & 0xFFFFFF));
				if (useMarker) {
					s = s.withFont(MARKER_FONT);
				}
			}
			if (c.bold() || m.bold.get()) {
				s = s.withBold(true);
			}
			if (c.italic() || m.italic.get()) {
				s = s.withItalic(true);
			}
			if (c.underline() || m.underline.get()) {
				s = s.withUnderline(true);
			}
			if (c.strike() || m.strikethrough.get()) {
				s = s.withStrikethrough(true);
			}
			if (c.obfuscated()) {
				s = s.withObfuscated(true);
			}
			out.add(new StyledChar(c.codepoint(), s));
		}
		return out;
	}

	private static boolean isDefaultFont(Style style) {
		StyleSpriteSource font = style.getFont();
		return font == null || font.equals(StyleSpriteSource.DEFAULT);
	}

	/** Seconds since start, the clock every animation uses. */
	static double time() {
		return (System.nanoTime() / 1_000_000L) / 1000.0;
	}

	// ============================================================================ replacing

	/** Flattens a text into styled characters (styles already merged with their parents). */
	public static List<StyledChar> flatten(Text text) {
		List<StyledChar> out = new ArrayList<>();
		text.visit((style, string) -> {
			string.codePoints().forEach(cp -> out.add(new StyledChar(cp, style)));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static boolean nameChar(int cp) {
		return cp == '_' || (cp < 128 && Character.isLetterOrDigit(cp));
	}

	/** Start indices of every whole-word match of {@code name} (case sensitive). */
	static List<Integer> find(List<StyledChar> chars, int[] name) {
		List<Integer> hits = new ArrayList<>();
		if (name.length == 0) {
			return hits;
		}
		int last = chars.size() - name.length;
		outer:
		for (int i = 0; i <= last; i++) {
			for (int k = 0; k < name.length; k++) {
				if (chars.get(i + k).codepoint() != name[k]) {
					continue outer;
				}
			}
			if (i > 0 && nameChar(chars.get(i - 1).codepoint())) {
				continue;
			}
			int after = i + name.length;
			if (after < chars.size() && nameChar(chars.get(after).codepoint())) {
				continue;
			}
			hits.add(i);
			i += name.length - 1;
		}
		return hits;
	}

	/**
	 * Copy of {@code chars} with every whole-word {@code realName} replaced by the custom name, or null
	 * when the name does not occur.
	 */
	public static List<StyledChar> replace(NameTag m, List<StyledChar> chars, String realName, boolean marker) {
		int[] name = realName.codePoints().toArray();
		List<Integer> hits = find(chars, name);
		if (hits.isEmpty()) {
			return null;
		}
		List<StyledChar> out = new ArrayList<>(chars.size() + 16);
		int pos = 0;
		for (int hit : hits) {
			out.addAll(chars.subList(pos, hit));
			out.addAll(build(m, realName, chars.get(hit).style(), marker));
			pos = hit + name.length;
		}
		out.addAll(chars.subList(pos, chars.size()));
		return out;
	}

	/** Builds a text from styled characters, one literal per run of equal style. */
	public static MutableText toText(List<StyledChar> chars) {
		MutableText root = Text.empty();
		StringBuilder run = new StringBuilder();
		Style runStyle = null;
		for (StyledChar c : chars) {
			if (runStyle != null && !runStyle.equals(c.style())) {
				root.append(Text.literal(run.toString()).setStyle(runStyle));
				run.setLength(0);
			}
			runStyle = c.style();
			run.appendCodePoint(c.codepoint());
		}
		if (runStyle != null && run.length() > 0) {
			root.append(Text.literal(run.toString()).setStyle(runStyle));
		}
		return root;
	}

	/** An ordered text that yields exactly these characters. */
	public static OrderedText toOrdered(List<StyledChar> chars) {
		return visitor -> {
			for (int i = 0; i < chars.size(); i++) {
				StyledChar c = chars.get(i);
				if (!visitor.accept(i, c.style(), c.codepoint())) {
					return false;
				}
			}
			return true;
		};
	}

	/** {@code text} with the real name replaced, or {@code text} itself when it does not contain it. */
	public static Text replaceIn(NameTag m, Text text, String realName, boolean marker) {
		if (text == null || realName == null || realName.isEmpty() || !text.getString().contains(realName)) {
			return text;
		}
		List<StyledChar> replaced = replace(m, flatten(text), realName, marker);
		return replaced == null ? text : toText(replaced);
	}

	// ============================================================================ chat animation

	/**
	 * Wraps a chat line so the marked name characters get this frame's animated colours. Characters
	 * keep their exact glyphs and widths (only the colour changes, the marker font is put back to the
	 * default font), so clicking / hovering in chat works as before.
	 */
	public static OrderedText animateLine(NameTag m, OrderedText line, int nameLength) {
		double time = time();
		int n = Math.max(1, nameLength);
		return visitor -> {
			int[] run = {0};
			return line.accept((index, style, cp) -> {
				StyleSpriteSource font = style.getFont();
				if (font != null && font.equals(MARKER_FONT)) {
					int i = run[0]++;
					Style s = style.withFont(StyleSpriteSource.DEFAULT).withColor(TextColor.fromRgb(m.colourAt(i % n, n, time) & 0xFFFFFF));
					return visitor.accept(index, s, cp);
				}
				run[0] = 0;
				return visitor.accept(index, style, cp);
			});
		};
	}

	/** Whether a chat line contains marked characters (cheap check before wrapping it). */
	public static boolean hasMarker(OrderedText line) {
		boolean[] found = {false};
		line.accept((index, style, cp) -> {
			StyleSpriteSource font = style.getFont();
			if (font != null && font.equals(MARKER_FONT)) {
				found[0] = true;
				return false;
			}
			return true;
		});
		return found[0];
	}

	// ============================================================================ colours

	/** Colour at position {@code p} of a colour set going round in a circle (last blends back to first). */
	static int cyclic(int[] colors, double p) {
		int k = colors.length;
		if (k == 1) {
			return colors[0];
		}
		double f = (p - Math.floor(p)) * k;
		int a = (int) Math.floor(f) % k;
		int b = (a + 1) % k;
		return Colors.lerp(colors[a], colors[b], (float) (f - Math.floor(f)));
	}

	/** Colour at {@code p} in [0, 1] of a colour set laid out once from first to last. */
	static int linear(int[] colors, double p) {
		int k = colors.length;
		if (k == 1) {
			return colors[0];
		}
		double f = Math.max(0, Math.min(1, p)) * (k - 1);
		int a = Math.min(k - 2, (int) Math.floor(f));
		return Colors.lerp(colors[a], colors[a + 1], (float) (f - a));
	}
}
