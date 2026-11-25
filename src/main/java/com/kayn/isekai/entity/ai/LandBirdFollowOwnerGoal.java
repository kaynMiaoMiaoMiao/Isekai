package com.kayn.isekai.entity.ai;

import com.kayn.isekai.entity.custom.LandBirdEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class LandBirdFollowOwnerGoal extends Goal {
    private final LandBirdEntity bird;
    @Nullable
    private LivingEntity owner;
    private int recalculateTicks;

    public LandBirdFollowOwnerGoal(LandBirdEntity bird) {
        this.bird = bird;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        LivingEntity candidate = bird.getOwner();
        if (!shouldFollow(candidate) || bird.squaredDistanceTo(candidate) < 16.0) {
            return false;
        }
        owner = candidate;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return shouldFollow(owner) && bird.squaredDistanceTo(owner) > 4.0;
    }

    private boolean shouldFollow(@Nullable LivingEntity candidate) {
        return bird.isTame() && bird.getMode() == LandBirdEntity.Mode.FOLLOW
                && !bird.hasPassengers() && !bird.hasVehicle() && !bird.isLeashed()
                && candidate != null && candidate.isAlive() && !candidate.isSpectator()
                && candidate.getWorld() == bird.getWorld();
    }

    @Override
    public void start() {
        recalculateTicks = 0;
    }

    @Override
    public void tick() {
        if (owner == null) {
            return;
        }
        bird.getLookControl().lookAt(owner);
        if (--recalculateTicks <= 0) {
            recalculateTicks = getTickCount(10);
            bird.getNavigation().startMovingTo(owner, 1.1);
        }
    }

    @Override
    public void stop() {
        owner = null;
        bird.getNavigation().stop();
    }
}
