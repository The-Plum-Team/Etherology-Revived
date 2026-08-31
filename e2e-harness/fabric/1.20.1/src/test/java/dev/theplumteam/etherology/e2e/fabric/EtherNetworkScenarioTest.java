package dev.theplumteam.etherology.e2e.fabric;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherNetworkScenarioTest {

    @Test
    void packagedScenarioUsesRegistryIdsWithoutProductionClassLinkage() throws IOException {
        String classConstants = packagedScenarioClassConstants();
        assertFalse(classConstants.contains("ru/feytox/etherology/"));
        assertTrue(classConstants.contains(EtherNetworkScenario.SCENARIO_ID));
        assertTrue(classConstants.contains("spinner"));
        assertTrue(classConstants.contains("ethereal_channel"));
        assertTrue(classConstants.contains("ethereal_storage"));
        assertTrue(classConstants.contains("levitator"));
        assertTrue(classConstants.contains("storage_ether"));
        assertTrue(classConstants.contains("stored_ether"));
    }

    @Test
    void forceProbeResolvesStableUuidWithoutTransientEntityId() throws IOException {
        String classConstants = packagedScenarioClassConstants();

        assertTrue(classConstants.contains(EtherNetworkScenario.FORCE_PROBE_ENTITY_NAME));
        assertTrue(classConstants.contains(
                EtherNetworkScenario.FORCE_PROBE_ENTITY_UUID_STRING
        ));
        assertTrue(classConstants.contains("findServerArmorStand"));
        assertTrue(classConstants.contains("findClientArmorStand"));
        assertTrue(classConstants.contains("getUuid"));
        assertTrue(classConstants.contains("ARMOR_STAND"));
        assertFalse(classConstants.contains("getEntityById"));
        assertFalse(classConstants.contains("armorStandEntityId"));
    }

    @Test
    void initialSetupValidatesSpawnedInstanceWithoutImmediateWorldLookup() throws IOException {
        List<String> calls = methodCallsContaining("setupInitialFixture");
        int spawned = calls.indexOf("spawnEntity");
        int validated = calls.indexOf("requireFixtureArmorStand");

        assertTrue(spawned >= 0);
        assertTrue(validated > spawned);
        assertFalse(calls.contains("findServerArmorStand"));
    }

    @Test
    void forceProbeKeepsGravityAndBuildsSupportBeforeSpawning() throws IOException {
        List<String> setupCalls = methodCallsContaining("setupInitialFixture");
        List<String> arenaCalls = methodCallsContaining("buildArena");
        List<String> trackCalls = methodCallsContaining("buildForceTrack");
        int arenaBuilt = setupCalls.indexOf("buildArena");
        int entitySpawned = setupCalls.indexOf("spawnEntity");

        assertFalse(setupCalls.contains("setNoGravity"));
        assertTrue(arenaBuilt >= 0);
        assertTrue(entitySpawned > arenaBuilt);
        assertTrue(arenaCalls.contains("buildForceTrack"));
        assertTrue(trackCalls.contains("setBlockState"));
    }

    @Test
    void fixtureForcesNetworkChunksAndReportsBoundedTimeoutState() throws IOException {
        String classConstants = packagedScenarioClassConstants();

        assertTrue(classConstants.contains("setChunkForced"));
        assertTrue(classConstants.contains("getForcedChunks"));
        assertTrue(classConstants.contains("server_fixture_chunks_forced"));
        assertTrue(classConstants.contains("serverProgressDiagnostic"));
        assertTrue(classConstants.contains("clientProgressDiagnostic"));
        assertTrue(classConstants.contains("client ticks; server="));
        assertTrue(classConstants.contains("lifecycle_failure"));
        assertTrue(classConstants.contains("network_diagnostics"));
        assertTrue(classConstants.contains("max_output_channel_ether"));
        assertTrue(classConstants.contains("max_levitator_ether"));
        assertTrue(classConstants.contains("max_levitator_fuel"));
        assertTrue(classConstants.contains("max_displacement"));
    }

    @Test
    void networkWaitRefreshesClientAndFailsFastAfterLostUnitGrace() throws IOException {
        List<String> waitCalls = methodCallsContaining("tickWaitingForNetwork");
        List<String> advanceCalls = methodCallsContaining("advanceNetwork");

        assertTrue(waitCalls.contains("captureClientNetwork"));
        assertTrue(advanceCalls.contains("observeNetworkProgress"));
        assertTrue(advanceCalls.contains("firstUnitIsGoneAfterGrace"));
    }

    @Test
    void sourceAcceptancePausesGenerationBeforePublishingChargeResult() throws IOException {
        List<String> calls = methodCallsContaining("submitSourceChargeProbe");
        int accepted = calls.indexOf("matchesChargedStorageState");
        int paused = calls.indexOf("pauseGenerator");
        int published = calls.indexOf("SourceChargeResult.<init>");

        assertTrue(accepted >= 0);
        assertTrue(paused > accepted);
        assertTrue(published > paused);
    }

    @Test
    void scenarioOwnsDistinctSaveAndBeforeAfterEvidenceNames() {
        assertTrue(EtherNetworkScenario.WORLD_DIRECTORY_NAME.contains("ether-network"));
        assertTrue(EtherNetworkScenario.BEFORE_SCREENSHOT_FILE_NAME.endsWith("-before.png"));
        assertTrue(EtherNetworkScenario.AFTER_SCREENSHOT_FILE_NAME.endsWith("-after.png"));
    }

    private String packagedScenarioClassConstants() throws IOException {
        return new String(packagedScenarioClassBytes(), StandardCharsets.ISO_8859_1);
    }

    private byte[] packagedScenarioClassBytes() throws IOException {
        String resourceName = EtherNetworkScenario.class.getSimpleName() + ".class";
        try (InputStream input = EtherNetworkScenario.class.getResourceAsStream(resourceName)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private List<String> methodCallsContaining(String containingMethodName) throws IOException {
        List<String> calls = new ArrayList<>();
        ClassReader reader = new ClassReader(packagedScenarioClassBytes());
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.contains(containingMethodName)) return null;

                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invokedName,
                            String invokedDescriptor,
                            boolean isInterface
                    ) {
                        int nestedSeparator = owner.lastIndexOf('$');
                        int packageSeparator = owner.lastIndexOf('/');
                        int separator = Math.max(nestedSeparator, packageSeparator);
                        if ("<init>".equals(invokedName)) {
                            calls.add(owner.substring(separator + 1) + "." + invokedName);
                        } else {
                            calls.add(invokedName);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return calls;
    }
}
