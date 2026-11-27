package com.kayn.isekai.block;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.entity.custom.LandBirdEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

import java.util.List;
import java.util.stream.IntStream;

public final class IsekaiBlock {
    /** Egg colour suffixes, index 0 = plumage 1. Must stay above LAND_BIRD_EGGS, which reads it while initializing. */
    private static final String[] EGG_COLORS = {"orange", "blue", "brown", "green", "black", "white"};

    public static final List<LandBirdEggBlock> LAND_BIRD_EGGS =
            IntStream.rangeClosed(1, LandBirdEntity.PLUMAGE_COUNT)
                    .mapToObj(plumage -> Registry.register(Registries.BLOCK, Isekai.id(eggPath(plumage)),
                            new LandBirdEggBlock(plumage,
                                    AbstractBlock.Settings.create().strength(0.5f)
                                            .nonOpaque().sounds(BlockSoundGroup.BONE))))
                    .toList();

    public static final BlockEntityType<LandBirdEggBlockEntity> LAND_BIRD_EGG = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, Isekai.id("yokibird_egg"),
            FabricBlockEntityTypeBuilder.create(LandBirdEggBlockEntity::new,
                    LAND_BIRD_EGGS.toArray(Block[]::new)).build());

    private IsekaiBlock() {
    }

    public static String eggPath(int plumage) {
        return "yokibird_egg_" + EGG_COLORS[plumage - 1];
    }

    public static void initialize() {
        // Calling this method initializes the block and block entity registrations above.
    }
}
