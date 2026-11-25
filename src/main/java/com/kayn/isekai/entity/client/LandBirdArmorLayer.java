package com.kayn.isekai.entity.client;

import com.kayn.isekai.entity.custom.LandBirdEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.HorseEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.DyeableHorseArmorItem;
import net.minecraft.item.HorseArmorItem;
import net.minecraft.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

class LandBirdArmorLayer extends GeoRenderLayer<LandBirdEntity> {
    private final ModelPart bodyArmor;
    private final ModelPart helmet;

    LandBirdArmorLayer(GeoRenderer<LandBirdEntity> renderer) {
        super(renderer);
        ModelPart vanillaArmor = TexturedModelData.of(HorseEntityModel.getModelData(new Dilation(0.1f)), 64, 64)
                .createModel();
        bodyArmor = vanillaArmor.getChild("body");
        bodyArmor.setPivot(0, 0, 0);
        bodyArmor.getChild("tail").visible = false;
        bodyArmor.getChild("saddle").visible = false;

        helmet = vanillaArmor.getChild("head_parts").getChild("head");
        helmet.getChild("left_ear").visible = false;
        helmet.getChild("right_ear").visible = false;
    }

    @Override
    public void renderForBone(MatrixStack matrices, LandBirdEntity bird, GeoBone bone, RenderLayer renderType,
                              VertexConsumerProvider vertices, VertexConsumer buffer,
                              float tickDelta, int light, int overlay) {
        if (bird.isBaby()) {
            return;
        }
        ModelPart part = switch (bone.getName()) {
            case "body" -> bodyArmor;
            case "h_head" -> helmet;
            default -> null;
        };
        if (part == null) {
            return;
        }
        ItemStack stack = bird.getEquippedStack(EquipmentSlot.CHEST);
        if (!(stack.getItem() instanceof HorseArmorItem armor)) {
            return;
        }
        int color = armor instanceof DyeableHorseArmorItem dyeable ? dyeable.getColor(stack) : 0xFFFFFF;
        float red = ((color >> 16) & 0xFF) / 255.0f;
        float green = ((color >> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        RenderLayer armorRenderType = RenderLayer.getEntityCutoutNoCull(armor.getEntityTexture());

        matrices.push();
        try {
            RenderUtils.translateToPivotPoint(matrices, bone);
            // Center and fit the vanilla plates to each bird bone instead of using a horse's proportions.
            if (part == bodyArmor) {
                matrices.translate(0, 0.5 / 16.0, -2.0 / 16.0);
                matrices.scale(1.24f, -1.1f, 0.63f);
                matrices.translate(0, 3.0 / 16.0, 6.0 / 16.0);
            } else {
                matrices.translate(0, 5.0 / 16.0, 0.5 / 16.0);
                matrices.scale(1.24f, -0.86f, 1.2f);
                matrices.translate(0, 8.5 / 16.0, -1.5 / 16.0);
            }
            part.render(matrices, vertices.getBuffer(armorRenderType), light, overlay, red, green, blue, 1.0f);
        } finally {
            matrices.pop();
            vertices.getBuffer(renderType);
        }
    }
}
