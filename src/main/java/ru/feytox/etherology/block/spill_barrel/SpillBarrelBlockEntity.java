package ru.feytox.etherology.block.spill_barrel;

import lombok.Setter;
import lombok.val;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Nameable;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.registry.block.EBlocks;
import ru.feytox.etherology.util.inventory.ListBackedInventory;
import ru.feytox.etherology.util.misc.TickableBlockEntity;

import java.util.Optional;
import java.util.stream.StreamSupport;

public class SpillBarrelBlockEntity extends TickableBlockEntity implements ListBackedInventory, SidedInventory, Nameable {
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(16, ItemStack.EMPTY);
    @Setter
    private Text customName;

    public SpillBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(EBlocks.SPILL_BARREL_BLOCK_ENTITY, pos, state);
    }

    /**
     * @param handStack ItemStack to be filled in the SpillBarrel
     * @return true if the ItemStack can be filled in the SpillBarrel
     */
    public boolean tryFillBarrel(ItemStack handStack) {
        if (!handStack.isOf(Items.POTION)) return false;

        if (isEmpty()) {
            items.set(0, handStack);
            markDirty();
            return true;
        }

        Potion barrelPotion = PotionUtil.getPotion(items.get(0));
        Potion stackPotion = PotionUtil.getPotion(handStack);
        if (!stackPotion.equals(barrelPotion) || !items.get(15).isEmpty()) return false;

        for (int i = 0; i < 16; i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, handStack);
                markDirty();
                return true;
            }
        }

        return false;
    }

    /**
     * @param handStack ItemStack to be emptied from the SpillBarrel
     * @return the ItemStack that was emptied or not from the SpillBarrel
     */
    public ItemStack tryEmptyBarrel(ItemStack handStack) {
        if (!handStack.isOf(Items.GLASS_BOTTLE) || isEmpty()) return handStack;

        Potion barrelPotion = PotionUtil.getPotion(items.get(0));

        for (int i = 15; i >= 0; i--) {
            if (!items.get(i).isEmpty()) {
                ItemStack outputStack = PotionUtil.setPotion(Items.POTION.getDefaultStack(), barrelPotion);
                if (hasCustomName()) outputStack.setCustomName(getCustomName());
                items.set(i, ItemStack.EMPTY);
                markDirty();
                return outputStack;
            }
        }
        return handStack;
    }

    /**
     * @param player Player to show the info about the SpillBarrel
     */
    public void showPotionsInfo(PlayerEntity player) {
        Text resultText = Text.translatable("lore.etherology.spill_barrel.empty").formatted(Formatting.GRAY);
        if (!isEmpty()) {
            MutableText potionInfo = getPotionInfo(items.get(0), getPotionCount(), hasCustomName(), getCustomName());
            if (potionInfo != null) resultText = potionInfo.formatted(Formatting.GRAY);
        }

        player.sendMessage(resultText, true);
    }

    @Nullable
    public static MutableText getPotionInfo(ItemStack potionStack, long potionCount, boolean withCustomName, Text customName) {
        if (withCustomName)
            return Text.translatable("lore.etherology.spill_barrel.filled", customName, potionCount);

        var effectsText = getEffectsText(PotionUtil.getPotionEffects(potionStack));
        return Text.translatable("lore.etherology.spill_barrel.filled", effectsText.getString(), potionCount);
    }

    private static MutableText getEffectsText(Iterable<StatusEffectInstance> effects) {
        var textsIterator = StreamSupport.stream(effects.spliterator(), false)
                .map(effect -> {
                    var effectText = Text.translatable(effect.getTranslationKey());
                    var levelText = Text.translatable("potion.potency." + effect.getAmplifier());
                    if (!levelText.getString().isEmpty())
                        effectText.append(" ").append(levelText);
                    return effectText;
                })
                .iterator();
        if (!textsIterator.hasNext())
            return Text.translatable("block.minecraft.water");

        var result = textsIterator.next();
        while (textsIterator.hasNext()) {
            result.append(" & ");
            result.append(textsIterator.next());
        }

        return result;
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return items;
    }

    public int getPotionCount() {
        int count = 0;
        for (int i = 0; i < 16; i++) {
            if (!items.get(i).isEmpty()) count++;
        }
        return count;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, items);
        if (customName != null) nbt.putString("CustomName", Text.Serializer.toJson(customName));
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        items.clear();
        Inventories.readNbt(nbt, items);
        customName = nbt.contains("CustomName") ? Text.Serializer.fromJson(nbt.getString("CustomName")) : null;
    }

    @Override
    public Text getName() {
        return hasCustomName() ? customName : Text.translatable("block.etherology.spill_barrel");
    }

    @Nullable
    @Override
    public Text getCustomName() {
        return customName;
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return new int[0];
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return false;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return false;
    }
}
