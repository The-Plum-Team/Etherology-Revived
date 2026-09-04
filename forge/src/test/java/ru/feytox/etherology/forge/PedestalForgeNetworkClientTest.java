package ru.feytox.etherology.forge;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import ru.feytox.etherology.forge.network.RemovePedestalBlockEntityS2C;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalForgeNetworkClientTest {

    private static final String NETWORK =
            "ru/feytox/etherology/forge/network/ForgePedestalNetwork";
    private static final String MESSAGE =
            "ru/feytox/etherology/forge/network/RemovePedestalBlockEntityS2C";
    private static final String CLIENT_EVENTS =
            "ru/feytox/etherology/forge/client/ForgeClientEvents";
    private static final String CLIENT_REMOVAL =
            "ru/feytox/etherology/forge/client/ForgePedestalClientRemoval";
    private static final String RENDERER =
            "ru/feytox/etherology/forge/client/PedestalRenderer";
    private static final String SHARED_PEDESTAL_BLOCKS =
            "ru/feytox/etherology/registry/block/SharedPedestalBlocks";
    private static final String SHARED_PEDESTAL_BLOCK_ENTITIES =
            "ru/feytox/etherology/registry/block/SharedPedestalBlockEntities";
    private static final String MESSAGE_DESCRIPTOR = "L" + MESSAGE + ";";

    @Test
    void removalMessageRoundTripsOneExactBlockPosition() throws IOException {
        BlockPos expectedPosition = new BlockPos(-31, 2047, 86);
        RemovePedestalBlockEntityS2C expected =
                new RemovePedestalBlockEntityS2C(expectedPosition);
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            RemovePedestalBlockEntityS2C.encode(expected, buffer);
            assertEquals(expected, RemovePedestalBlockEntityS2C.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }

        ClassNode message = PedestalBytecodeAssertions.readClass(MESSAGE + ".class");
        assertEquals("java/lang/Record", message.superName);
        assertEquals(1, message.recordComponents.size());
        assertEquals("pos", message.recordComponents.get(0).name);
        assertEquals("Lnet/minecraft/util/math/BlockPos;", message.recordComponents.get(0).descriptor);

        MethodNode encode = PedestalBytecodeAssertions.requireMethod(
                message,
                "encode",
                "(" + MESSAGE_DESCRIPTOR
                        + "Lnet/minecraft/network/PacketByteBuf;)V"
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countCalls(
                        encode,
                        "net/minecraft/network/PacketByteBuf",
                        "writeBlockPos",
                        "(Lnet/minecraft/util/math/BlockPos;)"
                                + "Lnet/minecraft/network/PacketByteBuf;"
                )
        );

        MethodNode decode = PedestalBytecodeAssertions.requireMethod(
                message,
                "decode",
                "(Lnet/minecraft/network/PacketByteBuf;)" + MESSAGE_DESCRIPTOR
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countCalls(
                        decode,
                        "net/minecraft/network/PacketByteBuf",
                        "readBlockPos",
                        "()Lnet/minecraft/util/math/BlockPos;"
                )
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countCalls(
                        decode,
                        MESSAGE,
                        "<init>",
                        "(Lnet/minecraft/util/math/BlockPos;)V"
                )
        );
    }

    @Test
    void simpleChannelUsesOneClientboundDiscriminatorAndExactCodecs()
            throws IOException {
        ClassNode network = readNetwork();
        FieldNode protocolVersion = requireField(network, "PROTOCOL_VERSION");
        assertEquals("1", protocolVersion.value);
        FieldNode discriminator = requireField(
                network,
                "REMOVE_BLOCK_ENTITY_DISCRIMINATOR"
        );
        assertEquals(0, discriminator.value);

        MethodNode classInitializer = PedestalBytecodeAssertions.requireMethod(
                network,
                "<clinit>",
                "()V"
        );
        assertTrue(PedestalBytecodeAssertions.stringConstants(classInitializer)
                .containsAll(List.of("etherology", "pedestal_sync", "1")));
        List<String> channelCalls = PedestalBytecodeAssertions.calls(classInitializer);
        assertTrue(channelCalls.contains(
                "net/minecraftforge/network/NetworkRegistry$ChannelBuilder#named"
                        + "(Lnet/minecraft/util/Identifier;)"
                        + "Lnet/minecraftforge/network/NetworkRegistry$ChannelBuilder;"
        ));
        assertTrue(channelCalls.contains(
                "net/minecraftforge/network/NetworkRegistry$ChannelBuilder#"
                        + "networkProtocolVersion(Ljava/util/function/Supplier;)"
                        + "Lnet/minecraftforge/network/NetworkRegistry$ChannelBuilder;"
        ));
        assertTrue(channelCalls.contains(
                "net/minecraftforge/network/NetworkRegistry$ChannelBuilder#"
                        + "clientAcceptedVersions(Ljava/util/function/Predicate;)"
                        + "Lnet/minecraftforge/network/NetworkRegistry$ChannelBuilder;"
        ));
        assertTrue(channelCalls.contains(
                "net/minecraftforge/network/NetworkRegistry$ChannelBuilder#"
                        + "serverAcceptedVersions(Ljava/util/function/Predicate;)"
                        + "Lnet/minecraftforge/network/NetworkRegistry$ChannelBuilder;"
        ));
        assertTrue(channelCalls.contains(
                "net/minecraftforge/network/NetworkRegistry$ChannelBuilder#"
                        + "simpleChannel()"
                        + "Lnet/minecraftforge/network/simple/SimpleChannel;"
        ));

        MethodNode register = PedestalBytecodeAssertions.requireMethod(
                network,
                "register",
                "()V"
        );
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED,
                register.access & (
                        Opcodes.ACC_PUBLIC
                                | Opcodes.ACC_PRIVATE
                                | Opcodes.ACC_PROTECTED
                                | Opcodes.ACC_STATIC
                                | Opcodes.ACC_SYNCHRONIZED
                )
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countFieldAccesses(
                        register,
                        "net/minecraftforge/network/NetworkDirection",
                        "PLAY_TO_CLIENT",
                        Opcodes.GETSTATIC
                )
        );
        List<String> registrationCalls = PedestalBytecodeAssertions.calls(register);
        assertEquals(
                List.of(
                        "net/minecraftforge/network/simple/SimpleChannel#messageBuilder"
                                + "(Ljava/lang/Class;I"
                                + "Lnet/minecraftforge/network/NetworkDirection;)"
                                + "Lnet/minecraftforge/network/simple/"
                                + "SimpleChannel$MessageBuilder;",
                        "net/minecraftforge/network/simple/SimpleChannel$MessageBuilder#"
                                + "encoder(Ljava/util/function/BiConsumer;)"
                                + "Lnet/minecraftforge/network/simple/"
                                + "SimpleChannel$MessageBuilder;",
                        "net/minecraftforge/network/simple/SimpleChannel$MessageBuilder#"
                                + "decoder(Ljava/util/function/Function;)"
                                + "Lnet/minecraftforge/network/simple/"
                                + "SimpleChannel$MessageBuilder;",
                        "net/minecraftforge/network/simple/SimpleChannel$MessageBuilder#"
                                + "consumerMainThread(Ljava/util/function/BiConsumer;)"
                                + "Lnet/minecraftforge/network/simple/"
                                + "SimpleChannel$MessageBuilder;",
                        "net/minecraftforge/network/simple/SimpleChannel$MessageBuilder#add()V"
                ),
                registrationCalls
        );
        assertEquals(
                List.of(
                        MESSAGE + "#encode(" + MESSAGE_DESCRIPTOR
                                + "Lnet/minecraft/network/PacketByteBuf;)V",
                        MESSAGE + "#decode(Lnet/minecraft/network/PacketByteBuf;)"
                                + MESSAGE_DESCRIPTOR,
                        NETWORK + "#handle(" + MESSAGE_DESCRIPTOR
                                + "Ljava/util/function/Supplier;)V"
                ),
                PedestalBytecodeAssertions.methodHandles(register)
        );
    }

    @Test
    void serverRemovalTracksTheChunkAndTheHandlerQueuesClientWork()
            throws IOException {
        ClassNode network = readNetwork();
        MethodNode send = PedestalBytecodeAssertions.requireMethod(
                network,
                "send",
                "(Lnet/minecraft/server/world/ServerWorld;"
                        + "Lnet/minecraft/util/math/BlockPos;)V"
        );
        int trackingChunk = PedestalBytecodeAssertions.fieldAccessIndex(
                send,
                "net/minecraftforge/network/PacketDistributor",
                "TRACKING_CHUNK",
                Opcodes.GETSTATIC
        );
        int target = PedestalBytecodeAssertions.callIndex(
                send,
                "net/minecraftforge/network/PacketDistributor",
                "with",
                "(Ljava/util/function/Supplier;)"
                        + "Lnet/minecraftforge/network/PacketDistributor$PacketTarget;"
        );
        int messageConstruction = PedestalBytecodeAssertions.typeInstructionIndex(
                send,
                Opcodes.NEW,
                MESSAGE
        );
        int channelSend = PedestalBytecodeAssertions.callIndex(
                send,
                "net/minecraftforge/network/simple/SimpleChannel",
                "send",
                "(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;"
                        + "Ljava/lang/Object;)V"
        );
        assertTrue(trackingChunk >= 0);
        assertTrue(trackingChunk < target);
        assertTrue(target < messageConstruction);
        assertTrue(messageConstruction < channelSend);
        assertTrue(PedestalBytecodeAssertions.methodHandles(send).contains(
                NETWORK + "#lambda$send$2"
                        + "(Lnet/minecraft/server/world/ServerWorld;"
                        + "Lnet/minecraft/util/math/BlockPos;)"
                        + "Lnet/minecraft/world/chunk/WorldChunk;"
        ));

        MethodNode trackingSupplier = PedestalBytecodeAssertions.requireMethod(
                network,
                "lambda$send$2",
                "(Lnet/minecraft/server/world/ServerWorld;"
                        + "Lnet/minecraft/util/math/BlockPos;)"
                        + "Lnet/minecraft/world/chunk/WorldChunk;"
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countCalls(
                        trackingSupplier,
                        "net/minecraft/server/world/ServerWorld",
                        "getWorldChunk",
                        "(Lnet/minecraft/util/math/BlockPos;)"
                                + "Lnet/minecraft/world/chunk/WorldChunk;"
                )
        );

        MethodNode handler = PedestalBytecodeAssertions.requireMethod(
                network,
                "handle",
                "(" + MESSAGE_DESCRIPTOR + "Ljava/util/function/Supplier;)V"
        );
        int enqueueWork = PedestalBytecodeAssertions.callIndex(
                handler,
                "net/minecraftforge/network/NetworkEvent$Context",
                "enqueueWork",
                "(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;"
        );
        int handled = PedestalBytecodeAssertions.callIndex(
                handler,
                "net/minecraftforge/network/NetworkEvent$Context",
                "setPacketHandled",
                "(Z)V"
        );
        assertTrue(enqueueWork >= 0);
        assertTrue(enqueueWork < handled);
        assertTrue(PedestalBytecodeAssertions.methodHandles(handler).contains(
                NETWORK + "#lambda$handle$3(" + MESSAGE_DESCRIPTOR + ")V"
        ));

        MethodNode clientTask = PedestalBytecodeAssertions.requireMethod(
                network,
                "lambda$handle$3",
                "(" + MESSAGE_DESCRIPTOR + ")V"
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countFieldAccesses(
                        clientTask,
                        NETWORK,
                        "clientRemoval",
                        Opcodes.GETSTATIC
                )
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countCalls(
                        clientTask,
                        MESSAGE,
                        "pos",
                        "()Lnet/minecraft/util/math/BlockPos;"
                )
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countCalls(
                        clientTask,
                        "java/util/function/Consumer",
                        "accept",
                        "(Ljava/lang/Object;)V"
                )
        );

        ClassNode backend = PedestalBytecodeAssertions.readClass(
                "ru/feytox/etherology/forge/block/pedestal/"
                        + "ForgePedestalBlockEntityRemovalBackend.class"
        );
        assertEquals(
                List.of(
                        "ru/feytox/etherology/block/pedestal/"
                                + "PedestalBlockEntityRemovalBackend"
                ),
                backend.interfaces
        );
        MethodNode backendSend = PedestalBytecodeAssertions.requireMethod(
                backend,
                "send",
                "(Lnet/minecraft/server/world/ServerWorld;"
                        + "Lnet/minecraft/util/math/BlockPos;)V"
        );
        assertEquals(
                List.of(
                        NETWORK + "#send(Lnet/minecraft/server/world/ServerWorld;"
                                + "Lnet/minecraft/util/math/BlockPos;)V"
                ),
                PedestalBytecodeAssertions.calls(backendSend)
        );
    }

    @Test
    void distScopedClientSetupBindsTheOnlyRemovalConsumer() throws IOException {
        ClassNode clientEvents = PedestalBytecodeAssertions.readClass(
                CLIENT_EVENTS + ".class"
        );
        String eventConstants = PedestalBytecodeAssertions.classConstants(
                CLIENT_EVENTS + ".class"
        );
        assertTrue(eventConstants.contains("EventBusSubscriber"));
        assertTrue(eventConstants.contains("CLIENT"));

        MethodNode clientSetup = PedestalBytecodeAssertions.requireMethod(
                clientEvents,
                "onClientSetup",
                "(Lnet/minecraftforge/fml/event/lifecycle/FMLClientSetupEvent;)V"
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countCalls(
                        clientSetup,
                        "net/minecraftforge/fml/event/lifecycle/FMLClientSetupEvent",
                        "enqueueWork",
                        "(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;"
                )
        );
        assertTrue(PedestalBytecodeAssertions.methodHandles(clientSetup).contains(
                CLIENT_EVENTS + "#registerClientContent()V"
        ));

        MethodNode registration = PedestalBytecodeAssertions.requireMethod(
                clientEvents,
                "registerClientContent",
                "()V"
        );
        int clientConsumer = PedestalBytecodeAssertions.fieldAccessIndex(
                registration,
                CLIENT_REMOVAL,
                "INSTANCE",
                Opcodes.GETSTATIC
        );
        int bind = PedestalBytecodeAssertions.callIndex(
                registration,
                NETWORK,
                "bindClientRemoval",
                "(Ljava/util/function/Consumer;)V"
        );
        assertTrue(clientConsumer >= 0);
        assertTrue(clientConsumer < bind);

        ClassNode removal = PedestalBytecodeAssertions.readClass(
                CLIENT_REMOVAL + ".class"
        );
        assertEquals(List.of("java/util/function/Consumer"), removal.interfaces);
        MethodNode accept = PedestalBytecodeAssertions.requireMethod(
                removal,
                "accept",
                "(Lnet/minecraft/util/math/BlockPos;)V"
        );
        int clientInstance = PedestalBytecodeAssertions.callIndex(
                accept,
                "net/minecraft/client/MinecraftClient",
                "getInstance",
                "()Lnet/minecraft/client/MinecraftClient;"
        );
        int clientWorld = PedestalBytecodeAssertions.fieldAccessIndex(
                accept,
                "net/minecraft/client/MinecraftClient",
                "world",
                Opcodes.GETFIELD
        );
        int remove = PedestalBytecodeAssertions.callIndex(
                accept,
                "net/minecraft/client/world/ClientWorld",
                "removeBlockEntity",
                "(Lnet/minecraft/util/math/BlockPos;)V"
        );
        assertTrue(clientInstance >= 0);
        assertTrue(clientInstance < clientWorld);
        assertTrue(clientWorld < remove);
        assertEquals(1, PedestalBytecodeAssertions.countCalls(
                accept,
                "net/minecraft/client/world/ClientWorld",
                "removeBlockEntity",
                "(Lnet/minecraft/util/math/BlockPos;)V"
        ));
    }

    @Test
    void clientSetupRegistersTheSharedRendererAndPedestalCutout()
            throws IOException {
        MethodNode registration = PedestalBytecodeAssertions.requireMethod(
                PedestalBytecodeAssertions.readClass(CLIENT_EVENTS + ".class"),
                "registerClientContent",
                "()V"
        );
        int blockEntitySupplier = PedestalBytecodeAssertions.fieldAccessIndex(
                registration,
                SHARED_PEDESTAL_BLOCK_ENTITIES,
                "PEDESTAL",
                Opcodes.GETSTATIC
        );
        int rendererRegistration = PedestalBytecodeAssertions.lastCallIndex(
                registration,
                "dev/architectury/registry/client/rendering/"
                        + "BlockEntityRendererRegistry",
                "register",
                "(Lnet/minecraft/block/entity/BlockEntityType;"
                        + "Lnet/minecraft/client/render/block/entity/"
                        + "BlockEntityRendererFactory;)V"
        );
        int cutout = PedestalBytecodeAssertions.callIndex(
                registration,
                "net/minecraft/client/render/RenderLayer",
                "getCutout",
                "()Lnet/minecraft/client/render/RenderLayer;"
        );
        int blockSupplier = PedestalBytecodeAssertions.fieldAccessIndex(
                registration,
                SHARED_PEDESTAL_BLOCKS,
                "PEDESTAL",
                Opcodes.GETSTATIC
        );
        int renderLayerRegistration = PedestalBytecodeAssertions.callIndex(
                registration,
                "dev/architectury/registry/client/rendering/RenderTypeRegistry",
                "register",
                "(Lnet/minecraft/client/render/RenderLayer;"
                        + "[Lnet/minecraft/block/Block;)V"
        );
        assertTrue(blockEntitySupplier >= 0);
        assertTrue(blockEntitySupplier < rendererRegistration);
        assertTrue(rendererRegistration < cutout);
        assertTrue(cutout < blockSupplier);
        assertTrue(blockSupplier < renderLayerRegistration);
        assertTrue(PedestalBytecodeAssertions.methodHandles(registration).contains(
                RENDERER + "#<init>(Lnet/minecraft/client/render/block/entity/"
                        + "BlockEntityRendererFactory$Context;)V"
        ));

        ClassNode renderer = PedestalBytecodeAssertions.readClass(RENDERER + ".class");
        assertEquals(
                List.of("net/minecraft/client/render/block/entity/BlockEntityRenderer"),
                renderer.interfaces
        );
        MethodNode render = PedestalBytecodeAssertions.requireMethod(
                renderer,
                "render",
                "(Lru/feytox/etherology/block/pedestal/PedestalBlockEntity;F"
                        + "Lnet/minecraft/client/util/math/MatrixStack;"
                        + "Lnet/minecraft/client/render/VertexConsumerProvider;II)V"
        );
        List<String> calls = PedestalBytecodeAssertions.calls(render);
        assertTrue(calls.contains(
                "ru/feytox/etherology/block/pedestal/PedestalBlockEntity#getStack"
                        + "(I)Lnet/minecraft/item/ItemStack;"
        ));
        assertTrue(calls.contains(
                "ru/feytox/etherology/block/pedestal/PedestalBlockEntity#"
                        + "getUniqueOffset(Lnet/minecraft/util/math/BlockPos;)F"
        ));
        assertTrue(calls.contains(
                "net/minecraft/client/render/item/ItemRenderer#renderItem"
                        + "(Lnet/minecraft/item/ItemStack;"
                        + "Lnet/minecraft/client/render/model/json/"
                        + "ModelTransformationMode;Z"
                        + "Lnet/minecraft/client/util/math/MatrixStack;"
                        + "Lnet/minecraft/client/render/VertexConsumerProvider;II"
                        + "Lnet/minecraft/client/render/model/BakedModel;)V"
        ));
        assertEquals(
                2,
                PedestalBytecodeAssertions.countFieldAccesses(
                        render,
                        "net/minecraft/client/render/model/json/"
                                + "ModelTransformationMode",
                        "GROUND",
                        Opcodes.GETSTATIC
                )
        );
        assertEquals(2, PedestalBytecodeAssertions.countCalls(
                render,
                "net/minecraft/client/util/math/MatrixStack",
                "push",
                "()V"
        ));
        assertEquals(2, PedestalBytecodeAssertions.countCalls(
                render,
                "net/minecraft/client/util/math/MatrixStack",
                "pop",
                "()V"
        ));
    }

    private static ClassNode readNetwork() throws IOException {
        return PedestalBytecodeAssertions.readClass(NETWORK + ".class");
    }

    private static FieldNode requireField(ClassNode classNode, String fieldName) {
        return classNode.fields.stream()
                .filter(field -> field.name.equals(fieldName))
                .findFirst()
                .orElseThrow();
    }
}
