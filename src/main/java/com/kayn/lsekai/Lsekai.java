package com.kayn.lsekai;

import com.kayn.lsekai.group.LsekaiGroup;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Lsekai implements ModInitializer {

	public static final String MOD_ID = "lsekai";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {


		LsekaiGroup.initialize();


		LOGGER.info("Hello Fabric world!");

	}
}