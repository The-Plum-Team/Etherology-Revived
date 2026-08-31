package ru.feytox.etherology.magic.ether;

final class EtherTransfer {

    private EtherTransfer() {
    }

    static void moveAvailable(EtherStorage supplier, EtherStorage consumer) {
        float availableCapacity = Math.max(
                consumer.getMaxEther() - consumer.getStoredEther(),
                0.0f
        );
        float transferSize = Math.min(
                Math.min(supplier.getTransferSize(), consumer.getTransferSize()),
                availableCapacity
        );
        if (!Float.isFinite(transferSize) || transferSize <= 0.0f) {
            return;
        }

        float transferredEther = supplier.decrement(transferSize);
        if (!Float.isFinite(transferredEther) || transferredEther <= 0.0f) {
            return;
        }

        float excessEther = consumer.increment(transferredEther);
        if (Float.isFinite(excessEther) && excessEther > 0.0f) {
            supplier.increment(excessEther);
        }
    }
}
