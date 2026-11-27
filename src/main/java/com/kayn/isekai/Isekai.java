package com.kayn.isekai;

import com.kayn.isekai.block.IsekaiBlock;
import com.kayn.isekai.config.IsekaiConfig;
import com.kayn.isekai.entity.IsekaiEntity;
import com.kayn.isekai.group.IsekaiGroup;
import com.kayn.isekai.item.IsekaiItem;
import com.kayn.isekai.sound.IsekaiSound;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

public class Isekai implements ModInitializer {

    public static final String MOD_ID = "isekai_creature";

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        IsekaiConfig.load();
        IsekaiSound.initialize();
        IsekaiBlock.initialize();
        IsekaiEntity.initialize();
        IsekaiItem.initialize();
        IsekaiGroup.initialize();
    }
}
