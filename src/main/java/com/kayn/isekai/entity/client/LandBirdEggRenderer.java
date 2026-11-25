package com.kayn.isekai.entity.client;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.block.LandBirdEggBlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class LandBirdEggRenderer extends GeoBlockRenderer<LandBirdEggBlockEntity> {
    private static final Identifier MODEL = Isekai.id("geo/entity/yoki_bird/egg.geo.json");
    private static final Identifier TEXTURE = Isekai.id("textures/entity/yoki_bird/egg.png");
    private static final Identifier ANIMATIONS = Isekai.id("animations/entity/yoki_bird/egg.animation.json");
    private static final int[] TINTS = {0xF58B70, 0x83B4FF, 0xEBA6B5, 0x80D6AC, 0x8D78B7, 0xCBDCFF};

    public LandBirdEggRenderer(BlockEntityRendererFactory.Context context) {
        super(new GeoModel<>() {
            @Override
            public Identifier getModelResource(LandBirdEggBlockEntity egg) {
                return MODEL;
            }

            @Override
            public Identifier getTextureResource(LandBirdEggBlockEntity egg) {
                return TEXTURE;
            }

            @Override
            public Identifier getAnimationResource(LandBirdEggBlockEntity egg) {
                return ANIMATIONS;
            }
        });
    }

    public static int tintFor(int plumage) {
        return TINTS[plumage - 1];
    }

    @Override
    public Color getRenderColor(LandBirdEggBlockEntity egg, float tickDelta, int light) {
        return Color.ofOpaque(tintFor(egg.getPlumage()));
    }
}
