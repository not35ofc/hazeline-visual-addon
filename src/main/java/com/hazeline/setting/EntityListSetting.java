package com.hazeline.setting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;

/**
 * A set of entity types (registry ids such as {@code minecraft:player}), picked in the GUI from a
 * searchable list grouped by Players / Hostile / Animals / Water / Other. Ids of entity types
 * that do not exist (removed mods) are kept, so they come back when the mod is added again.
 */
public class EntityListSetting extends Setting<Set<String>> {

	public enum Group {
		ALL("All"), PLAYERS("Players"), HOSTILE("Hostile"), ANIMALS("Animals"), WATER("Water"), OTHER("Other");

		private final String label;

		Group(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** One pickable entity type. */
	public record Entry(String id, String label, Group group) {
	}

	private static List<Entry> entries;

	public EntityListSetting(String name, String description, String... defaultIds) {
		super(name, description, Collections.unmodifiableSet(new LinkedHashSet<>(List.of(defaultIds))));
	}

	public boolean contains(String id) {
		return value.contains(id);
	}

	public void toggle(String id) {
		setAll(List.of(id), !contains(id));
	}

	/** Turns every id in {@code ids} on or off. */
	public void setAll(Collection<String> ids, boolean on) {
		Set<String> next = new LinkedHashSet<>(value);
		if (on) {
			next.addAll(ids);
		} else {
			next.removeAll(ids);
		}
		set(next);
	}

	@Override
	protected Set<String> sanitize(Set<String> newValue) {
		return Collections.unmodifiableSet(new LinkedHashSet<>(newValue));
	}

	/** Every entity type of the registry, sorted by group and then by name (built once). */
	public static synchronized List<Entry> entries() {
		if (entries == null) {
			List<Entry> list = new ArrayList<>();
			for (EntityType<?> type : Registries.ENTITY_TYPE) {
				String id = EntityType.getId(type).toString();
				list.add(new Entry(id, type.getName().getString(), groupOf(type)));
			}
			list.sort(Comparator.comparing((Entry e) -> e.group().ordinal()).thenComparing(e -> e.label().toLowerCase()));
			entries = Collections.unmodifiableList(list);
		}
		return entries;
	}

	private static Group groupOf(EntityType<?> type) {
		if (type == EntityType.PLAYER) {
			return Group.PLAYERS;
		}
		SpawnGroup g = type.getSpawnGroup();
		if (g == SpawnGroup.MONSTER) {
			return Group.HOSTILE;
		}
		if (g == SpawnGroup.CREATURE || g == SpawnGroup.AMBIENT || g == SpawnGroup.AXOLOTLS) {
			return Group.ANIMALS;
		}
		if (g == SpawnGroup.WATER_CREATURE || g == SpawnGroup.WATER_AMBIENT || g == SpawnGroup.UNDERGROUND_WATER_CREATURE) {
			return Group.WATER;
		}
		return Group.OTHER;
	}

	/** Short text for the closed row, e.g. "Players, Zombie +3". */
	public String summary() {
		if (value.isEmpty()) {
			return "None";
		}
		List<String> names = new ArrayList<>();
		for (Entry e : entries()) {
			if (value.contains(e.id())) {
				names.add(e.label());
			}
		}
		if (names.isEmpty()) {
			return value.size() + " selected";
		}
		if (names.size() <= 2) {
			return String.join(", ", names);
		}
		return names.get(0) + ", " + names.get(1) + " +" + (names.size() - 2);
	}

	@Override
	public JsonElement toJson() {
		JsonArray array = new JsonArray();
		for (String id : value) {
			array.add(id);
		}
		return array;
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json == null || !json.isJsonArray()) {
			return;
		}
		Set<String> next = new LinkedHashSet<>();
		for (JsonElement e : json.getAsJsonArray()) {
			if (e.isJsonPrimitive()) {
				String id = e.getAsString().trim();
				if (!id.isEmpty()) {
					next.add(id);
				}
			}
		}
		set(next);
	}
}
