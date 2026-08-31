package ru.feytox.etherology.magic.ether;

/**
 * Blocks, that should display ether points in Revelation View, should implement this.
 */
public interface EtherDisplay extends EtherStorage {

    /**
     * Returns the Ether units presented by revelation-style client overlays.
     *
     * @return display Ether units
     */
    float getDisplayEther();

    /**
     * Returns the capacity used to scale revelation-style client overlays.
     *
     * @return positive display capacity
     */
    float getDisplayMaxEther();
}
