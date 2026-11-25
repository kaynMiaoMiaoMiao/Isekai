package com.kayn.isekai.entity;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.entity.custom.LandBirdEntity;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;

public final class IsekaiEntity {

    public static final EntityType<LandBirdEntity> LAND_BIRD = Registry.register(
            Registries.ENTITY_TYPE,
            Isekai.id("land_bird"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, LandBirdEntity::new)
                    .dimensions(EntityDimensions.changing(
                            1.25f * LandBirdEntity.ADULT_MODEL_SCALE,
                            2.3125f * LandBirdEntity.ADULT_MODEL_SCALE))
                    .build()
    );

    private IsekaiEntity() {
    }

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(LAND_BIRD, LandBirdEntity.createBirdAttributes());
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                entity instanceof LandBirdEntity bird ? bird.interactCommand(player, hand) : ActionResult.PASS);
    }
}
