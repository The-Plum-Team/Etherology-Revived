package dev.theplumteam.etherology.e2e.shared;

final class FramebufferWindowSizing {

    private FramebufferWindowSizing() {
    }

    static int requestedWindowDimension(
            int targetFramebufferDimension,
            int currentWindowDimension,
            int currentFramebufferDimension
    ) {
        return Math.max(
                1,
                (int) Math.round(
                        (double) targetFramebufferDimension
                                * currentWindowDimension
                                / currentFramebufferDimension
                )
        );
    }
}
