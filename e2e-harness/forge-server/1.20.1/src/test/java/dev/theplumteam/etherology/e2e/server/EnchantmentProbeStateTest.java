package dev.theplumteam.etherology.e2e.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EnchantmentProbeStateTest {

    @Test
    void exactStateRecognizesBothEnchantmentsPropertiesAndTagMembership() {
        EnchantmentProbeState state = exactState(new Object(), new Object());

        assertTrue(state.hasExactRegistry());
        assertTrue(state.hasExactProperties());
        assertTrue(state.hasExactTagMembership());
    }

    @Test
    void missingStateRejectsEveryExactMilestone() {
        EnchantmentProbeState state = EnchantmentProbeState.missing();

        assertFalse(state.hasExactRegistry());
        assertFalse(state.hasExactProperties());
        assertFalse(state.hasExactTagMembership());
    }

    @Test
    void serverStartedStateRequiresTheSameRegistryObjectsAndValues() {
        Object peal = new Object();
        Object reflection = new Object();
        EnchantmentProbeState initial = exactState(peal, reflection);

        assertTrue(initial.sameStateAtServerStarted(exactState(peal, reflection)));
        assertFalse(initial.sameStateAtServerStarted(
                exactState(new Object(), reflection)
        ));
    }

    @Test
    void reloadComparisonsSeparateRegistryPropertiesAndTagStability() {
        Object peal = new Object();
        Object reflection = new Object();
        EnchantmentProbeState initial = exactState(peal, reflection);
        EnchantmentProbeState changedProperties = state(
                peal,
                reflection,
                2,
                true,
                EnchantmentProbeState.EXPECTED_ENCHANTMENT_IDS
        );
        EnchantmentProbeState changedTag = state(
                peal,
                reflection,
                3,
                false,
                List.of(EnchantmentProbeState.PEAL_ID.toString())
        );

        assertTrue(initial.hasSameRegistry(changedProperties));
        assertFalse(initial.hasSameProperties(changedProperties));
        assertTrue(initial.hasSameTagMembership(changedProperties));
        assertTrue(initial.hasSameRegistry(changedTag));
        assertTrue(initial.hasSameProperties(changedTag));
        assertFalse(initial.hasSameTagMembership(changedTag));
    }

    private static EnchantmentProbeState exactState(
            Object pealIdentity,
            Object reflectionIdentity
    ) {
        return state(
                pealIdentity,
                reflectionIdentity,
                3,
                true,
                EnchantmentProbeState.EXPECTED_ENCHANTMENT_IDS
        );
    }

    private static EnchantmentProbeState state(
            Object pealIdentity,
            Object reflectionIdentity,
            int pealMaxLevel,
            boolean reflectionInNonTreasure,
            List<String> nonTreasureIds
    ) {
        return new EnchantmentProbeState(
                pealIdentity,
                reflectionIdentity,
                EnchantmentProbeState.PEAL_ID.toString(),
                EnchantmentProbeState.REFLECTION_ID.toString(),
                EnchantmentProbeState.EXPECTED_ENCHANTMENT_IDS,
                EnchantmentProbeState.PEAL_CLASS,
                EnchantmentProbeState.REFLECTION_CLASS,
                pealMaxLevel,
                EnchantmentProbeState.EXPECTED_PEAL_MIN_POWERS,
                EnchantmentProbeState.EXPECTED_PEAL_MAX_POWERS,
                1,
                EnchantmentProbeState.EXPECTED_REFLECTION_MIN_POWERS,
                EnchantmentProbeState.EXPECTED_REFLECTION_MAX_POWERS,
                true,
                reflectionInNonTreasure,
                nonTreasureIds
        );
    }
}
