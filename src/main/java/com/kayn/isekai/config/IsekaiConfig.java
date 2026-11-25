package com.kayn.isekai.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

public final class IsekaiConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(IsekaiConfig.class);
    private static final String EGG_INCUBATION_KEY = "eggIncubationSeconds";
    private static final int DEFAULT_EGG_SECONDS = 1_200;
    private static final int MAX_EGG_SECONDS = Integer.MAX_VALUE / 20;
    private static int eggIncubationSeconds = DEFAULT_EGG_SECONDS;

    private IsekaiConfig() {
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("isekai.properties");
        try {
            createDefaultFile(path);
            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            }
            int seconds = Integer.parseInt(
                    properties.getProperty(EGG_INCUBATION_KEY, Integer.toString(DEFAULT_EGG_SECONDS)).trim());
            if (seconds < 1 || seconds > MAX_EGG_SECONDS) {
                throw new IllegalArgumentException(EGG_INCUBATION_KEY + " must be between 1 and " + MAX_EGG_SECONDS);
            }
            eggIncubationSeconds = seconds;
        } catch (IOException | IllegalArgumentException exception) {
            eggIncubationSeconds = DEFAULT_EGG_SECONDS;
            LOGGER.warn("Could not load {}. Using the default egg incubation time of {} seconds.",
                    path, DEFAULT_EGG_SECONDS, exception);
        }
    }

    private static void createDefaultFile(Path path) throws IOException {
        if (Files.exists(path)) {
            return;
        }
        Files.createDirectories(path.getParent());
        Properties defaults = new Properties();
        defaults.setProperty(EGG_INCUBATION_KEY, Integer.toString(DEFAULT_EGG_SECONDS));
        try (var writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            defaults.store(writer, "Seconds of heat required to hatch a land bird egg. Restart to apply changes.");
        } catch (FileAlreadyExistsException ignored) {
            // Another process created the file before this one could write it.
        }
    }

    public static int eggIncubationSeconds() {
        return eggIncubationSeconds;
    }

    public static int eggIncubationTicks() {
        return eggIncubationSeconds * 20;
    }
}
