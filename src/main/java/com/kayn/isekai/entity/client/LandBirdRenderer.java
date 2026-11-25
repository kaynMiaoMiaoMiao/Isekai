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
        // Juvenile models already have their own native dimensions.
        withScale(bird.isBaby() ? 1.0f : LandBirdEntity.ADULT_MODEL_SCALE);
        shadowRadius = bird.isBaby() ? 0.35f : 0.6f * LandBirdEntity.ADULT_MODEL_SCALE;
        super.render(bird, yaw, tickDelta, matrices, vertices, light);
    }

    @Override
    protected float getDeathMaxRotation(LandBirdEntity entity) {
        return 0;
    }
}
