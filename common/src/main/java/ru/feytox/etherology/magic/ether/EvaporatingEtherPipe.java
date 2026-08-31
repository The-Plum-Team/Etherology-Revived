package ru.feytox.etherology.magic.ether;

/**
 * Exposes the two visual evaporation states maintained by the shared Ether transfer flow.
 */
public interface EvaporatingEtherPipe extends EtherPipe {

    /**
     * Updates whether Ether is evaporating at an unconnected output.
     *
     * @param evaporating whether the output currently evaporates Ether
     */
    void setEvaporating(boolean evaporating);

    /**
     * Updates whether evaporation is caused by an incompatible crossing.
     *
     * @param crossEvaporating whether the current evaporation crosses an incompatible pipe
     */
    void setCrossEvaporating(boolean crossEvaporating);
}
