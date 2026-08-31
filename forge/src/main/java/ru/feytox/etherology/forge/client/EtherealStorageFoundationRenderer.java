package ru.feytox.etherology.forge.client;

import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import ru.feytox.etherology.block.etherealStorage.EtherealStorageFoundationBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Renders the shared storage block entity through its Forge-side Gecko model binding.
 */
public final class EtherealStorageFoundationRenderer
        extends GeoBlockRenderer<EtherealStorageFoundationBlockEntity> {

    /**
     * Creates one renderer instance for Forge's block-entity renderer registry.
     *
     * @param context client renderer construction context
     */
    public EtherealStorageFoundationRenderer(BlockEntityRendererFactory.Context context) {
        super(new EtherealStorageFoundationModel());
    }
}
