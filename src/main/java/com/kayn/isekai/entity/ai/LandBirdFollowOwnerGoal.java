package com.kayn.isekai.entity.ai;

import com.kayn.isekai.entity.custom.LandBirdEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class LandBirdFollowOwnerGoal extends Goal {
    private static final double START_DISTANCE_SQ = 4.0 * 4.0;
    private static final double STOP_DISTANCE_SQ = 2.0 * 2.0;
    private static final double TELEPORT_DISTANCE_SQ = 12.0 * 12.0;
    private static final int TELEPORT_ATTEMPTS = 10;

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
        if (!shouldFollow(candidate) || bird.squaredDistanceTo(candidate) < START_DISTANCE_SQ) {
            return false;
        }
        owner = candidate;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return shouldFollow(owner) && bird.squaredDistanceTo(owner) > STOP_DISTANCE_SQ;
    }

    private boolean shouldFollow(@Nullable LivingEntity candidate) {
        return bird.isTame() && bird.getMode() == LandBirdEntity.Mode.FOLLOW && !bird.isSitting()
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
        if (--recalculateTicks > 0) {
            return;
        }
        recalculateTicks = getTickCount(10);
        if (bird.squaredDistanceTo(owner) >= TELEPORT_DISTANCE_SQ) {
            tryTeleportNearOwner();
        } else {
            bird.getNavigation().startMovingTo(owner, 1.1);
        }
    }

    private void tryTeleportNearOwner() {
        BlockPos center = owner.getBlockPos();
        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            int dx = bird.getRandom().nextBetween(-3, 3);
            int dz = bird.getRandom().nextBetween(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }
            BlockPos target = center.add(dx, bird.getRandom().nextBetween(-1, 1), dz);
            if (canTeleportTo(target)) {
                bird.refreshPositionAndAngles(target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                        bird.getYaw(), bird.getPitch());
                bird.getNavigation().stop();
                return;
            }
        }
    }

    private boolean canTeleportTo(BlockPos target) {
        if (LandPathNodeMaker.getLandNodeType(bird.getWorld(), target.mutableCopy()) != PathNodeType.WALKABLE) {
            return false;
        }
        BlockPos offset = target.subtract(bird.getBlockPos());
        return bird.getWorld().isSpaceEmpty(bird, bird.getBoundingBox().offset(offset));
    }

    @Override
    public void stop() {
        owner = null;
        bird.getNavigation().stop();
    }
}
