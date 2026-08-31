package ru.feytox.etherology.network.interaction;

import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.gui.staff.LensSelectionType;
import ru.feytox.etherology.network.util.AbstractC2SPacket;
import ru.feytox.etherology.util.misc.EIdentifier;

public record StaffMenuSelectionC2S(LensSelectionType selected, ItemStack staffStack, ItemStack lensStack) implements AbstractC2SPacket {

    public static final Identifier ID = EIdentifier.of("staff_menu_c2s");
    public static final PacketType<StaffMenuSelectionC2S> TYPE = PacketType.create(ID, StaffMenuSelectionC2S::new);

    public StaffMenuSelectionC2S(PacketByteBuf buf) {
        this(readSelection(buf), buf.readItemStack(), buf.readItemStack());
    }

    public static void receive(StaffMenuSelectionC2S packet, ServerPlayerEntity player) {
        if (packet.selected.equals(LensSelectionType.NONE)) return;

        ItemStack staffStack = findInHands(player, packet.staffStack);
        if (staffStack == null) return;

        ItemStack stack = packet.lensStack;
        if (!packet.selected.isEmptySelectedItem()) {
            ItemStack foundStack = findOriginal(player.getInventory(), stack);
            if (foundStack == null) return;
            stack = foundStack;
        }

        packet.selected.getHandler().handle(player, staffStack, stack);
    }

    private static LensSelectionType readSelection(PacketByteBuf buf) {
        int ordinal = buf.readVarInt();
        LensSelectionType[] values = LensSelectionType.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : LensSelectionType.NONE;
    }

    // TODO: 15.07.2024 use different methods of validating item stacks.
    @Nullable
    private static ItemStack findOriginal(Inventory inventory, ItemStack copyStack) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && ItemStack.canCombine(stack, copyStack)) return stack;
        }

        return null;
    }

    @Nullable
    public static ItemStack findInHands(LivingEntity entity, ItemStack copyStack) {
        for (ItemStack stack : entity.getHandItems()) {
            if (ItemStack.canCombine(stack, copyStack)) {
                return stack;
            }
        }
        return null;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeVarInt(selected.ordinal());
        buf.writeItemStack(staffStack);
        buf.writeItemStack(lensStack);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
