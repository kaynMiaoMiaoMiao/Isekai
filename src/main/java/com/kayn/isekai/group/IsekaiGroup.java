package com.kayn.isekai.group;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.item.IsekaiItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class IsekaiGroup {

    private IsekaiGroup() {
    }

    public static void initialize() {
        Registry.register(Registries.ITEM_GROUP, Isekai.id("isekai"), FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.isekai"))
                .icon(IsekaiItem.GRAMME_COIN::getDefaultStack)
                .entries((context, entries) -> {
                    entries.add(IsekaiItem.GRAMME_COIN);
                    entries.add(IsekaiItem.LAND_BIRD_SPAWN_EGG);
                    for (var egg : IsekaiItem.LAND_BIRD_EGGS) {
                        entries.add(egg);
                    }
                })
                .build());
    }
}
