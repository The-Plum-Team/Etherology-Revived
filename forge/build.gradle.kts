import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.architectury.plugin.ArchitectPluginExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.jvm.tasks.Jar

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
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
val commonTransformProductionFabric =
    commonProject.tasks.named("transformProductionFabric")
val commonTransformProductionForge =
    commonProject.tasks.named("transformProductionForge")
val fabricProjectPath = requireNotNull(stonecutter.node.sibling("fabric")).hierarchy.toString()
val fabricProject = project(fabricProjectPath)
evaluationDependsOn(fabricProjectPath)
val fabricTest = fabricProject.tasks.named("test")
val fabricRemapJar = fabricProject.tasks.named<RemapJarTask>("remapJar")
val forgeJavaRoot = rootProject.file("forge/src/main/java")
val forgeResourcesRoot = rootProject.file("forge/src/main/resources")
val forgeMainClasses = layout.buildDirectory.dir("classes/java/main")
val forgeMainResources = layout.buildDirectory.dir("resources/main")
val acceptedForgeDataEntries = setOf(
    "etherology/loot_tables/blocks/ethereal_storage.json",
)
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
val glintEtherDataClassEntry =
    "ru/feytox/etherology/item/glints/GlintEtherData.class"
val sharedScreenHandlerRegistryClassEntry =
    "ru/feytox/etherology/registry/misc/SharedScreenHandlers.class"
val sharedSoundRegistryClassEntry =
    "ru/feytox/etherology/registry/misc/SharedSounds.class"
val canonicalFabricSoundRegistryClassEntry =
    "ru/feytox/etherology/registry/misc/EtherSounds.class"
val canonicalFabricInitializerClassEntry =
    "ru/feytox/etherology/Etherology.class"
val fabricEntrypointClassEntry =
    "ru/feytox/etherology/EtherologyFabric.class"
val forgeEntrypointClassEntry =
    "ru/feytox/etherology/forge/EtherologyForge.class"
val forgeClientEventsClassEntry =
    "ru/feytox/etherology/forge/client/ForgeClientEvents.class"
val forgeChannelLeverMixinClassEntry =
    "ru/feytox/etherology/forge/mixin/ChannelLeverSupportMixin.class"
val etherealStorageFoundationScreenClassEntry =
    "ru/feytox/etherology/forge/client/EtherealStorageFoundationScreen.class"
val etherealStorageFoundationModelClassEntry =
    "ru/feytox/etherology/forge/client/EtherealStorageFoundationModel.class"
val etherealStorageFoundationRendererClassEntry =
    "ru/feytox/etherology/forge/client/EtherealStorageFoundationRenderer.class"
val etherealStorageItemHandlerProviderClassEntry =
    "ru/feytox/etherology/forge/block/etherealStorage/EtherealStorageItemHandlerProvider.class"
val etherStorageContractClassEntry =
    "ru/feytox/etherology/magic/ether/EtherStorage.class"
val etherPipeContractClassEntry =
    "ru/feytox/etherology/magic/ether/EtherPipe.class"
val etherDisplayContractClassEntry =
    "ru/feytox/etherology/magic/ether/EtherDisplay.class"
val evaporatingEtherPipeContractClassEntry =
    "ru/feytox/etherology/magic/ether/EvaporatingEtherPipe.class"
val pipeSideClassEntry =
    "ru/feytox/etherology/enums/PipeSide.class"
val etherealChannelBlockClassEntry =
    "ru/feytox/etherology/block/etherealChannel/EtherealChannelFoundationBlock.class"
val etherealChannelBlockEntityClassEntry =
    "ru/feytox/etherology/block/etherealChannel/EtherealChannelFoundationBlockEntity.class"
val etherealChannelShapeClassEntry =
    "ru/feytox/etherology/block/etherealChannel/EtherealChannelFoundationShape.class"
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
val etherealStorageGeoModel =
    rootProject.file("src/client/resources/assets/etherology/geo/ethereal_storage.geo.json")
val etherealStorageAnimation =
    rootProject.file(
        "src/client/resources/assets/etherology/animations/ethereal_storage.animation.json",
    )
val etherealStorageMachineTexture =
    rootProject.file(
        "src/client/resources/assets/etherology/textures/machines/ethereal_storage.png",
    )
val glintShardItemModel =
    rootProject.file("src/client/resources/assets/etherology/models/item/glint_shard.json")
val glintShardItemTexture =
    rootProject.file("src/client/resources/assets/etherology/textures/item/glint_shard_0.png")
val etherealStorageLootTable =
    rootProject.file("src/main/generated/data/etherology/loot_tables/blocks/ethereal_storage.json")
val etherealStorageRecipe =
    rootProject.file("src/main/generated/data/etherology/recipes/ethereal_storage.json")
val etherealChannelBlockstate =
    rootProject.file("src/client/resources/assets/etherology/blockstates/ethereal_channel.json")
val etherealChannelBlockModels = listOf(
    "ethereal_channel_central_cross.json",
    "ethereal_channel_central_line.json",
    "ethereal_channel_in_case.json",
    "ethereal_channel_input.json",
    "ethereal_channel_output.json",
).map { modelName ->
    rootProject.file("src/client/resources/assets/etherology/models/block/$modelName")
}
val etherealChannelItemModel =
    rootProject.file("src/main/generated/assets/etherology/models/item/ethereal_channel.json")
val etherealChannelTextures = listOf(
    "channel_case.png",
    "channel_case_front.png",
    "ethereal_channel_central_cross.png",
    "ethereal_channel_central_line.png",
    "ethereal_channel_input.png",
    "ethereal_channel_inside.png",
    "ethereal_channel_output.png",
).map { textureName ->
    rootProject.file("src/client/resources/assets/etherology/textures/block/$textureName")
} + rootProject.file("src/client/resources/assets/etherology/textures/item/ethereal_channel.png")
val etherealChannelResources =
    listOf(etherealChannelBlockstate, etherealChannelItemModel) +
        etherealChannelBlockModels + etherealChannelTextures
val soundManifest =
    rootProject.file("src/client/resources/assets/etherology/sounds.json")
val soundDirectory =
    rootProject.file("src/client/resources/assets/etherology/sounds")
val canonicalSoundEventIds = setOf(
    "electricity_sound",
    "matrix_idle_sound",
    "deflect",
    "bubbles",
    "pouf",
    "ratchet",
    "brewing_dissolution",
    "thunder_zap",
    "tuning_mace",
    "tuning_fork_activate",
    "tuning_fork_tuning",
    "tuning_fork_resonance",
    "broadsword",
    "warp_counter",
)
val canonicalSoundFiles = setOf(
    "armillary_matrix_idle_loop.ogg",
    "brewing_dissolution_0.ogg",
    "broadsword.ogg",
    "bubbles_0.ogg",
    "bubbles_1.ogg",
    "bubbles_2.ogg",
    "deflect.ogg",
    "electricity_1.ogg",
    "electricity_2.ogg",
    "electricity_3.ogg",
    "pouf_0.ogg",
    "pouf_1.ogg",
    "ratchet_0.ogg",
    "thunder_zap_0.ogg",
    "thunder_zap_1.ogg",
    "thunder_zap_2.ogg",
    "tuning_fork_activate.ogg",
    "tuning_fork_resonance.ogg",
    "tuning_fork_tuning.ogg",
    "tuning_mace.ogg",
    "warp_counter.ogg",
)
val forgeChannelEvidenceRoot = rootProject.file("docs/evidence/forge-1.20.1")
val forgeChannelEvidenceVerifier =
    rootProject.file("scripts/e2e/forge_channel_evidence.py")
