package ru.feytox.etherology.client.item.pranaVision;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.client.TrinketRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.client.model.custom.PranaVisionModel;
import ru.feytox.etherology.util.misc.EIdentifier;

public class PranaVisionItemRenderer implements TrinketRenderer {

    private static final Identifier TEXTURE = EIdentifier.of("textures/entity/trinket/prana_vision_layer_1.png");
    private static final RenderLayer EYES_LAYER = RenderLayer.getEyes(EIdentifier.of("textures/entity/trinket/prana_vision_eyes.png"));
    private BipedEntityModel<LivingEntity> model;

    @Override
    public void render(ItemStack stack, SlotReference slotReference, EntityModel<? extends LivingEntity> contextModel, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        var model = getModel();
        model.setAngles(entity, limbAngle, limbDistance, animationProgress, animationProgress, headPitch);
        model.animateModel(entity, limbAngle, limbDistance, tickDelta);
        TrinketRenderer.followBodyRotations(entity, model);
        var vertexConsumer = vertexConsumers.getBuffer(model.getLayer(TEXTURE));
        model.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);

        renderEyes(model, matrices, vertexConsumers);
    }

    private void renderEyes(BipedEntityModel<LivingEntity> model, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        var vertexConsumer = vertexConsumers.getBuffer(EYES_LAYER);
        model.render(matrices, vertexConsumer, 15728640, OverlayTexture.DEFAULT_UV);
    }

    private BipedEntityModel<LivingEntity> getModel() {
        if (model != null)
            return model;

        model = new PranaVisionModel(PranaVisionModel.getTexturedModelData().createModel());
        return model;
    }
}
