package ru.feytox.etherology.forge.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

/**
 * Carries one exact Pedestal position from the server to tracking clients.
 *
 * @param pos removed Pedestal position
 */
public record RemovePedestalBlockEntityS2C(BlockPos pos) {

    /**
     * Writes the position to Forge's packet buffer.
     *
     * @param message removal message
     * @param buffer destination buffer
     */
    public static void encode(
            RemovePedestalBlockEntityS2C message,
            PacketByteBuf buffer
    ) {
        buffer.writeBlockPos(message.pos());
    }

    /**
     * Reads one removal message from Forge's packet buffer.
     *
     * @param buffer source buffer
     * @return decoded removal message
     */
    public static RemovePedestalBlockEntityS2C decode(PacketByteBuf buffer) {
        return new RemovePedestalBlockEntityS2C(buffer.readBlockPos());
    }
}