val forgeE2eProfileManifest = rootProject.file("scripts/e2e/forge-1.20.1-profile.json")
val forgeMixinConfig = forgeResourcesRoot.resolve("etherology.forge.mixins.json")

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

extensions.configure<LoomGradleExtensionAPI>("loom") {
    forge {
        mixinConfig("etherology.forge.mixins.json")
    }
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
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") {
        name = "GeckoLib"
        content {
            includeGroup("software.bernie.geckolib")
            includeGroup("com.eliotlash.mclib")
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
            include("data/etherology/loot_tables/blocks/ethereal_storage.json")
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
    "modImplementation"(
        "software.bernie.geckolib:geckolib-forge-$minecraftVersion:${
            versionProperty("geckolib_forge_version")
        }",
    )

    "common"(project.files(commonJar))
    "shadowBundle"(project.files(commonProject.tasks.named("transformProductionForge")))

    "testImplementation"("org.junit.jupiter:junit-jupiter:5.13.4")
    "testImplementation"("org.ow2.asm:asm:9.9")
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
    "geckolib_version_range" to "[${versionProperty("geckolib_forge_version")},5)",
    "resource_pack_format" to releaseMetadata["pack_format"].toString(),
)

tasks.named<ProcessResources>("processResources") {
    inputs.properties(expandedForgeMetadata)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(expandedForgeMetadata)
    }
}

