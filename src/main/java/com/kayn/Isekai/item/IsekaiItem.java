package com.kayn.Isekai.item;

import com.kayn.Isekai.Isekai;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * desc
 *
 * @author: kayn
 * 2024-12-05 13:54
 **/
public class LsekaiItem {

    public static final Item GRAMME_COIN = register(new Item(new Item.Settings()), "gramme_coin");

    private static Item register(Item item, String id) {

        Identifier itemIdentifier = new Identifier(Isekai.MOD_ID, id);

        return Registry.register(Registries.ITEM, itemIdentifier, item);
    }

    public static void initialize(){

        Event<ItemGroupEvents.ModifyEntries> modifyEntriesEvent = ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS);

        modifyEntriesEvent.register((itemGroup) -> {
            itemGroup.add(LsekaiItem.GRAMME_COIN);
        });

    }

}
