package com.kayn.isekai.item;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.block.IsekaiBlock;
import com.kayn.isekai.entity.IsekaiEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.List;

public final class IsekaiItem {

    public static final Item GRAMME_COIN = register("gramme_coin", new Item(new Item.Settings()));

    public static final Item LAND_BIRD_SPAWN_EGG = register(
            "yokibird_spawn_egg",
            new SpawnEggItem(IsekaiEntity.LAND_BIRD, 0xF8F8FF, 0xE3773F, new Item.Settings())
    );

    public static final List<Item> LAND_BIRD_EGGS = IsekaiBlock.LAND_BIRD_EGGS.stream()
            .map(block -> register(IsekaiBlock.eggPath(block.getPlumage()),
                    new BlockItem(block, new Item.Settings().maxCount(16))))
            .toList();

    public static Item egg(int plumage) {
        return LAND_BIRD_EGGS.get(plumage - 1);
    }

    private IsekaiItem() {
    }

    private static Item register(String path, Item item) {
        return Registry.register(Registries.ITEM, Isekai.id(path), item);
    }

    public static void initialize() {
        // Calling this method initializes the item registrations above.
    }
}
