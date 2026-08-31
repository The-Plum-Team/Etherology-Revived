package ru.feytox.etherology.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.junit.jupiter.api.Test;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherologyForgeTest {

    @Test
    void entrypointMetadataAndConstructorMatchForgeContract()
            throws IOException, NoSuchMethodException {
        Mod annotation = EtherologyForge.class.getAnnotation(Mod.class);
        assertNotNull(annotation);
        assertEquals(EtherologyBootstrap.MOD_ID, annotation.value());
        assertNotNull(EtherologyForge.class.getDeclaredConstructor(FMLJavaModLoadingContext.class));

        InputStream entrypointClassStream = EtherologyForge.class.getResourceAsStream(
                "EtherologyForge.class"
        );
        assertNotNull(entrypointClassStream);
        try (entrypointClassStream) {
            String classConstants = new String(
                    entrypointClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1
            );
            assertTrue(classConstants.contains("EventBuses"));
            assertTrue(classConstants.contains("registerModEventBus"));
            assertTrue(classConstants.contains("EtherologyBootstrap"));
            assertFalse(classConstants.contains("net/minecraft/client/"));
            assertFalse(classConstants.contains("ru/feytox/etherology/forge/client/"));
        }

        InputStream metadataStream = getClass().getResourceAsStream("/META-INF/mods.toml");
        assertNotNull(metadataStream);
        try (metadataStream) {
            String metadata = new String(metadataStream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(metadata.contains("modId=\"" + annotation.value() + "\""));
        }
    }
}
