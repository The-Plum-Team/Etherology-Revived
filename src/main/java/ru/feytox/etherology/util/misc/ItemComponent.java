package ru.feytox.etherology.util.misc;

import com.mojang.serialization.Codec;
import net.minecraft.item.ItemStack;

import java.util.Objects;

public record ItemComponent(ItemStack stack) {
    public static final Codec<ItemComponent> CODEC = ItemStack.CODEC.xmap(ItemComponent::new, ItemComponent::stack).stable();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemComponent that)) return false;

        return ItemStack.areEqual(stack, that.stack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stack.getItem(), stack.getCount(), stack.getNbt());
    }
}
