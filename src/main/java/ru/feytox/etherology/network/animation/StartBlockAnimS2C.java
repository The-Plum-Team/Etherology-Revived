package ru.feytox.etherology.network.animation;

import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import ru.feytox.etherology.network.util.AbstractS2CPacket;
import ru.feytox.etherology.util.gecko.EGeoBlockEntity;
import ru.feytox.etherology.util.misc.EIdentifier;

public record StartBlockAnimS2C(BlockPos pos, String animName) implements AbstractS2CPacket {

    public static final Identifier ID = EIdentifier.of("start_block_anim");
    public static final PacketType<StartBlockAnimS2C> TYPE = PacketType.create(ID, StartBlockAnimS2C::new);

    public StartBlockAnimS2C(PacketByteBuf buf) {
        this(buf.readBlockPos(), buf.readString());
    }

    public static <T extends BlockEntity & EGeoBlockEntity> void sendForTracking(T blockEntity, String animName) {
        new StartBlockAnimS2C(blockEntity.getPos(), animName).sendForTracking(blockEntity);
    }

    public static <T extends BlockEntity & EGeoBlockEntity> void sendForTracking(T blockEntity, String animName, PlayerEntity except) {
        new StartBlockAnimS2C(blockEntity.getPos(), animName).sendForTracking(blockEntity, except.getId());
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
