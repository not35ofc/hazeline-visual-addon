package com.hazeline.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import com.hazeline.Hazeline;
import com.hazeline.appearance.Appearance;
import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.module.ModuleManager;
import com.hazeline.module.modules.sounds.SoundsAndClicks;
import com.hazeline.setting.Setting;

/**
 * Reads and writes {@code config/hazeline/config.json}.
 *
 * <p>Layout:
 * <pre>
 * {
 *   "modules": { "Storage ESP": { "enabled": true, "settings": { "Line width": 2.0, ... } }, ... },
 *   "appearance": { "font": {...}, "colors": {...}, "customPresets": {...} },  ("sounds" before 1.35.0, migrated)
 *   "panels":  { "RENDER": { "x": 10, "y": 10, "collapsed": false }, ... }
 * }
 * </pre>
 * Unknown keys are ignored and missing keys keep their defaults, so the file survives
 * mod updates that add or remove settings. Modules that were removed from the mod (see
 * {@link #REMOVED_MODULES}) are skipped with one log line; the first time such entries are found
 * the old file is copied to {@code config.json.removed-modules.bak} before it is rewritten
 * without them. Writes go to a temp file first and are then
 * moved into place so a crash mid-write cannot corrupt the config.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final ConfigManager INSTANCE = new ConfigManager();

	/**
	 * Modules removed in 1.28.0 (Hazeline became customization-only). Their entries in old
	 * configs are skipped and dropped on the next save.
	 */
	public static final Set<String> REMOVED_MODULES = Set.of(
			"Crystal Macro", "Anchor Macro", "Aim Assist", "Auto Totem", "Auto Armor", "Criticals", "Hitboxes",
			"Free Look", "Free Cam", "Full Bright", "Hole ESP", "Player/Mob ESP", "No Render",
			"Sus Chunk Finder", "Storage ESP", "Stash Finder", "Spawner Finder",
			"Auto Eat", "Auto Sign", "Auto Tool", "EXP Thrower", "Name Protect",
			"Auto Walk", "Auto Jump", "Auto Rocket", "Safe Walk", "Auto Sneak", "Auto Sprint",
			"Nametags");

	private final Path file = ConfigDir.get().resolve("config.json");
	private final Map<Category, PanelState> panels = new EnumMap<>(Category.class);

	private ConfigManager() {
		resetPanels();
	}

	public static ConfigManager get() {
		return INSTANCE;
	}

	/** Persisted position/collapsed state of one GUI panel. */
	public static final class PanelState {
		public int x;
		public int y;
		public boolean collapsed;

		PanelState(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}

	public PanelState getPanel(Category category) {
		return panels.get(category);
	}

	/** Default layout: panels side by side along the top of the screen. */
	public void resetPanels() {
		resetPanels(150);
	}

	/** Panels side by side, {@code spacing} pixels apart (the GUI passes the scaled panel width). */
	public void resetPanels(int spacing) {
		int x = 10;
		for (Category category : Category.values()) {
			panels.put(category, new PanelState(x, 10));
			x += spacing;
		}
	}

	public void load() {
		if (!Files.exists(file)) {
			save(); // write defaults so users can see / edit the file
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (root.isJsonObject()) {
				apply(root.getAsJsonObject());
			}
		} catch (Exception e) {
			Hazeline.LOGGER.error("Failed to read {}, using defaults", file, e);
		}
	}

	private void apply(JsonObject root) {
		if (root.has("modules") && root.get("modules").isJsonObject()) {
			JsonObject modules = root.getAsJsonObject("modules");
			migrateRemovedModules(modules);
			for (Module module : ModuleManager.get().getModules()) {
				JsonElement element = modules.get(module.getName());
				if (element == null || !element.isJsonObject()) {
					continue;
				}
				JsonObject obj = element.getAsJsonObject();
				if (obj.has("settings") && obj.get("settings").isJsonObject()) {
					JsonObject settings = obj.getAsJsonObject("settings");
					Set<String> known = new HashSet<>();
					for (Setting<?> setting : module.getSettings()) {
						known.add(setting.getName());
					}
					for (String key : settings.keySet()) {
						if (!known.contains(key)) {
							Hazeline.LOGGER.warn("Config: skipping unknown setting '{}' of module '{}'", key, module.getName());
						}
					}
					for (Setting<?> setting : module.getSettings()) {
						if (!setting.isPersistent()) {
							continue;
						}
						try {
							setting.fromJson(migrateValue(module.getName(), setting.getName(), settings.get(setting.getName())));
						} catch (Exception e) {
							Hazeline.LOGGER.warn("Ignoring bad value for {}/{}", module.getName(), setting.getName());
						}
					}
				}
				if (obj.has("enabled") && obj.get("enabled").isJsonPrimitive()) {
					module.setEnabled(obj.get("enabled").getAsBoolean());
				}
			}
		}

		if (root.has("appearance") && root.get("appearance").isJsonObject()) {
			JsonObject appearance = root.getAsJsonObject("appearance");
			// 1.35.0: the menu sounds moved from Settings > Sounds (appearance "sounds") into the Sounds and
			// Clicks module - take them over once, while the config has no entry for that module yet.
			boolean hasSoundsModule = root.has("modules") && root.get("modules").isJsonObject()
					&& root.getAsJsonObject("modules").has("Sounds and Clicks");
			if (!hasSoundsModule && appearance.has("sounds") && appearance.get("sounds").isJsonObject()) {
				ModuleManager.get().get(SoundsAndClicks.class).migrateFromAppearance(appearance.getAsJsonObject("sounds"));
			}
			Appearance.get().load(appearance);
		}

		if (root.has("panels") && root.get("panels").isJsonObject()) {
			JsonObject panelsJson = root.getAsJsonObject("panels");
			placeNewPanels(panelsJson);
			for (Category category : Category.values()) {
				JsonElement element = panelsJson.get(category.name());
				if (element == null || !element.isJsonObject()) {
					continue;
				}
				JsonObject obj = element.getAsJsonObject();
				PanelState state = panels.get(category);
				if (obj.has("x")) state.x = obj.get("x").getAsInt();
				if (obj.has("y")) state.y = obj.get("y").getAsInt();
				if (obj.has("collapsed")) state.collapsed = obj.get("collapsed").getAsBoolean();
			}
		}
	}

	/**
	 * Logs (and skips) entries of modules that no longer exist. Removed modules get one summary
	 * line and a one-time backup of the old file; any other unknown module name is warned about
	 * individually. Nothing here throws: a broken entry only costs that entry.
	 */
	/** Saved values that changed with the rename to Hazeline (Tag background PASTA -> HAZELINE, default wordmark text). */
	private static JsonElement migrateValue(String module, String setting, JsonElement value) {
		if ("Name Tag".equals(module) && "Tag background".equals(setting) && value != null && value.isJsonPrimitive()
				&& "PASTA".equalsIgnoreCase(value.getAsString())) {
			return new com.google.gson.JsonPrimitive("HAZELINE");
		}
		// The old default wordmark text was saved into config.json; it follows the new name.
		if ("Custom Menus".equals(module) && "Wordmark text".equals(setting) && value != null && value.isJsonPrimitive()
				&& "Pasta Client".equals(value.getAsString())) {
			return new com.google.gson.JsonPrimitive("Hazeline");
		}
		return value;
	}

	private void migrateRemovedModules(JsonObject modules) {
		Set<String> current = new HashSet<>();
		for (Module module : ModuleManager.get().getModules()) {
			current.add(module.getName());
		}
		List<String> removed = new ArrayList<>();
		for (String name : modules.keySet()) {
			if (current.contains(name)) {
				continue;
			}
			if (REMOVED_MODULES.contains(name)) {
				removed.add(name);
			} else {
				Hazeline.LOGGER.warn("Config: skipping unknown module '{}'", name);
			}
		}
		if (removed.isEmpty()) {
			return;
		}
		Hazeline.LOGGER.warn("Config: skipping {} removed module(s): {}. They are dropped on the next save.",
				removed.size(), String.join(", ", removed));
		Path backup = file.resolveSibling("config.json.removed-modules.bak");
		try {
			if (!Files.exists(backup)) {
				Files.copy(file, backup);
				Hazeline.LOGGER.info("Config: old config backed up to {}", backup);
			}
		} catch (IOException | RuntimeException e) {
			Hazeline.LOGGER.warn("Config: could not back up the old config", e);
		}
	}

	/**
	 * Categories added in an update (not in the saved file yet) are placed to the right of the
	 * saved panels instead of on top of one of them.
	 */
	private void placeNewPanels(JsonObject panelsJson) {
		int right = 10;
		for (Category category : Category.values()) {
			JsonElement element = panelsJson.get(category.name());
			if (element != null && element.isJsonObject() && element.getAsJsonObject().has("x")) {
				right = Math.max(right, element.getAsJsonObject().get("x").getAsInt() + 150);
			}
		}
		for (Category category : Category.values()) {
			if (!panelsJson.has(category.name())) {
				panels.put(category, new PanelState(right, 10));
				right += 150;
			}
		}
	}

	public void save() {
		JsonObject root = new JsonObject();

		JsonObject modules = new JsonObject();
		for (Module module : ModuleManager.get().getModules()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("enabled", module.isEnabled());
			JsonObject settings = new JsonObject();
			for (Setting<?> setting : module.getSettings()) {
				if (setting.isPersistent()) {
					settings.add(setting.getName(), setting.toJson());
				}
			}
			obj.add("settings", settings);
			modules.add(module.getName(), obj);
		}
		root.add("modules", modules);
		// While the Appearance editor is open this is the last *applied* state, not the preview.
		root.add("appearance", Appearance.get().jsonForSave());

		JsonObject panelsJson = new JsonObject();
		for (Map.Entry<Category, PanelState> entry : panels.entrySet()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("x", entry.getValue().x);
			obj.addProperty("y", entry.getValue().y);
			obj.addProperty("collapsed", entry.getValue().collapsed);
			panelsJson.add(entry.getKey().name(), obj);
		}
		root.add("panels", panelsJson);

		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("config.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			Hazeline.LOGGER.error("Failed to save {}", file, e);
		}
	}
}
