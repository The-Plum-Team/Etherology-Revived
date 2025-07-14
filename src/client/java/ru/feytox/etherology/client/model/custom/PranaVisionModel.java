package ru.feytox.etherology.client.model.custom;

import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;

public class PranaVisionModel extends BipedEntityModel<LivingEntity> {

    public PranaVisionModel(ModelPart root) {
        super(root);
        setVisible(false);
        head.visible = true;
    }

    public static TexturedModelData getTexturedModelData() {
        var modelData = BipedEntityModel.getModelData(Dilation.NONE, 0f);
        var modelPartData = modelData.getRoot();

        modelPartData.addChild("head", ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new Dilation(0.5F))
                .uv(0, 16).cuboid(-4.0F, -5.0F, -5.0F, 3.0F, 3.0F, 1.0F, new Dilation(0.0F))
                .uv(8, 16).cuboid(1.0F, -5.0F, -5.0F, 3.0F, 3.0F, 1.0F, new Dilation(0.0F))
                .uv(0, 20).cuboid(-4.0F, -5.0F, -5.0F, 3.0F, 3.0F, 1.0F, new Dilation(-0.125F))
                .uv(8, 20).cuboid(1.0F, -5.0F, -5.0F, 3.0F, 3.0F, 1.0F, new Dilation(-0.125F)), ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        return TexturedModelData.of(modelData, 64, 32);
    }
}
