package com.kayn.isekai;

import com.kayn.isekai.entity.IsekaiEntity;
import com.kayn.isekai.entity.client.Bird6Renderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class IsekaiClient implements ClientModInitializer {


	@Override
	public void onInitializeClient() {


		EntityRendererRegistry.register(IsekaiEntity.BIRD6, Bird6Renderer::new);
	}
}