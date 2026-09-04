package ru.feytox.etherology.block.pedestal;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalBlockEntityRemovalTest {

    private static final String REMOVAL =
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntityRemoval";
    private static final String BACKEND =
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntityRemovalBackend";

    @BeforeEach
    @AfterEach
    void restoreUnavailableBackend() throws ReflectiveOperationException {
        Field unavailableField = PedestalBlockEntityRemoval.class.getDeclaredField(
                "UNAVAILABLE"
        );
        Field backendField = PedestalBlockEntityRemoval.class.getDeclaredField("backend");
        unavailableField.setAccessible(true);
        backendField.setAccessible(true);
        backendField.set(null, unavailableField.get(null));
    }

    @Test
    void bindingFailsClosedAllowsTheSameInstanceAndRejectsReplacement() {
        BlockPos position = new BlockPos(7, 11, -13);

        IllegalStateException unavailable = assertThrows(
                IllegalStateException.class,
                () -> PedestalBlockEntityRemoval.send(null, position)
        );
        assertEquals(
                "The Pedestal block-entity removal backend is not bound",
                unavailable.getMessage()
        );
        assertThrows(
                NullPointerException.class,
                () -> PedestalBlockEntityRemoval.bind(null)
        );

        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicReference<Object> receivedWorld = new AtomicReference<>();
        AtomicReference<BlockPos> receivedPosition = new AtomicReference<>();
        PedestalBlockEntityRemovalBackend primary = (world, pos) -> {
            primaryCalls.incrementAndGet();
            receivedWorld.set(world);
            receivedPosition.set(pos);
        };
        AtomicInteger replacementCalls = new AtomicInteger();
        PedestalBlockEntityRemovalBackend replacement = (world, pos) ->
                replacementCalls.incrementAndGet();

        PedestalBlockEntityRemoval.bind(primary);
        PedestalBlockEntityRemoval.bind(primary);
        PedestalBlockEntityRemoval.send(null, position);
        assertEquals(1, primaryCalls.get());
        assertNull(receivedWorld.get());
        assertSame(position, receivedPosition.get());

        IllegalStateException duplicate = assertThrows(
                IllegalStateException.class,
                () -> PedestalBlockEntityRemoval.bind(replacement)
        );
        assertEquals(
                "A Pedestal block-entity removal backend is already bound",
                duplicate.getMessage()
        );
        PedestalBlockEntityRemoval.send(null, position);
        assertEquals(2, primaryCalls.get());
        assertEquals(0, replacementCalls.get());
    }

    @Test
    void bytecodeKeepsOneVolatileBackendAndDirectDelegation() throws IOException {
        PedestalClassFile.ClassShape removal = PedestalClassFile.shape(REMOVAL);
        assertEquals("java/lang/Object", removal.superName());
        assertEquals(List.of(), removal.interfaces());
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                removal.fields().get("UNAVAILABLE").access()
        );
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE,
                removal.fields().get("backend").access()
        );
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED,
                removal.methods().get("bind(L" + BACKEND + ";)V").access()
        );

        PedestalClassFile.ClassShape backend = PedestalClassFile.shape(BACKEND);
        assertTrue((backend.access() & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((backend.access() & Opcodes.ACC_INTERFACE) != 0);
        assertTrue((backend.access() & Opcodes.ACC_ABSTRACT) != 0);
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                backend.methods().get(
                        "send(Lnet/minecraft/server/world/ServerWorld;"
                                + "Lnet/minecraft/util/math/BlockPos;)V"
                ).access()
        );

        PedestalClassFile.MethodTrace send = PedestalClassFile.trace(
                REMOVAL,
                "send"
        );
        assertEquals(
                List.of("backend"),
                send.fieldInstructions().stream()
                        .map(PedestalClassFile.FieldInstruction::name)
                        .toList()
        );
        assertEquals(1, send.invocations().size());
        assertEquals(BACKEND, send.invocations().get(0).owner());
        assertEquals("send", send.invocations().get(0).name());
        assertEquals(Opcodes.INVOKEINTERFACE, send.invocations().get(0).opcode());
    }
}
