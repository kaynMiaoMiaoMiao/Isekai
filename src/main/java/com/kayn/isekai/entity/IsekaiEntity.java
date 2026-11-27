package com.kayn.isekai.entity;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.config.BiomeMatcher;
import com.kayn.isekai.config.IsekaiConfig;
import com.kayn.isekai.entity.custom.LandBirdEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;
import net.minecraft.world.Heightmap;

public final class IsekaiEntity {

    public static final EntityType<LandBirdEntity> LAND_BIRD = Registry.register(
            Registries.ENTITY_TYPE,
            Isekai.id("yokibird"),
            FabricEntityTypeBuilder.<LandBirdEntity>createMob()
                    .spawnGroup(SpawnGroup.CREATURE)
                    .entityFactory(LandBirdEntity::new)
                    .dimensions(EntityDimensions.changing(
                            1.25f * LandBirdEntity.ADULT_MODEL_SCALE,
                            2.3125f * LandBirdEntity.ADULT_MODEL_SCALE))
                    .defaultAttributes(LandBirdEntity::createBirdAttributes)
                    .spawnRestriction(SpawnRestriction.Location.ON_GROUND,
                            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, LandBirdEntity::canSpawn)
                    .build()
    );

    private IsekaiEntity() {
    }

    public static void initialize() {
        registerNaturalSpawns();
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                entity instanceof LandBirdEntity bird ? bird.interactCommand(player, hand) : ActionResult.PASS);
    }

    private static void registerNaturalSpawns() {
        if (IsekaiConfig.spawnWeight() <= 0) {
            return;
        }
        for (int plumage = 1; plumage <= LandBirdEntity.PLUMAGE_COUNT; plumage++) {
            BiomeMatcher biomes = IsekaiConfig.plumageBiomes(plumage);
            if (!biomes.isEmpty()) {
                // A biome listed under several plumages still gets a single spawn entry.
                int current = plumage;
                BiomeModifications.addSpawn(context -> biomes.test(context) && isFirstPlumageFor(context, current),
                        SpawnGroup.CREATURE, LAND_BIRD, IsekaiConfig.spawnWeight(),
                        IsekaiConfig.spawnMinGroupSize(), IsekaiConfig.spawnMaxGroupSize());
            }
        }
    }

    private static boolean isFirstPlumageFor(BiomeSelectionContext context, int plumage) {
        for (int earlier = 1; earlier < plumage; earlier++) {
            if (IsekaiConfig.plumageBiomes(earlier).test(context)) {
                return false;
            }
        }
        return true;
    }
}
