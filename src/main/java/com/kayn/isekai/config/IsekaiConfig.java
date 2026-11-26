package com.kayn.isekai.config;

import com.kayn.isekai.entity.custom.LandBirdEntity;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;

public final class IsekaiConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(IsekaiConfig.class);
    private static final String FILE_NAME = "isekai.properties";

    private static final String EGG_INCUBATION_KEY = "eggIncubationSeconds";
    private static final int DEFAULT_EGG_SECONDS = 1_200;
    private static final int MAX_EGG_SECONDS = Integer.MAX_VALUE / 20;

    private static final String TAME_CHANCE_KEY = "tameChancePercent";
    private static final int DEFAULT_TAME_CHANCE = 15;

    private static final String RIDING_WALK_SPEED_KEY = "ridingWalkSpeedPercent";
    private static final int DEFAULT_RIDING_WALK_SPEED = 40;

    private static final String RIDING_SPRINT_SPEED_KEY = "ridingSprintSpeedPercent";
    private static final int DEFAULT_RIDING_SPRINT_SPEED = 80;

    private static final String GLIDE_SPEED_KEY = "glideSpeedPercent";
    private static final int DEFAULT_GLIDE_SPEED = 120;
    private static final int MAX_GLIDE_SPEED = 300;
    // Blocks per second. The floor keeps the glide sinking faster than 1/32 block per tick, or the server
    // kicks the rider for floating a vehicle.
    private static final String GLIDE_FALL_SPEED_KEY = "glideFallSpeed";
    private static final double DEFAULT_GLIDE_FALL_SPEED = 1.44;
    private static final double MIN_GLIDE_FALL_SPEED = 0.7;
    private static final double MAX_GLIDE_FALL_SPEED = 60.0;

    private static final String SPAWN_WEIGHT_KEY = "spawnWeight";
    private static final int DEFAULT_SPAWN_WEIGHT = 3;
    private static final String SPAWN_MIN_KEY = "spawnMinGroupSize";
    private static final int DEFAULT_SPAWN_MIN = 1;
    private static final String SPAWN_MAX_KEY = "spawnMaxGroupSize";
    private static final int DEFAULT_SPAWN_MAX = 3;

    /** Plumage colour names, index 0 = plumage 1. Only used for config comments. */
    private static final String[] PLUMAGE_NAMES = {"橙红 Orange", "深蓝 Navy", "粉棕 Tan", "翠绿 Jade", "墨黑 Ink", "银蓝 Silver Blue"};
    private static final String[] DEFAULT_BIOMES = {
            "minecraft:desert,#minecraft:is_badlands,#minecraft:is_savanna",
            "minecraft:taiga,minecraft:old_growth_pine_taiga,minecraft:old_growth_spruce_taiga,minecraft:windswept_hills",
            "minecraft:cherry_grove,minecraft:meadow",
            "minecraft:plains,minecraft:sunflower_plains,minecraft:forest,minecraft:flower_forest,minecraft:birch_forest,minecraft:sparse_jungle",
            "minecraft:dark_forest,minecraft:swamp,minecraft:mangrove_swamp",
            "minecraft:snowy_plains,minecraft:snowy_taiga,minecraft:ice_spikes,minecraft:grove"
    };

    private static int eggIncubationSeconds = DEFAULT_EGG_SECONDS;
    private static int tameChancePercent = DEFAULT_TAME_CHANCE;
    private static int ridingWalkSpeedPercent = DEFAULT_RIDING_WALK_SPEED;
    private static int ridingSprintSpeedPercent = DEFAULT_RIDING_SPRINT_SPEED;
    private static int glideSpeedPercent = DEFAULT_GLIDE_SPEED;
    private static double glideFallSpeed = DEFAULT_GLIDE_FALL_SPEED;
    private static int spawnWeight = DEFAULT_SPAWN_WEIGHT;
    private static int spawnMinGroupSize = DEFAULT_SPAWN_MIN;
    private static int spawnMaxGroupSize = DEFAULT_SPAWN_MAX;
    private static List<BiomeMatcher> plumageBiomes = List.of();

    private IsekaiConfig() {
    }

    private static String biomesKey(int plumage) {
        return "plumage" + plumage + "Biomes";
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties properties = new Properties();
        boolean exists = Files.exists(path);
        if (exists) {
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException exception) {
                LOGGER.warn("Could not read {}. Using defaults.", path, exception);
            }
        }

        eggIncubationSeconds = readInt(properties, EGG_INCUBATION_KEY, DEFAULT_EGG_SECONDS, 1, MAX_EGG_SECONDS);
        tameChancePercent = readInt(properties, TAME_CHANCE_KEY, DEFAULT_TAME_CHANCE, 0, 100);
        ridingWalkSpeedPercent = readInt(properties, RIDING_WALK_SPEED_KEY, DEFAULT_RIDING_WALK_SPEED, 1, 100);
        ridingSprintSpeedPercent = readInt(properties, RIDING_SPRINT_SPEED_KEY, DEFAULT_RIDING_SPRINT_SPEED, 1, 200);
        glideSpeedPercent = readInt(properties, GLIDE_SPEED_KEY, DEFAULT_GLIDE_SPEED, 1, MAX_GLIDE_SPEED);
        glideFallSpeed = readDouble(properties, GLIDE_FALL_SPEED_KEY, DEFAULT_GLIDE_FALL_SPEED,
                MIN_GLIDE_FALL_SPEED, MAX_GLIDE_FALL_SPEED);
        spawnWeight = readInt(properties, SPAWN_WEIGHT_KEY, DEFAULT_SPAWN_WEIGHT, 0, 1_000);
        spawnMinGroupSize = readInt(properties, SPAWN_MIN_KEY, DEFAULT_SPAWN_MIN, 1, 16);
        spawnMaxGroupSize = readInt(properties, SPAWN_MAX_KEY, DEFAULT_SPAWN_MAX, 1, 16);
        if (spawnMaxGroupSize < spawnMinGroupSize) {
            LOGGER.warn("{} is smaller than {}; using {} for both.", SPAWN_MAX_KEY, SPAWN_MIN_KEY, spawnMinGroupSize);
            spawnMaxGroupSize = spawnMinGroupSize;
        }
        List<BiomeMatcher> biomes = new ArrayList<>();
        for (int plumage = 1; plumage <= LandBirdEntity.PLUMAGE_COUNT; plumage++) {
            biomes.add(read(properties, biomesKey(plumage), DEFAULT_BIOMES[plumage - 1], BiomeMatcher::parse));
        }
        plumageBiomes = List.copyOf(biomes);

        // Rewrite the file when it is new or predates some keys, keeping every value the player set.
        if (!exists || !properties.stringPropertyNames().containsAll(allKeys())) {
            save(path);
        }
    }

    private static List<String> allKeys() {
        List<String> keys = new ArrayList<>(List.of(EGG_INCUBATION_KEY, TAME_CHANCE_KEY, RIDING_WALK_SPEED_KEY,
                RIDING_SPRINT_SPEED_KEY, GLIDE_SPEED_KEY, GLIDE_FALL_SPEED_KEY, SPAWN_WEIGHT_KEY, SPAWN_MIN_KEY, SPAWN_MAX_KEY));
        for (int plumage = 1; plumage <= LandBirdEntity.PLUMAGE_COUNT; plumage++) {
            keys.add(biomesKey(plumage));
        }
        return keys;
    }

    private static int readInt(Properties properties, String key, int fallback, int min, int max) {
        return read(properties, key, Integer.toString(fallback), text -> {
            int value = Integer.parseInt(text.trim());
            if (value < min || value > max) {
                throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
            }
            return value;
        });
    }

    private static double readDouble(Properties properties, String key, double fallback, double min, double max) {
        return read(properties, key, Double.toString(fallback), text -> {
            double value = Double.parseDouble(text.trim());
            if (!(value >= min && value <= max)) {
                throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
            }
            return value;
        });
    }

    private static <T> T read(Properties properties, String key, String fallback, Function<String, T> parser) {
        String text = properties.getProperty(key);
        if (text != null) {
            try {
                return parser.apply(text);
            } catch (RuntimeException exception) {
                LOGGER.warn("Invalid value for {} in {}: {}. Using the default {}.", key, FILE_NAME, text, fallback);
            }
        }
        return parser.apply(fallback);
    }

    private static void save(Path path) {
        StringBuilder out = new StringBuilder();
        out.append("# Isekai config. Restart the game or server to apply changes.\n\n");
        out.append("# Seconds of heat required to hatch a land bird egg.\n");
        out.append(EGG_INCUBATION_KEY).append('=').append(eggIncubationSeconds).append("\n\n");
        out.append("# Chance (0-100) that one carrot tames a wild land bird.\n");
        out.append(TAME_CHANCE_KEY).append('=').append(tameChancePercent).append("\n\n");
        out.append("# Riding speeds as percentages of the bird's base speed. Walking never outpaces sprinting.\n");
        out.append("# Without the sprint key (1-100).\n");
        out.append(RIDING_WALK_SPEED_KEY).append('=').append(ridingWalkSpeedPercent).append('\n');
        out.append("# With the sprint key: top running speed (1-200).\n");
        out.append(RIDING_SPRINT_SPEED_KEY).append('=').append(ridingSprintSpeedPercent).append("\n\n");
        out.append("# Gliding (hold jump in the air while riding). Plain falls drop at normal speed.\n");
        out.append("# Glide flying speed, as a percentage (1-" + MAX_GLIDE_SPEED + ") of the normal riding speed (walking or sprinting).\n");
        out.append(GLIDE_SPEED_KEY).append('=').append(glideSpeedPercent).append('\n');
        out.append("# Glide sinking speed in blocks per second (")
                .append(MIN_GLIDE_FALL_SPEED).append('-').append(MAX_GLIDE_FALL_SPEED).append("). Lower glides further.\n");
        out.append(GLIDE_FALL_SPEED_KEY).append('=').append(glideFallSpeed).append("\n\n");
        out.append("# Natural spawning. Weight 0 disables it. For reference, horses use weight 5 in plains.\n");
        out.append(SPAWN_WEIGHT_KEY).append('=').append(spawnWeight).append('\n');
        out.append(SPAWN_MIN_KEY).append('=').append(spawnMinGroupSize).append('\n');
        out.append(SPAWN_MAX_KEY).append('=').append(spawnMaxGroupSize).append("\n\n");
        out.append("# Biomes where each plumage spawns: comma-separated biome ids or #biome_tags.\n");
        out.append("# Leave a value empty to stop that plumage spawning naturally.\n");
        for (int plumage = 1; plumage <= LandBirdEntity.PLUMAGE_COUNT; plumage++) {
            out.append("# ").append(plumage).append(": ").append(PLUMAGE_NAMES[plumage - 1]).append('\n');
            out.append(biomesKey(plumage)).append('=').append(plumageBiomes.get(plumage - 1).source()).append('\n');
        }
        try {
            Files.createDirectories(path.getParent());
            // Properties files are Latin-1 by default; escape the Chinese comments to stay portable.
            Files.writeString(path, escapeNonAscii(out.toString()), StandardCharsets.ISO_8859_1);
        } catch (IOException exception) {
            LOGGER.warn("Could not write {}.", path, exception);
        }
    }

    private static String escapeNonAscii(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c < 0x80) {
                out.append(c);
            } else {
                out.append(String.format("\\u%04X", (int) c));
            }
        }
        return out.toString();
    }

    public static int eggIncubationSeconds() {
        return eggIncubationSeconds;
    }

    public static int eggIncubationTicks() {
        return eggIncubationSeconds * 20;
    }

    public static int tameChancePercent() {
        return tameChancePercent;
    }

    public static int ridingWalkSpeedPercent() {
        return ridingWalkSpeedPercent;
    }

    public static int ridingSprintSpeedPercent() {
        return ridingSprintSpeedPercent;
    }

    public static int glideSpeedPercent() {
        return glideSpeedPercent;
    }

    /** Glide sinking speed in blocks per tick. */
    public static double glideFallSpeedPerTick() {
        return glideFallSpeed / 20.0;
    }

    public static int spawnWeight() {
        return spawnWeight;
    }

    public static int spawnMinGroupSize() {
        return spawnMinGroupSize;
    }

    public static int spawnMaxGroupSize() {
        return spawnMaxGroupSize;
    }

    /** Biome list for a plumage, 1-based. */
    public static BiomeMatcher plumageBiomes(int plumage) {
        return plumageBiomes.get(plumage - 1);
    }
}
