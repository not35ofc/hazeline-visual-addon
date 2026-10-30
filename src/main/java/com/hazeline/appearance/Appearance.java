package com.hazeline.appearance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.MinecraftClient;

import com.hazeline.module.ModuleManager;
import com.hazeline.setting.ActionSetting;
import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.ChoiceSetting;
import com.hazeline.setting.ColorSetting;
import com.hazeline.setting.NumberSetting;
import com.hazeline.setting.PresetsSetting;
import com.hazeline.setting.FontPickerSetting;
import com.hazeline.setting.Setting;
import com.hazeline.setting.TextSetting;

/**
 * Every appearance choice of the click GUI (font, colours, effects), grouped by tab. (GUI sounds moved
 * to the Sounds and Clicks module in 1.35.0; an old "sounds" block is migrated by ConfigManager.) {@link com.hazeline.gui.Theme} reads from here;
 * nothing in the GUI hardcodes colours, fonts or sizes.
 *
 * <p>Values are ordinary {@link Setting}s, so the editor reuses the same widgets as the
 * module panels and persistence works the same way (config.json, key "appearance").
 *
 * <p>Edit sessions: while the editor is open, changes apply live (preview) but are only
 * written to disk on "Apply". {@link #beginEdit()} snapshots the committed state,
 * {@link #apply()} commits, {@link #discard()} reverts to the snapshot.
 */
public final class Appearance {
	public enum Tab {
		FONT("Font"), COLORS("Colors");

		private final String label;

