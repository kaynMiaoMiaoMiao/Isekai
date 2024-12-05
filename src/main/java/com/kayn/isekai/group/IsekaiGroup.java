package com.kayn.isekai.group;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.item.IsekaiItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * desc
 *
 * @author: kayn
 * 2024-12-05 10:39
 **/
public class IsekaiGroup {

    public static final ItemGroup LSEKAI = Registry.register(
            Registries.ITEM_GROUP,
            new Identifier(Isekai.MOD_ID,"isekai"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.isekai"))
                    .entries(IsekaiGroup::addItems)
                    .icon(IsekaiItem.GRAMME_COIN::getDefaultStack)
                    .build()
    );

    public static void addItems(ItemGroup.DisplayContext displayContext, ItemGroup.Entries entries) {
        entries.add(IsekaiItem.GRAMME_COIN);
    }

    public static void initialize(){

    }
}
