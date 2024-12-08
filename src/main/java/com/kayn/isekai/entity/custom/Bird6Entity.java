package com.kayn.isekai.entity.custom;

import com.kayn.isekai.entity.IsekaiEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * @author kayn
 * @date 2024/12/6 22:46
 **/

public class Bird6Entity extends AnimalEntity implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public Bird6Entity(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createBirdAttributes() {
        return AnimalEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 10)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1f)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0f)
                .add(EntityAttributes.GENERIC_ATTACK_SPEED, 0.1f)
                ;
    }

    protected void initGoals() {
        this.goalSelector.add(1,new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 0.1f, false));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, 0.1f, 1));

        this.goalSelector.add(4,new LookAroundGoal(this));

        this.goalSelector.add(3,new ActiveTargetGoal<>(this, ChickenEntity.class, true));
    }

    @Override
    public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return IsekaiEntity.BIRD6.create(world);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {

        controllerRegistrar.add(new AnimationController<>(this, "controller",0,this::predicate));

    }

    private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> bird6EntityAnimationState) {

        if (bird6EntityAnimationState.isMoving()) {

            bird6EntityAnimationState.getController().setAnimation(RawAnimation.begin().then("walk", Animation.LoopType.LOOP));

            return PlayState.CONTINUE;
        }

        bird6EntityAnimationState.getController().setAnimation(RawAnimation.begin().then("idle", Animation.LoopType.LOOP));

        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
