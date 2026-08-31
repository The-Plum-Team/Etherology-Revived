package ru.feytox.etherology.network.animation;

import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import ru.feytox.etherology.network.util.AbstractS2CPacket;
import ru.feytox.etherology.util.gecko.EGeo2BlockEntity;
import ru.feytox.etherology.util.gecko.EGeoBlockEntity;
import ru.feytox.etherology.util.misc.EIdentifier;

public record StopBlockAnimS2C(BlockPos pos, String animName) implements AbstractS2CPacket {

    public static final Identifier ID = EIdentifier.of("stop_block_anim");
    public static final PacketType<StopBlockAnimS2C> TYPE = PacketType.create(ID, StopBlockAnimS2C::new);

    public StopBlockAnimS2C(PacketByteBuf buf) {
        this(buf.readBlockPos(), buf.readString());
    }

    @Deprecated
    public static <T extends BlockEntity & EGeoBlockEntity> void sendForTrackingOld(T blockEntity, String animName) {
        new StopBlockAnimS2C(blockEntity.getPos(), animName).sendForTracking(blockEntity);
    }

    public static <T extends BlockEntity & EGeo2BlockEntity> void sendForTracking(T blockEntity, String animName) {
        new StopBlockAnimS2C(blockEntity.getPos(), animName).sendForTracking(blockEntity);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeString(animName);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
