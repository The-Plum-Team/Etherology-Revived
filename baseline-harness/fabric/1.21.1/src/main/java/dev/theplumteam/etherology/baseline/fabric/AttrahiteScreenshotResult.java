package dev.theplumteam.etherology.baseline.fabric;

record AttrahiteScreenshotResult(
        boolean passed,
        long size,
        String sha256,
        String failure
) {

    static AttrahiteScreenshotResult failed(String failure) {
        return new AttrahiteScreenshotResult(
                false,
                0L,
                "",
                failure == null ? "unknown error" : failure
        );
    }
}