		Tab(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	/** Appearance config format; 2 = 1.3.0 (smooth default font), 3 = 1.27.0 (window menu, dark Indigo colours). */
	private static final int CONFIG_VERSION = 3;
	private static final Appearance INSTANCE = new Appearance();

	public static Appearance get() {
		return INSTANCE;
	}

	private final Map<Tab, List<Setting<?>>> settings = new EnumMap<>(Tab.class);
	private final Map<String, ThemePreset> customPresets = new LinkedHashMap<>();
	private JsonObject committed;
	private boolean editing;
	private String status = "";
	private long statusTime;

	// ---------------------------------------------------------------- Font
	public final ChoiceSetting font;
	public final NumberSetting fontSize;
	public final BooleanSetting textShadow;
	public final BooleanSetting boldModuleNames;
	/** Debug only, never saved: draws the current glyph atlas on screen. */
	public final BooleanSetting showGlyphAtlas;

	// ---------------------------------------------------------------- Colors
	public final ColorSetting accent;
	public final ColorSetting panelBackground;
	public final ColorSetting panelHeader;
	public final ColorSetting text;
	public final ColorSetting textSecondary;
	public final ColorSetting border;
	public final BooleanSetting rainbowAccent;
	public final NumberSetting rainbowSpeed;
	public final BooleanSetting gradientHeader;
	public final ColorSetting headerGradientEnd;
	public final BooleanSetting backgroundBlur;
	public final TextSetting presetName;
	public final TextSetting themeCode;

	private Appearance() {
		for (Tab tab : Tab.values()) {
			settings.put(tab, new ArrayList<>());
		}

		// Font tab
		font = add(Tab.FONT, new FontPickerSetting("Font", "Font used for the whole menu - click a card to use it",
				FontManager.DEFAULT_ID, () -> FontManager.get().choices()));
		fontSize = add(Tab.FONT, new NumberSetting("Font size", "Text size in GUI pixels (9 = vanilla size). Rows and panels scale with it",
				9.0, 6.0, 24.0, 0.5).applyOnRelease());
		textShadow = add(Tab.FONT, new BooleanSetting("Text shadow", "Draw GUI text with a drop shadow", true));
		boldModuleNames = add(Tab.FONT, new BooleanSetting("Bold module names", "Draw module names in bold", false));
		showGlyphAtlas = add(Tab.FONT, new BooleanSetting("Show glyph atlas (debug)",
				"Debug: draw the current font's glyph atlas textures and atlas statistics in the top-left corner (not saved)", false) {
			@Override
			public boolean isPersistent() {
				return false;
			}
		});

		// Colors tab
		ThemePreset d = ThemePreset.DEFAULT;
		add(Tab.COLORS, new PresetsSetting());
		presetName = add(Tab.COLORS, new TextSetting("New preset name", "Name for \"Save current as preset\"", "", 24, false));
		add(Tab.COLORS, new ActionSetting("Save current as preset", "Store the current colours under the name above",
				this::saveCurrentAsPreset));
		accent = add(Tab.COLORS, new ColorSetting("Accent", "Enabled modules, slider fill, highlights", d.accent()));
		panelBackground = add(Tab.COLORS, new ColorSetting("Panel background", "Background of panels and the editor", d.panelBackground()));
		panelHeader = add(Tab.COLORS, new ColorSetting("Panel header", "Panel title bars", d.panelHeader()));
		text = add(Tab.COLORS, new ColorSetting("Text", "Main text colour", d.text()));
		textSecondary = add(Tab.COLORS, new ColorSetting("Secondary text", "Disabled modules, values, hints", d.textSecondary()));
		border = add(Tab.COLORS, new ColorSetting("Outline / border", "Outlines, slider tracks, separators", d.border()));
		rainbowAccent = add(Tab.COLORS, new BooleanSetting("Rainbow accent", "Cycle the accent colour through the rainbow", false));
		rainbowSpeed = add(Tab.COLORS, new NumberSetting("Rainbow speed", "Rainbow cycles per 10 seconds", 2.0, 0.1, 10.0, 0.1));
		rainbowSpeed.visibleWhen(rainbowAccent::get);
		gradientHeader = add(Tab.COLORS, new BooleanSetting("Gradient headers", "Fade panel headers from the header colour to a second colour", false));
		headerGradientEnd = add(Tab.COLORS, new ColorSetting("Header gradient end", "Second header colour", d.headerGradientEnd()));
		headerGradientEnd.visibleWhen(gradientHeader::get);
		backgroundBlur = add(Tab.COLORS, new BooleanSetting("Background blur",
				"Blur the world behind the GUI (strength follows Minecraft's Menu Background Blur option)", false));
		themeCode = add(Tab.COLORS, new TextSetting("Theme code", "Paste a code here (Ctrl+V) and press Import", "", 2000, false));
		add(Tab.COLORS, new ActionSetting("Export theme (copy code)", "Copy the current colours and effects as a code",
				this::exportThemeCode));
		add(Tab.COLORS, new ActionSetting("Import theme code", "Apply the code in the Theme code field", this::importThemeCode));

		committed = toJson();
	}

	private <S extends Setting<?>> S add(Tab tab, S setting) {
		settings.get(tab).add(setting);
		return setting;
	}

	public List<Setting<?>> getSettings(Tab tab) {
		return Collections.unmodifiableList(settings.get(tab));
	}

	// ------------------------------------------------------------------ presets

	public void applyPreset(ThemePreset preset) {
		accent.set(preset.accent());
		panelBackground.set(preset.panelBackground());
		panelHeader.set(preset.panelHeader());
		headerGradientEnd.set(preset.headerGradientEnd());
		text.set(preset.text());
		textSecondary.set(preset.textSecondary());
		border.set(preset.border());
		setStatus("Theme \"" + preset.name() + "\" applied");
	}

	public ThemePreset currentAsPreset(String name) {
		return new ThemePreset(name, accent.get(), panelBackground.get(), panelHeader.get(), headerGradientEnd.get(),
				text.get(), textSecondary.get(), border.get());
	}

	public List<ThemePreset> getAllPresets() {
		List<ThemePreset> all = new ArrayList<>(ThemePreset.BUILT_IN);
		all.addAll(customPresets.values());
		return all;
	}

	public boolean isCustomPreset(ThemePreset preset) {
		return customPresets.containsKey(preset.name()) && !ThemePreset.BUILT_IN.contains(preset);
	}

	public void deleteCustomPreset(String name) {
		if (customPresets.remove(name) != null) {
			setStatus("Deleted preset \"" + name + "\"");
			ModuleManager.get().markDirty();
		}
	}

	private void saveCurrentAsPreset() {
		String name = presetName.get().trim();
		if (name.isEmpty()) {
			setStatus("Type a name in \"New preset name\" first");
			return;
		}
		for (ThemePreset builtIn : ThemePreset.BUILT_IN) {
			if (builtIn.name().equalsIgnoreCase(name)) {
				setStatus("\"" + name + "\" is a built-in preset name - pick another");
				return;
			}
		}
		customPresets.put(name, currentAsPreset(name));
		presetName.set("");
		ModuleManager.get().markDirty(); // presets are saved immediately, independent of Apply
		setStatus("Saved preset \"" + name + "\"");
	}

	// ------------------------------------------------------------------ theme codes

	private void exportThemeCode() {
		String code = ThemeCode.encode(this);
		themeCode.set(code);
		MinecraftClient.getInstance().keyboard.setClipboard(code);
		setStatus("Theme code copied to clipboard (" + code.length() + " characters)");
	}

	private void importThemeCode() {
		try {
			ThemeCode.decodeInto(themeCode.get(), this);
			setStatus("Theme imported");
		} catch (IllegalArgumentException e) {
			setStatus("Invalid theme code: " + e.getMessage());
		}
	}

	// ------------------------------------------------------------------ resets

	public void resetTab(Tab tab) {
		for (Setting<?> setting : settings.get(tab)) {
			if (setting.isPersistent()) {
				setting.reset();
			}
		}
		setStatus(tab.getLabel() + " tab reset to defaults");
	}

	public void resetAll() {
		for (Tab tab : Tab.values()) {
			resetTab(tab);
		}
		setStatus("Everything reset to defaults");
	}

	// ------------------------------------------------------------------ edit session

	public void beginEdit() {
		committed = toJson();
		editing = true;
	}

	public boolean isDirty() {
		return editing && !toJson().equals(committed);
	}

	/** Commits the live values; the caller saves the config. */
	public void apply() {
		committed = toJson();
		setStatus("Applied and saved");
	}

	/** Reverts to the last committed state and ends the session. */
	public void discard() {
		fromJson(committed);
		editing = false;
	}

	public void endEdit() {
		editing = false;
	}

	/** What the config file should contain: the committed state while editing, else live. */
	public JsonObject jsonForSave() {
		JsonObject json = editing ? committed.deepCopy() : toJson();
		json.add("customPresets", presetsJson());
		return json;
	}

	public void setStatus(String message) {
		status = message;
		statusTime = System.currentTimeMillis();
	}

	/** Status line for the editor footer; fades out after 6 s. */
	public String getStatus() {
		return System.currentTimeMillis() - statusTime < 6000 ? status : "";
	}

	// ------------------------------------------------------------------ JSON

	public JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("version", CONFIG_VERSION);
		for (Tab tab : Tab.values()) {
			JsonObject obj = new JsonObject();
			for (Setting<?> setting : settings.get(tab)) {
				if (setting.isPersistent()) {
					obj.add(setting.getName(), setting.toJson());
				}
			}
			root.add(tab.name().toLowerCase(java.util.Locale.ROOT), obj);
		}
		return root;
	}

