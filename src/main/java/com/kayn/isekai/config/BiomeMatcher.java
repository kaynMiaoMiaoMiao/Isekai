package com.kayn.isekai.config;

import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A comma-separated biome list from the config: plain ids ({@code minecraft:desert})
 * and biome tags ({@code #minecraft:is_badlands}).
 */
public record BiomeMatcher(String source, Set<Identifier> ids, List<TagKey<Biome>> tags) {

    public static BiomeMatcher parse(String text) {
        Set<Identifier> ids = new LinkedHashSet<>();
        List<TagKey<Biome>> tags = new ArrayList<>();
        for (String raw : text.split(",")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            boolean tag = entry.startsWith("#");
            Identifier id = Identifier.tryParse(tag ? entry.substring(1) : entry);
            if (id == null) {
                throw new IllegalArgumentException("Invalid biome or biome tag: " + entry);
            }
            if (tag) {
                tags.add(TagKey.of(RegistryKeys.BIOME, id));
            } else {
                ids.add(id);
            }
        }
        return new BiomeMatcher(text.trim(), Set.copyOf(ids), List.copyOf(tags));
    }

    public boolean isEmpty() {
        return ids.isEmpty() && tags.isEmpty();
    }

    public boolean test(BiomeSelectionContext context) {
        return ids.contains(context.getBiomeKey().getValue()) || tags.stream().anyMatch(context::hasTag);
    }

    public boolean test(RegistryEntry<Biome> biome) {
        return ids.stream().anyMatch(biome::matchesId) || tags.stream().anyMatch(biome::isIn);
    }
}
