package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static ru.feytox.etherology.forge.PedestalBytecodeAssertions.*;
import static ru.feytox.etherology.forge.SharedItemCatalogAssertions.fieldsFrom;
import static ru.feytox.etherology.forge.SharedItemCatalogAssertions.singleMethod;

final class EbonyArmorBytecodeTest {

    static final String PREFIX = "ru/feytox/etherology/";
    static final String CATALOG = PREFIX + "registry/item/SharedArmorItems";
    static final String ARMOR = PREFIX + "item/EbonyArmorItem";
    static final String MATERIAL = PREFIX + "registry/item/EbonyArmorMaterial";
    static final List<String> SLOTS = List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
    private static final String ARMOR_TYPE = "net/minecraft/item/ArmorItem$Type";

    @Test
    void allFourArmorFactoriesRetainTheirCanonicalSlotAndMaterial() throws IOException {
        assertCatalog(readClass(CATALOG + ".class"), false);
    }

    static void assertCatalog(ClassNode catalog, boolean remapped) {
        Map<String, String> ids = new LinkedHashMap<>();
        SLOTS.forEach(slot -> ids.put("EBONY_" + slot, "ebony_" + slot.toLowerCase(Locale.ROOT)));
        Map<String, MethodNode> factories = SharedItemCatalogAssertions.assertFactories(catalog, ARMOR, ids);
        String type = remapped ? "net/minecraft/class_1738$class_8051" : ARMOR_TYPE;
        String settings = remapped ? "net/minecraft/class_1792$class_1793" : "net/minecraft/item/Item$Settings";
        String material = remapped ? "net/minecraft/class_1741" : "net/minecraft/item/ArmorMaterial";
        List<String> typeFields = remapped ? List.of("field_41934", "field_41935", "field_41936", "field_41937") : SLOTS;
        for (int index = 0; index < SLOTS.size(); index++) {
            MethodNode factory = factories.get("EBONY_" + SLOTS.get(index));
            assertEquals(List.of("INSTANCE"), fieldsFrom(factory, Set.of(MATERIAL)));
            assertEquals(List.of(typeFields.get(index)), fieldsFrom(factory, Set.of(type)));
            assertEquals(List.of(settings + "#<init>()V",
                    ARMOR + "#<init>(L" + material + ";L" + type + ";L" + settings + ";)V"), calls(factory));
            assertEquals(2, countOpcodes(factory, Opcodes.NEW));
        }
    }

    @Test
    void materialKeepsIronDurabilityProtectionEquipSoundToughnessAndKnockbackResistance()
            throws IOException {
        ClassNode material = readClass(MATERIAL + ".class");
        String vanilla = "net/minecraft/item/ArmorMaterials";
        for (String methodName : List.of("getDurability", "getProtection", "getEquipSound",
                "getToughness", "getKnockbackResistance")) {
            MethodNode method = singleMethod(material, methodName);
            assertEquals(List.of("IRON"), fieldsFrom(method, Set.of(vanilla)));
            assertEquals(List.of(vanilla + "#" + methodName + method.desc), calls(method));
        }
        MethodNode enchantability = singleMethod(material, "getEnchantability");
        assertEquals(List.of(16), Arrays.stream(enchantability.instructions.toArray())
                .filter(IntInsnNode.class::isInstance).map(IntInsnNode.class::cast)
                .map(instruction -> instruction.operand).toList());
        assertEquals(List.of("etherology:ebony"),
                stringConstants(singleMethod(material, "getName")));
    }

