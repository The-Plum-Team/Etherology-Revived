package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static ru.feytox.etherology.forge.PedestalBytecodeAssertions.*;
import static ru.feytox.etherology.forge.SharedItemCatalogAssertions.*;

final class PatternTabletBytecodeTest {

    static final String PREFIX = "ru/feytox/etherology/";
    static final String CATALOG = PREFIX + "registry/item/SharedPatternTabletItems";
    static final String TABLET = PREFIX + "item/PatternTabletItem";
    static final String STYLES = PREFIX + "magic/staff/StaffStyles";
    static final String ACCESSOR = PREFIX + "mixin/SmithingTemplateItemAccessor";
    static final String LOOT = PREFIX + "registry/misc/LootTablesModifyRegistry";
    static final String TRADE = PREFIX + "registry/misc/TradeOffersModificationRegistry";
    static final List<String> STYLE_NAMES = List.of(
            "ARISTOCRAT", "ASTRONOMY", "HEAVENLY", "OCULAR", "RITUAL", "ROYAL", "TRADITIONAL"
    );
    private static final String SUPPLIER = "dev/architectury/registry/registries/RegistrySupplier";

    @Test
    void eachLazyTabletFactoryMatchesItsIdFieldAndStaffStyle() throws IOException {
        assertCatalog(readClass(CATALOG + ".class"));
    }

    static void assertCatalog(ClassNode catalog) {
        Map<String, String> expectedIds = new LinkedHashMap<>();
        STYLE_NAMES.forEach(style -> expectedIds.put(style + "_PATTERN_TABLET",
                style.toLowerCase(Locale.ROOT) + "_pattern_tablet"));
        Map<String, MethodNode> factories = SharedItemCatalogAssertions.assertFactories(
                catalog, TABLET, expectedIds);
        for (String style : STYLE_NAMES) {
            MethodNode body = factories.get(style + "_PATTERN_TABLET");
            assertEquals(List.of(style), fieldsFrom(body, Set.of(STYLES)));
            assertEquals(List.of(TABLET + "#<init>(L" + STYLES + ";)V"), calls(body));
            assertEquals(1, countOpcodes(body, Opcodes.NEW));
        }
    }

    @Test
    void tabletKeepsDefaultStackingItsStyleAndBothTranslatedNames() throws IOException {
        ClassNode tablet = readClass(TABLET + ".class");
        assertEquals(List.of(
                        "net/minecraft/item/Item$Settings#<init>()V",
                        "net/minecraft/item/Item#<init>(Lnet/minecraft/item/Item$Settings;)V"
                ), calls(requireMethod(tablet, "<init>", "(L" + STYLES + ";)V")));
        assertEquals(List.of("staffStyle"), fieldsFrom(
                requireMethod(tablet, "getStaffStyle", "()L" + STYLES + ";"), Set.of(TABLET)));
        for (String descriptor : List.of("()Lnet/minecraft/text/Text;",
                "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/text/Text;")) {
            MethodNode name = requireMethod(tablet, "getName", descriptor);
            assertEquals(List.of("item.etherology.pattern_tablet"), stringConstants(name));
            assertEquals(List.of("net/minecraft/text/Text#translatable"
                    + "(Ljava/lang/String;)Lnet/minecraft/text/MutableText;"), calls(name));
        }
    }

    @Test
    void tooltipRetainsVanillaSmithingHeadingsSpacingAndFormatting() throws IOException {
        ClassNode tablet = readClass(TABLET + ".class");
        MethodNode tooltip = singleMethod(tablet, "appendTooltip");
        List<String> invocations = calls(tooltip);
        assertTrue(invocations.get(0).startsWith("net/minecraft/item/Item#appendTooltip("));
        assertEquals(List.of("getTitleFormatting", "getAppliesToText", "getIngredientsText"),
                methodNamesFrom(tooltip, ACCESSOR));
        assertEquals(6, countCalls(tooltip, "java/util/List", "add"));
        assertEquals(2, countCalls(tooltip, "net/minecraft/screen/ScreenTexts", "space"));
        assertEquals(List.of("EMPTY"),
                fieldsFrom(tooltip, Set.of("net/minecraft/screen/ScreenTexts")));
        assertEquals(List.of("APPLIES", "INGREDIENTS"), fieldsFrom(tooltip, Set.of(TABLET)));
        MethodNode initializer = requireMethod(tablet, "<clinit>", "()V");
        assertEquals(List.of("item.etherology.pattern_tablet.applies_to",
                "item.etherology.pattern_tablet.ingredients"), stringConstants(initializer));
        assertEquals(List.of("getDescriptionFormatting", "getDescriptionFormatting"),
                methodNamesFrom(initializer, ACCESSOR));
    }

