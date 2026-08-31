package ru.feytox.etherology.block.etherealStorage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class EtherealStorageFoundationStateTest {

    @Test
    void normalizesPersistentEtherBeforeItOwnsTheDisplay() {
        assertEquals(0.0f, EtherealStorageFoundationBlockEntity.normalizeEther(-1.0f));
        assertEquals(19.75f, EtherealStorageFoundationBlockEntity.normalizeEther(19.75f));
        assertEquals(64.0f, EtherealStorageFoundationBlockEntity.normalizeEther(128.0f));
        assertEquals(0.0f, EtherealStorageFoundationBlockEntity.normalizeEther(Float.NaN));
        assertEquals(0.0f, EtherealStorageFoundationBlockEntity.normalizeEther(Float.POSITIVE_INFINITY));
    }

    @Test
    void derivesTheClosedDisplayCountFromNormalizedEther() {
        assertEquals(0, EtherealStorageFoundationBlockEntity.displayCount(-4.0f));
        assertEquals(19, EtherealStorageFoundationBlockEntity.displayCount(19.75f));
        assertEquals(64, EtherealStorageFoundationBlockEntity.displayCount(128.0f));
        assertEquals(0, EtherealStorageFoundationBlockEntity.displayCount(Float.NaN));
    }

    @Test
    void clearingInputsRetainsTheRebuiltDerivedDisplay() {
        List<String> inventory = new ArrayList<>(
                List.of("input-0", "input-1", "input-2", "stale-display")
        );

        EtherealStorageFoundationBlockEntity.replaceInputsAndDisplay(
                inventory,
                "empty",
                "ether:19"
        );

        assertEquals(List.of("empty", "empty", "empty", "ether:19"), inventory);
    }

    @Test
    void tracksMultipleViewersWithoutUnderflowing() {
        int viewerCount = 0;
        viewerCount = EtherealStorageFoundationBlockEntity.incrementViewerCount(viewerCount);
        viewerCount = EtherealStorageFoundationBlockEntity.incrementViewerCount(viewerCount);
        assertEquals(2, viewerCount);

        viewerCount = EtherealStorageFoundationBlockEntity.decrementViewerCount(viewerCount);
        assertEquals(1, viewerCount);

        viewerCount = EtherealStorageFoundationBlockEntity.decrementViewerCount(viewerCount);
        viewerCount = EtherealStorageFoundationBlockEntity.decrementViewerCount(viewerCount);
        assertEquals(0, viewerCount);
    }

    @Test
    void chargesAtMostOneInternalEtherIntoTheFirstAvailableGlint() {
        assertEquals(1.0f, EtherealStorageFoundationBlockEntity.glintChargeOffer(2.25f));
        assertEquals(0.5f, EtherealStorageFoundationBlockEntity.glintChargeOffer(0.5f));
    }

    @Test
    void combinesPartialInternalAndGlintEtherWhenNeitherCanSatisfyTheDrain() {
        EtherDrainResult result = EtherealStorageFoundationBlockEntity.drainAvailableEther(
                0.5f,
                0.75f,
                1.0f
        );

        assertEquals(0.0f, result.storedEther());
        assertEquals(1.0f, result.removedEther());
    }

    @Test
    void preservesPartialInternalEtherWhenGlintsAloneSatisfyTheDrain() {
        EtherDrainResult result = EtherealStorageFoundationBlockEntity.drainAvailableEther(
                0.5f,
                2.0f,
                1.0f
        );

        assertEquals(0.5f, result.storedEther());
        assertEquals(1.0f, result.removedEther());
    }

    @Test
    void returnsEverythingAvailableWhenCombinedEtherCannotSatisfyTheDrain() {
        EtherDrainResult result = EtherealStorageFoundationBlockEntity.drainAvailableEther(
                0.25f,
                0.5f,
                1.0f
        );

        assertEquals(0.0f, result.storedEther());
        assertEquals(0.75f, result.removedEther());
    }
}
