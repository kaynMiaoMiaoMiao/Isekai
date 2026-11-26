package com.kayn.isekai.sound;

import com.kayn.isekai.Isekai;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class IsekaiSound {

    public static final SoundEvent LAND_BIRD_AMBIENT = register("entity.land_bird.ambient");
    public static final SoundEvent LAND_BIRD_HURT = register("entity.land_bird.hurt");
    public static final SoundEvent LAND_BIRD_DEATH = register("entity.land_bird.death");
    public static final SoundEvent LAND_BIRD_ANGRY = register("entity.land_bird.angry");
    public static final SoundEvent LAND_BIRD_EAT = register("entity.land_bird.eat");
    public static final SoundEvent LAND_BIRD_STEP = register("entity.land_bird.step");
    public static final SoundEvent LAND_BIRD_JUMP = register("entity.land_bird.jump");
    public static final SoundEvent LAND_BIRD_LAND = register("entity.land_bird.land");
    public static final SoundEvent LAND_BIRD_FLAP = register("entity.land_bird.flap");

    private IsekaiSound() {
    }

    private static SoundEvent register(String name) {
        Identifier id = Isekai.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void initialize() {
    }
}
