package ru.feytox.etherology.item.glints;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.item.TooltipData;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.item.EtherealStorageInputItem;

import java.util.List;
import java.util.Optional;

import static ru.feytox.etherology.registry.item.EItems.ETHER;

public class GlintItem extends EtherealStorageInputItem {

    /**
     * Creates the canonical Fabric presentation over the shared glint capacity contract.
     *
     * @param maxEther Ether capacity for this glint type
     */
    public GlintItem(float maxEther) {
        super(maxEther);
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        float storedEther = getStoredEther(stack);
        int etherValue = MathHelper.floor(storedEther);

        int slots = MathHelper.floor(getMaxEther() / 64);
        DefaultedList<ItemStack> defaultedList = DefaultedList.of();
        for (int i = 0; i < slots && etherValue > 0; i++) {
            int count = Math.min(64, etherValue);
            etherValue -= count;
            ItemStack etherStack = ETHER.getDefaultStack();
            etherStack.setCount(count);
            defaultedList.add(etherStack);
        }

        return Optional.of(new GlintTooltipData(
                defaultedList,
                MathHelper.floor(storedEther),
                MathHelper.floor(getMaxEther())
        ));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        float storedEther = getStoredEther(stack);
        int etherValue = MathHelper.floor(storedEther);

        tooltip.add(Text.translatable("item.etherology.glint.fullness", etherValue, MathHelper.floor(getMaxEther())).formatted(Formatting.GRAY));
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return MathHelper.clamp(
                Math.round(13.0f * getStoredEther(stack) / getMaxEther()),
                0,
                13
        );
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        float percent = Math.max(0.0F, (getStoredEther(stack) / getMaxEther()));
        return MathHelper.hsvToRgb(percent / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        float ether = getStoredEther(stack);
        return ether > 0 && ether < getMaxEther();
    }

    /**
     * Reads persisted Ether through the loader-neutral glint data owner.
     *
     * @param stack glint stack whose Ether is read
     * @return stored Ether units, or zero when absent
     */
    public static Float getStoredEther(ItemStack stack) {
        return GlintEtherData.getStoredEther(stack);
    }

    /**
     * Adds Ether up to this glint's capacity and returns the remainder.
     *
     * @param stack glint stack receiving Ether
     * @param maxEther capacity of the concrete glint type
     * @param value Ether units offered
     * @return Ether units that did not fit
     */
    public static float increment(ItemStack stack, float maxEther, float value) {
        return GlintEtherData.increment(stack, maxEther, value);
    }

    /**
     * Removes up to the requested Ether and returns the amount removed.
     *
     * @param stack glint stack supplying Ether
     * @param value maximum Ether units to remove
     * @return Ether units removed
     */
    public static float decrement(ItemStack stack, float value) {
        return GlintEtherData.decrement(stack, value);
    }
}
