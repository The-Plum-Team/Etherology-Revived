package ru.feytox.etherology.magic.seal;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import ru.feytox.etherology.particle.subtype.SparkSubtype;
import ru.feytox.etherology.util.misc.RGBColor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SealTypeAndRgbColorContractTest {

    private static final String SEAL_TYPE_CLASS =
            "/ru/feytox/etherology/magic/seal/SealType.class";

    private static final Map<SealType, SealContract> CONTRACTS = contracts();

    @Test
    void sealNamesOrdinalsColorsTexturesAndCodecRemainCanonical() {
        assertEquals(
                List.of("EMPTY", "KETA", "RELLA", "VIA", "CLOS"),
                java.util.Arrays.stream(SealType.values()).map(Enum::name).toList()
        );

        for (Map.Entry<SealType, SealContract> entry : CONTRACTS.entrySet()) {
            SealType sealType = entry.getKey();
            SealContract contract = entry.getValue();
            assertEquals(contract.ordinal(), sealType.ordinal());
            assertEquals(contract.serializedName(), sealType.asString());
            assertEquals(contract.startColor(), sealType.getStartColor());
            assertEquals(contract.endColor(), sealType.getEndColor());
            assertEquals(contract.textureId(), sealType.getTextureId());
            assertEquals(contract.textureLightId(), sealType.getTextureLightId());
            assertEquals(
                    new JsonPrimitive(contract.serializedName()),
                    SealType.CODEC.encodeStart(JsonOps.INSTANCE, sealType)
                            .result()
                            .orElseThrow()
            );
            assertEquals(
                    sealType,
                    SealType.CODEC.parse(
                                    JsonOps.INSTANCE,
                                    new JsonPrimitive(contract.serializedName())
                            )
                            .result()
                            .orElseThrow()
            );
        }
    }

    @Test
    void emptyRetainsItsNullOptionalAndHistoricalBlockExceptionContract() {
        SealType empty = SealType.EMPTY;
        assertFalse(empty.isSeal());
        assertNull(empty.getShardGetter());
        assertNull(empty.getStartColor());
        assertNull(empty.getEndColor());
        assertNull(empty.getTextureId());
        assertNull(empty.getTextureLightId());
        assertTrue(empty.getPrimoShard().isEmpty());

        ArrayIndexOutOfBoundsException exception = assertThrows(
                ArrayIndexOutOfBoundsException.class,
                empty::getBlock
        );
        assertEquals("EMPTY has no seal block", exception.getMessage());
        assertNull(SparkSubtype.of(empty));
        assertThrows(NullPointerException.class, () -> SparkSubtype.of(null));
    }

    @Test
    void nonEmptyLookupMetadataStaysLazyAndCanonical() {
        for (Map.Entry<SealType, SealContract> entry : CONTRACTS.entrySet()) {
            SealType sealType = entry.getKey();
            if (!sealType.isSeal()) {
                continue;
            }

            SealContract contract = entry.getValue();
            assertNotNull(sealType.getShardGetter());
            assertEquals(
                    "etherology:primoshard_" + sealType.asString(),
                    contract.shardId().toString()
            );
            assertEquals(
                    "etherology:" + sealType.asString() + "_seal",
                    contract.blockId().toString()
            );
            assertEquals(
                    SparkSubtype.valueOf(sealType.name()),
                    SparkSubtype.of(sealType)
            );
        }
    }

    @Test
    void publicGetterDescriptorsRemainCompatibleWithTheFormerLombokApi()
            throws ReflectiveOperationException {
        Map<String, Class<?>> getters = Map.of(
                "getShardGetter", java.util.function.Supplier.class,
                "getStartColor", RGBColor.class,
                "getEndColor", RGBColor.class,
                "getTextureId", Identifier.class,
                "getTextureLightId", Identifier.class
        );
        for (Map.Entry<String, Class<?>> entry : getters.entrySet()) {
            Method method = SealType.class.getMethod(entry.getKey());
            assertEquals(entry.getValue(), method.getReturnType(), entry.getKey());
            assertEquals(0, method.getParameterCount(), entry.getKey());
        }
    }

    @Test
    void enumInitializationDoesNotTouchTheItemOrBlockRegistries() throws IOException {
        AtomicInteger registryReferences = new AtomicInteger();
        reader(SEAL_TYPE_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (owner.equals("net/minecraft/registry/Registries")) {
                            registryReferences.incrementAndGet();
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (owner.equals("net/minecraft/registry/Registries")) {
                            registryReferences.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(0, registryReferences.get());
    }

    @Test
    void nonEmptyResolversCheckPresenceBeforeLookupAndVerifyIdentity()
            throws IOException {
        assertEquals(
                List.of("containsId", "get", "getId"),
                registryCalls(reader(SEAL_TYPE_CLASS), "getBlock")
        );
        assertEquals(
                List.of("containsId", "get", "getId"),
                registryCalls(reader(SEAL_TYPE_CLASS), "getRequiredItem")
        );
    }

    @Test
    void rgbRecordRetainsRawPackingAndMaskedUnpacking() {
        RGBColor color = new RGBColor(0x12, 0x34, 0x56);
        assertEquals(0x12, color.r());
        assertEquals(0x34, color.g());
        assertEquals(0x56, color.b());
        assertEquals(0x123456, color.asHex());
        assertEquals(color, RGBColor.of(0x123456));
        assertEquals(new RGBColor(0x34, 0x56, 0x78), RGBColor.of(0x12345678));
        assertEquals(0x01000000, new RGBColor(256, 0, 0).asHex());
    }

    private static Map<SealType, SealContract> contracts() {
        Map<SealType, SealContract> contracts = new LinkedHashMap<>();
        contracts.put(SealType.EMPTY, new SealContract(
                0,
                "empty",
                null,
                null,
                null,
                null,
                null,
                null
        ));
        contracts.put(SealType.KETA, seal(
                1,
                "keta",
                "primoshard_keta",
                new RGBColor(128, 205, 247),
                new RGBColor(105, 128, 231)
        ));
        contracts.put(SealType.RELLA, seal(
                2,
                "rella",
                "primoshard_rella",
                new RGBColor(177, 229, 106),
                new RGBColor(106, 182, 81)
        ));
        contracts.put(SealType.VIA, seal(
                3,
                "via",
                "primoshard_via",
                new RGBColor(248, 122, 95),
                new RGBColor(205, 58, 76)
        ));
        contracts.put(SealType.CLOS, seal(
                4,
                "clos",
                "primoshard_clos",
                new RGBColor(106, 182, 81),
                new RGBColor(208, 158, 89)
        ));
        return Map.copyOf(contracts);
    }

    private static SealContract seal(
            int ordinal,
            String name,
            String shardPath,
            RGBColor startColor,
            RGBColor endColor
    ) {
        return new SealContract(
                ordinal,
                name,
                Identifier.of("etherology", shardPath),
                Identifier.of("etherology", name + "_seal"),
                startColor,
                endColor,
                Identifier.of("etherology", "textures/block/" + name + "_seal.png"),
                Identifier.of(
                        "etherology",
                        "textures/block/" + name + "_seal_light.png"
                )
        );
    }

    private static ClassReader reader(String resource) throws IOException {
        InputStream classStream = SealTypeAndRgbColorContractTest.class
                .getResourceAsStream(resource);
        assertNotNull(classStream, "Missing class resource " + resource);
        try (classStream) {
            return new ClassReader(classStream);
        }
    }

    private static List<String> registryCalls(
            ClassReader reader,
            String expectedMethodName
    ) {
        List<String> calls = new java.util.ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(expectedMethodName)) {
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
                        if (owner.equals("net/minecraft/registry/DefaultedRegistry")) {
                            calls.add(name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return calls;
    }

    private record SealContract(
            int ordinal,
            String serializedName,
            Identifier shardId,
            Identifier blockId,
            RGBColor startColor,
            RGBColor endColor,
            Identifier textureId,
            Identifier textureLightId
    ) {
    }
}
