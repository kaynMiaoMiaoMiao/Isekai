package com.kayn.isekai.entity.client;

import com.kayn.isekai.entity.custom.LandBirdEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LandBirdRenderer extends GeoEntityRenderer<LandBirdEntity> {

    public LandBirdRenderer(EntityRendererFactory.Context context) {
        super(context, new LandBirdModel());
        addRenderLayer(new LandBirdArmorLayer(this));
    }

    @Override
    public void render(LandBirdEntity bird, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertices, int light) {
        // Juveniles use their own models, scaled down to roughly a chicken's size.
        withScale(bird.isBaby() ? LandBirdEntity.BABY_MODEL_SCALE : LandBirdEntity.ADULT_MODEL_SCALE);
        shadowRadius = bird.isBaby() ? 0.3f : 0.6f * LandBirdEntity.ADULT_MODEL_SCALE;
        super.render(bird, yaw, tickDelta, matrices, vertices, light);
    }

    @Override
    protected float getDeathMaxRotation(LandBirdEntity entity) {
        return 0;
    }
}
