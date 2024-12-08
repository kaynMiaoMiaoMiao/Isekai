package com.kayn.isekai.data;

import com.kayn.isekai.item.IsekaiItem;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.client.ModelIds;

public class ModModelProvider extends FabricModelProvider {

        public ModModelProvider(FabricDataOutput output) {
            super(output);
        }

        @Override
        public void generateBlockStateModels(BlockStateModelGenerator blockStateModelGenerator) {

            // 填充方块状态模型生成逻辑（如果有方块需要模型）
            blockStateModelGenerator.registerParentedItemModel(IsekaiItem.BIRD6_SPAWN_EGG, ModelIds.getMinecraftNamespacedItem("template_spawn_egg"));
        }

        @Override
        public void generateItemModels(ItemModelGenerator itemModelGenerator) {

            // 填充物品模型生成逻辑
            // itemModelGenerator.register(IsekaiItem.BIRD6_SPAWN_EGG, Models.GENERATED);
        }
    }