package ru.feytox.etherology.forge.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Math;
import ru.feytox.etherology.block.pedestal.PedestalBlockEntity;

/**
 * Renders the displayed Pedestal stack with the original bob and rotation.
 */
public final class PedestalRenderer
        implements BlockEntityRenderer<PedestalBlockEntity> {

    private static final Vec3d DISPLAY_OFFSET = new Vec3d(0.5, 1.0, 0.5);

    private final ItemRenderer itemRenderer;

    /**
     * Captures the client item renderer from Forge's block-entity renderer context.
     *
     * @param context client renderer factory context
     */
    public PedestalRenderer(BlockEntityRendererFactory.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    /** {@inheritDoc} */
    @Override
    public void render(
            PedestalBlockEntity entity,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay
    ) {
        World world = entity.getWorld();
        if (world == null || entity.isRemoved()) return;

        ItemStack stack = entity.getStack(0);
        if (stack.isEmpty()) return;

        BakedModel model = itemRenderer.getModel(
                stack,
                world,
                null,
                5678
        );
        float uniqueOffset = entity.getUniqueOffset(entity.getPos());
        long time = world.getTime();

        matrices.push();
        matrices.translate(
                DISPLAY_OFFSET.x,
                DISPLAY_OFFSET.y,
                DISPLAY_OFFSET.z
        );
        float yOffset = Math.sin(
                (time + tickDelta) / 10.0F + uniqueOffset
        ) * 0.1F + 0.1F;
        float groundScaleY = model.getTransformation()
                .getTransformation(ModelTransformationMode.GROUND)
                .scale.y();
        matrices.translate(0.0F, yOffset + 0.25F * groundScaleY, 0.0F);
        matrices.multiply(
                RotationAxis.POSITIVE_Y.rotation(
                        (time + tickDelta) / 20.0F + uniqueOffset
                )
        );

        matrices.push();
        itemRenderer.renderItem(
                stack,
                ModelTransformationMode.GROUND,
                false,
                matrices,
                vertexConsumers,
                light,
                OverlayTexture.DEFAULT_UV,
                model
        );
        matrices.pop();

        if (!model.hasDepth()) {
            float groundScaleZ = model.getTransformation().ground.scale.z();
            matrices.translate(0.0F, 0.0F, 0.09375F * groundScaleZ);
        }
        matrices.pop();
    }
}
