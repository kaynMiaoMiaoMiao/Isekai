package com.kayn.isekai.entity.client;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.entity.custom.LandBirdEntity;
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

    @Override
    public void setCustomAnimations(LandBirdEntity entity, long instanceId, AnimationState<LandBirdEntity> state) {
        if (entity.isDead()) {
            return;
        }
        CoreGeoBone head = getAnimationProcessor().getBone("h_head");
        EntityModelData look = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head != null && look != null) {
            head.setRotX(head.getRotX() + look.headPitch() * MathHelper.RADIANS_PER_DEGREE);
            head.setRotY(head.getRotY() + look.netHeadYaw() * MathHelper.RADIANS_PER_DEGREE);
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
