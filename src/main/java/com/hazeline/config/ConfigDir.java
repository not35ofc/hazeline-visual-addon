package com.hazeline.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import net.fabricmc.loader.api.FabricLoader;

import com.hazeline.Hazeline;

/**
 * {@code config/hazeline/}. The mod was called "Pasta Client" before 1.41.0 and kept its files in
 * {@code config/pastaclient/}; the first time the new folder is needed and does not exist yet, the old
 * {@code config.json} is copied over (the old folder is left untouched as a backup).
 */
public final class ConfigDir {
	private static Path dir;

	private ConfigDir() {
	}

	public static synchronized Path get() {
		if (dir == null) {
			Path config = FabricLoader.getInstance().getConfigDir();
			Path target = config.resolve("hazeline");
			Path legacy = config.resolve("pastaclient").resolve("config.json");
			if (!Files.exists(target) && Files.isRegularFile(legacy)) {
				try {
					Files.createDirectories(target);
					Files.copy(legacy, target.resolve("config.json"), StandardCopyOption.COPY_ATTRIBUTES);
					Hazeline.LOGGER.info("Copied the settings of the old Pasta Client (config/pastaclient/config.json) to config/hazeline/");
				} catch (IOException e) {
					Hazeline.LOGGER.warn("Could not copy the old Pasta Client settings; starting with defaults", e);
				}
			}
			dir = target;
		}
		return dir;
	}
}
