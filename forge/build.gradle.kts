import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.architectury.plugin.ArchitectPluginExtension
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.jvm.tasks.Jar

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.ZipFile

plugins {
    java
}

apply(from = rootProject.file("gradle/archive-conventions.gradle.kts"))

val minecraftVersion = stonecutter.current.version
val commonProjectPath = requireNotNull(stonecutter.node.sibling("common")).hierarchy.toString()
val commonProject = project(commonProjectPath)
evaluationDependsOn(commonProjectPath)
val commonJar = commonProject.tasks.named<Jar>("jar")
val commonTest = commonProject.tasks.named("test")
val forgeJavaRoot = rootProject.file("forge/src/main/java")
val forgeResourcesRoot = rootProject.file("forge/src/main/resources")
val forgeMainClasses = layout.buildDirectory.dir("classes/java/main")
val commonBootstrapClassEntry =
    "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class"
val platformRegistrarClassEntry =
    "ru/feytox/etherology/bootstrap/PlatformRegistrar.class"
val sharedDeferredRegisterClassEntry =
    "ru/feytox/etherology/registry/SharedDeferredRegister.class"
val sharedItemRegistryClassEntry =
    "ru/feytox/etherology/registry/item/SharedItems.class"
val sharedBlockRegistryClassEntry =
    "ru/feytox/etherology/registry/block/SharedBlocks.class"
val sharedBlockEntityRegistryClassEntry =
    "ru/feytox/etherology/registry/block/SharedBlockEntities.class"
val etherealStorageFoundationBlockClassEntry =
    "ru/feytox/etherology/block/etherealStorage/EtherealStorageFoundationBlock.class"
val etherealStorageFoundationBlockEntityClassEntry =
    "ru/feytox/etherology/block/etherealStorage/EtherealStorageFoundationBlockEntity.class"
val etherealStorageFoundationScreenHandlerClassEntry =
    "ru/feytox/etherology/block/etherealStorage/EtherealStorageFoundationScreenHandler.class"
val etherealStorageInputItemClassEntry =
    "ru/feytox/etherology/item/EtherealStorageInputItem.class"
val sharedScreenHandlerRegistryClassEntry =
    "ru/feytox/etherology/registry/misc/SharedScreenHandlers.class"
val forgeEntrypointClassEntry =
    "ru/feytox/etherology/forge/EtherologyForge.class"
val forgeClientEventsClassEntry =
    "ru/feytox/etherology/forge/client/ForgeClientEvents.class"
val etherealStorageFoundationScreenClassEntry =
    "ru/feytox/etherology/forge/client/EtherealStorageFoundationScreen.class"
val etherealStorageItemHandlerProviderClassEntry =
    "ru/feytox/etherology/forge/block/etherealStorage/EtherealStorageItemHandlerProvider.class"
val etherStorageContractClassEntry =
    "ru/feytox/etherology/magic/ether/EtherStorage.class"
val etherealChannelBlockClassEntry =
    "ru/feytox/etherology/block/etherealChannel/EtherealChannelBlock.class"
val etherealChannelBlockEntityClassEntry =
    "ru/feytox/etherology/block/etherealChannel/EtherealChannelBlockEntity.class"
val etherItemModel = rootProject.file("src/client/resources/assets/etherology/models/item/ether.json")
val etherItemTexture = rootProject.file("src/client/resources/assets/etherology/textures/item/ether.png")
val englishLanguageFile = rootProject.file("src/client/resources/assets/etherology/lang/en_us.json")
val etherealStorageBlockstate =
    rootProject.file("src/client/resources/assets/etherology/blockstates/ethereal_storage.json")
val etherealStorageBlockModel =
    rootProject.file("src/client/resources/assets/etherology/models/block/ethereal_storage.json")
val etherealStorageItemModel =
    rootProject.file("src/client/resources/assets/etherology/models/item/ethereal_storage.json")
val etherealStorageTexture =
    rootProject.file("src/client/resources/assets/etherology/textures/block/ethereal_storage.png")
val etherealStorageGuiTexture =
    rootProject.file("src/client/resources/assets/etherology/textures/gui/ethereal_storage.png")
val glintShardItemModel =
    rootProject.file("src/client/resources/assets/etherology/models/item/glint_shard.json")
val glintShardItemTexture =
    rootProject.file("src/client/resources/assets/etherology/textures/item/glint_shard_0.png")
val etherealStorageLootTable =
    rootProject.file("src/main/generated/data/etherology/loot_tables/blocks/ethereal_storage.json")
val etherealStorageRecipe =
    rootProject.file("src/main/generated/data/etherology/recipes/ethereal_storage.json")

apply(plugin = "dev.architectury.loom")
apply(plugin = "architectury-plugin")
apply(plugin = "com.gradleup.shadow")

fun Project.versionProperty(base: String): String =
    rootProject.property("${base}_${minecraftVersion.replace(".", "_")}") as String

group = rootProject.property("maven_group") as String
version = rootProject.property("mod_version") as String

extensions.configure<BasePluginExtension>("base") {
    archivesName.set("Etherology - Forge - $minecraftVersion")
}

extensions.configure<ArchitectPluginExtension>("architectury") {
    minecraft = minecraftVersion
    platformSetupLoomIde()
    forge()
}

