package ru.feytox.etherology.network;

import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.feytox.etherology.network.interaction.EntityComponentC2SType;
import ru.feytox.etherology.network.interaction.QuestCompleteC2S;
import ru.feytox.etherology.network.interaction.StaffMenuSelectionC2S;
import ru.feytox.etherology.network.interaction.StaffTakeLensC2S;
import ru.feytox.etherology.network.util.AbstractC2SPacket;

import java.util.function.BiConsumer;

public class EtherologyNetwork {

    // TODO: 08.12.2024 move C2S

    public static void registerCommonSide() {
        registerC2S(StaffMenuSelectionC2S.TYPE, StaffMenuSelectionC2S::receive);
        registerC2S(StaffTakeLensC2S.TYPE, StaffTakeLensC2S::receive);
        registerC2S(QuestCompleteC2S.TYPE, QuestCompleteC2S::receive);

        // entity components
        registerTypedC2S(EntityComponentC2SType.TELDECORE_SELECTED);
        registerTypedC2S(EntityComponentC2SType.TELDECORE_PAGE);
        registerTypedC2S(EntityComponentC2SType.TELDECORE_TAB);
        registerTypedC2S(EntityComponentC2SType.TELDECORE_OPENED);
    }

    private static <C, V> void registerTypedC2S(EntityComponentC2SType<C, V> packetType) {
        registerC2S(packetType.getPacketType(), packetType::receive);
    }

    private static <T extends AbstractC2SPacket> void registerC2S(PacketType<T> packetType, BiConsumer<T, ServerPlayerEntity> handler) {
        ServerPlayNetworking.registerGlobalReceiver(packetType.getId(), (server, player, networkHandler, buf, responseSender) -> {
            T packet = packetType.read(buf);
            server.execute(() -> handler.accept(packet, player));
        });
    }
}
