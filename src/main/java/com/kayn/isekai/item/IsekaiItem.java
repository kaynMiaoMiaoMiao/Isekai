package com.kayn.isekai.item;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.entity.IsekaiEntity;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * desc
 *
 * @author: kayn
 * 2024-12-05 13:54
 **/
public class IsekaiItem {

    public static final Item GRAMME_COIN = register(new Item(new Item.Settings()), "gramme_coin");

    public static final Item BIRD6_SPAWN_EGG = register(
            new SpawnEggItem(
                    IsekaiEntity.BIRD6,
                    0xD57E36,
                    0x1D0D00,
                    new FabricItemSettings()
            ),
            "bird6_spawn_egg"
    );


    private static Item register(Item item, String id) {

        Identifier itemIdentifier = new Identifier(Isekai.MOD_ID, id);

        return Registry.register(Registries.ITEM, itemIdentifier, item);
    }

    public static void initialize(){

        Event<ItemGroupEvents.ModifyEntries> modifyEntriesEvent = ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS);

        modifyEntriesEvent.register((itemGroup) -> {
            itemGroup.add(IsekaiItem.GRAMME_COIN);
            itemGroup.add(IsekaiItem.BIRD6_SPAWN_EGG);
        });

    }

}