repositories {
    maven("https://maven.fabricmc.net/") {
        name = "Fabric"
        content {
            includeGroupByRegex("net\\.fabricmc(\\..*)?")
        }
    }
    maven("https://maven.architectury.dev/") {
        name = "Architectury"
        content {
            includeGroupByRegex("dev\\.architectury(\\..*)?")
        }
    }
    mavenCentral()
}

sourceSets {
    main {
        java.setSrcDirs(listOf(forgeJavaRoot))
        resources {
            setSrcDirs(
                listOf(
                    rootProject.file("src/main/resources"),
                    rootProject.file("src/client/resources"),
                    rootProject.file("src/main/generated"),
                    forgeResourcesRoot,
                ),
            )
            include("assets/**")
            include("data/**")
            include("META-INF/**")
            include("pack.mcmeta")
            include("etherology.forge.mixins.json")
        }
    }
    test {
        java.setSrcDirs(listOf(rootProject.file("forge/src/test/java")))
        resources.setSrcDirs(listOf(rootProject.file("forge/src/test/resources")))
    }
}

configurations {
    create("common") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
    create("shadowBundle") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
    compileClasspath.get().extendsFrom(configurations["common"])
    runtimeClasspath.get().extendsFrom(configurations["common"])
    testCompileClasspath.get().extendsFrom(configurations["common"])
    testRuntimeClasspath.get().extendsFrom(configurations["common"])
    named("developmentForge") {
        extendsFrom(configurations["common"])
    }
}

dependencies {
    "minecraft"("net.minecraft:minecraft:${versionProperty("minecraft_version")}")
    "mappings"(
        "net.fabricmc:yarn:${versionProperty("yarn_mappings")}:v2",
    )
    "forge"("net.minecraftforge:forge:${versionProperty("forge_version")}")
    "modImplementation"(
        "dev.architectury:architectury-forge:${versionProperty("architectury_api_version")}",
    )

    "common"(project.files(commonJar))
    "shadowBundle"(project.files(commonProject.tasks.named("transformProductionForge")))

    "testImplementation"("org.junit.jupiter:junit-jupiter:5.13.4")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:1.13.4")
}

val javaVersion = versionProperty("java_version").toInt()
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
}

tasks.test {
    useJUnitPlatform()
}

@Suppress("UNCHECKED_CAST")
val releaseArtifacts = rootProject.gradle.extensions.extraProperties["etherologyReleaseArtifacts"]
    as List<Map<*, *>>
val releaseArtifact = releaseArtifacts.single { artifact ->
    artifact["loader"] == "forge" && artifact["artifact_version"] == minecraftVersion
}
val releaseMetadata = releaseArtifact["metadata"] as Map<*, *>
check(releaseMetadata["pack_format"] == releaseMetadata["server_data_pack_format"]) {
    "Forge $minecraftVersion requires separate resource and server data pack metadata"
}
val expandedForgeMetadata = mapOf(
    "version" to project.version.toString(),
    "minecraft_version_range" to releaseArtifact["metadata_range"].toString(),
    "forge_loader_range" to releaseMetadata["loader_api"].toString(),
    "forge_version_range" to releaseMetadata["loader"].toString(),
    "architectury_version_range" to releaseMetadata["architectury"].toString(),
    "resource_pack_format" to releaseMetadata["pack_format"].toString(),
)

tasks.named<ProcessResources>("processResources") {
    inputs.properties(expandedForgeMetadata)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(expandedForgeMetadata)
    }
}

fun readClassUtf8Constants(classBytes: ByteArray): Set<String> {
    val constants = mutableSetOf<String>()
    DataInputStream(ByteArrayInputStream(classBytes)).use { input ->
        check(input.readInt() == 0xCAFEBABE.toInt()) { "Invalid Java class magic" }
        input.readUnsignedShort()
        input.readUnsignedShort()
        val constantPoolCount = input.readUnsignedShort()
        var constantPoolIndex = 1
        while (constantPoolIndex < constantPoolCount) {
            when (val tag = input.readUnsignedByte()) {
                1 -> constants.add(input.readUTF())
                3, 4 -> input.skipBytes(4)
                5, 6 -> {
                    input.skipBytes(8)
                    constantPoolIndex++
                }
                7, 8, 16, 19, 20 -> input.skipBytes(2)
                9, 10, 11, 12, 17, 18 -> input.skipBytes(4)
                15 -> input.skipBytes(3)
                else -> error("Unsupported Java class constant-pool tag $tag")
            }
            constantPoolIndex++
        }
    }
    return constants
}

fun readCompiledClassConstants(
    classesDirectory: File,
    classEntry: String,
): Set<String>? {
    val classFile = classesDirectory.resolve(classEntry)
    return if (classFile.isFile) readClassUtf8Constants(classFile.readBytes()) else null
}

