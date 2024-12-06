package com.kayn.isekai.entity.client;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.entity.custom.Bird6Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * @author kayn
 * @date 2024/12/6 23:09
 **/
public class Bird6Model extends GeoModel<Bird6Entity> {
    @Override
    public Identifier getModelResource(Bird6Entity bird6Entity) {
        return new Identifier(Isekai.MOD_ID, "geo/bird6.geo.json");
    }

    @Override
    public Identifier getTextureResource(Bird6Entity bird6Entity) {
        return new Identifier(Isekai.MOD_ID, "textures/entity/bird6.png");
    }

    @Override
    public Identifier getAnimationResource(Bird6Entity bird6Entity) {
        return new Identifier(Isekai.MOD_ID, "animations/bird6.animation.json");
    }

    @Override
    public void setCustomAnimations(Bird6Entity animatable, long instanceId, AnimationState<Bird6Entity> animationState) {

        CoreGeoBone head = getAnimationProcessor().getBone("body.h_head");

        if (head != null) {

            EntityModelData entityModelData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(entityModelData.headPitch() * MathHelper.RADIANS_PER_DEGREE);
            head.setRotY(entityModelData.netHeadYaw() * MathHelper.RADIANS_PER_DEGREE);

        }

        super.setCustomAnimations(animatable, instanceId, animationState);
    }
}
