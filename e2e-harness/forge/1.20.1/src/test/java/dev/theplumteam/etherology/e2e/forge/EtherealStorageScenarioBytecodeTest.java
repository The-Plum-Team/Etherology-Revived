package dev.theplumteam.etherology.e2e.forge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherealStorageScenarioBytecodeTest {

    private static final String SCENARIO_CLASS =
            "dev/theplumteam/etherology/e2e/forge/EtherealStorageScenario.class";
    private static final String CLIENT_WORLD = "net/minecraft/client/world/ClientWorld";
    private static final String MINECRAFT_CLIENT = "net/minecraft/client/MinecraftClient";
    private static final String SCENARIO =
            "dev/theplumteam/etherology/e2e/forge/EtherealStorageScenario";

    @Test
    void disconnectsTheWorldBeforeWaitingForTheIntegratedServer() throws IOException {
        List<Invocation> invocations = invocations("tickDisconnecting");

        int worldDisconnect = invocations.indexOf(new Invocation(
                CLIENT_WORLD,
                "disconnect",
                "()V"
        ));
        int clientDisconnect = invocations.indexOf(new Invocation(
                MINECRAFT_CLIENT,
                "disconnect",
                "(Lnet/minecraft/client/gui/screen/Screen;)V"
        ));
        int transition = invocations.indexOf(new Invocation(
                SCENARIO,
                "transition",
                "(Ldev/theplumteam/etherology/e2e/forge/EtherealStorageScenario$Stage;)V"
        ));

        assertTrue(worldDisconnect >= 0, "The restart must close the client world connection");
        assertTrue(clientDisconnect > worldDisconnect, "The client must disconnect second");
        assertTrue(transition > clientDisconnect, "The stage must advance after disconnecting");
    }

    @Test
    void probesUnsidedAndEveryDirectionalViewBeforeSeedingTransferInput()
            throws IOException {
        List<Invocation> invocations = invocations("inspectAndUseCapability");

        assertEquals(2, countInvocations(invocations, SCENARIO, "inspectCapabilityView"));
        assertEquals(
                1,
                countInvocations(invocations, "net/minecraft/util/math/Direction", "values")
        );
        assertEquals(1, countInvocations(invocations, SCENARIO, "seedSingleChargedGlint"));

        int unsidedProbe = indexOfInvocation(invocations, SCENARIO, "inspectCapabilityView");
        int directions = indexOfInvocation(
                invocations,
                "net/minecraft/util/math/Direction",
                "values"
        );
        int directionalProbe = lastIndexOfInvocation(
                invocations,
                SCENARIO,
                "inspectCapabilityView"
        );
        int finalSeed = indexOfInvocation(invocations, SCENARIO, "seedSingleChargedGlint");
        assertTrue(unsidedProbe >= 0 && directions > unsidedProbe);
        assertTrue(directionalProbe > directions && finalSeed > directionalProbe);

        List<Invocation> aggregation = invocations("inspectCapabilityView");
        assertEquals(6, countInvocations(aggregation, SCENARIO, "recordFailedView"));
    }

    @Test
    void isolatesEveryMutatingProbeAgainstTheAuthoritativeInventory()
            throws IOException {
        List<Invocation> viewProbe = invocations("probeCapabilityView");
        assertEquals(
                2,
                countInvocations(viewProbe, SCENARIO, "resetCapabilityProbeInventory")
        );
        assertEquals(1, countInvocations(viewProbe, SCENARIO, "seedSingleChargedGlint"));
        assertEquals(1, countInvocations(viewProbe, SCENARIO, "probeThreeSlots"));
        assertEquals(1, countInvocations(viewProbe, SCENARIO, "probeGlintValidity"));
        assertEquals(1, countInvocations(viewProbe, SCENARIO, "probeSimulatedInsertion"));
        assertEquals(1, countInvocations(viewProbe, SCENARIO, "probeLiveInsertion"));
        assertEquals(1, countInvocations(viewProbe, SCENARIO, "probeBlockedExtraction"));
        assertEquals(1, countInvocations(viewProbe, SCENARIO, "probeDisplaySlotHidden"));

        List<Invocation> simulated = invocations("probeSimulatedInsertion");
        assertEquals(
                1,
                countInvocations(simulated, "net/minecraftforge/items/IItemHandler", "insertItem")
        );
        assertEquals(1, countInvocations(simulated, SCENARIO, "copyInventory"));
        assertEquals(1, countInvocations(simulated, SCENARIO, "inventoryMatches"));

        List<Invocation> live = invocations("probeLiveInsertion");
        assertEquals(
                1,
                countInvocations(live, "net/minecraftforge/items/IItemHandler", "insertItem")
        );
        assertEquals(1, countInvocations(live, SCENARIO, "hasExactlyOneChargedInput"));

        List<Invocation> extraction = invocations("probeBlockedExtraction");
        assertEquals(
                1,
                countInvocations(extraction, "net/minecraftforge/items/IItemHandler", "extractItem")
        );
        assertEquals(1, countInvocations(extraction, SCENARIO, "copyInventory"));
        assertEquals(1, countInvocations(extraction, SCENARIO, "inventoryMatches"));

        List<Invocation> seed = invocations("seedSingleChargedGlint");
        assertEquals(1, countInvocations(seed, SCENARIO, "resetCapabilityProbeInventory"));
        assertEquals(1, countInvocations(seed, "net/minecraft/inventory/Inventory", "setStack"));
        assertEquals(1, countInvocations(seed, SCENARIO, "hasExactlyOneChargedInput"));
    }

    private static List<Invocation> invocations(String methodName) throws IOException {
        List<Invocation> invocations = new ArrayList<>();
        try (InputStream input = EtherealStorageScenarioBytecodeTest.class
                .getClassLoader()
                .getResourceAsStream(SCENARIO_CLASS)) {
            assertNotNull(input, "The compiled Forge scenario class is missing");
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    if (!name.equals(methodName)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface
                        ) {
                            invocations.add(new Invocation(owner, name, descriptor));
                        }
                    };
                }
            }, 0);
        }
        return invocations;
    }

    private static int countInvocations(
            List<Invocation> invocations,
            String owner,
            String name
    ) {
        int count = 0;
        for (Invocation invocation : invocations) {
            if (invocation.owner().equals(owner) && invocation.name().equals(name)) {
                count++;
            }
        }
        return count;
    }

    private static int indexOfInvocation(
            List<Invocation> invocations,
            String owner,
            String name
    ) {
        for (int index = 0; index < invocations.size(); index++) {
            Invocation invocation = invocations.get(index);
            if (invocation.owner().equals(owner) && invocation.name().equals(name)) {
                return index;
            }
        }
        return -1;
    }

    private static int lastIndexOfInvocation(
            List<Invocation> invocations,
            String owner,
            String name
    ) {
        for (int index = invocations.size() - 1; index >= 0; index--) {
            Invocation invocation = invocations.get(index);
            if (invocation.owner().equals(owner) && invocation.name().equals(name)) {
                return index;
            }
        }
        return -1;
    }

    private record Invocation(String owner, String name, String descriptor) {
    }
}