fun missingForgeEtherItemMilestone(commonJarFile: File): List<String> {
    val missingConditions = mutableListOf<String>()
    ZipFile(commonJarFile).use { commonZip ->
        val itemRegistryEntry = commonZip.getEntry(sharedItemRegistryClassEntry)
        if (itemRegistryEntry == null) {
            missingConditions.add("common JAR has no loader-neutral SharedItems gameplay registry")
        } else {
            val itemRegistryConstants = readClassUtf8Constants(
                commonZip.getInputStream(itemRegistryEntry).use { input -> input.readAllBytes() },
            )
            if ("ru/feytox/etherology/registry/SharedDeferredRegister" !in itemRegistryConstants) {
                missingConditions.add("SharedItems is not backed by the shared deferred registry")
            }
            if ("ether" !in itemRegistryConstants || "register" !in itemRegistryConstants) {
                missingConditions.add("SharedItems does not register the ether item")
            }
        }

        val sharedDeferredRegisterEntry = commonZip.getEntry(sharedDeferredRegisterClassEntry)
        if (sharedDeferredRegisterEntry == null) {
            missingConditions.add("common JAR has no shared deferred registry lifecycle")
        } else {
            val sharedDeferredRegisterConstants = readClassUtf8Constants(
                commonZip.getInputStream(sharedDeferredRegisterEntry)
                    .use { input -> input.readAllBytes() },
            )
            if ("dev/architectury/registry/registries/DeferredRegister"
                !in sharedDeferredRegisterConstants
            ) {
                missingConditions.add("shared registry lifecycle is not backed by Architectury DeferredRegister")
            }
        }

        val bootstrapEntry = commonZip.getEntry(commonBootstrapClassEntry)
        if (bootstrapEntry == null) {
            missingConditions.add("common JAR has no loader lifecycle bootstrap")
        } else {
            val bootstrapConstants = readClassUtf8Constants(
                commonZip.getInputStream(bootstrapEntry).use { input -> input.readAllBytes() },
            )
            if ("ru/feytox/etherology/registry/item/SharedItems" !in bootstrapConstants) {
                missingConditions.add("loader-neutral bootstrap does not install SharedItems")
            }
        }
    }

    if (!etherItemModel.isFile) {
        missingConditions.add("ether item model is missing")
    }
    if (!etherItemTexture.isFile) {
        missingConditions.add("ether item texture is missing")
    }
    if (!englishLanguageFile.isFile
        || !englishLanguageFile.readText().contains("\"item.etherology.ether\"")
    ) {
        missingConditions.add("ether item English translation is missing")
    }
    return missingConditions
}

fun missingForgeStorageFoundationMilestone(commonJarFile: File): List<String> {
    val missingConditions = mutableListOf<String>()
    ZipFile(commonJarFile).use { commonZip ->
        val blockRegistryEntry = commonZip.getEntry(sharedBlockRegistryClassEntry)
        if (blockRegistryEntry == null) {
            missingConditions.add("common JAR has no loader-neutral SharedBlocks registry")
        } else {
            val blockRegistryConstants = readClassUtf8Constants(
                commonZip.getInputStream(blockRegistryEntry).use { input -> input.readAllBytes() },
            )
            if ("ru/feytox/etherology/registry/SharedDeferredRegister"
                !in blockRegistryConstants
            ) {
                missingConditions.add("SharedBlocks is not backed by the shared deferred registry")
            }
            if ("ethereal_storage" !in blockRegistryConstants
                || "register" !in blockRegistryConstants
            ) {
                missingConditions.add("SharedBlocks does not register the ethereal storage block")
            }
            if ("ru/feytox/etherology/block/etherealStorage/EtherealStorageFoundationBlock"
                !in blockRegistryConstants
            ) {
                missingConditions.add("SharedBlocks does not construct the ethereal storage foundation")
            }
        }

        val itemRegistryEntry = commonZip.getEntry(sharedItemRegistryClassEntry)
        if (itemRegistryEntry != null) {
            val itemRegistryConstants = readClassUtf8Constants(
                commonZip.getInputStream(itemRegistryEntry).use { input -> input.readAllBytes() },
            )
            if ("net/minecraft/item/BlockItem" !in itemRegistryConstants
                || "ethereal_storage" !in itemRegistryConstants
                || "ru/feytox/etherology/registry/block/SharedBlocks" !in itemRegistryConstants
            ) {
                missingConditions.add("SharedItems does not register the ethereal storage block item")
            }
        }

        val blockEntityRegistryEntry = commonZip.getEntry(sharedBlockEntityRegistryClassEntry)
        if (blockEntityRegistryEntry == null) {
            missingConditions.add("common JAR has no loader-neutral SharedBlockEntities registry")
        } else {
            val blockEntityRegistryConstants = readClassUtf8Constants(
                commonZip.getInputStream(blockEntityRegistryEntry).use { input -> input.readAllBytes() },
            )
            if ("ru/feytox/etherology/registry/SharedDeferredRegister"
                !in blockEntityRegistryConstants
            ) {
                missingConditions.add("SharedBlockEntities is not backed by the shared deferred registry")
            }
            if ("ethereal_storage_block_entity" !in blockEntityRegistryConstants
                || "register" !in blockEntityRegistryConstants
            ) {
                missingConditions.add("SharedBlockEntities does not register ethereal storage")
            }
        }

        val storageBlockEntry = commonZip.getEntry(etherealStorageFoundationBlockClassEntry)
        if (storageBlockEntry == null) {
            missingConditions.add("common JAR has no ethereal storage foundation block")
        } else {
            val storageBlockConstants = readClassUtf8Constants(
                commonZip.getInputStream(storageBlockEntry).use { input -> input.readAllBytes() },
            )
            if ("net/minecraft/block/BlockEntityProvider" !in storageBlockConstants
                || "ru/feytox/etherology/block/etherealStorage/EtherealStorageFoundationBlockEntity"
                !in storageBlockConstants
            ) {
                missingConditions.add("ethereal storage foundation does not create its block entity")
            }
        }

        val storageBlockEntityEntry =
            commonZip.getEntry(etherealStorageFoundationBlockEntityClassEntry)
        if (storageBlockEntityEntry == null) {
            missingConditions.add("common JAR has no ethereal storage foundation block entity")
        } else {
            val storageBlockEntityConstants = readClassUtf8Constants(
                commonZip.getInputStream(storageBlockEntityEntry).use { input -> input.readAllBytes() },
            )
            if ("ru/feytox/etherology/registry/block/SharedBlockEntities"
                !in storageBlockEntityConstants
            ) {
                missingConditions.add("ethereal storage foundation does not use the shared block-entity type")
            }
        }

        val bootstrapEntry = commonZip.getEntry(commonBootstrapClassEntry)
        if (bootstrapEntry != null) {
            val bootstrapConstants = readClassUtf8Constants(
                commonZip.getInputStream(bootstrapEntry).use { input -> input.readAllBytes() },
            )
            if ("ru/feytox/etherology/registry/block/SharedBlocks" !in bootstrapConstants) {
                missingConditions.add("loader-neutral bootstrap does not install SharedBlocks")
            }
            if ("ru/feytox/etherology/registry/block/SharedBlockEntities" !in bootstrapConstants) {
                missingConditions.add("loader-neutral bootstrap does not install SharedBlockEntities")
            }
        }
    }

    val storageResources = listOf(
        etherealStorageBlockstate,
        etherealStorageBlockModel,
        etherealStorageItemModel,
        etherealStorageTexture,
        etherealStorageLootTable,
        etherealStorageRecipe,
    )
    storageResources.filterNot(File::isFile).forEach { missingResource ->
        missingConditions.add("ethereal storage resource is missing: ${missingResource.name}")
    }
    if (!englishLanguageFile.isFile
        || !englishLanguageFile.readText().contains("\"block.etherology.ethereal_storage\"")
    ) {
        missingConditions.add("ethereal storage English translation is missing")
    }
    return missingConditions
}

