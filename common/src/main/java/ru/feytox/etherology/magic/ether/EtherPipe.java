package ru.feytox.etherology.magic.ether;

/**
 * Marks an Ether node whose output may feed any compatible storage implementation.
 */
public interface EtherPipe extends EtherStorage {
    /**
     * Allows pipe output to participate in the consumer's own compatibility checks.
     *
     * @param consumer adjacent consumer
     * @return always {@code true}
     */
    @Override
    default boolean canOutputTo(EtherStorage consumer) {
        return true;
    }
}
