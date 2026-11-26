package com.kayn.isekai.entity.client;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.entity.custom.LandBirdEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

import java.util.List;
import java.util.stream.IntStream;

public class LandBirdModel extends GeoModel<LandBirdEntity> {
    private static final List<Assets> ADULTS = variants("adult");
    private static final List<Assets> BABIES = variants("baby");
    private static final float MAX_HEAD_YAW = 75;
    private static final float MAX_HEAD_PITCH = 45;

    private static List<Assets> variants(String stage) {
        return IntStream.rangeClosed(1, LandBirdEntity.PLUMAGE_COUNT)
                .mapToObj(variant -> new Assets(stage + "_" + variant)).toList();
    }

    private static Assets assetsFor(LandBirdEntity entity) {
        return (entity.isBaby() ? BABIES : ADULTS).get(entity.getPlumage() - 1);
    }

    @Override
    public Identifier getModelResource(LandBirdEntity entity) {
        return assetsFor(entity).model();
    }

    @Override
    public Identifier getTextureResource(LandBirdEntity entity) {
        return assetsFor(entity).texture();
    }

    @Override
    public Identifier getAnimationResource(LandBirdEntity entity) {
        return assetsFor(entity).animations();
    }

    /**
     * Back-face culled, unlike GeckoLib's default no-cull type. The feather planes are zero-thickness cubes
     * whose front and back faces share one plane; drawing both makes them z-fight and flicker.
     */
    @Override
    public RenderLayer getRenderType(LandBirdEntity animatable, Identifier texture) {
        return RenderLayer.getEntityCutout(texture);
    }

    @Override
    public void setCustomAnimations(LandBirdEntity entity, long instanceId, AnimationState<LandBirdEntity> state) {
        if (entity.isDead()) {
            return;
        }
        CoreGeoBone head = getAnimationProcessor().getBone("h_head");
        EntityModelData look = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head != null && look != null) {
            // GeckoLib leaves netHeadYaw unwrapped: once head and body yaw drift across the ±180° seam
            // (e.g. a sitting bird being pushed) the raw difference would spin the head in full circles.
            float yaw = MathHelper.clamp(MathHelper.wrapDegrees(look.netHeadYaw()), -MAX_HEAD_YAW, MAX_HEAD_YAW);
            float pitch = MathHelper.clamp(look.headPitch(), -MAX_HEAD_PITCH, MAX_HEAD_PITCH);
            head.setRotX(head.getRotX() + pitch * MathHelper.RADIANS_PER_DEGREE);
            head.setRotY(head.getRotY() + yaw * MathHelper.RADIANS_PER_DEGREE);
            // The setters above mark the bone as animated. When the current animation has no head keys
            // (sit), that marker would make GeckoLib skip resetting the bone next frame, so the look offset
            // would pile up every frame and spin the head. Clear it so the bone starts from its rest pose.
            head.resetStateChanges();
        }
    }

    private record Assets(Identifier model, Identifier texture, Identifier animations) {
        Assets(String name) {
            this(Isekai.id("geo/entity/yoki_bird/" + name + ".geo.json"),
                    Isekai.id("textures/entity/yoki_bird/" + name + ".png"),
                    Isekai.id("animations/entity/yoki_bird/" + name + ".animation.json"));
        }
    }
}
