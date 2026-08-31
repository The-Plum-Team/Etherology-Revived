package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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

    private static String classConstants(String path) throws IOException {
        InputStream classStream = EtherealStorageClientRegistrationTest.class
                .getResourceAsStream(path);
        assertNotNull(classStream);
        try (classStream) {
            return new String(classStream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
