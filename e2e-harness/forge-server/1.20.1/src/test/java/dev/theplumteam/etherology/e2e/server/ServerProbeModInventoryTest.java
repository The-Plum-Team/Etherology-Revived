package dev.theplumteam.etherology.e2e.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ServerProbeModInventoryTest {

    @Test
    void preservesTheDeclaredProfileOrder() {
        assertEquals(
                List.of("etherology_e2e_harness", "quickskin", "cpm"),
                ServerProbeModInventory.parseDeclaredIds(
                        "etherology_e2e_harness,quickskin,cpm"
                )
        );
    }

    @Test
    void rejectsMissingUnsafeAndDuplicateDeclarations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerProbeModInventory.parseDeclaredIds("")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerProbeModInventory.parseDeclaredIds("quickskin,QuickSkin")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerProbeModInventory.parseDeclaredIds("quickskin,quickskin")
        );
    }

    @Test
    void sortsAndDeduplicatesTheLoadedInventory() {
        assertEquals(
                List.of("architectury", "etherology", "forge"),
                ServerProbeModInventory.sortedUniqueIds(
                        List.of("forge", "etherology", "architectury", "forge")
                )
        );
    }

    @Test
    void reportsOnlyTheSortedForbiddenIntersection() {
        assertEquals(
                List.of("cpm", "quickskin"),
                ServerProbeModInventory.sortedIntersection(
                        List.of("forge", "quickskin", "cpm"),
                        List.of("quickskin", "ears", "cpm")
                )
        );
        assertEquals(
                List.of(),
                ServerProbeModInventory.sortedIntersection(
                        List.of("forge", "etherology"),
                        List.of("quickskin", "ears")
                )
        );
    }
}
