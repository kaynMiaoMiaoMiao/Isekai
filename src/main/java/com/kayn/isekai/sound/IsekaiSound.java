package com.kayn.isekai.sound;

import com.kayn.isekai.Isekai;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class IsekaiSound {

    public static final SoundEvent LAND_BIRD_AMBIENT = register("entity.yokibird.ambient");
    public static final SoundEvent LAND_BIRD_HURT = register("entity.yokibird.hurt");
    public static final SoundEvent LAND_BIRD_DEATH = register("entity.yokibird.death");
    public static final SoundEvent LAND_BIRD_ANGRY = register("entity.yokibird.angry");
    public static final SoundEvent LAND_BIRD_EAT = register("entity.yokibird.eat");
    public static final SoundEvent LAND_BIRD_STEP = register("entity.yokibird.step");
    public static final SoundEvent LAND_BIRD_JUMP = register("entity.yokibird.jump");
    public static final SoundEvent LAND_BIRD_LAND = register("entity.yokibird.land");
    public static final SoundEvent LAND_BIRD_FLAP = register("entity.yokibird.flap");

    private IsekaiSound() {
    }

    private static SoundEvent register(String name) {
        Identifier id = Isekai.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void initialize() {
    }
}
