package dev.theplumteam.etherology.e2e.server;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.List;

record EnchantmentProbeState(
        Object pealIdentity,
        Object reflectionIdentity,
        String pealId,
        String reflectionId,
        List<String> etherologyEnchantmentIds,
        String pealClass,
        String reflectionClass,
        int pealMaxLevel,
        List<Integer> pealMinPowers,
        List<Integer> pealMaxPowers,
        int reflectionMaxLevel,
        List<Integer> reflectionMinPowers,
        List<Integer> reflectionMaxPowers,
        boolean pealInNonTreasure,
        boolean reflectionInNonTreasure,
        List<String> nonTreasureEtherologyEnchantmentIds
) {

    static final String ENCHANTMENT_REGISTRY_ID = "minecraft:enchantment";
    static final String NON_TREASURE_TAG_ID = "minecraft:non_treasure";
    static final Identifier PEAL_ID = Identifier.of("etherology", "peal");
    static final Identifier REFLECTION_ID = Identifier.of("etherology", "reflection");
    static final String PEAL_CLASS =
            "ru.feytox.etherology.registry.misc.PealEnchantment";
    static final String REFLECTION_CLASS =
            "ru.feytox.etherology.registry.misc.ReflectionEnchantment";
    static final List<String> EXPECTED_ENCHANTMENT_IDS = List.of(
            PEAL_ID.toString(),
            REFLECTION_ID.toString()
    );
    static final List<Integer> EXPECTED_PEAL_MIN_POWERS = List.of(1, 12, 23);
    static final List<Integer> EXPECTED_PEAL_MAX_POWERS = List.of(21, 32, 43);
    static final List<Integer> EXPECTED_REFLECTION_MIN_POWERS = List.of(1);
    static final List<Integer> EXPECTED_REFLECTION_MAX_POWERS = List.of(21);

    static EnchantmentProbeState capture() {
        Enchantment peal = Registries.ENCHANTMENT.getOrEmpty(PEAL_ID).orElse(null);
        Enchantment reflection = Registries.ENCHANTMENT
                .getOrEmpty(REFLECTION_ID)
                .orElse(null);
        List<String> etherologyEnchantmentIds = Registries.ENCHANTMENT.getIds().stream()
                .filter(identifier -> "etherology".equals(identifier.getNamespace()))
                .map(Identifier::toString)
                .sorted()
                .toList();
        List<String> nonTreasureEtherologyEnchantmentIds = Registries.ENCHANTMENT
                .getIds()
                .stream()
                .filter(identifier -> "etherology".equals(identifier.getNamespace()))
                .filter(identifier -> isInNonTreasure(
                        Registries.ENCHANTMENT.get(identifier)
                ))
                .map(Identifier::toString)
                .sorted()
                .toList();

        return new EnchantmentProbeState(
                peal,
                reflection,
                identifierString(peal),
                identifierString(reflection),
                etherologyEnchantmentIds,
                className(peal),
                className(reflection),
                maxLevel(peal),
                collectMinPowers(peal),
                collectMaxPowers(peal),
                maxLevel(reflection),
                collectMinPowers(reflection),
                collectMaxPowers(reflection),
                isInNonTreasure(peal),
                isInNonTreasure(reflection),
                nonTreasureEtherologyEnchantmentIds
        );
    }

    static EnchantmentProbeState missing() {
        return new EnchantmentProbeState(
                null,
                null,
                "",
                "",
                List.of(),
                "",
                "",
                -1,
                List.of(),
                List.of(),
                -1,
                List.of(),
                List.of(),
                false,
                false,
                List.of()
        );
    }

    boolean hasExactRegistry() {
        return pealIdentity != null
                && reflectionIdentity != null
                && PEAL_ID.toString().equals(pealId)
                && REFLECTION_ID.toString().equals(reflectionId)
                && EXPECTED_ENCHANTMENT_IDS.equals(etherologyEnchantmentIds)
                && PEAL_CLASS.equals(pealClass)
                && REFLECTION_CLASS.equals(reflectionClass);
    }

    boolean hasExactProperties() {
        return pealMaxLevel == 3
                && EXPECTED_PEAL_MIN_POWERS.equals(pealMinPowers)
                && EXPECTED_PEAL_MAX_POWERS.equals(pealMaxPowers)
                && reflectionMaxLevel == 1
                && EXPECTED_REFLECTION_MIN_POWERS.equals(reflectionMinPowers)
                && EXPECTED_REFLECTION_MAX_POWERS.equals(reflectionMaxPowers);
    }

    boolean hasExactTagMembership() {
        return pealInNonTreasure
                && reflectionInNonTreasure
                && EXPECTED_ENCHANTMENT_IDS.equals(
                        nonTreasureEtherologyEnchantmentIds
                );
    }

    boolean sameStateAtServerStarted(EnchantmentProbeState startedState) {
        return hasSameRegistry(startedState)
                && hasSameProperties(startedState)
                && hasSameTagMembership(startedState);
    }

    boolean hasSameRegistry(EnchantmentProbeState other) {
        return pealIdentity != null
                && reflectionIdentity != null
                && pealIdentity == other.pealIdentity
                && reflectionIdentity == other.reflectionIdentity
                && pealId.equals(other.pealId)
                && reflectionId.equals(other.reflectionId)
                && etherologyEnchantmentIds.equals(other.etherologyEnchantmentIds)
                && pealClass.equals(other.pealClass)
                && reflectionClass.equals(other.reflectionClass);
    }

    boolean hasSameProperties(EnchantmentProbeState other) {
        return pealMaxLevel == other.pealMaxLevel
                && pealMinPowers.equals(other.pealMinPowers)
                && pealMaxPowers.equals(other.pealMaxPowers)
                && reflectionMaxLevel == other.reflectionMaxLevel
                && reflectionMinPowers.equals(other.reflectionMinPowers)
                && reflectionMaxPowers.equals(other.reflectionMaxPowers);
    }

    boolean hasSameTagMembership(EnchantmentProbeState other) {
        return pealInNonTreasure == other.pealInNonTreasure
                && reflectionInNonTreasure == other.reflectionInNonTreasure
                && nonTreasureEtherologyEnchantmentIds.equals(
                        other.nonTreasureEtherologyEnchantmentIds
                );
    }

    String pealMinPower(int level) {
        return powerValue(pealMinPowers, level);
    }

    String pealMaxPower(int level) {
        return powerValue(pealMaxPowers, level);
    }

    String reflectionMinPower(int level) {
        return powerValue(reflectionMinPowers, level);
    }

    String reflectionMaxPower(int level) {
        return powerValue(reflectionMaxPowers, level);
    }

    private static String identifierString(Enchantment enchantment) {
        if (enchantment == null) {
            return "";
        }
        Identifier identifier = Registries.ENCHANTMENT.getId(enchantment);
        return identifier == null ? "" : identifier.toString();
    }

    private static String className(Enchantment enchantment) {
        return enchantment == null ? "" : enchantment.getClass().getName();
    }

    private static int maxLevel(Enchantment enchantment) {
        return enchantment == null ? -1 : enchantment.getMaxLevel();
    }

    private static List<Integer> collectMinPowers(Enchantment enchantment) {
        if (enchantment == null) {
            return List.of();
        }
        return java.util.stream.IntStream.rangeClosed(1, enchantment.getMaxLevel())
                .map(enchantment::getMinPower)
                .boxed()
                .toList();
    }

    private static List<Integer> collectMaxPowers(Enchantment enchantment) {
        if (enchantment == null) {
            return List.of();
        }
        return java.util.stream.IntStream.rangeClosed(1, enchantment.getMaxLevel())
                .map(enchantment::getMaxPower)
                .boxed()
                .toList();
    }

    private static boolean isInNonTreasure(Enchantment enchantment) {
        return enchantment != null
                && Registries.ENCHANTMENT.getEntry(enchantment).isIn(nonTreasureTag());
    }

    private static TagKey<Enchantment> nonTreasureTag() {
        return TagKey.of(
                RegistryKeys.ENCHANTMENT,
                Identifier.of("minecraft", "non_treasure")
        );
    }

    private static String powerValue(List<Integer> powers, int level) {
        return level > 0 && level <= powers.size()
                ? Integer.toString(powers.get(level - 1))
                : "missing";
    }
}
