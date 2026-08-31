package ru.feytox.etherology.util.misc;

import net.minecraft.item.ItemStack;
import org.apache.commons.lang3.function.TriFunction;
import org.slf4j.helpers.CheckReturnValue;

import java.util.function.BiFunction;

/**
 * Holds one decoded item value while a mechanic applies immutable updates to it.
 */
public class ItemData<C> {

    private final ItemStack stack;
    private final ItemDataKey<C> dataKey;
    private C component;
    private boolean saved;

    /**
     * Creates a wrapper whose changes are written through the supplied data key.
     */
    public ItemData(ItemStack stack, ItemDataKey<C> dataKey, C component) {
        this.stack = stack;
        this.dataKey = dataKey;
        this.component = component;
    }

    /**
     * Returns the value including changes made through this wrapper.
     */
    public C getComponent() {
        return component;
    }

    /**
     * Replaces the held value with the result of a single-argument immutable update.
     */
    @CheckReturnValue
    public <T> ItemData<C> set(T value, BiFunction<C, T, C> withFunc) {
        component = withFunc.apply(component, value);
        return this;
    }

    /**
     * Replaces the held value with the result of a two-argument immutable update.
     */
    @CheckReturnValue
    public <T, M> ItemData<C> set(T value1, M value2, TriFunction<C, T, M, C> func) {
        component = func.apply(component, value1, value2);
        return this;
    }

    /**
     * Writes the held value to the stack and reports success only after encoding completes.
     */
    public ItemData<C> save() {
        dataKey.set(stack, component);
        saved = true;
        return this;
    }

    /**
     * Returns whether this wrapper wrote its component back to the wrapped stack.
     */
    public boolean wasSaved() {
        return saved;
    }
}
