package ru.feytox.etherology.network.interaction;

import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import ru.feytox.etherology.network.util.AbstractS2CPacket;
import ru.feytox.etherology.util.misc.EIdentifier;

public record RedstoneLensStreamS2C(Vec3d start, Vec3d end, boolean isMiss) implements AbstractS2CPacket {

    public static final Identifier ID = EIdentifier.of("redstone_lens_stream_s2c");
    public static final PacketType<RedstoneLensStreamS2C> TYPE = PacketType.create(ID, RedstoneLensStreamS2C::new);

    public RedstoneLensStreamS2C(PacketByteBuf buf) {
        this(readVec3d(buf), readVec3d(buf), buf.readBoolean());
    }

    private static Vec3d readVec3d(PacketByteBuf buf) {
        return new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writeVec3d(PacketByteBuf buf, Vec3d pos) {
        buf.writeDouble(pos.getX());
        buf.writeDouble(pos.getY());
        buf.writeDouble(pos.getZ());
    }

    @Override
    public void write(PacketByteBuf buf) {
        writeVec3d(buf, start);
        writeVec3d(buf, end);
        buf.writeBoolean(isMiss);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