fun missingForgePersistentStorageMenuCoreMilestone(
    commonJarFile: File,
    forgeClassesDirectory: File,
): List<String> {
    val missingConditions = mutableListOf<String>()
    ZipFile(commonJarFile).use { commonZip ->
        val storageBlockEntityEntry =
            commonZip.getEntry(etherealStorageFoundationBlockEntityClassEntry)
        if (storageBlockEntityEntry == null) {
            missingConditions.add("common JAR has no persistent ethereal-storage block entity")
        } else {
            val blockEntityConstants = readClassUtf8Constants(
                commonZip.getInputStream(storageBlockEntityEntry).use { input -> input.readAllBytes() },
            )
            if ("storage_ether" !in blockEntityConstants
                || "writeNbt" !in blockEntityConstants
                || "readNbt" !in blockEntityConstants
            ) {
                missingConditions.add("ethereal storage does not persist its ether value")
            }
            if ("net/minecraft/inventory/Inventories" !in blockEntityConstants
                || "net/minecraft/util/collection/DefaultedList" !in blockEntityConstants
                || "net/minecraft/item/ItemStack" !in blockEntityConstants
            ) {
                missingConditions.add("ethereal storage does not own and persist an item inventory")
            }
            if ("net/minecraft/screen/NamedScreenHandlerFactory" !in blockEntityConstants
                || "createMenu" !in blockEntityConstants
            ) {
                missingConditions.add("ethereal storage does not expose a server menu factory")
            }
            if ("net/minecraft/inventory/SidedInventory" !in blockEntityConstants
                || "getAvailableSlots" !in blockEntityConstants
                || "canInsert" !in blockEntityConstants
                || "canExtract" !in blockEntityConstants
            ) {
                missingConditions.add("ethereal storage does not expose bounded vanilla sided automation")
            }
            if ("onOpen" !in blockEntityConstants
                || "onClose" !in blockEntityConstants
                || "viewers" !in blockEntityConstants
                || "BLOCK_CHEST_OPEN" !in blockEntityConstants
                || "BLOCK_CHEST_CLOSE" !in blockEntityConstants
            ) {
                missingConditions.add("ethereal storage does not own basic multi-viewer menu lifecycle")
            }
        }

        val storageBlockEntry = commonZip.getEntry(etherealStorageFoundationBlockClassEntry)
        if (storageBlockEntry == null) {
            missingConditions.add("common JAR has no persistent ethereal-storage block")
        } else {
            val blockConstants = readClassUtf8Constants(
                commonZip.getInputStream(storageBlockEntry).use { input -> input.readAllBytes() },
            )
            if ("onUse" !in blockConstants
                || "isClient" !in blockConstants
                || "openHandledScreen" !in blockConstants
            ) {
                missingConditions.add("ethereal storage block does not open its server-owned menu")
            }
            if ("onStateReplaced" !in blockConstants
                || "net/minecraft/util/ItemScatterer" !in blockConstants
            ) {
                missingConditions.add("ethereal storage block does not drop its real inputs on removal")
            }
        }

        val storageInputItemEntry = commonZip.getEntry(etherealStorageInputItemClassEntry)
        if (storageInputItemEntry == null) {
            missingConditions.add("common JAR has no bounded ethereal-storage input item")
        } else {
            val itemRegistryEntry = commonZip.getEntry(sharedItemRegistryClassEntry)
            val itemRegistryConstants = itemRegistryEntry?.let { entry ->
                readClassUtf8Constants(
                    commonZip.getInputStream(entry).use { input -> input.readAllBytes() },
                )
            }.orEmpty()
            if ("glint_shard" !in itemRegistryConstants
                || "ru/feytox/etherology/item/EtherealStorageInputItem" !in itemRegistryConstants
            ) {
                missingConditions.add("SharedItems does not register the bounded glint_shard input")
            }
        }

        val storageScreenHandlerEntry =
            commonZip.getEntry(etherealStorageFoundationScreenHandlerClassEntry)
        if (storageScreenHandlerEntry == null) {
            missingConditions.add("common JAR has no ethereal-storage screen handler")
        } else {
            val screenHandlerConstants = readClassUtf8Constants(
                commonZip.getInputStream(storageScreenHandlerEntry)
                    .use { input -> input.readAllBytes() },
            )
            if ("ru/feytox/etherology/block/etherealStorage/EtherealStorageInputSlot"
                !in screenHandlerConstants
                || "ru/feytox/etherology/block/etherealStorage/EtherealStorageDisplaySlot"
                !in screenHandlerConstants
                || "quickMove" !in screenHandlerConstants
                || "insertItem" !in screenHandlerConstants
            ) {
                missingConditions.add("ethereal storage menu does not enforce its typed 3+1 topology")
            }
        }

        val screenHandlerRegistryEntry = commonZip.getEntry(sharedScreenHandlerRegistryClassEntry)
        if (screenHandlerRegistryEntry == null) {
            missingConditions.add("common JAR has no loader-neutral SharedScreenHandlers registry")
        } else {
            val screenHandlerConstants = readClassUtf8Constants(
                commonZip.getInputStream(screenHandlerRegistryEntry).use { input -> input.readAllBytes() },
            )
            if ("ru/feytox/etherology/registry/SharedDeferredRegister"
                !in screenHandlerConstants
                || "ethereal_storage_screen_handler" !in screenHandlerConstants
            ) {
                missingConditions.add("SharedScreenHandlers does not register the ethereal storage menu")
            }
        }

        val bootstrapEntry = commonZip.getEntry(commonBootstrapClassEntry)
        if (bootstrapEntry != null) {
            val bootstrapConstants = readClassUtf8Constants(
                commonZip.getInputStream(bootstrapEntry).use { input -> input.readAllBytes() },
            )
            if ("ru/feytox/etherology/registry/misc/SharedScreenHandlers"
                !in bootstrapConstants
            ) {
                missingConditions.add("loader-neutral bootstrap does not install SharedScreenHandlers")
            }
        }
    }

    val storageMenuResources = listOf(
        etherealStorageGuiTexture,
        glintShardItemModel,
        glintShardItemTexture,
    )
    storageMenuResources.filterNot(File::isFile).forEach { missingResource ->
        missingConditions.add("ethereal storage menu resource is missing: ${missingResource.name}")
    }
    if (!englishLanguageFile.isFile
        || !englishLanguageFile.readText()
            .contains("\"block.etherology.ethereal_storage.title\"")
    ) {
        missingConditions.add("ethereal storage menu title translation is missing")
    } else if (!englishLanguageFile.readText().contains("\"item.etherology.glint_shard\"")) {
        missingConditions.add("bounded glint-shard input translation is missing")
    }
    val clientEventConstants = readCompiledClassConstants(
        forgeClassesDirectory,
        forgeClientEventsClassEntry,
    )
    if (clientEventConstants == null) {
        missingConditions.add("Forge has no Dist-scoped storage client-registration owner")
    } else if ("CLIENT" !in clientEventConstants
        || "net/minecraftforge/fml/event/lifecycle/FMLClientSetupEvent" !in clientEventConstants
        || "enqueueWork" !in clientEventConstants
        || "net/minecraft/client/gui/screen/ingame/HandledScreens" !in clientEventConstants
        || "register" !in clientEventConstants
    ) {
        missingConditions.add("Forge does not bind the storage screen from enqueued client setup")
    }
    val storageScreenConstants = readCompiledClassConstants(
        forgeClassesDirectory,
        etherealStorageFoundationScreenClassEntry,
    )
    if (storageScreenConstants == null) {
        missingConditions.add("Forge has no client-only ethereal-storage screen")
    } else if ("net/minecraft/client/gui/screen/ingame/HandledScreen" !in storageScreenConstants
        || storageScreenConstants.none { constant ->
            constant.contains(
                "ru/feytox/etherology/block/etherealStorage/" +
                    "EtherealStorageFoundationScreenHandler",
            )
        }
        || "textures/gui/ethereal_storage.png" !in storageScreenConstants
    ) {
        missingConditions.add("Forge ethereal-storage screen is not bound to the shared menu and GUI")
    }
    val forgeEntrypointConstants = readCompiledClassConstants(
        forgeClassesDirectory,
        forgeEntrypointClassEntry,
    )
    if (forgeEntrypointConstants == null) {
        missingConditions.add("Forge has no JavaFML entrypoint for Dist-safety validation")
    } else if (forgeEntrypointConstants.any { constant ->
            constant.startsWith("net/minecraft/client/")
                || constant.startsWith("ru/feytox/etherology/forge/client/")
        }
    ) {
        missingConditions.add("Forge JavaFML entrypoint links client-only storage classes")
    }
    return missingConditions
}

