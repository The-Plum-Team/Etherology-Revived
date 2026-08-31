package ru.feytox.etherology.network.animation;

import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import ru.feytox.etherology.network.util.AbstractS2CPacket;
import ru.feytox.etherology.util.gecko.EGeo2BlockEntity;
import ru.feytox.etherology.util.misc.EIdentifier;

public record SwitchBlockAnimS2C(BlockPos pos, String stopAnim, String startAnim) implements AbstractS2CPacket {

    public static final Identifier ID = EIdentifier.of("switch_block_anim");
    public static final PacketType<SwitchBlockAnimS2C> TYPE = PacketType.create(ID, SwitchBlockAnimS2C::new);

    public SwitchBlockAnimS2C(PacketByteBuf buf) {
        this(buf.readBlockPos(), buf.readString(), buf.readString());
    }

    public static <T extends BlockEntity & EGeo2BlockEntity> void sendForTracking(T blockEntity, String stopAnim, String startAnim) {
        SwitchBlockAnimS2C packet = new SwitchBlockAnimS2C(blockEntity.getPos(), stopAnim, startAnim);
        packet.sendForTracking(blockEntity);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeString(stopAnim);
        buf.writeString(startAnim);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
