package ru.feytox.etherology.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.client.MinecraftClient;
import ru.feytox.etherology.network.animation.StartBlockAnimS2C;
import ru.feytox.etherology.network.animation.StopBlockAnimS2C;
import ru.feytox.etherology.network.animation.SwitchBlockAnimS2C;
import ru.feytox.etherology.network.interaction.RedstoneLensStreamS2C;
import ru.feytox.etherology.network.interaction.RemoveBlockEntityS2C;
import ru.feytox.etherology.network.util.AbstractS2CPacket;

import java.util.function.BiConsumer;

public class EtherologyNetworkClient {

    public static void registerAll() {
        // animation
        registerHandlerS2C(StartBlockAnimS2C.TYPE, S2CHandlers::receiveStartBlockAnim);
        registerHandlerS2C(StopBlockAnimS2C.TYPE, S2CHandlers::receiveStopBlockAnim);
        registerHandlerS2C(SwitchBlockAnimS2C.TYPE, S2CHandlers::receiveSwitchBlockAnim);

        // interaction
        registerHandlerS2C(RedstoneLensStreamS2C.TYPE, S2CHandlers::receiveRedstoneStream);
        registerHandlerS2C(RemoveBlockEntityS2C.TYPE, S2CHandlers::receiveRemoveBlockEntity);
    }

    private static <T extends AbstractS2CPacket> void registerHandlerS2C(PacketType<T> packetType, BiConsumer<T, MinecraftClient> handler) {
        ClientPlayNetworking.registerGlobalReceiver(packetType.getId(), (client, networkHandler, buf, responseSender) -> {
            T packet = packetType.read(buf);
            client.execute(() -> handler.accept(packet, client));
        });
    }
}