fun missingForgeStorageParityMilestone(
    commonJarFile: File,
    forgeClassesDirectory: File,
): List<String> {
    val missingConditions = mutableListOf<String>()
    ZipFile(commonJarFile).use { commonZip ->
        val storageBlockEntityEntry =
            commonZip.getEntry(etherealStorageFoundationBlockEntityClassEntry)
        if (storageBlockEntityEntry == null) {
            missingConditions.add("common JAR has no ethereal-storage block entity")
        } else {
            val blockEntityConstants = readClassUtf8Constants(
                commonZip.getInputStream(storageBlockEntityEntry).use { input -> input.readAllBytes() },
            )
            if ("getGlintEther" !in blockEntityConstants
                || "incrementGlint" !in blockEntityConstants
                || "decrementGlint" !in blockEntityConstants
                || "stored_ether" !in blockEntityConstants
            ) {
                missingConditions.add(
                    "storage glints do not persist or transfer their own Ether arithmetic",
                )
            }
            if ("StartBlockAnimS2C" !in blockEntityConstants
                || "StopBlockAnimS2C" !in blockEntityConstants
                || "registerControllers" !in blockEntityConstants
            ) {
                missingConditions.add(
                    "storage viewer open/close state has no synchronized Gecko animation lifecycle",
                )
            }
        }
    }

    val capabilityProviderConstants = readCompiledClassConstants(
        forgeClassesDirectory,
        etherealStorageItemHandlerProviderClassEntry,
    )
    if (capabilityProviderConstants == null) {
        missingConditions.add(
            "ethereal storage has no invalidatable Forge ITEM_HANDLER capability interop",
        )
    } else if ("net/minecraftforge/common/capabilities/ForgeCapabilities"
            !in capabilityProviderConstants
        || "ITEM_HANDLER" !in capabilityProviderConstants
        || "net/minecraftforge/common/util/LazyOptional" !in capabilityProviderConstants
        || "invalidateCaps" !in capabilityProviderConstants
        || capabilityProviderConstants.none { constant ->
            constant.contains(
                "ru/feytox/etherology/block/etherealStorage/" +
                    "EtherealStorageFoundationBlockEntity",
            )
        }
    ) {
        missingConditions.add(
            "ethereal storage Forge ITEM_HANDLER owner lacks binding or invalidation",
        )
    }
    return missingConditions
}