val validateForgeAcceptedDataSet = tasks.register("validateForgeAcceptedDataSet") {
    group = "verification"
    description =
        "Rejects server data that references gameplay registrations outside the accepted Forge slice."
    dependsOn(tasks.named("processResources"))
    inputs.dir(forgeMainResources)

    doLast {
        val packagedDataDirectory = forgeMainResources.get().asFile.resolve("data")
        val packagedDataEntries = if (packagedDataDirectory.isDirectory) {
            packagedDataDirectory.walkTopDown()
                .filter(File::isFile)
                .map { file -> file.relativeTo(packagedDataDirectory).invariantSeparatorsPath }
                .toSet()
        } else {
            emptySet()
        }
        check(packagedDataEntries == acceptedForgeDataEntries) {
            "Forge $minecraftVersion packaged an unaccepted server-data set.\n" +
                "Expected: ${acceptedForgeDataEntries.sorted()}\n" +
                "Actual: ${packagedDataEntries.sorted()}"
        }
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
        val glintEtherDataEntry = commonZip.getEntry(glintEtherDataClassEntry)
        if (glintEtherDataEntry == null) {
            missingConditions.add("common JAR has no authoritative glint Ether data owner")
        } else {
            val glintEtherDataConstants = readClassUtf8Constants(
                commonZip.getInputStream(glintEtherDataEntry).use { input -> input.readAllBytes() },
            )
            if ("ru/feytox/etherology/util/misc/ItemDataKey" !in glintEtherDataConstants
                || "stored_ether" !in glintEtherDataConstants
                || "getStoredEther" !in glintEtherDataConstants
                || "increment" !in glintEtherDataConstants
                || "decrement" !in glintEtherDataConstants
                || "incrementRemainder" !in glintEtherDataConstants
                || "removedEther" !in glintEtherDataConstants
            ) {
                missingConditions.add(
                    "shared glint Ether data does not own persisted remainder/removal arithmetic",
                )
            }
        }

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
                || "ru/feytox/etherology/item/glints/GlintEtherData"
                !in blockEntityConstants
            ) {
                missingConditions.add(
                    "storage glints do not persist or transfer their own Ether arithmetic",
                )
            }
            if ("software/bernie/geckolib/animatable/GeoBlockEntity"
                !in blockEntityConstants
                || "software/bernie/geckolib/util/GeckoLibUtil" !in blockEntityConstants
                || "createInstanceCache" !in blockEntityConstants
                || "storage_controller" !in blockEntityConstants
                || "open" !in blockEntityConstants
                || "close" !in blockEntityConstants
                || "animation.ether_storage.open" !in blockEntityConstants
                || "animation.ether_storage.close" !in blockEntityConstants
                || "thenPlayAndHold" !in blockEntityConstants
                || "thenPlay" !in blockEntityConstants
                || "registerControllers" !in blockEntityConstants
                || "triggerableAnim" !in blockEntityConstants
                || "triggerAnim" !in blockEntityConstants
            ) {
                missingConditions.add(
                    "storage viewer open/close state has no synchronized Gecko animation lifecycle",
                )
            }
            if ("StartBlockAnimS2C" in blockEntityConstants
                || "StopBlockAnimS2C" in blockEntityConstants
                || "stopClientAnim" in blockEntityConstants
            ) {
                missingConditions.add(
                    "storage Gecko lifecycle still depends on custom animation packets or stop calls",
                )
            }
        }

        val storageBlockEntry = commonZip.getEntry(etherealStorageFoundationBlockClassEntry)
        if (storageBlockEntry == null) {
            missingConditions.add("common JAR has no ethereal-storage block render owner")
        } else {
            val storageBlockConstants = readClassUtf8Constants(
                commonZip.getInputStream(storageBlockEntry).use { input -> input.readAllBytes() },
            )
            if ("ENTITYBLOCK_ANIMATED" !in storageBlockConstants) {
                missingConditions.add(
                    "ethereal storage block does not leave visual ownership to its Geo renderer",
                )
            }
        }
    }

    val forgeEntrypointConstants = readCompiledClassConstants(
        forgeClassesDirectory,
        forgeEntrypointClassEntry,
    )
    if (forgeEntrypointConstants == null
        || "software/bernie/geckolib/GeckoLib" !in forgeEntrypointConstants
        || "initialize" !in forgeEntrypointConstants
    ) {
        missingConditions.add("Forge does not initialize GeckoLib before shared storage bootstrap")
    }

    val clientEventConstants = readCompiledClassConstants(
        forgeClassesDirectory,
        forgeClientEventsClassEntry,
    )
    if (clientEventConstants == null
        || "dev/architectury/registry/client/rendering/BlockEntityRendererRegistry"
        !in clientEventConstants
        || "ru/feytox/etherology/forge/client/EtherealStorageFoundationRenderer"
        !in clientEventConstants
        || "dev/architectury/registry/client/rendering/RenderTypeRegistry"
        !in clientEventConstants
        || "getCutout" !in clientEventConstants
        || "net/minecraft/client/item/ModelPredicateProviderRegistry" !in clientEventConstants
        || "ether_percentage" !in clientEventConstants
        || "ru/feytox/etherology/item/glints/GlintEtherData" !in clientEventConstants
        || "getStoredEther" !in clientEventConstants
        || "ru/feytox/etherology/item/EtherealStorageInputItem" !in clientEventConstants
        || "getMaxEther" !in clientEventConstants
    ) {
        missingConditions.add(
            "Forge client setup does not bind the storage renderer, cutout, and shared glint predicate",
        )
    }

    val storageRendererConstants = readCompiledClassConstants(
        forgeClassesDirectory,
        etherealStorageFoundationRendererClassEntry,
    )
    if (storageRendererConstants == null
        || "software/bernie/geckolib/renderer/GeoBlockRenderer"
        !in storageRendererConstants
        || "ru/feytox/etherology/forge/client/EtherealStorageFoundationModel"
        !in storageRendererConstants
    ) {
        missingConditions.add("Forge has no Gecko block renderer for ethereal storage")
    }

    val storageModelConstants = readCompiledClassConstants(
        forgeClassesDirectory,
        etherealStorageFoundationModelClassEntry,
    )
    if (storageModelConstants == null
        || "software/bernie/geckolib/model/GeoModel" !in storageModelConstants
        || "geo/ethereal_storage.geo.json" !in storageModelConstants
        || "textures/machines/ethereal_storage.png" !in storageModelConstants
        || "animations/ethereal_storage.animation.json" !in storageModelConstants
    ) {
        missingConditions.add("Forge storage Geo model does not bind the canonical client assets")
    }

    listOf(
        etherealStorageGeoModel,
        etherealStorageAnimation,
        etherealStorageMachineTexture,
    ).filterNot(File::isFile).forEach { missingResource ->
        missingConditions.add("ethereal storage Gecko resource is missing: ${missingResource.name}")
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

fun missingForgeChannelImplementationMilestone(commonJarFile: File): List<String> {
    val missingConditions = mutableListOf<String>()
    ZipFile(commonJarFile).use { commonZip ->
        val etherStorageEntry = commonZip.getEntry(etherStorageContractClassEntry)
        if (etherStorageEntry == null) {
            missingConditions.add("common JAR has no shared Ether storage transfer contract")
        } else {
            val etherStorageConstants = readClassUtf8Constants(
                commonZip.getInputStream(etherStorageEntry).use { input -> input.readAllBytes() },
            )
            if ("getMaxEther" !in etherStorageConstants
                || "getStoredEther" !in etherStorageConstants
                || "getTransferSize" !in etherStorageConstants
                || "setStoredEther" !in etherStorageConstants
                || "isInputSide" !in etherStorageConstants
                || "getOutputSide" !in etherStorageConstants
                || "getStoragePos" !in etherStorageConstants
                || "transferTick" !in etherStorageConstants
                || "getTransportableEther" !in etherStorageConstants
                || "isOutputSide" !in etherStorageConstants
                || "canInputFrom" !in etherStorageConstants
                || "canOutputTo" !in etherStorageConstants
                || "isActivated" !in etherStorageConstants
                || "transfer" !in etherStorageConstants
                || "transferTo" !in etherStorageConstants
                || "evaporate" !in etherStorageConstants
                || "increment" !in etherStorageConstants
                || "decrement" !in etherStorageConstants
                || "ru/feytox/etherology/magic/ether/EvaporatingEtherPipe"
                !in etherStorageConstants
                || "setEvaporating" !in etherStorageConstants
                || "setCrossEvaporating" !in etherStorageConstants
            ) {
                missingConditions.add("shared Ether storage contract has no bounded transfer flow")
            }
        }

        listOf(
            etherPipeContractClassEntry to "shared Ether pipe contract",
            etherDisplayContractClassEntry to "shared Ether display contract",
            evaporatingEtherPipeContractClassEntry to "shared evaporating-pipe contract",
            pipeSideClassEntry to "shared pipe-side state",
        ).forEach { (entryName, description) ->
            if (commonZip.getEntry(entryName) == null) {
                missingConditions.add("common JAR has no $description")
            }
        }

        val evaporatingPipeEntry = commonZip.getEntry(evaporatingEtherPipeContractClassEntry)
        if (evaporatingPipeEntry != null) {
            val evaporatingPipeConstants = readClassUtf8Constants(
                commonZip.getInputStream(evaporatingPipeEntry).use { input -> input.readAllBytes() },
            )
            if ("ru/feytox/etherology/magic/ether/EtherPipe" !in evaporatingPipeConstants
                || "setEvaporating" !in evaporatingPipeConstants
                || "setCrossEvaporating" !in evaporatingPipeConstants
            ) {
                missingConditions.add("shared evaporating-pipe contract has no two-state API")
            }
        }

        val pipeSideEntry = commonZip.getEntry(pipeSideClassEntry)
        if (pipeSideEntry != null) {
            val pipeSideConstants = readClassUtf8Constants(
                commonZip.getInputStream(pipeSideEntry).use { input -> input.readAllBytes() },
            )
            val requiredPipeSideConstants = setOf(
                "net/minecraft/util/StringIdentifiable",
                "EMPTY",
                "IN",
                "OUT",
                "isInput",
                "isOutput",
                "isEmpty",
                "asString",
                "toLowerCase",
            )
            val missingPipeSideConstants = requiredPipeSideConstants - pipeSideConstants
            if (missingPipeSideConstants.isNotEmpty()) {
                missingConditions.add(
                    "shared pipe-side state lost its serialized three-value contract: " +
                        missingPipeSideConstants.sorted(),
                )
            }
        }

        val storageEntry = commonZip.getEntry(etherealStorageFoundationBlockEntityClassEntry)
        if (storageEntry == null) {
            missingConditions.add("common JAR has no channel-compatible ethereal storage")
        } else {
            val storageConstants = readClassUtf8Constants(
                commonZip.getInputStream(storageEntry).use { input -> input.readAllBytes() },
            )
            if ("ru/feytox/etherology/magic/ether/EtherStorage" !in storageConstants
                || "transferTick" !in storageConstants
                || "transfer" !in storageConstants
                || "chargeGlints" !in storageConstants
                || "getTime" !in storageConstants
            ) {
                missingConditions.add(
                    "ethereal storage does not run directed transfer before its fifth-tick Glint flow",
                )
            }
        }

        val channelBlockEntry = commonZip.getEntry(etherealChannelBlockClassEntry)
        if (channelBlockEntry == null) {
            missingConditions.add("common JAR has no shared ethereal-channel block")
        } else {
            val channelBlockConstants = readClassUtf8Constants(
                commonZip.getInputStream(channelBlockEntry).use { input -> input.readAllBytes() },
            )
            val requiredBlockConstants = setOf(
                "net/minecraft/block/BlockEntityProvider",
                "net/minecraft/block/Waterloggable",
                "ru/feytox/etherology/enums/PipeSide",
                "ACTIVATED",
                "FACING",
                "IN_CASE",
                "IS_CROSS",
                "WATERLOGGED",
                "getChannelState",
                "isNeighborOutput",
                "leverOutputDirection",
                "applyFacingState",
                "inputProperty",
                "outputProperty",
                "neighborUpdate",
                "getReceivedStrongRedstonePower",
                "getFluidState",
                "scheduleFluidTick",
                "getOutlineShape",
                "getTicker",
                "ru/feytox/etherology/block/etherealChannel/" +
                    "EtherealChannelFoundationBlockEntity",
            )
            val missingBlockConstants = requiredBlockConstants - channelBlockConstants
            if (missingBlockConstants.isNotEmpty()) {
                missingConditions.add(
                    "shared ethereal channel lacks canonical state, shape, water, redstone, " +
                        "topology, or server-ticker behavior: ${missingBlockConstants.sorted()}",
                )
            }
        }

        val channelShapeEntry = commonZip.getEntry(etherealChannelShapeClassEntry)
        if (channelShapeEntry == null) {
            missingConditions.add("common JAR has no precomputed ethereal-channel shape owner")
        } else {
            val channelShapeConstants = readClassUtf8Constants(
                commonZip.getInputStream(channelShapeEntry).use { input -> input.readAllBytes() },
            )
            val requiredShapeConstants = setOf(
                "NORTH",
                "SOUTH",
                "EAST",
                "WEST",
                "UP",
                "DOWN",
                "SIDE_PROPERTIES",
                "DIRECTIONS",
                "CENTER",
                "buildShapes",
                "getShape",
                "withEmptySides",
                "combineAndSimplify",
            )
            val missingShapeConstants = requiredShapeConstants - channelShapeConstants
            if (missingShapeConstants.isNotEmpty()) {
                missingConditions.add(
                    "shared ethereal channel lacks its six-face precomputed shapes: " +
                        missingShapeConstants.sorted(),
                )
            }
        }

        val channelBlockEntityEntry = commonZip.getEntry(etherealChannelBlockEntityClassEntry)
        if (channelBlockEntityEntry == null) {
            missingConditions.add("common JAR has no shared ethereal-channel block entity")
        } else {
            val channelConstants = readClassUtf8Constants(
                commonZip.getInputStream(channelBlockEntityEntry)
                    .use { input -> input.readAllBytes() },
            )
            val requiredBlockEntityConstants = setOf(
                "ru/feytox/etherology/magic/ether/EtherDisplay",
                "ru/feytox/etherology/magic/ether/EvaporatingEtherPipe",
                "transferTick",
                "transfer",
                "stored_ether",
                "evaporating",
                "cross_evaporating",
                "getOutputSide",
                "getStoredEther",
                "setStoredEther",
                "getMaxEther",
                "getTransferSize",
                "isInputSide",
                "isActivated",
                "getDisplayEther",
                "getDisplayMaxEther",
                "setEvaporating",
                "setCrossEvaporating",
                "writeNbt",
                "readNbt",
                "toUpdatePacket",
                "toInitialChunkDataNbt",
                "net/minecraft/network/packet/s2c/play/BlockEntityUpdateS2CPacket",
            )
            val missingBlockEntityConstants = requiredBlockEntityConstants - channelConstants
            if (missingBlockEntityConstants.isNotEmpty()) {
                missingConditions.add(
                    "shared ethereal channel has no persistent, synchronized directed transfer " +
                        "behavior: ${missingBlockEntityConstants.sorted()}",
                )
            }
        }

        val sharedBlockEntry = commonZip.getEntry(sharedBlockRegistryClassEntry)
        val sharedBlockConstants = sharedBlockEntry?.let { entry ->
            readClassUtf8Constants(
                commonZip.getInputStream(entry).use { input -> input.readAllBytes() },
            )
        }.orEmpty()
        if ("ethereal_channel" !in sharedBlockConstants
            || "ru/feytox/etherology/block/etherealChannel/EtherealChannelFoundationBlock"
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
        val channelBlockEntityClassName =
            "ru/feytox/etherology/block/etherealChannel/" +
                "EtherealChannelFoundationBlockEntity"
        if ("ethereal_channel_block_entity" !in sharedBlockEntityConstants
            || channelBlockEntityClassName !in sharedBlockEntityConstants
        ) {
            missingConditions.add("SharedBlockEntities does not register the ethereal channel")
        }

        val sharedItemEntry = commonZip.getEntry(sharedItemRegistryClassEntry)
        val sharedItemConstants = sharedItemEntry?.let { entry ->
            readClassUtf8Constants(
                commonZip.getInputStream(entry).use { input -> input.readAllBytes() },
            )
        }.orEmpty()
        if ("ethereal_channel" !in sharedItemConstants
            || "net/minecraft/item/BlockItem" !in sharedItemConstants
            || "ru/feytox/etherology/registry/block/SharedBlocks" !in sharedItemConstants
        ) {
            missingConditions.add("SharedItems does not register the ethereal-channel block item")
        }
    }

    etherealChannelResources.filterNot(File::isFile).forEach { missingResource ->
        missingConditions.add("ethereal channel resource is missing: ${missingResource.name}")
    }
    if (etherealChannelBlockstate.isFile) {
        val blockstateText = etherealChannelBlockstate.readText()
        listOf(
            "ethereal_channel_input",
            "ethereal_channel_output",
            "ethereal_channel_central_line",
            "ethereal_channel_central_cross",
            "ethereal_channel_in_case",
            "\"in_case\"",
            "\"is_cross\"",
        ).filterNot(blockstateText::contains).forEach { missingToken ->
            missingConditions.add("ethereal channel blockstate lacks $missingToken")
        }
    }
    if (!englishLanguageFile.isFile
        || !englishLanguageFile.readText().contains("\"block.etherology.ethereal_channel\"")
    ) {
        missingConditions.add("ethereal channel English translation is missing")
    }
    val compiledMixin = forgeMainClasses.get().asFile.resolve(
        forgeChannelLeverMixinClassEntry,
    )
    if (!compiledMixin.isFile) {
        missingConditions.add("Forge has no compiled channel lever-support mixin")
    } else {
        val mixinConstants = readClassUtf8Constants(compiledMixin.readBytes())
        val requiredMixinConstants = setOf(
            "Lnet/minecraft/block/WallMountedBlock;",
            "net/minecraft/block/LeverBlock",
            "ru/feytox/etherology/registry/block/SharedBlocks",
            "ETHEREAL_CHANNEL",
            "canPlaceAt(Lnet/minecraft/block/BlockState;" +
                "Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z",
            "getDirection",
        )
        val missingMixinConstants = requiredMixinConstants - mixinConstants
        if (missingMixinConstants.isNotEmpty()) {
            missingConditions.add(
                "Forge channel lever-support mixin lacks its narrow canonical binding: " +
                    missingMixinConstants.sorted(),
            )
        }
    }
    if (!forgeMixinConfig.isFile) {
        missingConditions.add("Forge channel mixin configuration is missing")
    } else {
        val mixinConfigText = forgeMixinConfig.readText()
        listOf(
            "ru.feytox.etherology.forge.mixin",
            "ChannelLeverSupportMixin",
        ).filterNot(mixinConfigText::contains).forEach { missingToken ->
            missingConditions.add("Forge channel mixin configuration lacks $missingToken")
        }
    }
    return missingConditions
}

fun missingForgeChannelEvidenceMilestone(
    currentProduction: File? = null,
    currentHarness: File? = null,
): List<String> {
    val missingConditions = mutableListOf<String>()
    if ((currentProduction == null) != (currentHarness == null)) {
        missingConditions.add(
            "current production and harness artifacts must be verified together",
        )
        return missingConditions
    }
    if (!forgeChannelEvidenceVerifier.isFile) {
        missingConditions.add("strict native Forge ethereal-channel verifier is missing")
        return missingConditions
    }

    val archiveDirectories = forgeChannelEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("ethereal-channel-v[1-9][0-9]*").matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories.size != 1) {
        missingConditions.add(
            "exactly one frozen native Forge ethereal-channel evidence archive is required",
        )
        return missingConditions
    }

    val archiveDirectory = archiveDirectories.single()
    val command = mutableListOf(
        "python3",
        "-B",
        forgeChannelEvidenceVerifier.absolutePath,
        "--archive",
        archiveDirectory.absolutePath,
    )
    if (currentProduction != null && currentHarness != null) {
        command.addAll(
            listOf(
                "--current-production",
                currentProduction.absolutePath,
                "--current-harness",
                currentHarness.absolutePath,
                "--current-profile",
                forgeE2eProfileManifest.absolutePath,
            ),
        )
    }

    try {
        val process = ProcessBuilder(command)
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        process.outputStream.close()
        val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8)
            .use { reader -> reader.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val detail = output.trim().ifEmpty { "verifier exited without diagnostics" }
            missingConditions.add(
                "strict native Forge ethereal-channel evidence verification failed: " +
                    detail.take(4_000),
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict native Forge ethereal-channel evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeChannelNetworkMilestone(commonJarFile: File): List<String> =
    missingForgeChannelImplementationMilestone(commonJarFile) +
        missingForgeChannelEvidenceMilestone()

fun taskOutputJar(task: Task, description: String): File {
    val jarFiles = task.outputs.files.files.filter { output ->
        output.isFile && output.extension == "jar"
    }
    check(jarFiles.size == 1) {
        "$description must produce exactly one JAR, found ${jarFiles.sorted()}"
    }
    return jarFiles.single()
}

fun missingForgeSoundRegistryMilestone(
    commonJarFile: File,
    fabricTransformedCommonJarFile: File,
    forgeTransformedCommonJarFile: File,
    fabricProductionJarFile: File,
    forgeShadowJarFile: File,
): List<String> {
    val missingConditions = mutableListOf<String>()

    if (!soundManifest.isFile || Files.isSymbolicLink(soundManifest.toPath())) {
        missingConditions.add("canonical sounds.json is missing or linked")
    }

    val diskSoundFiles = if (soundDirectory.isDirectory
        && !Files.isSymbolicLink(soundDirectory.toPath())
    ) {
        soundDirectory.walkTopDown()
            .filter(File::isFile)
            .map { sound -> sound.relativeTo(soundDirectory).invariantSeparatorsPath }
            .toSet()
    } else {
        emptySet()
    }
    if (diskSoundFiles != canonicalSoundFiles) {
        missingConditions.add(
            "canonical packaged OGG inventory changed: " +
                "expected=${canonicalSoundFiles.sorted()}, actual=${diskSoundFiles.sorted()}",
        )
    }

    if (!englishLanguageFile.isFile || Files.isSymbolicLink(englishLanguageFile.toPath())) {
        missingConditions.add("canonical English language file is missing or linked")
    }

    fun inspectArtifact(
        artifact: File,
        description: String,
        requireSoundResources: Boolean,
        requireFabricEntrypoint: Boolean = false,
    ) {
        if (!artifact.isFile || Files.isSymbolicLink(artifact.toPath())) {
            missingConditions.add("$description is missing or linked")
            return
        }
        try {
            ZipFile(artifact).use { zip ->
                val entryNames = zip.entries().asSequence().map { entry -> entry.name }.toList()
                val entries = entryNames.toSet()
                val sharedOwnerCount = entryNames.count(sharedSoundRegistryClassEntry::equals)
                if (sharedOwnerCount != 1) {
                    missingConditions.add(
                        "$description must contain one shared sound registry, " +
                            "found $sharedOwnerCount",
                    )
                } else {
                    val soundEntry = requireNotNull(zip.getEntry(sharedSoundRegistryClassEntry))
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(soundEntry).use { input -> input.readAllBytes() },
                    )
                    val missingSoundIds = canonicalSoundEventIds - constants
                    if (missingSoundIds.isNotEmpty()
                        || "ru/feytox/etherology/registry/SharedDeferredRegister" !in constants
                    ) {
                        missingConditions.add(
                            "$description shared sound registry lost deferred ownership or IDs: " +
                                missingSoundIds.sorted(),
                        )
                    }
                }
                val legacyOwnerCount = entryNames.count(
                    canonicalFabricSoundRegistryClassEntry::equals,
                )
                if (legacyOwnerCount != 0) {
                    missingConditions.add(
                        "$description contains $legacyOwnerCount legacy EtherSounds owner(s)",
                    )
                }
                val soundIdOwners = mutableMapOf<String, Set<String>>()
                val soundRegistryWriteOwners = mutableSetOf<String>()
                entryNames.filter { entryName -> entryName.endsWith(".class") }
                    .forEach { classEntryName ->
                        val classEntry = requireNotNull(zip.getEntry(classEntryName))
                        val constants = readClassUtf8Constants(
                            zip.getInputStream(classEntry).use { input -> input.readAllBytes() },
                        )
                        val declaredSoundIds = canonicalSoundEventIds.intersect(constants)
                        val hasSoundEventType = listOf(
                            "net/minecraft/sound/SoundEvent",
                            "net/minecraft/class_3414",
                        ).any(constants::contains)
                        if (hasSoundEventType && declaredSoundIds.isNotEmpty()) {
                            soundIdOwners[classEntryName] = declaredSoundIds
                        }
                        val hasNamedSoundRegistryReference =
                            "SOUND_EVENT" in constants && (
                                "net/minecraft/registry/RegistryKeys" in constants
                                    || "net/minecraft/registry/Registries" in constants
                            )
                        val hasIntermediarySoundRegistryReference =
                            "net/minecraft/class_7924" in constants
                                && "field_41225" in constants
                                || "net/minecraft/class_7923" in constants
                                && "field_41172" in constants
                        if (hasNamedSoundRegistryReference
                            || hasIntermediarySoundRegistryReference
                        ) {
                            soundRegistryWriteOwners.add(classEntryName)
                        }
                    }
                val expectedSoundOwner = setOf(sharedSoundRegistryClassEntry)
                if (soundIdOwners.keys != expectedSoundOwner
                    || soundIdOwners[sharedSoundRegistryClassEntry] != canonicalSoundEventIds
                ) {
                    missingConditions.add(
                        "$description canonical sound-ID declaration owners changed: " +
                            soundIdOwners.toSortedMap(),
                    )
                }
                if (soundRegistryWriteOwners != expectedSoundOwner) {
                    missingConditions.add(
                        "$description sound-event registry-write owners changed: " +
                            soundRegistryWriteOwners.sorted(),
                    )
                }
                val bootstrapEntry = zip.getEntry(commonBootstrapClassEntry)
                if (bootstrapEntry == null) {
                    missingConditions.add("$description has no loader-neutral bootstrap")
                } else {
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(bootstrapEntry).use { input -> input.readAllBytes() },
                    )
                    if ("ru/feytox/etherology/registry/misc/SharedSounds" !in constants) {
                        missingConditions.add(
                            "$description bootstrap does not attach the shared sound registry",
                        )
                    }
                }
                if (requireFabricEntrypoint) {
                    val initializerEntry = zip.getEntry(canonicalFabricInitializerClassEntry)
                    if (initializerEntry == null) {
                        missingConditions.add("$description has no canonical Fabric initializer")
                    } else {
                        val constants = readClassUtf8Constants(
                            zip.getInputStream(initializerEntry).use { input ->
                                input.readAllBytes()
                            },
                        )
                        if ("ru/feytox/etherology/registry/misc/SharedSounds" !in constants
                            || "ru/feytox/etherology/bootstrap/EtherologyBootstrap" in constants
                        ) {
                            missingConditions.add(
                                "$description initializer does not directly own the Fabric " +
                                    "SharedSounds attachment path",
                            )
                        }
                    }
                    val entrypointEntry = zip.getEntry(fabricEntrypointClassEntry)
                    if (entrypointEntry == null) {
                        missingConditions.add("$description has no Fabric entrypoint")
                    } else {
                        val constants = readClassUtf8Constants(
                            zip.getInputStream(entrypointEntry).use { input ->
                                input.readAllBytes()
                            },
                        )
                        if ("ru/feytox/etherology/Etherology" !in constants
                            || "ru/feytox/etherology/registry/misc/SharedSounds" in constants
                            || "ru/feytox/etherology/bootstrap/EtherologyBootstrap" in constants
                        ) {
                            missingConditions.add(
                                "$description Fabric entrypoint bypasses its canonical initializer",
                            )
                        }
                    }
                }
                if (requireSoundResources) {
                    val packagedSoundFiles = entries
                        .filter { entry ->
                            entry.startsWith("assets/etherology/sounds/")
                                && entry.endsWith(".ogg")
                        }
                        .map { entry -> entry.removePrefix("assets/etherology/sounds/") }
                        .toSet()
                    if (packagedSoundFiles != canonicalSoundFiles) {
                        missingConditions.add(
                            "$description packaged OGG inventory changed: " +
                                "expected=${canonicalSoundFiles.sorted()}, " +
                                "actual=${packagedSoundFiles.sorted()}",
                        )
                    }
                    val canonicalResources = buildMap<String, File> {
                        put("assets/etherology/sounds.json", soundManifest)
                        put("assets/etherology/lang/en_us.json", englishLanguageFile)
                        canonicalSoundFiles.forEach { soundFile ->
                            put(
                                "assets/etherology/sounds/$soundFile",
                                soundDirectory.resolve(soundFile),
                            )
                        }
                    }
                    canonicalResources.forEach { (entryPath, sourceFile) ->
                        if (!sourceFile.isFile
                            || Files.isSymbolicLink(sourceFile.toPath())
                        ) {
                            missingConditions.add(
                                "canonical source for $entryPath is missing or linked",
                            )
                            return@forEach
                        }
                        val resourceEntry = zip.getEntry(entryPath)
                        if (resourceEntry == null) {
                            missingConditions.add("$description has no $entryPath")
                            return@forEach
                        }
                        val packagedBytes = zip.getInputStream(resourceEntry).use { input ->
                            input.readAllBytes()
                        }
                        if (!packagedBytes.contentEquals(sourceFile.readBytes())) {
                            missingConditions.add(
                                "$description $entryPath differs from its canonical source",
                            )
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            missingConditions.add(
                "$description could not be inspected: " +
                    "${exception.javaClass.simpleName}: ${exception.message}",
            )
        }
    }

    inspectArtifact(commonJarFile, "common JAR", false)
    inspectArtifact(
        fabricTransformedCommonJarFile,
        "Fabric-transformed common JAR",
        false,
    )
    inspectArtifact(
        forgeTransformedCommonJarFile,
        "Forge-transformed common JAR",
        false,
    )
    inspectArtifact(
        fabricProductionJarFile,
        "Fabric remapped production JAR",
        true,
        true,
    )
    inspectArtifact(forgeShadowJarFile, "Forge shadow JAR", true)
    return missingConditions
}

fun missingForgeAuthoritativeRegistrySpineMilestone(): List<String> = listOf(
    "the shared block and item catalogs do not cover every canonical runtime ID",
    "entity, enchantment, recipe, screen, effect, event, loot, particle, tree, and " +
        "world-generation registries are not loader-neutral",
    "creative tabs, fuel, reload, lifecycle, trade, brewing, wood, sculk, and command hooks " +
        "are not accepted on both loaders",
    "the exact Fabric/Forge registry manifest and dedicated-server placement/save smoke are " +
        "not accepted",
)

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
    fabricTransformedCommonJarFile: File,
    forgeTransformedCommonJarFile: File,
    fabricProductionJarFile: File,
    forgeShadowJarFile: File,
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

    val missingSoundRegistry = missingForgeSoundRegistryMilestone(
        commonJarFile,
        fabricTransformedCommonJarFile,
        forgeTransformedCommonJarFile,
        fabricProductionJarFile,
        forgeShadowJarFile,
    )
    if (missingSoundRegistry.isNotEmpty()) {
        return "sound registry and resources" to missingSoundRegistry
    }

    val missingRegistrySpine = missingForgeAuthoritativeRegistrySpineMilestone()
    if (missingRegistrySpine.isNotEmpty()) {
        return "authoritative registry spine" to missingRegistrySpine
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
    dependsOn(validateForgeEtherItemMilestone, validateForgeAcceptedDataSet, commonJar)
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
    inputs.files(
        etherealStorageGeoModel,
        etherealStorageAnimation,
        etherealStorageMachineTexture,
    )
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

val validateForgeChannelImplementationMilestone =
    tasks.register("validateForgeChannelImplementationMilestone") {
        group = "verification"
        description =
            "Checks the shared ethereal-channel implementation before native evidence acceptance."
        dependsOn(
            validateForgeStorageParityMilestone,
            commonJar,
            commonTest,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(etherealChannelResources + englishLanguageFile + forgeMixinConfig)
        inputs.dir(forgeMainClasses)
        doLast {
            val commonJarFile = commonJar.get().archiveFile.get().asFile
            val missingConditions = missingForgeChannelImplementationMilestone(commonJarFile)
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion ethereal-channel implementation is incomplete:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeChannelEvidenceArchiveIntegrity =
    tasks.register("validateForgeChannelEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Forge channel archive without comparing later artifacts."
        inputs.file(forgeChannelEvidenceVerifier)
        inputs.dir(forgeChannelEvidenceRoot)
            .withPropertyName("forgeChannelEvidenceRoot")
            .optional()
        doLast {
            val missingConditions = missingForgeChannelEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion native ethereal-channel archive is invalid:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeChannelNetworkMilestone = tasks.register("validateForgeChannelNetworkMilestone") {
    group = "verification"
    description =
        "Requires the shared directed-transfer implementation and its frozen native Forge proof."
    dependsOn(
        validateForgeChannelImplementationMilestone,
        validateForgeChannelEvidenceArchiveIntegrity,
    )
    inputs.file(commonJar.flatMap { it.archiveFile })
    inputs.files(etherealChannelResources + englishLanguageFile)
    inputs.dir(forgeChannelEvidenceRoot)
        .withPropertyName("forgeChannelEvidenceRoot")
        .optional()
    doLast {
        val commonJarFile = commonJar.get().archiveFile.get().asFile
        val missingConditions = missingForgeChannelImplementationMilestone(commonJarFile)
        check(missingConditions.isEmpty()) {
            "Forge $minecraftVersion ethereal channel/network milestone is incomplete:\n${
                missingConditions.joinToString("\n") { condition -> " - $condition" }
            }"
        }
    }
}

val forgeShadowJar = tasks.named<ShadowJar>("shadowJar")
val validateForgeSoundRegistryMilestone = tasks.register("validateForgeSoundRegistryMilestone") {
    group = "verification"
    description =
        "Accepts the shared sound registry and its exact cross-loader packaged resources."
    dependsOn(
        validateForgeChannelNetworkMilestone,
        commonJar,
        commonTest,
        fabricTest,
        fabricRemapJar,
        tasks.named("test"),
        commonTransformProductionFabric,
        commonTransformProductionForge,
        forgeShadowJar,
    )
    inputs.file(commonJar.flatMap { it.archiveFile })
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("fabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("forgeTransformedCommonJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
    inputs.files(soundManifest, englishLanguageFile)
    inputs.dir(soundDirectory)
    doLast {
        val commonJarFile = commonJar.get().archiveFile.get().asFile
        val fabricTransformedCommonJarFile = taskOutputJar(
            commonTransformProductionFabric.get(),
            "Fabric common production transform",
        )
        val forgeTransformedCommonJarFile = taskOutputJar(
            commonTransformProductionForge.get(),
            "Forge common production transform",
        )
        val fabricProductionJarFile = fabricRemapJar.get().archiveFile.get().asFile
        val forgeShadowJarFile = forgeShadowJar.get().archiveFile.get().asFile
        val missingConditions = missingForgeSoundRegistryMilestone(
            commonJarFile,
            fabricTransformedCommonJarFile,
            forgeTransformedCommonJarFile,
            fabricProductionJarFile,
            forgeShadowJarFile,
        )
        check(missingConditions.isEmpty()) {
            "Forge $minecraftVersion shared sound registry milestone is incomplete:\n${
                missingConditions.joinToString("\n") { condition -> " - $condition" }
            }"
        }
    }
}

val validateForgeAuthoritativeRegistrySpineMilestone =
    tasks.register("validateForgeAuthoritativeRegistrySpineMilestone") {
        group = "verification"
        description =
            "Blocks broad gameplay until every canonical runtime registry has one shared owner."
        dependsOn(validateForgeSoundRegistryMilestone)
        doLast {
            val missingConditions = missingForgeAuthoritativeRegistrySpineMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion authoritative registry spine is incomplete:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeReleaseReadinessMilestone = tasks.register("validateForgeReleaseReadinessMilestone") {
    group = "verification"
    description =
        "Permanently blocks artifacts until complete gameplay and packaged native Forge E2E are accepted."
    dependsOn(validateForgeAuthoritativeRegistrySpineMilestone)
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
        validateForgeChannelImplementationMilestone,
        validateForgeChannelNetworkMilestone,
        validateForgeSoundRegistryMilestone,
        validateForgeAuthoritativeRegistrySpineMilestone,
        validateForgeReleaseReadinessMilestone,
    )
}

tasks.register("verifyForgePortGateClosed") {
    group = "verification"
    description = "Reports the first incomplete forward milestone without serving as a release gate."
    dependsOn(validateForgeSoundRegistryMilestone)
    inputs.file(commonJar.flatMap { it.archiveFile })
    inputs.dir(forgeMainClasses)
    inputs.files(etherealChannelResources + englishLanguageFile)
    inputs.files(soundManifest, englishLanguageFile)
    inputs.dir(soundDirectory)
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("fabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("forgeTransformedCommonJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
    inputs.dir(forgeChannelEvidenceRoot)
        .withPropertyName("forgeChannelEvidenceRoot")
        .optional()
    doLast {
        val commonJarFile = commonJar.get().archiveFile.get().asFile
        val firstIncompleteMilestone = firstIncompleteForgeMilestone(
            commonJarFile,
            forgeMainClasses.get().asFile,
            taskOutputJar(
                commonTransformProductionFabric.get(),
                "Fabric common production transform",
            ),
            taskOutputJar(
                commonTransformProductionForge.get(),
                "Forge common production transform",
            ),
            fabricRemapJar.get().archiveFile.get().asFile,
            forgeShadowJar.get().archiveFile.get().asFile,
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

if (minecraftVersion == "1.20.1") {
    val e2eHarness = sourceSets.create("e2eHarness") {
        java.setSrcDirs(
            listOf(rootProject.file("e2e-harness/forge/1.20.1/src/main/java")),
        )
        resources.setSrcDirs(
            listOf(rootProject.file("e2e-harness/forge/1.20.1/src/main/resources")),
        )
        compileClasspath += sourceSets.main.get().compileClasspath
        runtimeClasspath += output + compileClasspath
    }

    val e2eHarnessTest = sourceSets.create("e2eHarnessTest") {
        java.setSrcDirs(
            listOf(rootProject.file("e2e-harness/forge/1.20.1/src/test/java")),
        )
        resources.setSrcDirs(emptyList<String>())
        compileClasspath += e2eHarness.output + e2eHarness.compileClasspath
        runtimeClasspath += output + e2eHarness.output + e2eHarness.runtimeClasspath
    }
    configurations[e2eHarnessTest.implementationConfigurationName]
        .extendsFrom(configurations["testImplementation"])
    configurations[e2eHarnessTest.runtimeOnlyConfigurationName]
        .extendsFrom(configurations["testRuntimeOnly"])

    val e2eHarnessTestTask = tasks.register<Test>("e2eHarnessTest") {
        group = "verification"
        description = "Runs focused unit tests for the Forge 1.20.1 E2E harness."
        dependsOn(e2eHarness.classesTaskName)
        testClassesDirs = e2eHarnessTest.output.classesDirs
        classpath = e2eHarnessTest.runtimeClasspath
        useJUnitPlatform()
    }

    val forgeChannelEvidenceVerifierTest =
        tasks.register<Exec>("forgeChannelEvidenceVerifierTest") {
            group = "verification"
            description =
                "Runs adversarial tests for the Forge 1.20.1 channel evidence verifier."
            workingDir(rootProject.projectDir)
            commandLine(
                "python3",
                "-B",
                "-m",
                "unittest",
                "scripts/e2e/test_forge_channel_evidence.py",
            )
            inputs.files(
                forgeChannelEvidenceVerifier,
                forgeE2eProfileManifest,
                rootProject.file("scripts/e2e/test_forge_channel_evidence.py"),
                rootProject.file("scripts/e2e/forge_client.py"),
                rootProject.file("scripts/e2e/forge_evidence.py"),
            )
        }

    validateForgeChannelImplementationMilestone.configure {
        dependsOn(e2eHarnessTestTask, forgeChannelEvidenceVerifierTest)
    }

    val expandedE2eHarnessMetadata = mapOf(
        "version" to project.version.toString(),
        "minecraft_version_range" to releaseArtifact["metadata_range"].toString(),
        "forge_loader_range" to releaseMetadata["loader_api"].toString(),
        "forge_version_range" to releaseMetadata["loader"].toString(),
    )

    tasks.named<ProcessResources>(e2eHarness.processResourcesTaskName) {
        inputs.properties(expandedE2eHarnessMetadata)
        filesMatching("META-INF/mods.toml") {
            expand(expandedE2eHarnessMetadata)
        }
    }

    val e2eHarnessJar = tasks.register<Jar>("e2eHarnessJar") {
        group = "e2e"
        description = "Packages the named Forge 1.20.1 client E2E harness classes."
        dependsOn(e2eHarness.classesTaskName)
        from(e2eHarness.output)
        archiveBaseName.set("Etherology-E2E-Harness-Forge-$minecraftVersion")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("dev")
        destinationDirectory.set(layout.buildDirectory.dir("e2e-harness/devlibs"))
    }

    val remapE2eHarnessJar = tasks.register<RemapJarTask>("remapE2eHarnessJar") {
        group = "e2e"
        description = "Remaps the separate Forge 1.20.1 client E2E harness for a packaged run."
        dependsOn(e2eHarnessJar)
        inputFile.set(e2eHarnessJar.flatMap { it.archiveFile })
        classpath.from(e2eHarness.compileClasspath)
        addNestedDependencies.set(false)
        useMixinAP.set(false)
        archiveBaseName.set("Etherology-E2E-Harness-Forge-$minecraftVersion")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")
        destinationDirectory.set(layout.buildDirectory.dir("e2e-harness/libs"))
    }

    val remapE2eUnderTestJar = tasks.register<RemapJarTask>("remapE2eUnderTestJar") {
        group = "e2e"
        description =
            "Builds a marked Forge test artifact without opening the fail-closed release lane."
        dependsOn(tasks.named("shadowJar"), validateForgeAcceptedDataSet)
        val shadowJar = tasks.named<ShadowJar>("shadowJar")
        inputFile.set(shadowJar.flatMap { it.archiveFile })
        addNestedDependencies.set(true)
        archiveBaseName.set("Etherology-Forge-$minecraftVersion")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("e2e-under-test")
        destinationDirectory.set(layout.buildDirectory.dir("e2e-under-test/libs"))
        manifest.attributes[
            "Etherology-E2E-Only"
        ] = "true"
    }

    fun validateE2eHarnessJar(harnessFile: File) {
        ZipFile(harnessFile).use { harnessZip ->
            val harnessEntries = harnessZip.entries().asSequence().map { it.name }.toSet()
            val harnessClassEntries = harnessEntries.filter { it.endsWith(".class") }
            check(
                "dev/theplumteam/etherology/e2e/forge/ForgeE2eHarness.class" in
                    harnessEntries,
            ) {
                "Forge E2E harness JAR has no client entrypoint class"
            }
            check(harnessClassEntries.all {
                it.startsWith("dev/theplumteam/etherology/e2e/forge/")
            }) {
                "Forge E2E harness JAR contains classes outside its isolated package"
            }
            check(harnessEntries.none { it.endsWith(".jar") }) {
                "Forge E2E harness JAR contains nested dependencies"
            }
            harnessClassEntries.forEach { classEntryName ->
                val classEntry = requireNotNull(harnessZip.getEntry(classEntryName))
                val classConstants = harnessZip.getInputStream(classEntry).use { input ->
                    String(input.readAllBytes(), StandardCharsets.ISO_8859_1)
                }
                check(!classConstants.contains("ru/feytox/etherology/")
                    && !classConstants.contains("ru.feytox.etherology.")
                ) {
                    "Forge E2E harness class $classEntryName links to production Etherology code"
                }
            }

            val metadataEntry = requireNotNull(
                harnessZip.getEntry("META-INF/mods.toml"),
            ) {
                "Forge E2E harness JAR has no META-INF/mods.toml"
            }
            val metadataText = harnessZip.getInputStream(metadataEntry)
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }
            check(metadataText.contains("modId=\"etherology_e2e_harness\"")) {
                "Forge E2E harness metadata has the wrong mod id"
            }
            check(metadataText.contains("modId=\"etherology\"")) {
                "Forge E2E harness metadata does not require Etherology"
            }
            check(metadataText.contains("versionRange=\"[${project.version}]\"")) {
                "Forge E2E harness does not require the exact production mod version"
            }
        }
    }

    val verifyE2eHarnessArtifact = tasks.register("verifyE2eHarnessArtifact") {
        group = "verification"
        description = "Validates the remapped Forge 1.20.1 client E2E harness artifact."
        dependsOn(remapE2eHarnessJar)
        inputs.file(remapE2eHarnessJar.flatMap { it.archiveFile })

        doLast {
            validateE2eHarnessJar(remapE2eHarnessJar.get().archiveFile.get().asFile)
        }
    }

    val verifyE2eUnderTestIsolation = tasks.register("verifyE2eUnderTestIsolation") {
        group = "verification"
        description = "Proves that Forge E2E artifacts are marked and isolated from publication."
        dependsOn(remapE2eUnderTestJar, verifyE2eHarnessArtifact)
        inputs.file(remapE2eUnderTestJar.flatMap { it.archiveFile })
        inputs.file(remapE2eHarnessJar.flatMap { it.archiveFile })

        doLast {
            val productionFile = remapE2eUnderTestJar.get().archiveFile.get().asFile
            val harnessFile = remapE2eHarnessJar.get().archiveFile.get().asFile
            check(productionFile != harnessFile) {
                "The Forge production-under-test and E2E harness resolve to the same artifact"
            }
            ZipFile(productionFile).use { productionZip ->
                val productionEntries = productionZip.entries().asSequence()
                    .map { it.name }
                    .toSet()
                check(productionEntries.none {
                    it.startsWith("dev/theplumteam/etherology/e2e/")
                }) {
                    "Forge production-under-test JAR contains E2E harness classes"
                }
                val manifestEntry = requireNotNull(
                    productionZip.getEntry("META-INF/MANIFEST.MF"),
                ) {
                    "Forge production-under-test JAR has no manifest"
                }
                val manifestText = productionZip.getInputStream(manifestEntry)
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }
                check(manifestText.contains("Etherology-E2E-Only: true")) {
                    "Forge production-under-test JAR is not visibly marked E2E-only"
                }
                val packagedDataEntries = productionEntries
                    .filter { entry -> entry.startsWith("data/") && !entry.endsWith("/") }
                    .map { entry -> entry.removePrefix("data/") }
                    .toSet()
                check(packagedDataEntries == acceptedForgeDataEntries) {
                    "Forge production-under-test JAR packaged an unaccepted server-data set.\n" +
                        "Expected: ${acceptedForgeDataEntries.sorted()}\n" +
                        "Actual: ${packagedDataEntries.sorted()}"
                }
            }
        }
    }

    tasks.register("validateForgeChannelCurrentArtifactDiagnostic") {
        group = "verification"
        description =
            "Diagnoses whether current remapped artifacts still equal the frozen channel capture."
        dependsOn(verifyE2eUnderTestIsolation)
        inputs.files(forgeChannelEvidenceVerifier, forgeE2eProfileManifest)
        inputs.file(remapE2eUnderTestJar.flatMap { it.archiveFile })
        inputs.file(remapE2eHarnessJar.flatMap { it.archiveFile })
        inputs.dir(forgeChannelEvidenceRoot)
            .withPropertyName("forgeChannelEvidenceRoot")
            .optional()

        doLast {
            val productionFile = remapE2eUnderTestJar.get().archiveFile.get().asFile
            val harnessFile = remapE2eHarnessJar.get().archiveFile.get().asFile
            val missingConditions = missingForgeChannelEvidenceMilestone(
                productionFile,
                harnessFile,
            )
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion native ethereal-channel provenance is invalid:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

    tasks.register("buildE2eHarness") {
        group = "e2e"
        description = "Builds and validates the separate Forge 1.20.1 packaged E2E harness."
        dependsOn(e2eHarnessTestTask, verifyE2eHarnessArtifact, verifyE2eUnderTestIsolation)
    }
}
