package ru.feytox.etherology.magic.staff;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

final class StaffStylesTest {

    @Test
    void canonicalStyleNamesAndOrderRemainCompatibleWithSavedStaffPatterns() {
        assertEquals(
                List.of("aristocrat", "astronomy", "heavenly", "ocular",
                        "ritual", "royal", "traditional"),
                Arrays.stream(StaffStyles.values()).map(StaffStyles::getName).toList()
        );
        for (StaffStyles style : StaffStyles.values()) {
            assertFalse(style.isEmpty());
        }
    }

    @Test
    void allSevenStylesShareTheCanonicalMemoizedPatternList() {
        assertEquals(List.of(StaffStyles.values()), StaffStyles.STYLES.get());
        assertSame(StaffStyles.STYLES.get(), StaffStyles.STYLES.get());
    }
}