fun missingForgeChannelNetworkMilestone(commonJarFile: File): List<String> {
    val missingConditions = mutableListOf<String>()
    ZipFile(commonJarFile).use { commonZip ->
        val etherStorageEntry = commonZip.getEntry(etherStorageContractClassEntry)
        if (etherStorageEntry == null) {
            missingConditions.add("common JAR has no shared Ether storage transfer contract")
        } else {
            val etherStorageConstants = readClassUtf8Constants(
                commonZip.getInputStream(etherStorageEntry).use { input -> input.readAllBytes() },
            )
            if ("getTransportableEther" !in etherStorageConstants
                || "transferTo" !in etherStorageConstants
                || "increment" !in etherStorageConstants
                || "decrement" !in etherStorageConstants
            ) {
                missingConditions.add("shared Ether storage contract has no bounded transfer flow")
            }
        }

        val channelBlockEntry = commonZip.getEntry(etherealChannelBlockClassEntry)
        if (channelBlockEntry == null) {
            missingConditions.add("common JAR has no shared ethereal-channel block")
        }

        val channelBlockEntityEntry = commonZip.getEntry(etherealChannelBlockEntityClassEntry)
        if (channelBlockEntityEntry == null) {
            missingConditions.add("common JAR has no shared ethereal-channel block entity")
        } else {
            val channelConstants = readClassUtf8Constants(
                commonZip.getInputStream(channelBlockEntityEntry)
                    .use { input -> input.readAllBytes() },
            )
            if ("ru/feytox/etherology/magic/ether/EtherStorage" !in channelConstants
                || "transferTick" !in channelConstants
                || "stored_ether" !in channelConstants
                || "getOutputSide" !in channelConstants
            ) {
                missingConditions.add("shared ethereal channel has no persistent directed transfer behavior")
            }
        }

        val sharedBlockEntry = commonZip.getEntry(sharedBlockRegistryClassEntry)
        val sharedBlockConstants = sharedBlockEntry?.let { entry ->
            readClassUtf8Constants(
                commonZip.getInputStream(entry).use { input -> input.readAllBytes() },
            )
        }.orEmpty()
        if ("ethereal_channel" !in sharedBlockConstants
            || "ru/feytox/etherology/block/etherealChannel/EtherealChannelBlock"
            !in sharedBlockConstants
        ) {
            missingConditions.add("SharedBlocks does not register the ethereal channel")
        }

        val sharedBlockEntityEntry = commonZip.getEntry(sharedBlockEntityRegistryClassEntry)
        val sharedBlockEntityConstants = sharedBlockEntityEntry?.let { entry ->
            readClassUtf8Constants(
                commonZip.getInputStream(entry).use { input -> input.readAllBytes() },
            )
        }.orEmpty()
        if ("ethereal_channel_block_entity" !in sharedBlockEntityConstants
            || "ru/feytox/etherology/block/etherealChannel/EtherealChannelBlockEntity"
            !in sharedBlockEntityConstants
        ) {
            missingConditions.add("SharedBlockEntities does not register the ethereal channel")
        }
    }
    return missingConditions
}

