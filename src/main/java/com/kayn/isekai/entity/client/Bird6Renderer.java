package com.kayn.isekai.entity.client;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.entity.custom.Bird6Entity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * @author kayn
 * @date 2024/12/6 23:09
 **/
public class Bird6Renderer extends GeoEntityRenderer<Bird6Entity> {

    public Bird6Renderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new Bird6Model());
    }

    @Override
    public Identifier getTextureLocation(Bird6Entity animatable) {
        return new Identifier(Isekai.MOD_ID, "textures/entity/bird6.png");
    }

    @Override
    public void render(Bird6Entity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {

        if (entity.isBaby()) {
            poseStack.scale(0.4f, 0.4f, 0.4f);
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
