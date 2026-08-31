package ru.feytox.etherology.magic.ether;

import org.junit.jupiter.api.Test;
import ru.feytox.etherology.enums.PipeSide;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherStorageStateTest {

    @Test
    void canonicalIncrementCapsTheBufferAndReturnsOnlyTheExcess() {
        MutableEtherStorage storage = new MutableEtherStorage(4.0f, 3.5f);

        assertEquals(1.5f, storage.increment(2.0f));
        assertEquals(4.0f, storage.getStoredEther());
    }

    @Test
    void canonicalDecrementReturnsOnlyTheEtherThatWasAvailable() {
        MutableEtherStorage storage = new MutableEtherStorage(4.0f, 0.75f);

        assertEquals(0.75f, storage.decrement(2.0f));
        assertEquals(0.0f, storage.getStoredEther());
    }

    @Test
    void fivePointTwoDecrementsExhaustOneEtherWithoutAResidualTick() {
        MutableEtherStorage storage = new MutableEtherStorage(1.0f, 1.0f);

        for (int step = 0; step < 5; step++) {
            storage.decrement(0.2f);
        }

        assertEquals(0.0f, storage.getStoredEther());
        assertEquals(0.0f, storage.decrement(0.2f));
    }

    @Test
    void roundingRepairNeverClearsARealSubMicroEtherBalance() {
        MutableEtherStorage storage = new MutableEtherStorage(1.0f, 0.00000105f);

        assertEquals(0.0f, storage.decrement(0.0f));
        assertEquals(0.00000105f, storage.getStoredEther());
        float removedEther = storage.decrement(0.0000001f);
        assertEquals(
                0.0000001f,
                removedEther,
                4.0f * Math.ulp(0.0000001f)
        );
        assertTrue(storage.getStoredEther() > 0.0f);
        assertEquals(
                0.00000105f,
                removedEther + storage.getStoredEther(),
                Math.ulp(0.00000105f)
        );
    }

    @Test
    void quiescentAndPoweredPipesClearBothEvaporationFlags() {
        MutableEvaporatingPipe emptyPipe = new MutableEvaporatingPipe(0.0f, false);
        emptyPipe.setEvaporating(true);
        emptyPipe.setCrossEvaporating(true);

        emptyPipe.transfer(null);

        assertFalse(emptyPipe.isEvaporating());
        assertFalse(emptyPipe.isCrossEvaporating());

        MutableEvaporatingPipe poweredPipe = new MutableEvaporatingPipe(1.0f, true);
        poweredPipe.setEvaporating(true);
        poweredPipe.setCrossEvaporating(true);

        poweredPipe.transfer(null);

        assertEquals(1.0f, poweredPipe.getStoredEther());
        assertFalse(poweredPipe.isEvaporating());
        assertFalse(poweredPipe.isCrossEvaporating());
    }

    @Test
    void canonicalStorageOutputsOnlyToPipesWhilePipesOutputToEveryStorage() {
        MutableEtherStorage storage = new MutableEtherStorage(4.0f, 1.0f);
        MutableEtherPipe pipe = new MutableEtherPipe(1.0f, 0.0f);

        assertFalse(storage.canOutputTo(storage));
        assertTrue(storage.canOutputTo(pipe));
        assertTrue(pipe.canOutputTo(storage));
    }

    @Test
    void fullConsumerDoesNotPublishARevertedSupplierMutation() {
        MutationCountingPipe supplier = new MutationCountingPipe(1.0f, 1.0f);
        MutationCountingPipe consumer = new MutationCountingPipe(1.0f, 1.0f);

        EtherTransfer.moveAvailable(supplier, consumer);

        assertEquals(1.0f, supplier.getStoredEther());
        assertEquals(1.0f, consumer.getStoredEther());
        assertEquals(0, supplier.getMutationCount());
        assertEquals(0, consumer.getMutationCount());
    }

    @Test
    void capacityCappedTransferConservesASubUlpConsumerBalance() {
        MutableEtherPipe supplier = new MutableEtherPipe(1.0f, 1.0f);
        MutableEtherPipe consumer = new MutableEtherPipe(1.0f, 0.00000001f);
        double initialEther = (double) supplier.getStoredEther()
                + (double) consumer.getStoredEther();

        EtherTransfer.moveAvailable(supplier, consumer);

        double resultingEther = (double) supplier.getStoredEther()
                + (double) consumer.getStoredEther();
        assertEquals(initialEther, resultingEther);
        assertEquals(0.00000001f, supplier.getStoredEther());
        assertEquals(1.0f, consumer.getStoredEther());
    }

    @Test
    void movedPipeSideRetainsItsExactSerializedAndPredicateBehavior() {
        assertEquals("empty", PipeSide.EMPTY.asString());
        assertEquals("in", PipeSide.IN.asString());
        assertEquals("out", PipeSide.OUT.asString());
        assertTrue(PipeSide.EMPTY.isEmpty());
        assertTrue(PipeSide.IN.isInput());
        assertTrue(PipeSide.OUT.isOutput());
        assertFalse(PipeSide.OUT.isInput());
    }

    @Test
    void pipeSideSerializationIgnoresTheProcessLocale() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("in", PipeSide.IN.asString());
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    private static final class MutableEvaporatingPipe extends MutableEtherStorage
            implements EvaporatingEtherPipe {

        private final boolean activated;
        private boolean evaporating;
        private boolean crossEvaporating;

        private MutableEvaporatingPipe(float storedEther, boolean activated) {
            super(1.0f, storedEther);
            this.activated = activated;
        }

        @Override
        public void setEvaporating(boolean value) {
            evaporating = value;
        }

        @Override
        public void setCrossEvaporating(boolean value) {
            crossEvaporating = value;
        }

        @Override
        public boolean isActivated() {
            return activated;
        }

        private boolean isEvaporating() {
            return evaporating;
        }

        private boolean isCrossEvaporating() {
            return crossEvaporating;
        }
    }

    private static final class MutationCountingPipe extends MutableEtherStorage
            implements EtherPipe {

        private int mutationCount;

        private MutationCountingPipe(float maxEther, float storedEther) {
            super(maxEther, storedEther);
        }

        @Override
        public void setStoredEther(float value) {
            mutationCount++;
            super.setStoredEther(value);
        }

        private int getMutationCount() {
            return mutationCount;
        }
    }
}
