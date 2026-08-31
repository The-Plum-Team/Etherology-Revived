package ru.feytox.etherology.network.interaction;

import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import ru.feytox.etherology.network.util.AbstractS2CPacket;
import ru.feytox.etherology.util.misc.EIdentifier;

public record RemoveBlockEntityS2C(BlockPos pos) implements AbstractS2CPacket {

    public static final Identifier ID = EIdentifier.of("remove_block_entity_s2c");
    public static final PacketType<RemoveBlockEntityS2C> TYPE = PacketType.create(ID, RemoveBlockEntityS2C::new);

    public RemoveBlockEntityS2C(PacketByteBuf buf) {
        this(buf.readBlockPos());
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
