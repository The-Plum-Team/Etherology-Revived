package ru.feytox.etherology.util.misc;

import com.mojang.serialization.Codec;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Stores one codec-backed value under Etherology's compound inside vanilla item NBT.
 */
public final class ItemDataKey<T> {

    private static final String ROOT_NBT_KEY = "etherology:components";

    private final String nbtKey;
    private final Codec<T> codec;

    /**
     * Creates a key whose codec defines the complete persisted representation.
     */
    public ItemDataKey(String nbtKey, Codec<T> codec) {
        this.nbtKey = nbtKey;
        this.codec = codec;
    }

    /**
     * Returns an absent value only when the key is missing; malformed stored data throws.
     */
    public Optional<T> get(ItemStack stack) {
        NbtCompound components = getExistingComponents(stack.getNbt());
        if (components == null || !components.contains(nbtKey)) return Optional.empty();

        NbtElement encoded = components.get(nbtKey);
        if (encoded == null) return Optional.empty();

        return Optional.of(decode(encoded));
    }

    T decode(NbtElement encoded) {
        return codec.parse(NbtOps.INSTANCE, encoded)
                .getOrThrow(false, message -> {
                    throw new IllegalStateException("Could not decode Etherology item data '" + nbtKey + "': " + message);
                });
    }

    /**
     * Uses the fallback only for a missing key and never for malformed stored data.
     */
    public T getOrDefault(ItemStack stack, T defaultValue) {
        return get(stack).orElse(defaultValue);
    }

    /**
     * Encodes before mutating the stack, leaving existing NBT intact when encoding fails.
     */
    public void set(ItemStack stack, T value) {
        NbtElement encoded = encode(value);
        NbtCompound components = getExistingComponents(stack.getNbt());
        if (components == null) components = stack.getOrCreateSubNbt(ROOT_NBT_KEY);
        components.put(nbtKey, encoded);
    }

    NbtElement encode(T value) {
        return codec.encodeStart(NbtOps.INSTANCE, value)
                .getOrThrow(false, message -> {
                    throw new IllegalStateException("Could not encode Etherology item data '" + nbtKey + "': " + message);
                });
    }

    /**
     * Updates the current or fallback value and returns the value written to the stack.
     */
    public T apply(ItemStack stack, T defaultValue, UnaryOperator<T> operator) {
        T value = operator.apply(getOrDefault(stack, defaultValue));
        set(stack, value);
        return value;
    }

    /**
     * Removes this value and cleans up Etherology's root compound when it becomes empty.
     */
    public void remove(ItemStack stack) {
        NbtCompound components = getExistingComponents(stack.getNbt());
        if (components == null) return;

        components.remove(nbtKey);
        if (components.isEmpty()) stack.removeSubNbt(ROOT_NBT_KEY);
    }

    NbtCompound getExistingComponents(NbtCompound stackNbt) {
        if (stackNbt == null || !stackNbt.contains(ROOT_NBT_KEY)) return null;

        NbtElement root = stackNbt.get(ROOT_NBT_KEY);
        if (root instanceof NbtCompound components) return components;

        throw new IllegalStateException("Etherology item data root '" + ROOT_NBT_KEY + "' is not a compound");
    }
}
