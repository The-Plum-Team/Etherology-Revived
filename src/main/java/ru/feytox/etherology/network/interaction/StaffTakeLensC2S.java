package ru.feytox.etherology.network.interaction;

import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.item.LensItem;
import ru.feytox.etherology.network.util.AbstractC2SPacket;
import ru.feytox.etherology.util.misc.EIdentifier;

public record StaffTakeLensC2S(ItemStack staffStack) implements AbstractC2SPacket {

    public static final Identifier ID = EIdentifier.of("staff_take_lens_c2s");
    public static final PacketType<StaffTakeLensC2S> TYPE = PacketType.create(ID, StaffTakeLensC2S::new);

    public StaffTakeLensC2S(PacketByteBuf buf) {
        this(buf.readItemStack());
    }

    public static void receive(StaffTakeLensC2S packet, ServerPlayerEntity player) {
        ItemStack staffStack = StaffMenuSelectionC2S.findInHands(player, packet.staffStack);
        if (staffStack == null) return;

        ItemStack lensStack = LensItem.takeLensFromStaff(staffStack);
        if (lensStack != null) player.giveItemStack(lensStack);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeItemStack(staffStack);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
