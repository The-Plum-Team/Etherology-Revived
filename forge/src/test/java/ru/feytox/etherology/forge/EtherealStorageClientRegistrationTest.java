package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherealStorageClientRegistrationTest {

    @Test
    void bindsTheStorageScreenFromDistScopedEnqueuedClientSetup() throws IOException {
        String eventConstants = classConstants(
                "/ru/feytox/etherology/forge/client/ForgeClientEvents.class"
        );
        assertTrue(eventConstants.contains("EventBusSubscriber"));
        assertTrue(eventConstants.contains("CLIENT"));
        assertTrue(eventConstants.contains("FMLClientSetupEvent"));
        assertTrue(eventConstants.contains("enqueueWork"));
        assertTrue(eventConstants.contains("HandledScreens"));
        assertTrue(eventConstants.contains("SharedScreenHandlers"));
        assertTrue(eventConstants.contains("EtherealStorageFoundationScreen"));

        String screenConstants = classConstants(
                "/ru/feytox/etherology/forge/client/EtherealStorageFoundationScreen.class"
        );
        assertTrue(screenConstants.contains("HandledScreen"));
        assertTrue(screenConstants.contains("EtherealStorageFoundationScreenHandler"));
        assertTrue(screenConstants.contains("textures/gui/ethereal_storage.png"));
    }

    @Test
    void bindsTheGeckoRendererCutoutLayerAndSharedGlintPredicate() throws IOException {
        String eventConstants = classConstants(
                "/ru/feytox/etherology/forge/client/ForgeClientEvents.class"
        );
        assertTrue(eventConstants.contains("BlockEntityRendererRegistry"));
        assertTrue(eventConstants.contains("SharedBlockEntities"));
        assertTrue(eventConstants.contains("EtherealStorageFoundationRenderer"));
        assertTrue(eventConstants.contains("RenderTypeRegistry"));
        assertTrue(eventConstants.contains("getCutout"));
        assertTrue(eventConstants.contains("SharedBlocks"));
        assertTrue(eventConstants.contains("ModelPredicateProviderRegistry"));
        assertTrue(eventConstants.contains("ether_percentage"));
        assertFalse(eventConstants.contains("etherology:ether_percentage"));
        assertTrue(eventConstants.contains("SharedItems"));
        assertTrue(eventConstants.contains("GlintEtherData"));
        assertTrue(eventConstants.contains("getStoredEther"));
        assertTrue(eventConstants.contains("EtherealStorageInputItem"));
        assertTrue(eventConstants.contains("getMaxEther"));
    }

    @Test
    void bindsTheCanonicalStorageAssetsThroughAForgeGeoRenderer() throws IOException {
        String rendererConstants = classConstants(
                "/ru/feytox/etherology/forge/client/EtherealStorageFoundationRenderer.class"
        );
        assertTrue(rendererConstants.contains("GeoBlockRenderer"));
        assertTrue(rendererConstants.contains("EtherealStorageFoundationModel"));

        String modelConstants = classConstants(
                "/ru/feytox/etherology/forge/client/EtherealStorageFoundationModel.class"
        );
        assertTrue(modelConstants.contains("GeoModel"));
        assertTrue(modelConstants.contains("geo/ethereal_storage.geo.json"));
        assertTrue(modelConstants.contains("textures/machines/ethereal_storage.png"));
        assertTrue(modelConstants.contains("animations/ethereal_storage.animation.json"));
    }

    private static String classConstants(String path) throws IOException {
        InputStream classStream = EtherealStorageClientRegistrationTest.class
                .getResourceAsStream(path);
        assertNotNull(classStream);
        try (classStream) {
            return new String(classStream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
