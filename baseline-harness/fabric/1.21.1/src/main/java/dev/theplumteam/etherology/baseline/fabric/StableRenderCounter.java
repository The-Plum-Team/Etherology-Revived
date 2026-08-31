package dev.theplumteam.etherology.baseline.fabric;

final class StableRenderCounter {

    private final int requiredRenders;
    private int completedRenders;

    StableRenderCounter(int requiredRenders) {
        this.requiredRenders = requiredRenders;
    }

    boolean observe(boolean captureStateExact) {
        if (!captureStateExact) {
            completedRenders = 0;
            return false;
        }

        completedRenders++;
        return completedRenders >= requiredRenders;
    }

    int completedRenders() {
        return completedRenders;
    }
}
