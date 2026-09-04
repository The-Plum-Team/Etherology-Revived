package ru.feytox.etherology.forge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalCrossArtifactTest {

    private static final String CLASS_PREFIX = "ru/feytox/etherology/";
    private static final String PEDESTAL_PREFIX =
            CLASS_PREFIX + "block/pedestal/";
    private static final String PEDESTAL_BLOCK =
            PEDESTAL_PREFIX + "PedestalBlock.class";
    private static final String PEDESTAL_BLOCK_ENTITY =
            PEDESTAL_PREFIX + "PedestalBlockEntity.class";
    private static final String PEDESTAL_REMOVAL =
            PEDESTAL_PREFIX + "PedestalBlockEntityRemoval.class";
    private static final String PEDESTAL_REMOVAL_BACKEND =
            PEDESTAL_PREFIX + "PedestalBlockEntityRemovalBackend.class";
    private static final String PEDESTAL_DISPENSER =
            PEDESTAL_PREFIX + "PedestalDispenserBehavior.class";
    private static final String PEDESTAL_SHAPE =
            PEDESTAL_PREFIX + "PedestalShape.class";
    private static final String PEDESTAL_SWITCH_SUPPORT =
            PEDESTAL_PREFIX + "PedestalBlock$1.class";
    private static final String LIST_BACKED_INVENTORY =
            CLASS_PREFIX + "util/inventory/ListBackedInventory.class";
    private static final String UNIQUE_PROVIDER =
            CLASS_PREFIX + "util/misc/UniqueProvider.class";
    private static final String SHARED_PEDESTAL_BLOCKS =
            CLASS_PREFIX + "registry/block/SharedPedestalBlocks.class";
    private static final String SHARED_PEDESTAL_ITEMS =
            CLASS_PREFIX + "registry/item/SharedPedestalBlockItems.class";
    private static final String SHARED_PEDESTAL_BLOCK_ENTITIES =
            CLASS_PREFIX + "registry/block/SharedPedestalBlockEntities.class";
    private static final String BOOTSTRAP =
            CLASS_PREFIX + "bootstrap/EtherologyBootstrap.class";
    private static final String REVELATION_PROVIDER_OWNER =
            CLASS_PREFIX + "magic/aspects/RevelationAspectProvider";
    private static final String ASPECTS_LOADER_OWNER =
            CLASS_PREFIX + "data/aspects/AspectsLoader";
    private static final Set<String> ITEM_STACK_BY_SLOT_DESCRIPTORS = Set.of(
            "(I)Lnet/minecraft/item/ItemStack;",
            "(I)Lnet/minecraft/class_1799;"
    );

    private static final List<String> SHARED_REGISTRY_OWNERS = List.of(
            classOwner(SHARED_PEDESTAL_BLOCKS),
            classOwner(SHARED_PEDESTAL_ITEMS),
            classOwner(SHARED_PEDESTAL_BLOCK_ENTITIES)
    );
    private static final Set<String> SHARED_CLASSES = Set.of(
            PEDESTAL_BLOCK,
            PEDESTAL_BLOCK_ENTITY,
            PEDESTAL_REMOVAL,
            PEDESTAL_REMOVAL_BACKEND,
            PEDESTAL_DISPENSER,
            PEDESTAL_SHAPE,
            LIST_BACKED_INVENTORY,
            UNIQUE_PROVIDER,
            SHARED_PEDESTAL_BLOCKS,
            SHARED_PEDESTAL_ITEMS,
            SHARED_PEDESTAL_BLOCK_ENTITIES
    );
    private static final Set<String> SHARED_PEDESTAL_PACKAGE_CLASSES = Set.of(
            PEDESTAL_BLOCK,
            PEDESTAL_BLOCK_ENTITY,
            PEDESTAL_REMOVAL,
            PEDESTAL_REMOVAL_BACKEND,
            PEDESTAL_DISPENSER,
            PEDESTAL_SHAPE,
            PEDESTAL_SWITCH_SUPPORT
    );

    private static final String FABRIC_ENTRYPOINT =
            CLASS_PREFIX + "EtherologyFabric.class";
    private static final String FABRIC_DISPENSER_MIXIN =
            CLASS_PREFIX + "mixin/DispenserBlockMixin.class";
    private static final String FABRIC_PEDESTAL_BACKEND =
            PEDESTAL_PREFIX + "FabricPedestalBlockEntityRemovalBackend.class";
    private static final Set<String> FABRIC_OVERLAY_CLASSES = Set.of(
            FABRIC_ENTRYPOINT,
            FABRIC_PEDESTAL_BACKEND,
            CLASS_PREFIX + "client/block/pedestal/PedestalRenderer.class",
            CLASS_PREFIX + "client/registry/BlockRenderLayerMapRegistry.class",
            CLASS_PREFIX + "client/registry/BlockRenderersRegistry.class",
            FABRIC_DISPENSER_MIXIN,
            CLASS_PREFIX + "network/interaction/RemoveBlockEntityS2C.class"
    );

    private static final String FORGE_ENTRYPOINT =
            CLASS_PREFIX + "forge/EtherologyForge.class";
    private static final String FORGE_DISPENSER_MIXIN =
            CLASS_PREFIX + "forge/mixin/PedestalDispenserBlockMixin.class";
    private static final Set<String> FORGE_PEDESTAL_CLASSES = Set.of(
            CLASS_PREFIX + "forge/block/pedestal/"
                    + "ForgePedestalBlockEntityRemovalBackend.class",
            CLASS_PREFIX + "forge/client/ForgePedestalClientRemoval.class",
            CLASS_PREFIX + "forge/client/PedestalRenderer.class",
            FORGE_DISPENSER_MIXIN,
            CLASS_PREFIX + "forge/network/ForgePedestalNetwork.class",
            CLASS_PREFIX + "forge/network/RemovePedestalBlockEntityS2C.class"
    );
    private static final Set<String> FORGE_OVERLAY_CLASSES = Set.of(
            FORGE_ENTRYPOINT,
            CLASS_PREFIX + "forge/block/pedestal/"
                    + "ForgePedestalBlockEntityRemovalBackend.class",
            CLASS_PREFIX + "forge/client/ForgeClientEvents.class",
            CLASS_PREFIX + "forge/client/ForgePedestalClientRemoval.class",
            CLASS_PREFIX + "forge/client/PedestalRenderer.class",
            FORGE_DISPENSER_MIXIN,
            CLASS_PREFIX + "forge/network/ForgePedestalNetwork.class",
            CLASS_PREFIX + "forge/network/RemovePedestalBlockEntityS2C.class"
    );
    private static final Set<String> ALL_OVERLAY_CLASSES = Stream.concat(
            FABRIC_OVERLAY_CLASSES.stream(),
            FORGE_OVERLAY_CLASSES.stream()
    ).collect(Collectors.toUnmodifiableSet());

    private static final String FABRIC_MIXIN_CONFIG = "etherology.mixins.json";
    private static final String FORGE_MIXIN_CONFIG =
            "etherology.forge.mixins.json";
    private static final Set<String> MIXIN_CONFIGS = Set.of(
            FABRIC_MIXIN_CONFIG,
            FORGE_MIXIN_CONFIG
    );
    private static final Set<String> PEDESTAL_DATA = Set.of(
            "data/etherology/advancements/recipes/decorations/pedestal.json",
            "data/etherology/loot_tables/blocks/pedestal.json",
            "data/etherology/recipes/pedestal.json"
    );
    private static final Map<String, String> LANGUAGE_ALIASES = Map.of(
            "assets/etherology/lang/en_us.json", "Pedestal",
            "assets/etherology/lang/ru_ru.json", "Пьедестал"
    );

    @Test
    void everyArtifactContainsExactlyOneCopyOfTheElevenSharedClasses()
            throws IOException {
        assertEquals(11, SHARED_CLASSES.size());

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = entryNames(jar);
                for (String sharedClass : SHARED_CLASSES) {
                    assertEntryCount(artifact, entries, sharedClass, 1);
                    String constants = new String(
                            readEntry(jar, sharedClass),
                            StandardCharsets.ISO_8859_1
                    );
                    assertFalse(constants.contains("net/fabricmc/"), sharedClass);
                    assertFalse(constants.contains("net/minecraftforge/"), sharedClass);
                    assertFalse(constants.contains("net/neoforged/"), sharedClass);
                    assertFalse(constants.contains("lombok/"), sharedClass);
                }
                assertEntryCount(artifact, entries, PEDESTAL_SWITCH_SUPPORT, 1);
                assertEquals(
                        SHARED_CLASSES,
                        presentEntries(entries, SHARED_CLASSES),
                        artifact.description()
                );
            }
        }
    }

    @Test
    void loaderOverlaysAndMixinConfigsAreStrictlyIsolated() throws IOException {
        Path repositoryRoot = repositoryRoot();
        Map<String, Path> canonicalConfigs = Map.of(
                FABRIC_MIXIN_CONFIG,
                requiredRegularFile(
                        repositoryRoot.resolve("src/main/resources/")
                                .resolve(FABRIC_MIXIN_CONFIG)
                ),
                FORGE_MIXIN_CONFIG,
                requiredRegularFile(
                        repositoryRoot.resolve("forge/src/main/resources/")
                                .resolve(FORGE_MIXIN_CONFIG)
                )
        );

        for (Artifact artifact : artifacts()) {
            Set<String> expectedOverlay = switch (artifact.application()) {
                case NONE -> Set.of();
                case FABRIC -> FABRIC_OVERLAY_CLASSES;
                case FORGE -> FORGE_OVERLAY_CLASSES;
            };
            Set<String> expectedConfigs = switch (artifact.application()) {
                case NONE -> Set.of();
                case FABRIC -> Set.of(FABRIC_MIXIN_CONFIG);
                case FORGE -> Set.of(FORGE_MIXIN_CONFIG);
            };

            try (JarFile jar = artifact.open()) {
                List<String> entries = entryNames(jar);
                Set<String> expectedPedestalPackage = new LinkedHashSet<>(
                        SHARED_PEDESTAL_PACKAGE_CLASSES
                );
                if (artifact.application() == Application.FABRIC) {
                    expectedPedestalPackage.add(FABRIC_PEDESTAL_BACKEND);
                }
                assertEquals(
                        expectedPedestalPackage,
                        entries.stream()
                                .filter(entry -> entry.startsWith(PEDESTAL_PREFIX))
                                .filter(entry -> entry.endsWith(".class"))
                                .collect(Collectors.toUnmodifiableSet()),
                        artifact.description()
                );
                assertEquals(
                        artifact.application() == Application.FORGE
                                ? FORGE_PEDESTAL_CLASSES
                                : Set.of(),
                        entries.stream()
                                .filter(entry -> entry.startsWith(
                                        CLASS_PREFIX + "forge/"
                                ))
                                .filter(entry -> entry.endsWith(".class"))
                                .filter(entry -> fileName(entry).contains("Pedestal"))
                                .collect(Collectors.toUnmodifiableSet()),
                        artifact.description()
                );
                if (artifact.application() != Application.FORGE) {
                    assertFalse(
                            entries.stream().anyMatch(entry -> entry.startsWith(
                                    CLASS_PREFIX + "forge/"
                            )),
                            artifact.description()
                    );
                }
                assertEquals(
                        expectedOverlay,
                        presentEntries(entries, ALL_OVERLAY_CLASSES),
                        artifact.description()
                );
                for (String overlayClass : ALL_OVERLAY_CLASSES) {
                    assertEntryCount(
                            artifact,
                            entries,
                            overlayClass,
                            expectedOverlay.contains(overlayClass) ? 1 : 0
                    );
                }

                assertEquals(
                        expectedConfigs,
                        presentEntries(entries, MIXIN_CONFIGS),
                        artifact.description()
                );
                for (String config : MIXIN_CONFIGS) {
                    int expectedCount = expectedConfigs.contains(config) ? 1 : 0;
                    assertEntryCount(artifact, entries, config, expectedCount);
                    if (expectedCount == 1) {
                        assertArrayEquals(
                                Files.readAllBytes(canonicalConfigs.get(config)),
                                readEntry(jar, config),
                                artifact.description() + ":" + config
                        );
                    }
                }
            }
        }
    }

    @Test
    void registrationRevelationRemovalAndDispenserContractsSurviveTransforms()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                MethodNode initialize = requireMethod(
                        readClass(jar, BOOTSTRAP),
                        "initialize"
                );
                List<String> pedestalRegistrations = methodCalls(initialize).stream()
                        .filter(call -> call.name.equals("register"))
                        .map(call -> call.owner)
                        .filter(SHARED_REGISTRY_OWNERS::contains)
                        .toList();
                assertEquals(
                        SHARED_REGISTRY_OWNERS,
                        pedestalRegistrations,
                        artifact.description()
                );

                ClassNode blockEntity = readClass(jar, PEDESTAL_BLOCK_ENTITY);
                assertTrue(
                        blockEntity.interfaces.contains(REVELATION_PROVIDER_OWNER),
                        artifact.description()
                );
                MethodNode revelation = requireMethod(
                        blockEntity,
                        "getRevelationAspects"
                );
                assertEquals(
                        1,
                        methodCalls(revelation).stream()
                                .filter(call -> call.owner.equals(
                                        classOwner(PEDESTAL_BLOCK_ENTITY)
                                ))
                                .filter(call -> ITEM_STACK_BY_SLOT_DESCRIPTORS
                                        .contains(call.desc))
                                .count(),
                        artifact.description()
                );
                assertEquals(
                        1,
                        countCalls(revelation, ASPECTS_LOADER_OWNER, "getAspects"),
                        artifact.description()
                );

                MethodNode removal = requireMethod(
                        readClass(jar, PEDESTAL_REMOVAL),
                        "send"
                );
                assertEquals(
                        1,
                        countCalls(
                                removal,
                                classOwner(PEDESTAL_REMOVAL_BACKEND),
                                "send"
                        ),
                        artifact.description()
                );

                MethodNode selector = requireMethod(
                        readClass(jar, PEDESTAL_DISPENSER),
                        "testDispenser"
                );
                assertEquals(
                        1,
                        countTypeInstructions(
                                selector,
                                Opcodes.INSTANCEOF,
                                classOwner(PEDESTAL_BLOCK_ENTITY)
                        ),
                        artifact.description()
                );

                if (artifact.application() == Application.FABRIC) {
                    assertFabricEntrypointBinding(artifact, jar);
                    assertDispenserHook(
                            artifact,
                            jar,
                            FABRIC_DISPENSER_MIXIN,
                            "injectPedestalBehavior"
                    );
                }
                if (artifact.application() == Application.FORGE) {
                    assertForgeEntrypointBinding(artifact, jar);
                    assertDispenserHook(
                            artifact,
                            jar,
                            FORGE_DISPENSER_MIXIN,
                            "etherology$selectPedestalBehavior"
                    );
                }
            }
        }
    }

    @Test
    void applicationArtifactsPackageExactCanonicalPedestalResources()
            throws IOException {
        Path repositoryRoot = repositoryRoot();
        Map<String, Path> canonicalAssets = canonicalPedestalAssets(repositoryRoot);
        Map<String, Path> canonicalData = canonicalPedestalData(repositoryRoot);
        Map<String, Path> canonicalLanguages = canonicalLanguages(repositoryRoot);
        assertEquals(59, canonicalAssets.size());
        assertEquals(3, canonicalData.size());
        assertEquals(LANGUAGE_ALIASES.keySet(), canonicalLanguages.keySet());

        for (Map.Entry<String, String> alias : LANGUAGE_ALIASES.entrySet()) {
            JsonObject language = JsonParser.parseString(
                    Files.readString(
                            canonicalLanguages.get(alias.getKey()),
                            StandardCharsets.UTF_8
                    )
            ).getAsJsonObject();
            assertEquals(
                    alias.getValue(),
                    language.get("block.etherology.pedestal").getAsString(),
                    alias.getKey()
            );
        }

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = entryNames(jar);
                Set<String> packagedAssets = matchingPedestalAssets(entries);
                Set<String> packagedData = matchingPedestalData(entries);

                if (artifact.application() == Application.NONE) {
                    assertEquals(Set.of(), packagedAssets, artifact.description());
                    assertEquals(Set.of(), packagedData, artifact.description());
                    for (String language : LANGUAGE_ALIASES.keySet()) {
                        assertEntryCount(artifact, entries, language, 0);
                    }
                    continue;
                }

                assertEquals(
                        canonicalAssets.keySet(),
                        packagedAssets,
                        artifact.description()
                );
                assertEquals(
                        canonicalData.keySet(),
                        packagedData,
                        artifact.description()
                );
                assertCanonicalResources(artifact, jar, entries, canonicalAssets);
                assertCanonicalResources(artifact, jar, entries, canonicalData);
                assertCanonicalResources(artifact, jar, entries, canonicalLanguages);
            }
        }
    }

    private static void assertFabricEntrypointBinding(
            Artifact artifact,
            JarFile jar
    ) throws IOException {
        MethodNode initializer = requireMethod(
                readClass(jar, FABRIC_ENTRYPOINT),
                "onInitialize"
        );
        int bind = callIndex(
                initializer,
                classOwner(PEDESTAL_REMOVAL),
                "bind"
        );
        int initialize = callIndex(
                initializer,
                CLASS_PREFIX + "Etherology",
                "initialize"
        );
        assertTrue(bind >= 0, artifact.description());
        assertTrue(bind < initialize, artifact.description());
    }

    private static void assertForgeEntrypointBinding(
            Artifact artifact,
            JarFile jar
    ) throws IOException {
        MethodNode constructor = requireMethod(
                readClass(jar, FORGE_ENTRYPOINT),
                "<init>"
        );
        int network = callIndex(
                constructor,
                CLASS_PREFIX + "forge/network/ForgePedestalNetwork",
                "register"
        );
        int bind = callIndex(
                constructor,
                classOwner(PEDESTAL_REMOVAL),
                "bind"
        );
        int initialize = callIndex(
                constructor,
                classOwner(BOOTSTRAP),
                "initialize"
        );
        assertTrue(network >= 0, artifact.description());
        assertTrue(network < bind, artifact.description());
        assertTrue(bind < initialize, artifact.description());
    }

    private static void assertDispenserHook(
            Artifact artifact,
            JarFile jar,
            String mixinClass,
            String methodName
    ) throws IOException {
        MethodNode hook = requireMethod(readClass(jar, mixinClass), methodName);
        int test = callIndex(
                hook,
                classOwner(PEDESTAL_DISPENSER),
                "testDispenser"
        );
        int selection = callIndex(
                hook,
                classOwner(PEDESTAL_DISPENSER),
                "getInstance"
        );
        assertTrue(test >= 0, artifact.description());
        assertTrue(test < selection, artifact.description());
        assertEquals(
                1,
                countCalls(
                        hook,
                        classOwner(PEDESTAL_DISPENSER),
                        "testDispenser"
                ),
                artifact.description()
        );
        assertEquals(
                1,
                countCalls(
                        hook,
                        classOwner(PEDESTAL_DISPENSER),
                        "getInstance"
                ),
                artifact.description()
        );
    }

    private static void assertCanonicalResources(
            Artifact artifact,
            JarFile jar,
            List<String> entries,
            Map<String, Path> canonicalResources
    ) throws IOException {
        for (Map.Entry<String, Path> resource : canonicalResources.entrySet()) {
            assertEntryCount(artifact, entries, resource.getKey(), 1);
            assertArrayEquals(
                    Files.readAllBytes(resource.getValue()),
                    readEntry(jar, resource.getKey()),
                    artifact.description() + ":" + resource.getKey()
            );
        }
    }

    private static Map<String, Path> canonicalPedestalAssets(Path repositoryRoot)
            throws IOException {
        Path assetRoot = requiredDirectory(
                repositoryRoot.resolve("src/client/resources/assets/etherology")
        );
        Map<String, Path> assets = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(assetRoot)) {
            for (Path path : paths
                    .filter(candidate -> Files.isRegularFile(
                            candidate,
                            LinkOption.NOFOLLOW_LINKS
                    ))
                    .filter(candidate -> candidate.getFileName()
                            .toString()
                            .contains("pedestal"))
                    .sorted()
                    .toList()) {
                assertFalse(Files.isSymbolicLink(path), path.toString());
                String relative = assetRoot.relativize(path)
                        .toString()
                        .replace(path.getFileSystem().getSeparator(), "/");
                String entry = "assets/etherology/" + relative;
                assertFalse(assets.containsKey(entry), entry);
                assets.put(entry, path.toRealPath(LinkOption.NOFOLLOW_LINKS));
            }
        }
        assertEquals(59, assets.size());
        return Map.copyOf(assets);
    }

    private static Map<String, Path> canonicalPedestalData(Path repositoryRoot)
            throws IOException {
        Map<String, Path> data = new LinkedHashMap<>();
        for (String entry : PEDESTAL_DATA) {
            data.put(
                    entry,
                    requiredRegularFile(
                            repositoryRoot.resolve("src/main/generated").resolve(entry)
                    )
            );
        }
        return Map.copyOf(data);
    }

    private static Map<String, Path> canonicalLanguages(Path repositoryRoot)
            throws IOException {
        return Map.of(
                "assets/etherology/lang/en_us.json",
                requiredRegularFile(repositoryRoot.resolve(
                        "src/client/resources/assets/etherology/lang/en_us.json"
                )),
                "assets/etherology/lang/ru_ru.json",
                requiredRegularFile(repositoryRoot.resolve(
                        "src/main/generated/assets/etherology/lang/ru_ru.json"
                ))
        );
    }

    private static Set<String> matchingPedestalAssets(List<String> entries) {
        return entries.stream()
                .filter(entry -> entry.startsWith("assets/etherology/"))
                .filter(entry -> fileName(entry).contains("pedestal"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> matchingPedestalData(List<String> entries) {
        return entries.stream()
                .filter(entry -> entry.startsWith("data/"))
                .filter(entry -> fileName(entry).equals("pedestal.json"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String fileName(String entry) {
        int separator = entry.lastIndexOf('/');
        return separator < 0 ? entry : entry.substring(separator + 1);
    }

    private static Set<String> presentEntries(
            List<String> entries,
            Set<String> candidates
    ) {
        return candidates.stream()
                .filter(entries::contains)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static void assertEntryCount(
            Artifact artifact,
            List<String> entries,
            String entry,
            long expected
    ) {
        assertEquals(
                expected,
                entries.stream().filter(entry::equals).count(),
                artifact.description() + ":" + entry
        );
    }

    private static ClassNode readClass(JarFile jar, String entryName)
            throws IOException {
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        try (InputStream input = jar.getInputStream(requiredEntry(jar, entryName))) {
            new ClassReader(input).accept(classNode, 0);
        }
        return classNode;
    }

    private static MethodNode requireMethod(ClassNode classNode, String name) {
        List<MethodNode> methods = classNode.methods.stream()
                .filter(method -> method.name.equals(name))
                .toList();
        assertEquals(1, methods.size(), classNode.name + "#" + name);
        return methods.get(0);
    }

    private static List<MethodInsnNode> methodCalls(MethodNode method) {
        List<MethodInsnNode> calls = new java.util.ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                calls.add(call);
            }
        }
        return List.copyOf(calls);
    }

    private static long countCalls(
            MethodNode method,
            String owner,
            String name
    ) {
        return methodCalls(method).stream()
                .filter(call -> call.owner.equals(owner))
                .filter(call -> call.name.equals(name))
                .count();
    }

    private static int callIndex(
            MethodNode method,
            String owner,
            String name
    ) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(owner)
                    && call.name.equals(name)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static long countTypeInstructions(
            MethodNode method,
            int opcode,
            String type
    ) {
        long count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode typeInstruction
                    && typeInstruction.getOpcode() == opcode
                    && typeInstruction.desc.equals(type)) {
                count++;
            }
        }
        return count;
    }

    private static List<String> entryNames(JarFile jar) {
        return jar.stream().map(JarEntry::getName).toList();
    }

    private static byte[] readEntry(JarFile jar, String entryName)
            throws IOException {
        try (InputStream input = jar.getInputStream(requiredEntry(jar, entryName))) {
            return input.readAllBytes();
        }
    }

    private static JarEntry requiredEntry(JarFile jar, String entryName) {
        JarEntry entry = jar.getJarEntry(entryName);
        assertNotNull(entry, entryName);
        return entry;
    }

    private static String classOwner(String classEntry) {
        assertTrue(classEntry.endsWith(".class"), classEntry);
        return classEntry.substring(0, classEntry.length() - ".class".length());
    }

    private static Path repositoryRoot() throws IOException {
        return requiredDirectory(requiredPath("etherology.pedestal.repositoryRoot"));
    }

    private static Path requiredRegularFile(Path path) throws IOException {
        assertTrue(
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS),
                path.toString()
        );
        assertFalse(Files.isSymbolicLink(path), path.toString());
        return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static Path requiredDirectory(Path path) throws IOException {
        assertTrue(
                Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS),
                path.toString()
        );
        assertFalse(Files.isSymbolicLink(path), path.toString());
        return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static Path requiredPath(String propertyName) throws IOException {
        String value = System.getProperty(propertyName);
        assertNotNull(value, propertyName);
        Path path = Path.of(value);
        assertTrue(Files.exists(path, LinkOption.NOFOLLOW_LINKS), path.toString());
        assertFalse(Files.isSymbolicLink(path), path.toString());
        return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static List<Artifact> artifacts() throws IOException {
        List<Artifact> artifacts = List.of(
                artifact("commonJar", "common JAR", Application.NONE),
                artifact(
                        "fabricTransformedCommonJar",
                        "Fabric-transformed common JAR",
                        Application.NONE
                ),
                artifact(
                        "forgeTransformedCommonJar",
                        "Forge-transformed common JAR",
                        Application.NONE
                ),
                artifact(
                        "fabricDevelopmentJar",
                        "Fabric development JAR",
                        Application.FABRIC
                ),
                artifact(
                        "fabricProductionJar",
                        "Fabric remapped production JAR",
                        Application.FABRIC
                ),
                artifact(
                        "forgeShadowJar",
                        "Forge shadow JAR",
                        Application.FORGE
                )
        );
        assertEquals(
                6,
                artifacts.stream()
                        .map(Artifact::path)
                        .collect(Collectors.toSet())
                        .size(),
                "Pedestal artifact paths must be distinct"
        );
        return artifacts;
    }

    private static Artifact artifact(
            String suffix,
            String description,
            Application application
    ) throws IOException {
        Path path = requiredPath("etherology.pedestal." + suffix);
        assertTrue(
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS),
                path.toString()
        );
        return new Artifact(path, description, application);
    }

    private enum Application {
        NONE,
        FABRIC,
        FORGE
    }

    private record Artifact(
            Path path,
            String description,
            Application application
    ) {

        private JarFile open() throws IOException {
            return new JarFile(path.toFile());
        }
    }
}