fun missingForgeReleaseReadinessMilestone(): List<String> = listOf(
    "the complete authoritative Forge gameplay registry and lifecycle graph is not accepted",
    "the remaining components, packets, recipes, entities, worldgen, and client slices " +
        "are not accepted",
    "packaged native Forge client, dedicated-server, persistence, and E2E evidence " +
        "is not accepted",
)

fun firstIncompleteForgeMilestone(
    commonJarFile: File,
    forgeClassesDirectory: File,
): Pair<String, List<String>> {
    val missingStorageParity = missingForgeStorageParityMilestone(
        commonJarFile,
        forgeClassesDirectory,
    )
    if (missingStorageParity.isNotEmpty()) {
        return "ethereal-storage parity" to missingStorageParity
    }

    val missingChannelNetwork = missingForgeChannelNetworkMilestone(commonJarFile)
    if (missingChannelNetwork.isNotEmpty()) {
        return "ethereal channel/network" to missingChannelNetwork
    }

    return "complete gameplay and native release readiness" to
        missingForgeReleaseReadinessMilestone()
}

val validateForgeBootstrapInputs = tasks.register("validateForgeBootstrapInputs") {
    group = "verification"
    description = "Checks the loader-neutral lifecycle handshake and native Forge adapter."
    dependsOn(commonJar)
    inputs.dir(forgeJavaRoot)
    inputs.file(forgeResourcesRoot.resolve("META-INF/mods.toml"))
    inputs.file(commonJar.flatMap { it.archiveFile })
    doLast {
        val forgeJavaSources = fileTree(forgeJavaRoot) {
            include("**/*.java")
        }
        check(!forgeJavaSources.isEmpty) {
            "Forge $minecraftVersion has no native Java sources in $forgeJavaRoot"
        }
        check(forgeResourcesRoot.resolve("META-INF/mods.toml").isFile) {
            "Forge $minecraftVersion is missing META-INF/mods.toml"
        }
        val forgeEntrypointSources = forgeJavaSources.files.filter { source ->
            source.readText().contains("@Mod(")
        }
        check(forgeEntrypointSources.isNotEmpty()) {
            "Forge $minecraftVersion has no @Mod entry point in $forgeJavaRoot"
        }
        val commonJarFile = commonJar.get().archiveFile.get().asFile
        val commonEntries = ZipFile(commonJarFile).use { commonZip ->
            commonZip.entries().asSequence().map { it.name }.toSet()
        }
        check(commonBootstrapClassEntry in commonEntries) {
            "Forge $minecraftVersion has no loader-neutral EtherologyBootstrap lifecycle"
        }
        check(platformRegistrarClassEntry in commonEntries) {
            "Forge $minecraftVersion has no loader-neutral PlatformRegistrar contract"
        }
        check(
            forgeEntrypointSources.any { source ->
                source.readText().contains("EtherologyBootstrap.initialize(")
            },
        ) {
            "Forge $minecraftVersion @Mod entry point does not invoke EtherologyBootstrap.initialize(...)"
        }
        check(
            forgeEntrypointSources.any { source ->
                source.readText().contains("new ForgePlatformRegistrar(")
            },
        ) {
            "Forge $minecraftVersion @Mod entry point does not install ForgePlatformRegistrar"
        }
        check(
            forgeEntrypointSources.any { source ->
                val sourceText = source.readText()
                val eventBusRegistration = sourceText.indexOf("EventBuses.registerModEventBus(")
                val sharedBootstrap = sourceText.indexOf("EtherologyBootstrap.initialize(")
                eventBusRegistration >= 0 && eventBusRegistration < sharedBootstrap
            },
        ) {
            "Forge $minecraftVersion @Mod entry point does not register Architectury's event bus before shared registries"
        }
    }
}

val validateForgeEtherItemMilestone = tasks.register("validateForgeEtherItemMilestone") {
    group = "verification"
    description = "Checks the complete loader-neutral ether item registration vertical."
    dependsOn(commonJar)
    inputs.file(commonJar.flatMap { it.archiveFile })
    inputs.files(etherItemModel, etherItemTexture, englishLanguageFile)
    doLast {
        val commonJarFile = commonJar.get().archiveFile.get().asFile
        val missingConditions = missingForgeEtherItemMilestone(commonJarFile)
        check(missingConditions.isEmpty()) {
            "Forge $minecraftVersion ether item milestone is incomplete:\n${
                missingConditions.joinToString("\n") { condition -> " - $condition" }
            }"
        }
    }
}

val validateForgeStorageFoundationMilestone = tasks.register("validateForgeStorageFoundationMilestone") {
    group = "verification"
    description = "Checks the loader-neutral ethereal-storage registration foundation."
    dependsOn(validateForgeEtherItemMilestone, commonJar)
    inputs.file(commonJar.flatMap { it.archiveFile })
    inputs.files(
        etherealStorageBlockstate,
        etherealStorageBlockModel,
        etherealStorageItemModel,
        etherealStorageTexture,
        etherealStorageLootTable,
        etherealStorageRecipe,
        englishLanguageFile,
    )
    doLast {
        val commonJarFile = commonJar.get().archiveFile.get().asFile
        val missingConditions = missingForgeStorageFoundationMilestone(commonJarFile)
        check(missingConditions.isEmpty()) {
            "Forge $minecraftVersion ethereal-storage foundation is incomplete:\n${
                missingConditions.joinToString("\n") { condition -> " - $condition" }
            }"
        }
    }
}

