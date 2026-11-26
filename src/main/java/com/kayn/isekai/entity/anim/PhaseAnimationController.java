package com.kayn.isekai.entity.anim;

import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationController;

/**
 * An {@link AnimationController} whose playback speed can change every frame without the pose jumping.
 *
 * <p>GeckoLib computes animation time as {@code speed * (tick - startTick)}, so changing the speed
 * rescales all the time already played: a gait whose speed follows the (noisy) ground speed skips
 * back and forth within its cycle, which reads as jittering legs. This controller instead advances its
 * own clock by {@code frame delta * speed}, so speed changes only alter how fast it moves on.
 */
public class PhaseAnimationController<T extends GeoAnimatable> extends AnimationController<T> {
    private double playback = 1.0;
    private double phase;
    private double lastTick = Double.NaN;

    public PhaseAnimationController(T animatable, String name, int transitionTicks, AnimationStateHandler<T> handler) {
        super(animatable, name, transitionTicks, handler);
    }

    /** Sets how fast the clock advances from now on (1 = authored speed). */
    public void setPlayback(double playback) {
        this.playback = Math.max(0.0, playback);
    }

    @Override
    protected double adjustTick(double tick) {
        if (shouldResetTick) {
            // Same bookkeeping as GeckoLib: a new animation (or a new loop) starts from zero.
            if (getAnimationState() != State.STOPPED) {
                tickOffset = tick;
            }
            shouldResetTick = false;
            phase = 0.0;
            lastTick = tick;
            return 0.0;
        }
        double delta = Double.isNaN(lastTick) ? 0.0 : tick - lastTick;
        lastTick = tick;
        if (delta > 0.0) {
            phase += delta * playback;
        }
        return phase;
    }
}
