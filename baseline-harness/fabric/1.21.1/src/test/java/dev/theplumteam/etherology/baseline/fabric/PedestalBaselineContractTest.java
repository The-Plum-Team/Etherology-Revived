package dev.theplumteam.etherology.baseline.fabric;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalBaselineContractTest {

    @Test
    void profileScenarioAndEvidenceInventoryAreExact() {
        assertEquals(
                new ScenarioDefinition(
                        "pedestal-baseline",
                        "pedestal-gallery.png",
                        "etherology-original-pedestal-baseline-world",
                        "Etherology Original 0.1.7 Pedestal",
                        0x4554485045443131L
                ),
                PedestalBaselineScenario.DEFINITION
        );
        assertEquals(
                List.of(
                        "pedestal-gallery.png",
                        "pedestal-transition-drops.png",
                        "pedestal-persistence-initial.png",
                        "pedestal-persistence-reopened.png"
                ),
                PedestalBaselineContract.SCREENSHOT_FILE_NAMES
        );
        assertEquals(74, PedestalBaselineContract.assertionNames().size());
        assertEquals(
                74,
                new HashSet<>(PedestalBaselineContract.assertionNames()).size()
        );
    }

    @Test
    void publishedResourcePinsAreCompleteAndUnique() {
        var paths = PedestalBaselineContract.RESOURCE_PINS.keySet();
        assertEquals(64, paths.size());
        assertEquals(1L, paths.stream()
                .filter(path -> path.startsWith("assets/etherology/blockstates/"))
                .count());
        assertEquals(2L, paths.stream()
                .filter(path -> path.startsWith("assets/etherology/lang/"))
                .count());
        assertEquals(20L, paths.stream()
                .filter(path -> path.startsWith("assets/etherology/models/block/"))
                .count());
        assertEquals(1L, paths.stream()
                .filter(path -> path.startsWith("assets/etherology/models/item/"))
                .count());
        assertEquals(37L, paths.stream()
                .filter(path -> path.startsWith("assets/etherology/textures/block/"))
                .count());
        assertEquals(3L, paths.stream()
                .filter(path -> path.startsWith("data/etherology/"))
                .count());
        assertTrue(PedestalBaselineContract.RESOURCE_PINS.values().stream()
                .allMatch(pin -> pin.size() > 0
                        && pin.sha256().matches("[0-9a-f]{64}")));
    }

    @Test
    void emptySlotVerticalCarpetBytecodePathIsAStaticSafetyExclusion() {
        assertEquals(
                List.of("down", "up", "north", "south", "west", "east"),
                PedestalBaselineContract.ITEM_DISPENSER_DIRECTIONS
        );
        assertEquals(
                List.of("north", "south", "west", "east"),
                PedestalBaselineContract.CARPET_DISPENSER_DIRECTIONS
        );
        assertEquals(
                List.of("down", "up"),
                PedestalBaselineContract.GUARDED_CARPET_DISPENSER_DIRECTIONS
        );
        assertTrue(PedestalBaselineContract.VERTICAL_CARPET_LIMITATION
                .startsWith(
                        "hash-pinned-published-0.1.7-bytecode-"
                                + "not-executed-safety-guard:"
                ));
    }

    @Test
    void cameraPoseRequiresExactDeterministicCoordinates() {
        assertTrue(PedestalBaselineScenario.isExactCameraPose(
                0.5, 121.0, -15.5, 0.0F, 10.0F
        ));
        assertFalse(PedestalBaselineScenario.isExactCameraPose(
                0.50005, 121.0, -15.5, 0.0F, 10.0F
        ));
        assertFalse(PedestalBaselineScenario.isExactCameraPose(
                0.5, 121.00005, -15.5, 0.0F, 10.0F
        ));
        assertFalse(PedestalBaselineScenario.isExactCameraPose(
                0.5, 121.0, -15.49995, 0.0F, 10.0F
        ));
        assertFalse(PedestalBaselineScenario.isExactCameraPose(
                0.5, 121.0, -15.5, 0.00005F, 10.0F
        ));
        assertFalse(PedestalBaselineScenario.isExactCameraPose(
                0.5, 121.0, -15.5, 0.0F, 10.00005F
        ));
    }
}
