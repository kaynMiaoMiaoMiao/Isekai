package com.kayn.isekai.entity.ai;

import com.kayn.isekai.entity.custom.LandBirdEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/** Runs away from whatever woke the bird at night until it reaches a safe spot to sleep again. */
public class LandBirdFleeGoal extends Goal {
    private static final double SPEED = 1.4;
    private final LandBirdEntity bird;
    private int repathTicks;

    public LandBirdFleeGoal(LandBirdEntity bird) {
        this.bird = bird;
        setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        return bird.isAlarmed() && !bird.hasPassengers() && !bird.hasVehicle() && !bird.isLeashed()
                && moveAway();
    }

    @Override
    public boolean shouldContinue() {
        return bird.isAlarmed() && !bird.hasPassengers() && !bird.getNavigation().isIdle();
    }

    @Override
    public void start() {
        repathTicks = getTickCount(20);
    }

    @Override
    public void tick() {
        // Re-route while the threat keeps chasing.
        if (--repathTicks <= 0) {
            repathTicks = getTickCount(20);
            moveAway();
        }
    }

    private boolean moveAway() {
        LivingEntity threat = bird.getThreat();
        Vec3d target = threat != null
                ? NoPenaltyTargeting.findFrom(bird, 16, 7, threat.getPos())
                : NoPenaltyTargeting.find(bird, 10, 5);
        return target != null && bird.getNavigation().startMovingTo(target.x, target.y, target.z, SPEED);
    }
}