val validateForgePersistentStorageMenuCoreMilestone =
    tasks.register("validateForgePersistentStorageMenuCoreMilestone") {
        group = "verification"
        description = "Checks the bounded persistent ethereal-storage inventory and menu core."
        dependsOn(
            validateForgeStorageFoundationMilestone,
            commonJar,
            commonTest,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.dir(forgeMainClasses)
        inputs.files(
            etherealStorageGuiTexture,
            glintShardItemModel,
            glintShardItemTexture,
            englishLanguageFile,
        )
        doLast {
            val commonJarFile = commonJar.get().archiveFile.get().asFile
            val missingConditions = missingForgePersistentStorageMenuCoreMilestone(
                commonJarFile,
                forgeMainClasses.get().asFile,
            )
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion persistent ethereal-storage/menu core is incomplete:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeStorageParityMilestone = tasks.register("validateForgeStorageParityMilestone") {
    group = "verification"
    description =
        "Requires per-glint Ether, Forge item-handler, and synchronized storage animation parity."
    dependsOn(
        validateForgePersistentStorageMenuCoreMilestone,
        commonJar,
        commonTest,
        tasks.named("test"),
    )
    inputs.file(commonJar.flatMap { it.archiveFile })
    inputs.dir(forgeMainClasses)
    doLast {
        val commonJarFile = commonJar.get().archiveFile.get().asFile
        val missingConditions = missingForgeStorageParityMilestone(
            commonJarFile,
            forgeMainClasses.get().asFile,
        )
        check(missingConditions.isEmpty()) {
            "Forge $minecraftVersion ethereal-storage parity is incomplete:\n${
                missingConditions.joinToString("\n") { condition -> " - $condition" }
            }"
        }
    }
}

val validateForgeChannelNetworkMilestone = tasks.register("validateForgeChannelNetworkMilestone") {
    group = "verification"
    description = "Requires the next shared ethereal-channel and directed transfer vertical."
    dependsOn(
        validateForgeStorageParityMilestone,
        commonJar,
        commonTest,
        tasks.named("test"),
    )
    inputs.file(commonJar.flatMap { it.archiveFile })
    doLast {
        val commonJarFile = commonJar.get().archiveFile.get().asFile
        val missingConditions = missingForgeChannelNetworkMilestone(commonJarFile)
        check(missingConditions.isEmpty()) {
            "Forge $minecraftVersion ethereal channel/network milestone is incomplete:\n${
                missingConditions.joinToString("\n") { condition -> " - $condition" }
            }"
        }
    }
}

val validateForgeReleaseReadinessMilestone = tasks.register("validateForgeReleaseReadinessMilestone") {
    group = "verification"
    description =
        "Permanently blocks artifacts until complete gameplay and packaged native Forge E2E are accepted."
    dependsOn(validateForgeChannelNetworkMilestone)
    doLast {
        val missingConditions = missingForgeReleaseReadinessMilestone()
        check(missingConditions.isEmpty()) {
            "Forge $minecraftVersion complete gameplay/native release readiness is incomplete:\n${
                missingConditions.joinToString("\n") { condition -> " - $condition" }
            }"
        }
    }
}

val validateForgePortInputs = tasks.register("validateForgePortInputs") {
    group = "verification"
    description = "Rejects Forge artifacts until every current positive and forward gameplay gate passes."
    dependsOn(
        validateForgeBootstrapInputs,
        validateForgeEtherItemMilestone,
        validateForgeStorageFoundationMilestone,
        validateForgePersistentStorageMenuCoreMilestone,
        validateForgeStorageParityMilestone,
        validateForgeChannelNetworkMilestone,
        validateForgeReleaseReadinessMilestone,
    )
}

tasks.register("verifyForgePortGateClosed") {
    group = "verification"
    description = "Reports the first incomplete forward milestone without serving as a release gate."
    dependsOn(
        validateForgeBootstrapInputs,
        validateForgeEtherItemMilestone,
        validateForgeStorageFoundationMilestone,
        validateForgePersistentStorageMenuCoreMilestone,
        commonJar,
        commonTest,
        tasks.named("test"),
    )
    inputs.file(commonJar.flatMap { it.archiveFile })
    inputs.dir(forgeMainClasses)
    doLast {
        val commonJarFile = commonJar.get().archiveFile.get().asFile
        val firstIncompleteMilestone = firstIncompleteForgeMilestone(
            commonJarFile,
            forgeMainClasses.get().asFile,
        )
        val (milestoneName, missingConditions) = firstIncompleteMilestone
        logger.lifecycle(
            "Forge $minecraftVersion first incomplete milestone is $milestoneName:\n{}",
            missingConditions.joinToString("\n") { condition -> " - $condition" },
        )
    }
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(project.configurations["shadowBundle"])
    archiveClassifier.set("dev-shadow")
}

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    dependsOn(validateForgePortInputs, tasks.named("shadowJar"))
    val shadowJar = tasks.named<ShadowJar>("shadowJar")
    inputFile.set(shadowJar.get().archiveFile)
}

tasks.matching { task -> task.name.startsWith("publish") }.configureEach {
    dependsOn(validateForgePortInputs)
}
