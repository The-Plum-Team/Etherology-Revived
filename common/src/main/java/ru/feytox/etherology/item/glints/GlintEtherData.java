package ru.feytox.etherology.item.glints;

import com.mojang.serialization.Codec;
import net.minecraft.item.ItemStack;
import ru.feytox.etherology.util.misc.ItemDataKey;

/**
 * Owns the loader-neutral persisted Ether value and arithmetic shared by every glint item.
 */
public final class GlintEtherData {

    /**
     * Persists a glint's float Ether value at {@code etherology:components/stored_ether}.
     */
    public static final ItemDataKey<Float> STORED_ETHER =
            new ItemDataKey<>("stored_ether", Codec.FLOAT);

    private GlintEtherData() {
    }

    /**
     * Reads a glint's persisted Ether, treating an absent value as an empty glint.
     *
     * @param stack glint stack whose component is read
     * @return persisted Ether units, or zero when the component is absent
     */
    public static float getStoredEther(ItemStack stack) {
        return STORED_ETHER.getOrDefault(stack, 0.0f);
    }

    /**
     * Adds Ether up to the supplied glint capacity and returns the remainder.
     *
     * @param stack glint stack receiving Ether
     * @param maxEther capacity of this glint type
     * @param value Ether units offered to the glint
     * @return Ether units that did not fit
     */
    public static float increment(ItemStack stack, float maxEther, float value) {
        float storedEther = getStoredEther(stack);
        float newEther = incrementedStoredEther(storedEther, maxEther, value);
        STORED_ETHER.set(stack, newEther);
        return incrementRemainder(storedEther, newEther, value);
    }

    /**
     * Removes up to the requested Ether and returns the amount actually removed.
     *
     * @param stack glint stack supplying Ether
     * @param value maximum Ether units to remove
     * @return Ether units removed from the glint
     */
    public static float decrement(ItemStack stack, float value) {
        float storedEther = getStoredEther(stack);
        float newEther = decrementedStoredEther(storedEther, value);
        STORED_ETHER.set(stack, newEther);
        return removedEther(storedEther, newEther);
    }

    static float incrementedStoredEther(float storedEther, float maxEther, float value) {
        return Math.min(storedEther + value, maxEther);
    }

    static float incrementRemainder(float storedEther, float newEther, float value) {
        return value + storedEther - newEther;
    }

    static float decrementedStoredEther(float storedEther, float value) {
        return Math.max(storedEther - value, 0.0f);
    }

    static float removedEther(float storedEther, float newEther) {
        return storedEther - newEther;
    }
}
