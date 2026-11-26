package com.kayn.isekai.entity.client;

import com.kayn.isekai.entity.custom.LandBirdEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
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
     * The feather planes are zero-thickness cubes, so their front and back faces share one plane and,
     * with the no-cull render type, both are drawn. GeckoLib then gives the two faces different normals,
     * and they z-fight with different shading, flickering as the camera moves. Draw only the face that
     * points at the camera instead.
     */
    @Override
    public void renderCube(MatrixStack poseStack, GeoCube cube, VertexConsumer buffer, int packedLight,
                           int packedOverlay, float red, float green, float blue, float alpha) {
        Vec3d size = cube.size();
        if (size.x != 0 && size.y != 0 && size.z != 0) {
            super.renderCube(poseStack, cube, buffer, packedLight, packedOverlay, red, green, blue, alpha);
            return;
        }
        RenderUtils.translateToPivotPoint(poseStack, cube);
        RenderUtils.rotateMatrixAroundCube(poseStack, cube);
        RenderUtils.translateAwayFromPivotPoint(poseStack, cube);

        Matrix3f normalMatrix = poseStack.peek().getNormalMatrix();
        Matrix4f poseMatrix = new Matrix4f(poseStack.peek().getPositionMatrix());
        // Inventory/GUI previews use an orthographic projection, where the view direction is fixed.
        boolean orthographic = RenderSystem.getProjectionMatrix().m23() == 0;

        for (GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            Vector3f normal = normalMatrix.transform(new Vector3f(quad.normal()));
            float facing;
            if (orthographic) {
                facing = -normal.z();
            } else {
                Vector3f corner = quad.vertices()[0].position();
                Vector4f view = poseMatrix.transform(new Vector4f(corner.x(), corner.y(), corner.z(), 1));
                facing = normal.dot(view.x(), view.y(), view.z());
            }
            if (facing > 0) {
                continue;
            }
            createVerticesOfQuad(quad, poseMatrix, normal, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }
}
