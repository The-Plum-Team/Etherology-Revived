package ru.feytox.etherology.forge.client;

import net.minecraft.util.Identifier;
import ru.feytox.etherology.block.etherealStorage.EtherealStorageFoundationBlockEntity;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;
import software.bernie.geckolib.model.GeoModel;

/**
 * Binds the shared storage animatable to its canonical geometry, texture, and animation assets.
 */
public final class EtherealStorageFoundationModel
        extends GeoModel<EtherealStorageFoundationBlockEntity> {

    /**
     * Returns the canonical storage geometry packaged with the Forge client artifact.
     *
     * @param animatable rendered storage block entity
     * @return geometry resource identifier
     */
    @Override
    public Identifier getModelResource(EtherealStorageFoundationBlockEntity animatable) {
        return Identifier.of(EtherologyBootstrap.MOD_ID, "geo/ethereal_storage.geo.json");
    }

    /**
     * Returns the cutout-capable canonical machine texture.
     *
     * @param animatable rendered storage block entity
     * @return texture resource identifier
     */
    @Override
    public Identifier getTextureResource(EtherealStorageFoundationBlockEntity animatable) {
        return Identifier.of(
                EtherologyBootstrap.MOD_ID,
                "textures/machines/ethereal_storage.png"
        );
    }

    /**
     * Returns the animation resource containing the synchronized open and close clips.
     *
     * @param animatable rendered storage block entity
     * @return animation resource identifier
     */
    @Override
    public Identifier getAnimationResource(EtherealStorageFoundationBlockEntity animatable) {
        return Identifier.of(
                EtherologyBootstrap.MOD_ID,
                "animations/ethereal_storage.animation.json"
        );
    }
}
