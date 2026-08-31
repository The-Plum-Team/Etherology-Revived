package ru.feytox.etherology.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

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
            assertTrue(classConstants.contains("software/bernie/geckolib/GeckoLib"));
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

    @Test
    void constructorInitializesGeckoBeforeTheSharedBootstrap() throws IOException {
        AtomicInteger instruction = new AtomicInteger();
        AtomicInteger geckoInitialization = new AtomicInteger(-1);
        AtomicInteger sharedBootstrap = new AtomicInteger(-1);
        try (InputStream entrypointClass = requiredEntrypointClass()) {
            ClassReader reader = new ClassReader(entrypointClass);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    if (!name.equals("<init>")) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface
                        ) {
                            int currentInstruction = instruction.getAndIncrement();
                            if (owner.equals("software/bernie/geckolib/GeckoLib")
                                    && name.equals("initialize")) {
                                geckoInitialization.set(currentInstruction);
                            }
                            if (owner.equals(
                                    "ru/feytox/etherology/bootstrap/EtherologyBootstrap"
                            ) && name.equals("initialize")) {
                                sharedBootstrap.set(currentInstruction);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertTrue(geckoInitialization.get() >= 0);
        assertTrue(sharedBootstrap.get() > geckoInitialization.get());
    }

    @Test
    void metadataRequiresTheAuthoritativeForgeGeckoVersionAfterBothSides()
            throws IOException {
        InputStream metadataStream = getClass().getResourceAsStream("/META-INF/mods.toml");
        assertNotNull(metadataStream);
        try (metadataStream) {
            String metadata = new String(metadataStream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(metadata.contains(
                    "[[dependencies.etherology]]\n"
                            + "modId=\"geckolib\"\n"
                            + "mandatory=true\n"
                            + "versionRange=\"[4.7.4,5)\"\n"
                            + "ordering=\"AFTER\"\n"
                            + "side=\"BOTH\""
            ));
        }
    }

    private static InputStream requiredEntrypointClass() {
        InputStream classStream = EtherologyForge.class.getResourceAsStream(
                "EtherologyForge.class"
        );
        assertNotNull(classStream);
        return classStream;
    }
}
