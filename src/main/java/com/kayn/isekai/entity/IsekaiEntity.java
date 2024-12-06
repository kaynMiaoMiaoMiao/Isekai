package com.kayn.isekai.entity;

import com.kayn.isekai.Isekai;
import com.kayn.isekai.entity.custom.Bird6Entity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * @author kayn
 * @date 2024/12/6 22:45
 **/
public class IsekaiEntity {

    public static final EntityType<Bird6Entity> BIRD6 = Registry.register(

            Registries.ENTITY_TYPE,

            new Identifier(Isekai.MOD_ID,"bird6"),

            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, Bird6Entity::new)
                    .dimensions(EntityDimensions.fixed(1.5f,1.75f))
                    .build()
    );

}
