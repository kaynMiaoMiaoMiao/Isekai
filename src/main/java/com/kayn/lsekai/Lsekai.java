package com.kayn.lsekai;

import com.kayn.lsekai.group.LsekaiGroup;
import com.kayn.lsekai.item.LsekaiItem;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Lsekai implements ModInitializer {

	public static final String MOD_ID = "lsekai";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {

		LsekaiItem.initialize();

		LsekaiGroup.initialize();

		LOGGER.info("Hello Fabric world!");

	}
}