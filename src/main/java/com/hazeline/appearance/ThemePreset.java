package com.hazeline.appearance;

import java.util.List;

import com.google.gson.JsonObject;

/**
 * A named colour palette (ARGB values). Built-in presets are defined here; presets saved by
 * the user are stored in config.json under appearance.customPresets.
 */
public record ThemePreset(String name, int accent, int panelBackground, int panelHeader, int headerGradientEnd,
		int text, int textSecondary, int border) {

	/** Warm yellow / cream - the default look. */
	public static final ThemePreset HAZELINE = new ThemePreset("Hazeline",
			0xFFE8A33D, 0xF2FFF6E0, 0xFFF4C95D, 0xFFE8A33D, 0xFF3B2A1A, 0xFF8A7055, 0xFFDDBB70);
	public static final ThemePreset DARK = new ThemePreset("Dark",
			0xFFE8A33D, 0xE6151515, 0xFF2B2B2B, 0xFF1C1C1C, 0xFFF0F0F0, 0xFF9A9A9A, 0xFF3A3A3A);
	public static final ThemePreset LIGHT = new ThemePreset("Light",
			0xFF3D7BE8, 0xF2F5F5F5, 0xFFDEDEDE, 0xFFCACACA, 0xFF1E1E1E, 0xFF6E6E6E, 0xFFBDBDBD);
	public static final ThemePreset OCEAN = new ThemePreset("Ocean",
			0xFF2EC4D6, 0xE60B1E2D, 0xFF0E4D6E, 0xFF0A3550, 0xFFE6F7FF, 0xFF8FB3C7, 0xFF1F5C7A);
	public static final ThemePreset SAKURA = new ThemePreset("Sakura",
			0xFFE85D8E, 0xF0FFF0F5, 0xFFF7A8C4, 0xFFF08DB2, 0xFF4A2433, 0xFF9C6B7E, 0xFFF2C2D3);

	// Dark "glass" themes for the window-style menu: navy/charcoal glass with one accent colour.
	public static final ThemePreset INDIGO = new ThemePreset("Indigo",
			0xFF6E5BFF, 0xEE0D0E22, 0xFF15163A, 0xFF101130, 0xFFECEBFA, 0xFF8B8AAE, 0xFF262849);
	public static final ThemePreset VAULT = new ThemePreset("Vault",
			0xFF8B6CFF, 0xEE0E0D1C, 0xFF19162E, 0xFF120F24, 0xFFEEEAFB, 0xFF8E87AD, 0xFF2A2546);
	public static final ThemePreset NOVA = new ThemePreset("Nova",
			0xFF2ED3E6, 0xEE0B1020, 0xFF111A33, 0xFF0C1428, 0xFFE6F6FA, 0xFF7F98AD, 0xFF1E2C47);
	public static final ThemePreset ROSE = new ThemePreset("Rose",
			0xFFF0508F, 0xEE170C14, 0xFF26121F, 0xFF1C0D17, 0xFFF8E9F0, 0xFFA8879A, 0xFF3A1F30);
	public static final ThemePreset EMBER = new ThemePreset("Ember",
			0xFFFF7A2E, 0xEE141012, 0xFF22191A, 0xFF191314, 0xFFF7ECE6, 0xFFA59287, 0xFF35282A);
	public static final ThemePreset FOREST = new ThemePreset("Forest",
			0xFF22C281, 0xEE0A1512, 0xFF10231D, 0xFF0C1B16, 0xFFE6F6EF, 0xFF7FA595, 0xFF1C3830);
	public static final ThemePreset MAGENTA = new ThemePreset("Magenta",
			0xFFD24DF0, 0xEE140B1A, 0xFF22122C, 0xFF1A0D22, 0xFFF5E9FA, 0xFF9F86AD, 0xFF351F44);
	public static final ThemePreset CRIMSON = new ThemePreset("Crimson",
			0xFFE8414B, 0xEE120A0C, 0xFF201114, 0xFF180C0F, 0xFFF8E8E9, 0xFFA48589, 0xFF381C21);
	public static final ThemePreset LIME = new ThemePreset("Lime",
			0xFF9BD43A, 0xEE0F130A, 0xFF1A2111, 0xFF13190C, 0xFFF0F6E4, 0xFF97A47F, 0xFF2D371E);
	public static final ThemePreset GOLD = new ThemePreset("Gold",
			0xFFF2B630, 0xEE14110A, 0xFF231D10, 0xFF1A150B, 0xFFF8F1E1, 0xFFA89B7C, 0xFF3A311B);
	public static final ThemePreset AURORA = new ThemePreset("Aurora",
			0xFF5BE0C4, 0xEE091417, 0xFF0F2328, 0xFF0B1A1E, 0xFFE4F7F3, 0xFF7DA39C, 0xFF1A3A3B);

	public static final List<ThemePreset> BUILT_IN = List.of(INDIGO, VAULT, NOVA, ROSE, EMBER, FOREST, MAGENTA, CRIMSON,
			LIME, GOLD, AURORA, HAZELINE, DARK, LIGHT, OCEAN, SAKURA);

	/** Default colours of a fresh install (and of "Reset" in the Colors tab). */
	public static final ThemePreset DEFAULT = INDIGO;

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("accent", hex(accent));
		o.addProperty("panelBackground", hex(panelBackground));
		o.addProperty("panelHeader", hex(panelHeader));
		o.addProperty("headerGradientEnd", hex(headerGradientEnd));
		o.addProperty("text", hex(text));
		o.addProperty("textSecondary", hex(textSecondary));
		o.addProperty("border", hex(border));
		return o;
	}

	public static ThemePreset fromJson(String name, JsonObject o) {
		return new ThemePreset(name,
				parse(o, "accent", HAZELINE.accent), parse(o, "panelBackground", HAZELINE.panelBackground),
				parse(o, "panelHeader", HAZELINE.panelHeader), parse(o, "headerGradientEnd", HAZELINE.headerGradientEnd),
				parse(o, "text", HAZELINE.text), parse(o, "textSecondary", HAZELINE.textSecondary),
				parse(o, "border", HAZELINE.border));
	}

	private static String hex(int argb) {
		return String.format(java.util.Locale.ROOT, "#%08X", argb);
	}

	private static int parse(JsonObject o, String key, int fallback) {
		try {
			String s = o.get(key).getAsString().replace("#", "");
			return (int) Long.parseLong(s, 16);
		} catch (Exception e) {
			return fallback;
		}
	}
}
