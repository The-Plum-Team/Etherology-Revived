package dev.theplumteam.etherology.e2e.shared;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FramebufferWindowSizingTest {

    @ParameterizedTest
    @CsvSource({
            "1920, 960, 1920, 960",
            "1920, 960, 960, 1920",
            "1920, 1280, 1920, 1280",
            "1080, 540, 1080, 540",
            "1080, 540, 540, 1080",
            "1080, 810, 1215, 720"
    })
    void derivesLogicalWindowDimensionFromLiveBackingScale(
            int targetFramebufferDimension,
            int currentWindowDimension,
            int currentFramebufferDimension,
            int expectedWindowDimension
    ) {
        assertEquals(
                expectedWindowDimension,
                FramebufferWindowSizing.requestedWindowDimension(
                        targetFramebufferDimension,
                        currentWindowDimension,
                        currentFramebufferDimension
                )
        );
    }
}
