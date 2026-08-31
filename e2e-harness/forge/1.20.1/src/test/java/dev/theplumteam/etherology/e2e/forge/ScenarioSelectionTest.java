package dev.theplumteam.etherology.e2e.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScenarioSelectionTest {

    @Test
    void defaultsOnlyWhenPropertyIsAbsent() {
        assertEquals(ScenarioSelection.ETHEREAL_STORAGE, ScenarioSelection.parse(null));
        assertEquals(
                ScenarioSelection.ETHEREAL_STORAGE,
                ScenarioSelection.parse(ScenarioSelection.ETHEREAL_STORAGE)
        );
        assertEquals(
                ScenarioSelection.ETHEREAL_CHANNEL,
                ScenarioSelection.parse(ScenarioSelection.ETHEREAL_CHANNEL)
        );
    }

    @Test
    void rejectsEmptyPaddedAndUnknownValues() {
        assertThrows(IllegalArgumentException.class, () -> ScenarioSelection.parse(""));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioSelection.parse(" ethereal-storage")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioSelection.parse("ethereal-storage ")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioSelection.parse(" ethereal-channel")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioSelection.parse("phase0-smoke")
        );
    }
}