    @Test
    void repairStaysLazyMemoizedAndBoundOnlyToTheSharedEbonyIngot() throws IOException {
        ClassNode material = readClass(MATERIAL + ".class");
        String supplier = "dev/architectury/registry/registries/RegistrySupplier";
        String items = PREFIX + "registry/item/SharedMaterialItems";
        List<MethodNode> repairFactories = material.methods.stream()
                .filter(method -> !fieldsFrom(method, Set.of(items)).isEmpty()).toList();
        assertEquals(1, repairFactories.size());
        MethodNode factory = repairFactories.get(0);
        assertTrue(factory.name.startsWith("lambda$"));
        assertEquals(List.of("EBONY_INGOT"), fieldsFrom(factory, Set.of(items)));
        assertEquals(1, calls(factory).stream().filter(call -> call.startsWith(supplier + "#get")).count());
        assertEquals(1, calls(requireMethod(material, "<init>", "()V")).stream()
                .filter(call -> call.startsWith("com/google/common/base/Suppliers#memoize")).count());
        assertEquals(List.of("java/util/function/Supplier#get()Ljava/lang/Object;"),
                calls(singleMethod(material, "getRepairIngredient")));
        for (MethodNode method : material.methods) {
            if (!method.name.startsWith("lambda$")) {
                assertFalse(calls(method).stream().anyMatch(call -> call.startsWith(supplier + "#get")));
            }
        }
    }

    @Test
    void eachArmorSlotKeepsItsOriginalIndependentSpeedModifierUuid() throws IOException {
        MethodNode initializer = requireMethod(readClass(ARMOR + ".class"), "<clinit>", "()V");
        assertEquals(List.of("BOOTS", "LEGGINGS", "CHESTPLATE", "HELMET"),
                fieldsFrom(initializer, Set.of(ARMOR_TYPE)));
        List<String> uuids = stringConstants(initializer);
        assertEquals(List.of(
                "845DB27C-C624-495F-8C9F-6020A9A58B6B", "D8499B04-0E66-4726-AB29-64469D734E0D",
                "9F3D476D-C118-4544-8365-64846904B48E", "2AD3F246-FEE1-4E67-B886-69FD380BB150"
        ), uuids);
        assertEquals(4, uuids.stream().map(java.util.UUID::fromString).distinct().count());
    }

    @Test
    void armorRetainsVanillaModifiersAndAddsSevenPointFivePercentOnlyInItsOwnSlot()
            throws IOException {
        ClassNode armor = readClass(ARMOR + ".class");
        MethodNode constructor = singleMethod(armor, "<init>");
        assertEquals(List.of("GENERIC_MOVEMENT_SPEED"),
                fieldsFrom(constructor, Set.of("net/minecraft/entity/attribute/EntityAttributes")));
        assertEquals(List.of("MULTIPLY_TOTAL"), fieldsFrom(constructor,
                Set.of("net/minecraft/entity/attribute/EntityAttributeModifier$Operation")));
        assertEquals(List.of("Ebony armor movement speed"), stringConstants(constructor));
        assertEquals(List.of(0.075D), Arrays.stream(constructor.instructions.toArray())
                .filter(LdcInsnNode.class::isInstance).map(LdcInsnNode.class::cast)
                .map(constant -> constant.cst).filter(Double.class::isInstance).toList());
        assertEquals(1, calls(constructor).stream()
                .filter(call -> call.startsWith("net/minecraft/item/ArmorItem#getAttributeModifiers")).count());
        assertEquals(1, calls(constructor).stream()
                .filter(call -> call.startsWith("com/google/common/collect/ImmutableMultimap$Builder#putAll")).count());
        MethodNode getter = singleMethod(armor, "getAttributeModifiers");
        assertEquals(1, countOpcodes(getter, Opcodes.IF_ACMPNE));
        assertEquals(1, calls(getter).stream()
                .filter(call -> call.startsWith(ARMOR_TYPE + "#getEquipmentSlot")).count());
        assertEquals(1, calls(getter).stream()
                .filter(call -> call.startsWith("net/minecraft/item/ArmorItem#getAttributeModifiers")).count());
        assertEquals(1, countFieldAccesses(getter, ARMOR, "modifiers", Opcodes.GETFIELD));
    }
}
