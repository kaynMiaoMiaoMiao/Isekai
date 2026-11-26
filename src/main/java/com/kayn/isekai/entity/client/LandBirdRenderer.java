package com.kayn.isekai.entity.client;

import com.kayn.isekai.entity.custom.LandBirdEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

public class LandBirdRenderer extends GeoEntityRenderer<LandBirdEntity> {

    public LandBirdRenderer(EntityRendererFactory.Context context) {
        super(context, new LandBirdModel());
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

    /**
     * Same as GeckoLib's cube rendering minus {@code RenderUtils.fixInvertedFlatCube}. That helper flips
     * the normals of zero-thickness cubes (the feather planes) based on their camera-space direction, so a
     * plane's shading jumps as the camera or bird turns, which shows up as flickering shadows, especially
     * under shader packs. Back faces are culled by the render type instead (see {@link LandBirdModel}).
     */
    @Override
    public void renderCube(MatrixStack poseStack, GeoCube cube, VertexConsumer buffer, int packedLight,
                           int packedOverlay, float red, float green, float blue, float alpha) {
        RenderUtils.translateToPivotPoint(poseStack, cube);
        RenderUtils.rotateMatrixAroundCube(poseStack, cube);
        RenderUtils.translateAwayFromPivotPoint(poseStack, cube);

        Matrix3f normalMatrix = poseStack.peek().getNormalMatrix();
        Matrix4f poseMatrix = new Matrix4f(poseStack.peek().getPositionMatrix());
        for (GeoQuad quad : cube.quads()) {
            if (quad != null) {
                Vector3f normal = normalMatrix.transform(new Vector3f(quad.normal()));
                createVerticesOfQuad(quad, poseMatrix, normal, buffer, packedLight, packedOverlay,
                        red, green, blue, alpha);
            }
        }
    }
}
