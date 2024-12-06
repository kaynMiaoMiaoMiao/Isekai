package com.kayn.isekai.entity.custom;

import com.kayn.isekai.entity.IsekaiEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;

/**
 * @author kayn
 * @date 2024/12/6 22:46
 **/

public class Bird6Entity extends AnimalEntity implements GeoEntity {

    private AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    public Bird6Entity(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createBirdAttributes() {
        return AnimalEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 10)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 1.0D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0D)
                .add(EntityAttributes.GENERIC_ATTACK_SPEED, 0.4D)
                ;
    }

    protected void initGoals() {
        this.goalSelector.add(1,new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, 1.0D, 1));

        this.goalSelector.add(4,new LookAroundGoal(this));

        this.goalSelector.add(2,new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.goalSelector.add(2,new ActiveTargetGoal<>(this, MerchantEntity.class, true));
        this.goalSelector.add(2,new ActiveTargetGoal<>(this, ChickenEntity.class, true));
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
            bird6EntityAnimationState.getController().setAnimation(RawAnimation.begin().then("animation.bird6.walk", Animation.LoopType.LOOP));
        }

        bird6EntityAnimationState.getController().setAnimation(RawAnimation.begin().then("animation.bird6.idle", Animation.LoopType.LOOP));


        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
