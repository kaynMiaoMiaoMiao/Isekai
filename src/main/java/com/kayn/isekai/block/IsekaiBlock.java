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
    public static final List<LandBirdEggBlock> LAND_BIRD_EGGS =
            IntStream.rangeClosed(1, LandBirdEntity.PLUMAGE_COUNT)
                    .mapToObj(plumage -> Registry.register(Registries.BLOCK, Isekai.id(eggPath(plumage)),
                            new LandBirdEggBlock(plumage,
                                    AbstractBlock.Settings.create().strength(0.5f)
                                            .nonOpaque().sounds(BlockSoundGroup.BONE))))
                    .toList();

    public static final BlockEntityType<LandBirdEggBlockEntity> LAND_BIRD_EGG = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, Isekai.id("land_bird_egg"),
            FabricBlockEntityTypeBuilder.create(LandBirdEggBlockEntity::new,
                    LAND_BIRD_EGGS.toArray(Block[]::new)).build());

    private IsekaiBlock() {
    }

    public static String eggPath(int plumage) {
        return plumage == 1 ? "land_bird_egg" : "land_bird_egg_" + plumage;
    }

    public static void initialize() {
        // Calling this method initializes the block and block entity registrations above.
    }
}
