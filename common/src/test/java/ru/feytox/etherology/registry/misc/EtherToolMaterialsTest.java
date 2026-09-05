package ru.feytox.etherology.registry.misc;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherToolMaterialsTest {

    @Test
    void bothMaterialsPreserveTheirMiningDurabilityDamageAndEnchantingStats() {
        assertEquals(2, EtherToolMaterials.values().length);
        assertMaterial(EtherToolMaterials.EBONY, 2, 320, 7.0F, 3.0F, 16);
        assertMaterial(EtherToolMaterials.ETHRIL, 3, 1561, 8.0F, 3.0F, 10);
    }

    @Test
    void repairIngredientsStayMemoizedAndResolveSharedIngotsOnlyOnDemand()
            throws IOException {
        List<String> ingredientFields = new ArrayList<>();
        List<String> supplierGets = new ArrayList<>();
        List<String> memoizeCalls = new ArrayList<>();
        List<String> repairCalls = new ArrayList<>();
        try (InputStream stream = getClass().getResourceAsStream(
                "/ru/feytox/etherology/registry/misc/EtherToolMaterials.class"
        )) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access, String name, String descriptor,
                        String signature, String[] exceptions
                ) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitFieldInsn(
                                int opcode, String owner, String field,
                                String fieldDescriptor
                        ) {
                            assertTrue(!owner.contains("net/fabricmc/")
                                    && !owner.contains("net/minecraftforge/")
                                    && !owner.endsWith("/EItems"), owner);
                            if (owner.endsWith("/SharedMaterialItems")) {
                                assertTrue(name.startsWith("lambda$"), name);
                                assertEquals(Opcodes.GETSTATIC, opcode);
                                ingredientFields.add(field);
                            }
                        }

                        @Override
                        public void visitMethodInsn(
                                int opcode, String owner, String invokedName,
                                String invokedDescriptor, boolean isInterface
                        ) {
                            if (owner.equals("com/google/common/base/Suppliers")
                                    && invokedName.equals("memoize")) {
                                memoizeCalls.add(name);
                            }
                            if (owner.equals(
                                    "dev/architectury/registry/registries/RegistrySupplier"
                            ) && invokedName.equals("get")) {
                                assertTrue(name.startsWith("lambda$"), name);
                                supplierGets.add(name);
                            }
                            if (name.equals("getRepairIngredient")) {
                                repairCalls.add(owner + "#" + invokedName);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(List.of("EBONY_INGOT", "ETHRIL_INGOT"),
                ingredientFields.stream().sorted().toList());
        assertEquals(2, supplierGets.size());
        assertEquals(List.of("<init>"), memoizeCalls);
        assertEquals(List.of("com/google/common/base/Supplier#get"), repairCalls);
    }

    private static void assertMaterial(
            EtherToolMaterials material, int level, int durability,
            float speed, float damage, int enchantability
    ) {
        assertEquals(level, material.getMiningLevel());
        assertEquals(durability, material.getDurability());
        assertEquals(speed, material.getMiningSpeedMultiplier());
        assertEquals(damage, material.getAttackDamage());
        assertEquals(enchantability, material.getEnchantability());
    }
}