	public void fromJson(JsonObject root) {
		for (Tab tab : Tab.values()) {
			JsonElement element = root.get(tab.name().toLowerCase(java.util.Locale.ROOT));
			if (element == null || !element.isJsonObject()) {
				continue;
			}
			JsonObject obj = element.getAsJsonObject();
			for (Setting<?> setting : settings.get(tab)) {
				if (setting.isPersistent()) {
					try {
						setting.fromJson(obj.get(setting.getName()));
					} catch (Exception ignored) {
						// keep default for bad values
					}
				}
			}
		}
		if (root.has("customPresets") && root.get("customPresets").isJsonObject()) {
			customPresets.clear();
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("customPresets").entrySet()) {
				if (e.getValue().isJsonObject()) {
					customPresets.put(e.getKey(), ThemePreset.fromJson(e.getKey(), e.getValue().getAsJsonObject()));
				}
			}
		}
	}

	/**
	 * Called by ConfigManager at startup. A saved font that is not one of the five bundled fonts
	 * (removed fonts, the vanilla font, custom fonts) is switched to Inter Medium and logged.
	 */
	public void load(JsonObject root) {
		fromJson(root);
		// 1.27.0 replaced the panel menu with a dark window menu: older configs get the new
		// default colours once (their own colours can be picked again on the Theme page).
		if (!root.has("version") || root.get("version").getAsInt() < 3) {
			applyPreset(ThemePreset.DEFAULT);
			gradientHeader.set(false);
			setStatus("");
		}
		FontManager.migrateSavedFont(font);
		committed = toJson();
	}

	/**
	 * Font picker click: keep the new font even outside "Apply" by writing it into the committed
	 * snapshot too, so the caller's config save stores it (other unapplied edits stay unapplied).
	 */
	public void commitFont() {
		JsonObject fontTab = committed.getAsJsonObject(Tab.FONT.name().toLowerCase(java.util.Locale.ROOT));
		if (fontTab != null) {
			fontTab.add(font.getName(), font.toJson());
		}
		setStatus("Font set to " + font.getLabel() + " and saved");
	}

	private JsonObject presetsJson() {
		JsonObject obj = new JsonObject();
		for (ThemePreset preset : customPresets.values()) {
			obj.add(preset.name(), preset.toJson());
		}
		return obj;
	}
}
