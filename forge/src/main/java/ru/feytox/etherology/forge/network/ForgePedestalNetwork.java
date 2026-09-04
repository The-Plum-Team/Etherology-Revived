package ru.feytox.etherology.forge.network;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns the Forge channel used for explicit client Pedestal entity removal.
 */
public final class ForgePedestalNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static final int REMOVE_BLOCK_ENTITY_DISCRIMINATOR = 0;
    private static final Consumer<BlockPos> UNAVAILABLE_CLIENT_REMOVAL = pos -> {
        throw new IllegalStateException(
                "The Forge Pedestal client-removal consumer is not bound"
        );
    };

    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new Identifier(
                    EtherologyBootstrap.MOD_ID,
                    "pedestal_sync"
            ))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static volatile Consumer<BlockPos> clientRemoval =
            UNAVAILABLE_CLIENT_REMOVAL;
    private static boolean registered;

    private ForgePedestalNetwork() {
    }

    /** Registers the one clientbound message exactly once. */
    public static synchronized void register() {
        if (registered) return;
        CHANNEL.messageBuilder(
                        RemovePedestalBlockEntityS2C.class,
                        REMOVE_BLOCK_ENTITY_DISCRIMINATOR,
                        NetworkDirection.PLAY_TO_CLIENT
                )
                .encoder(RemovePedestalBlockEntityS2C::encode)
                .decoder(RemovePedestalBlockEntityS2C::decode)
                .consumerMainThread(ForgePedestalNetwork::handle)
                .add();
        registered = true;
    }

    /**
     * Binds the physical-client removal action without linking client classes here.
     *
     * @param clientRemoval client-only position consumer
     */
    public static synchronized void bindClientRemoval(
            Consumer<BlockPos> clientRemoval
    ) {
        Objects.requireNonNull(clientRemoval, "clientRemoval");
        if (ForgePedestalNetwork.clientRemoval != UNAVAILABLE_CLIENT_REMOVAL
                && ForgePedestalNetwork.clientRemoval != clientRemoval) {
            throw new IllegalStateException(
                    "The Forge Pedestal client-removal consumer is already bound"
            );
        }
        ForgePedestalNetwork.clientRemoval = clientRemoval;
    }

    /**
     * Sends a removal message to clients tracking the target chunk.
     *
     * @param world authoritative server world
     * @param pos removed Pedestal position
     */
    public static void send(ServerWorld world, BlockPos pos) {
        CHANNEL.send(
                PacketDistributor.TRACKING_CHUNK.with(
                        () -> world.getWorldChunk(pos)
                ),
                new RemovePedestalBlockEntityS2C(pos)
        );
    }

    private static void handle(
            RemovePedestalBlockEntityS2C message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> clientRemoval.accept(message.pos()));
        context.setPacketHandled(true);
    }
}
