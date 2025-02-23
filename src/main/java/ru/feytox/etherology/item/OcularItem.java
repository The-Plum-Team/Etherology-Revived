package ru.feytox.etherology.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import ru.feytox.etherology.util.misc.DoubleModel;
import ru.feytox.etherology.util.misc.EtherProxy;

public class OcularItem extends Item implements DoubleModel {

    public OcularItem() {
        super(new Settings().maxCount(1));
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
//        return EUseAction.OCULAR_ETHEROLOGY.getUseAction();
//        return UseAction.NONE;
        return UseAction.SPYGLASS;
    }

    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.playSound(SoundEvents.ITEM_SPYGLASS_USE, 1.0F, 1.0F);
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        return ItemUsage.consumeHeldItem(world, user, hand);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        EtherProxy.getInstance().tickOcular(world);
    }

    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 1200;
    }

    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        this.playStopUsingSound(user);
        return stack;
    }

    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        this.playStopUsingSound(user);
    }

    private void playStopUsingSound(LivingEntity user) {
        user.playSound(SoundEvents.ITEM_SPYGLASS_STOP_USING, 1.0F, 1.0F);
    }
}