    @Test
    void lootKeepsAllNineChestBindingsAndTheOriginalRollAndCountRanges() throws IOException {
        ClassNode loot = readClass(LOOT + ".class");
        assertEquals(List.of(
                        "BASTION_OTHER_CHEST", "BASTION_BRIDGE_CHEST", "BASTION_TREASURE_CHEST",
                        "BASTION_HOGLIN_STABLE_CHEST", "ROYAL_PATTERN_TABLET",
                        "WOODLAND_MANSION_CHEST", "ARISTOCRAT_PATTERN_TABLET",
                        "DESERT_PYRAMID_CHEST", "RITUAL_PATTERN_TABLET",
                        "END_CITY_TREASURE_CHEST", "OCULAR_PATTERN_TABLET",
                        "SHIPWRECK_TREASURE_CHEST", "HEAVENLY_PATTERN_TABLET",
                        "JUNGLE_TEMPLE_CHEST", "ASTRONOMY_PATTERN_TABLET"
                ), fieldsFrom(singleMethod(loot, "registerModifications"),
                        Set.of("net/minecraft/loot/LootTables", CATALOG)));
        MethodNode injection = singleMethod(loot, "injectTabletPattern");
        assertEquals(List.of(0.0F, 1.0F, 1.0F, 3.0F), floatConstants(injection));
        assertEquals(1, countCalls(injection, SUPPLIER, "get"));
        assertEquals(1, countCalls(injection,
                "dev/architectury/event/events/common/LootEvent$LootTableModificationContext",
                "addPool"));
        for (AbstractInsnNode instruction : injection.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("weight")) {
                assertEquals(Opcodes.ICONST_1, instruction.getPrevious().getOpcode());
            }
        }
        assertSingleAttachment(loot, "dev/architectury/event/Event", "register");
    }

    @Test
    void apprenticeToolsmithTradeWaitsForRegistrationAndKeepsItsPriceAndLimits()
            throws IOException {
        ClassNode trade = readClass(TRADE + ".class");
        MethodNode registration = requireMethod(trade, "registerAll", "()V");
        assertEquals(List.of("TRADITIONAL_PATTERN_TABLET"),
                fieldsFrom(registration, Set.of(CATALOG)));
        assertSingleAttachment(trade, SUPPLIER, "listen");
        assertFalse(calls(registration).stream().anyMatch(call -> call.contains("#get(")));
        List<Handle> factories = Arrays.stream(registration.instructions.toArray())
                .filter(InvokeDynamicInsnNode.class::isInstance)
                .map(InvokeDynamicInsnNode.class::cast)
                .map(PatternTabletBytecodeTest::factoryHandle).toList();
        assertEquals(1, factories.size());
        Handle factory = factories.get(0);
        MethodNode callback = requireMethod(trade, factory.getName(), factory.getDesc());
        assertEquals(List.of("TOOLSMITH"),
                fieldsFrom(callback, Set.of("net/minecraft/village/VillagerProfession")));
        assertEquals(List.of(2, 1, 0, 12, 1, 8, 2), integerConstants(callback));
        assertEquals(1, countCalls(callback,
                "net/minecraft/village/TradeOffers$SellItemFactory", "<init>"));
        assertEquals(1, countCalls(callback,
                "dev/architectury/registry/level/entity/trade/TradeRegistry", "registerVillagerTrade"));
    }

    private static void assertSingleAttachment(ClassNode owner, String api, String operation) {
        MethodNode registration = requireMethod(owner, "registerAll", "()V");
        assertTrue((registration.access & Opcodes.ACC_SYNCHRONIZED) != 0);
        assertEquals(1, countFieldAccesses(registration, owner.name, "registered", Opcodes.GETSTATIC));
        assertEquals(1, countFieldAccesses(registration, owner.name, "registered", Opcodes.PUTSTATIC));
        assertEquals(1, countCalls(registration, api, operation));
    }

    private static List<String> methodNamesFrom(MethodNode method, String owner) {
        return Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals(owner))
                .map(call -> call.name).toList();
    }

    private static long countCalls(MethodNode method, String owner, String name) {
        return methodNamesFrom(method, owner).stream().filter(name::equals).count();
    }

    private static Handle factoryHandle(InvokeDynamicInsnNode dynamic) {
        List<Handle> handles = Arrays.stream(dynamic.bsmArgs)
                .filter(Handle.class::isInstance).map(Handle.class::cast).toList();
        assertEquals(1, handles.size());
        return handles.get(0);
    }

    private static List<Float> floatConstants(MethodNode method) {
        List<Float> constants = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            int opcode = instruction.getOpcode();
            if (opcode >= Opcodes.FCONST_0 && opcode <= Opcodes.FCONST_2) {
                constants.add((float) (opcode - Opcodes.FCONST_0));
            } else if (instruction instanceof LdcInsnNode constant
                    && constant.cst instanceof Float value) {
                constants.add(value);
            }
        }
        return constants;
    }

    private static List<Integer> integerConstants(MethodNode method) {
        List<Integer> constants = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            int opcode = instruction.getOpcode();
            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                constants.add(opcode - Opcodes.ICONST_0);
            } else if (instruction instanceof IntInsnNode constant
                    && (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH)) {
                constants.add(constant.operand);
            }
        }
        return constants;
    }
}
