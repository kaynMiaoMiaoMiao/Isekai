package com.kayn.isekai;

import com.kayn.isekai.block.IsekaiBlock;
import com.kayn.isekai.entity.IsekaiEntity;
import com.kayn.isekai.entity.client.LandBirdRenderer;
import com.kayn.isekai.entity.client.LandBirdEggRenderer;
import com.kayn.isekai.entity.custom.LandBirdEntity;
import com.kayn.isekai.item.IsekaiItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class IsekaiClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(IsekaiEntity.LAND_BIRD, LandBirdRenderer::new);
        BlockEntityRendererFactories.register(IsekaiBlock.LAND_BIRD_EGG, LandBirdEggRenderer::new);
        for (int plumage = 1; plumage <= LandBirdEntity.PLUMAGE_COUNT; plumage++) {
            int tint = LandBirdEggRenderer.tintFor(plumage);
            ColorProviderRegistry.ITEM.register((stack, layer) -> layer == 0 ? tint : -1, IsekaiItem.egg(plumage));
        }
    }
}
