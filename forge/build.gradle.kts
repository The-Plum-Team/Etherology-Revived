import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.architectury.plugin.ArchitectPluginExtension
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
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
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
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
val fabricJar = fabricProject.tasks.named<Jar>("jar")
val fabricShadowJar = fabricProject.tasks.named<ShadowJar>("shadowJar")
val fabricRemapJar = fabricProject.tasks.named<RemapJarTask>("remapJar")
val forgeJavaRoot = rootProject.file("forge/src/main/java")
val forgeResourcesRoot = rootProject.file("forge/src/main/resources")
val forgeMainClasses = layout.buildDirectory.dir("classes/java/main")
val forgeMainResources = layout.buildDirectory.dir("resources/main")
val canonicalGameEventTagEntries = setOf(
    "data/minecraft/tags/game_events/vibrations.json",
    "data/minecraft/tags/game_events/warden_can_listen.json",
)
val canonicalEnchantmentTagEntry =
    "data/minecraft/tags/enchantment/non_treasure.json"
val commonEtherSourceDataEntry = "etherology/ether_sources/default.json"
val commonAspectRegistryDataEntries = setOf(
    "etherology/etherology/aspects/etherology.json",
    "etherology/etherology/aspects/vanilla.json",
)
val canonicalMetalBlockDataEntries = setOf(
    "etherology/loot_tables/blocks/azel_block.json",
    "etherology/loot_tables/blocks/ethril_block.json",
    "etherology/loot_tables/blocks/ebony_block.json",
    "etherology/recipes/azel_block.json",
    "etherology/recipes/azel_ingot_from_azel_block.json",
    "etherology/recipes/ethril_block.json",
    "etherology/recipes/ethril_ingot_from_ethril_block.json",
    "etherology/recipes/ebony_block.json",
    "etherology/recipes/ebony_ingot_from_ebony_block.json",
    "minecraft/tags/blocks/mineable/pickaxe.json",
    "minecraft/tags/blocks/needs_iron_tool.json",
    "minecraft/tags/blocks/beacon_base_blocks.json",
)
val canonicalForestLanternDataEntries = setOf(
    "etherology/loot_tables/blocks/forest_lantern.json",
    "etherology/recipes/forest_lantern_crumb.json",
    "etherology/recipes/forest_lantern_crumb_from_campfire.json",
    "etherology/recipes/forest_lantern_crumb_from_smoking.json",
    "etherology/recipes/leather.json",
    "etherology/advancements/recipes/food/forest_lantern_crumb.json",
    "etherology/advancements/recipes/food/forest_lantern_crumb_from_campfire.json",
    "etherology/advancements/recipes/food/forest_lantern_crumb_from_smoking.json",
    "etherology/advancements/recipes/misc/leather.json",
    "etherology/tags/blocks/peach_logs.json",
    "minecraft/tags/blocks/mineable/hoe.json",
)
val canonicalAttrahiteBlockDataEntries = setOf(
    "etherology/loot_tables/blocks/attrahite.json",
    "etherology/loot_tables/blocks/attrahite_bricks.json",
    "etherology/loot_tables/blocks/attrahite_brick_slab.json",
    "etherology/loot_tables/blocks/attrahite_brick_stairs.json",
    "etherology/recipes/attrahite_brick.json",
    "etherology/recipes/attrahite_bricks.json",
    "etherology/recipes/attrahite_brick_slab.json",
    "etherology/recipes/attrahite_brick_slab_from_attrahite_bricks_stonecutting.json",
    "etherology/recipes/attrahite_brick_stairs.json",
    "etherology/recipes/attrahite_brick_stairs_from_attrahite_bricks_stonecutting.json",
    "etherology/recipes/raw_azel.json",
    "etherology/recipes/azel_ingot.json",
    "etherology/recipes/azel_ingot_from_blasting.json",
    "etherology/advancements/recipes/misc/attrahite_brick.json",
    "etherology/advancements/recipes/building_blocks/attrahite_bricks.json",
    "etherology/advancements/recipes/building_blocks/attrahite_brick_slab.json",
    "etherology/advancements/recipes/building_blocks/" +
        "attrahite_brick_slab_from_attrahite_bricks_stonecutting.json",
    "etherology/advancements/recipes/building_blocks/attrahite_brick_stairs.json",
    "etherology/advancements/recipes/building_blocks/" +
        "attrahite_brick_stairs_from_attrahite_bricks_stonecutting.json",
    "etherology/advancements/recipes/misc/raw_azel.json",
    "etherology/advancements/recipes/misc/azel_ingot.json",
    "etherology/advancements/recipes/misc/azel_ingot_from_blasting.json",
    "minecraft/tags/blocks/mineable/pickaxe.json",
    "minecraft/tags/blocks/needs_stone_tool.json",
    "minecraft/tags/blocks/slabs.json",
    "minecraft/tags/blocks/stairs.json",
    "minecraft/tags/items/slabs.json",
    "minecraft/tags/items/stairs.json",
)
val canonicalSlitheriteLootDataEntries = setOf(
    "chiseled_polished_slitherite",
    "chiseled_polished_slitherite_bricks",
    "cracked_polished_slitherite_bricks",
    "polished_slitherite",
    "polished_slitherite_brick_slab",
    "polished_slitherite_brick_stairs",
    "polished_slitherite_brick_wall",
    "polished_slitherite_bricks",
    "polished_slitherite_button",
    "polished_slitherite_pressure_plate",
    "polished_slitherite_slab",
    "polished_slitherite_stairs",
    "polished_slitherite_wall",
    "slitherite",
    "slitherite_slab",
    "slitherite_stairs",
    "slitherite_wall",
).map { id -> "etherology/loot_tables/blocks/$id.json" }.toSet()
val canonicalSlitheriteRecipeIds = setOf(
    "chiseled_polished_slitherite",
    "chiseled_polished_slitherite_bricks",
    "chiseled_polished_slitherite_bricks_from_polished_slitherite_bricks_stonecutting",
    "chiseled_polished_slitherite_from_polished_slitherite_stonecutting",
    "cracked_polished_slitherite_bricks",
    "polished_slitherite",
    "polished_slitherite_brick_slab",
    "polished_slitherite_brick_slab_from_polished_slitherite_bricks_stonecutting",
    "polished_slitherite_brick_stairs",
    "polished_slitherite_brick_stairs_from_polished_slitherite_bricks_stonecutting",
    "polished_slitherite_brick_wall",
    "polished_slitherite_brick_wall_from_polished_slitherite_bricks_stonecutting",
    "polished_slitherite_bricks",
    "polished_slitherite_bricks_from_polished_slitherite_stonecutting",
    "polished_slitherite_button",
    "polished_slitherite_from_slitherite_stonecutting",
    "polished_slitherite_pressure_plate",
    "polished_slitherite_slab",
    "polished_slitherite_slab_from_polished_slitherite_stonecutting",
    "polished_slitherite_stairs",
    "polished_slitherite_stairs_from_polished_slitherite_stonecutting",
    "polished_slitherite_wall",
    "polished_slitherite_wall_from_polished_slitherite_stonecutting",
    "slitherite_slab",
    "slitherite_slab_from_slitherite_stonecutting",
    "slitherite_stairs",
    "slitherite_stairs_from_slitherite_stonecutting",
    "slitherite_wall",
    "slitherite_wall_from_slitherite_stonecutting",
)
val canonicalSlitheriteRecipeDataEntries = canonicalSlitheriteRecipeIds
    .map { id -> "etherology/recipes/$id.json" }
    .toSet()
val canonicalSlitheriteAdvancementDataEntries = setOf(
    "building_blocks/chiseled_polished_slitherite",
    "building_blocks/chiseled_polished_slitherite_bricks",
    "building_blocks/chiseled_polished_slitherite_bricks_from_" +
        "polished_slitherite_bricks_stonecutting",
    "building_blocks/chiseled_polished_slitherite_from_" +
        "polished_slitherite_stonecutting",
    "building_blocks/cracked_polished_slitherite_bricks",
    "building_blocks/polished_slitherite",
    "building_blocks/polished_slitherite_brick_slab",
    "building_blocks/polished_slitherite_brick_slab_from_" +
        "polished_slitherite_bricks_stonecutting",
    "building_blocks/polished_slitherite_brick_stairs",
    "building_blocks/polished_slitherite_brick_stairs_from_" +
        "polished_slitherite_bricks_stonecutting",
    "building_blocks/polished_slitherite_bricks",
    "building_blocks/polished_slitherite_bricks_from_" +
        "polished_slitherite_stonecutting",
    "building_blocks/polished_slitherite_from_slitherite_stonecutting",
    "building_blocks/polished_slitherite_slab",
    "building_blocks/polished_slitherite_slab_from_" +
        "polished_slitherite_stonecutting",
    "building_blocks/polished_slitherite_stairs",
    "building_blocks/polished_slitherite_stairs_from_" +
        "polished_slitherite_stonecutting",
    "building_blocks/slitherite_slab",
    "building_blocks/slitherite_slab_from_slitherite_stonecutting",
    "building_blocks/slitherite_stairs",
    "building_blocks/slitherite_stairs_from_slitherite_stonecutting",
    "decorations/polished_slitherite_brick_wall",
    "decorations/polished_slitherite_brick_wall_from_" +
        "polished_slitherite_bricks_stonecutting",
    "decorations/polished_slitherite_wall",
    "decorations/polished_slitherite_wall_from_polished_slitherite_stonecutting",
    "decorations/slitherite_wall",
    "decorations/slitherite_wall_from_slitherite_stonecutting",
    "redstone/polished_slitherite_button",
    "redstone/polished_slitherite_pressure_plate",
).map { path -> "etherology/advancements/recipes/$path.json" }.toSet()
val canonicalSlitheriteTagDataEntries = setOf(
    "minecraft/tags/blocks/mineable/pickaxe.json",
    "minecraft/tags/blocks/needs_stone_tool.json",
    "minecraft/tags/blocks/slabs.json",
    "minecraft/tags/blocks/stairs.json",
    "minecraft/tags/blocks/stone_bricks.json",
    "minecraft/tags/blocks/stone_pressure_plates.json",
    "minecraft/tags/blocks/walls.json",
    "minecraft/tags/items/buttons.json",
    "minecraft/tags/items/slabs.json",
    "minecraft/tags/items/stairs.json",
    "minecraft/tags/items/walls.json",
)
val canonicalSlitheriteOwnedDataEntries =
    canonicalSlitheriteLootDataEntries +
        canonicalSlitheriteRecipeDataEntries +
        canonicalSlitheriteAdvancementDataEntries +
        canonicalSlitheriteTagDataEntries
val canonicalSlitheriteVanillaRelatedRecipeIds = setOf(
    "comparator",
    "repeater",
    "stonecutter",
)
val canonicalSlitheriteVanillaRelatedRecipeDataEntries =
    canonicalSlitheriteVanillaRelatedRecipeIds
        .map { id -> "etherology/recipes/$id.json" }
        .toSet()
val canonicalSlitheriteVanillaRelatedAdvancementDataEntries = setOf(
    "etherology/advancements/recipes/decorations/stonecutter.json",
    "etherology/advancements/recipes/redstone/comparator.json",
    "etherology/advancements/recipes/redstone/repeater.json",
)
val canonicalSlitheriteVanillaRelatedDataEntries =
    canonicalSlitheriteVanillaRelatedRecipeDataEntries +
        canonicalSlitheriteVanillaRelatedAdvancementDataEntries
val canonicalSlitheriteDataEntries =
    canonicalSlitheriteOwnedDataEntries + canonicalSlitheriteVanillaRelatedDataEntries
val canonicalWarpCounterDataEntries = setOf(
    "etherology/recipes/warp_counter.json",
    "etherology/advancements/recipes/tools/warp_counter.json",
)
val canonicalAlchemyRecipeDataEntries = setOf(
    "etherology/recipes/binder.json",
    "etherology/recipes/ebony_ingot.json",
    "etherology/recipes/glint_shard.json",
    "etherology/recipes/unadjusted_lens.json",
)
val canonicalPedestalDataEntries = setOf(
    "etherology/advancements/recipes/decorations/pedestal.json",
    "etherology/loot_tables/blocks/pedestal.json",
    "etherology/recipes/pedestal.json",
)
val acceptedForgeDirectDataEntries = setOf(
    "etherology/loot_tables/blocks/ethereal_storage.json",
) + canonicalMetalBlockDataEntries + canonicalForestLanternDataEntries +
    canonicalAttrahiteBlockDataEntries + canonicalSlitheriteDataEntries +
    canonicalWarpCounterDataEntries + canonicalPedestalDataEntries +
    canonicalAlchemyRecipeDataEntries +
    (canonicalGameEventTagEntries + canonicalEnchantmentTagEntry)
    .map { entry -> entry.removePrefix("data/") }
val acceptedForgeArtifactDataEntries =
    acceptedForgeDirectDataEntries + commonEtherSourceDataEntry +
        commonAspectRegistryDataEntries
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
val sharedGameEventRegistryClassEntry =
    "ru/feytox/etherology/registry/misc/SharedGameEvents.class"
val sharedLootConditionRegistryClassEntry =
    "ru/feytox/etherology/registry/misc/SharedLootConditions.class"
val sharedEnchantmentRegistryClassEntry =
    "ru/feytox/etherology/registry/misc/SharedEnchantments.class"
val pealEnchantmentClassEntry =
    "ru/feytox/etherology/registry/misc/PealEnchantment.class"
val reflectionEnchantmentClassEntry =
    "ru/feytox/etherology/registry/misc/ReflectionEnchantment.class"
val canonicalFabricEnchantmentPolicyClassEntry =
    "ru/feytox/etherology/registry/misc/EtherEnchantments.class"
val randomChanceWithFortuneConditionClassEntry =
    "ru/feytox/etherology/util/misc/RandomChanceWithFortuneCondition.class"
val randomChanceWithFortuneConditionSerializerClassEntry =
    "ru/feytox/etherology/util/misc/RandomChanceWithFortuneConditionSerializer.class"
val resourceReloadersClassEntry =
    "ru/feytox/etherology/registry/misc/ResourceReloaders.class"
val etherSourceLoaderClassEntry =
    "ru/feytox/etherology/data/ethersource/EtherSourceLoader.class"
val etherSourcesClassEntry =
    "ru/feytox/etherology/data/ethersource/EtherSources.class"
val etherSourcesDeserializerClassEntry =
    "ru/feytox/etherology/data/ethersource/EtherSourcesDeserializer.class"
val canonicalFabricSoundRegistryClassEntry =
    "ru/feytox/etherology/registry/misc/EtherSounds.class"
val canonicalFabricGameEventRegistryClassEntry =
    "ru/feytox/etherology/registry/misc/EventsRegistry.class"
val canonicalFabricLootConditionRegistryClassEntry =
    "ru/feytox/etherology/registry/misc/LootConditions.class"
val canonicalFabricInitializerClassEntry =
    "ru/feytox/etherology/Etherology.class"
val fabricGameEventHooksClassEntry =
    "ru/feytox/etherology/FabricGameEventHooks.class"
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
val canonicalGameEventTagFiles = canonicalGameEventTagEntries.associateWith { entry ->
    rootProject.file("src/main/generated/$entry")
}
val canonicalEnchantmentTagFile =
    rootProject.file("src/main/generated/$canonicalEnchantmentTagEntry")
val legacyFabricEnchantmentConcreteOwners = listOf(
    rootProject.file(
        "src/main/java/ru/feytox/etherology/registry/misc/PealEnchantment.java",
    ),
    rootProject.file(
        "src/main/java/ru/feytox/etherology/registry/misc/ReflectionEnchantment.java",
    ),
)
val canonicalAttrahiteLootTable =
    rootProject.file("src/main/generated/data/etherology/loot_tables/blocks/attrahite.json")
val canonicalEtherSourceDefault = rootProject.file(
    "common/src/main/resources/data/etherology/ether_sources/default.json",
)
val legacyFabricEtherSourceOwners = listOf(
    rootProject.file(
        "src/main/java/ru/feytox/etherology/registry/misc/ResourceReloaders.java",
    ),
    rootProject.file(
        "src/main/java/ru/feytox/etherology/data/ethersource/EtherSourceLoader.java",
    ),
    rootProject.file(
        "src/main/java/ru/feytox/etherology/data/ethersource/EtherSources.java",
    ),
    rootProject.file(
        "src/main/java/ru/feytox/etherology/data/ethersource/EtherSourcesDeserializer.java",
    ),
    rootProject.file("src/main/resources/data/etherology/ether_sources/default.json"),
)
val expectedEtherSourceValues = linkedMapOf(
    "etherology:primoshard_keta" to 4,
    "etherology:primoshard_rella" to 4,
    "etherology:primoshard_clos" to 4,
    "etherology:primoshard_via" to 4,
    "minecraft:redstone" to 2,
    "minecraft:glowstone_dust" to 1,
    "minecraft:lapis_lazuli" to 1,
    "minecraft:quartz" to 1,
    "minecraft:ender_pearl" to 4,
    "minecraft:ender_eye" to 6,
    "minecraft:blaze_powder" to 2,
    "minecraft:ancient_debris" to 4,
    "minecraft:chorus_fruit" to 2,
    "minecraft:experience_bottle" to 8,
    "minecraft:echo_shard" to 12,
    "minecraft:sculk" to 12,
    "minecraft:crying_obsidian" to 6,
    "minecraft:magma_cream" to 2,
    "minecraft:heart_of_the_sea" to 12,
    "minecraft:gunpowder" to 1,
    "minecraft:prismarine_crystals" to 1,
    "minecraft:ghast_tear" to 4,
    "minecraft:honeycomb" to 1,
)
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
val forgeRegistryFoundationServerEvidenceRoot =
    rootProject.file("docs/evidence/forge-1.20.1")
val forgeRegistryFoundationServerEvidenceArchive =
    forgeRegistryFoundationServerEvidenceRoot.resolve(
        "registry-foundation-server-v4",
    )
val forgeRegistryFoundationServerEvidenceVerifier =
    rootProject.file("scripts/e2e/forge_server_evidence.py")
val forgeRegistryFoundationServerEvidenceTest =
    rootProject.file("scripts/e2e/test_forge_server_evidence.py")
val forgeEtherSourceReloadServerEvidenceArchive =
    forgeRegistryFoundationServerEvidenceRoot.resolve(
        "ether-source-reload-server-v6",
    )
val forgeEtherSourceReloadServerEvidenceVerifier =
    rootProject.file("scripts/e2e/forge_server_reload_evidence_v6.py")
val forgeEtherSourceReloadServerEvidenceTest =
    rootProject.file("scripts/e2e/test_forge_server_reload_evidence_v6.py")
val forgeServerContractV6 = rootProject.file("scripts/e2e/forge_server_contract_v6.py")
val forgeServerProfileSnapshotV6 =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile-v6.json")
val forgeEnchantmentRegistryServerEvidenceArchive =
    forgeRegistryFoundationServerEvidenceRoot.resolve(
        "enchantment-registry-server-v7",
    )
val forgeEnchantmentRegistryServerEvidenceVerifier =
    rootProject.file("scripts/e2e/forge_server_enchantment_evidence_v7.py")
val forgeEnchantmentRegistryServerEvidenceTest =
    rootProject.file("scripts/e2e/test_forge_server_enchantment_evidence_v7.py")
val forgeServerContractV7 = rootProject.file("scripts/e2e/forge_server_contract_v7.py")
val forgeServerProfileSnapshotV7 =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile-v7.json")
val forgeParticleRegistryServerEvidenceArchive =
    forgeRegistryFoundationServerEvidenceRoot.resolve(
        "particle-registry-server-v10",
    )
val forgeParticleRegistryServerEvidenceVerifier =
    rootProject.file("scripts/e2e/forge_server_particle_evidence_v10.py")
val forgeParticleRegistryServerEvidenceTest =
    rootProject.file("scripts/e2e/test_forge_server_particle_evidence_v10.py")
val forgeServerContractV10 = rootProject.file("scripts/e2e/forge_server_contract_v10.py")
val forgeServerProfileSnapshotV10 =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile-v10.json")
val forgeMaterialItemRegistryServerEvidenceArchive =
    forgeRegistryFoundationServerEvidenceRoot.resolve(
        "material-item-registry-server-v11",
    )
val forgeMaterialItemRegistryServerEvidenceVerifier =
    rootProject.file("scripts/e2e/forge_server_material_item_evidence_v11.py")
val forgeMaterialItemRegistryServerEvidenceTest =
    rootProject.file("scripts/e2e/test_forge_server_material_item_evidence_v11.py")
val forgeServerContractV11 = rootProject.file("scripts/e2e/forge_server_contract_v11.py")
val forgeServerProfileSnapshotV11 =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile-v11.json")
val forgeMetalBlockRegistryServerEvidenceVerifierV12 =
    rootProject.file("scripts/e2e/forge_server_metal_block_evidence_v12.py")
val forgeMetalBlockRegistryServerEvidenceTestV12 =
    rootProject.file("scripts/e2e/test_forge_server_metal_block_evidence_v12.py")
val forgeServerContractV12 = rootProject.file("scripts/e2e/forge_server_contract_v12.py")
val forgeServerProfileSnapshotV12 =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile-v12.json")
val forgeMetalBlockRegistryServerEvidenceArchive =
    forgeRegistryFoundationServerEvidenceRoot.resolve(
        "metal-block-registry-server-v13",
    )
val forgeMetalBlockRegistryServerEvidenceVerifier =
    rootProject.file("scripts/e2e/forge_server_metal_block_evidence_v13.py")
val forgeMetalBlockRegistryServerEvidenceTest =
    rootProject.file("scripts/e2e/test_forge_server_metal_block_evidence_v13.py")
val forgeServerContractV13 = rootProject.file("scripts/e2e/forge_server_contract_v13.py")
val forgeServerProfileSnapshotV13 =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile-v13.json")
val forgeFoodItemRegistryServerEvidenceArchive =
    forgeRegistryFoundationServerEvidenceRoot.resolve(
        "food-item-registry-server-v14",
    )
val forgeFoodItemRegistryServerEvidenceVerifier =
    rootProject.file("scripts/e2e/forge_server_food_item_evidence_v14.py")
val forgeFoodItemRegistryServerEvidenceTest =
    rootProject.file("scripts/e2e/test_forge_server_food_item_evidence_v14.py")
val forgeServerContractV14 = rootProject.file("scripts/e2e/forge_server_contract_v14.py")
val forgeServerProfileSnapshotV14 =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile-v14.json")
val forgeForestLanternServerEvidenceVerifierV15 =
    rootProject.file("scripts/e2e/forge_server_forest_lantern_evidence_v15.py")
val forgeForestLanternServerEvidenceTestV15 =
    rootProject.file("scripts/e2e/test_forge_server_forest_lantern_evidence_v15.py")
val forgeServerContractV15 = rootProject.file("scripts/e2e/forge_server_contract_v15.py")
val forgeServerProfileSnapshotV15 =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile-v15.json")
val forgeForestLanternServerEvidenceArchive =
    forgeRegistryFoundationServerEvidenceRoot.resolve(
        "forest-lantern-server-v16",
    )
val forgeForestLanternServerEvidenceVerifier =
    rootProject.file("scripts/e2e/forge_server_forest_lantern_evidence_v16.py")
val forgeForestLanternServerEvidenceTest =
    rootProject.file("scripts/e2e/test_forge_server_forest_lantern_evidence_v16.py")
val forgeServerContractV16 = rootProject.file("scripts/e2e/forge_server_contract_v16.py")
val forgeServerProfileSnapshotV16 =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile-v16.json")
val forgeAttrahiteBlockRegistryServerEvidenceArchive =
    forgeRegistryFoundationServerEvidenceRoot.resolve(
        "attrahite-block-registry-server-v17",
    )
val forgeAttrahiteBlockRegistryServerEvidenceVerifier =
    rootProject.file("scripts/e2e/forge_server_attrahite_evidence_v17.py")
val forgeAttrahiteBlockRegistryServerEvidenceTest =
    rootProject.file("scripts/e2e/test_forge_server_attrahite_evidence_v17.py")
val forgeServerContractV17 = rootProject.file("scripts/e2e/forge_server_contract_v17.py")
val forgeServerProfileSnapshotV17 =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile-v17.json")
val forgeAttrahiteBlockRegistryServerEvidenceArchiveV18 =
    forgeRegistryFoundationServerEvidenceRoot.resolve(
        "attrahite-block-registry-server-v18",
    )
val forgeAttrahiteBlockRegistryServerEvidenceVerifierV18 =
    rootProject.file("scripts/e2e/forge_server_attrahite_evidence_v18.py")
val forgeAttrahiteBlockRegistryServerEvidenceTestV18 =
    rootProject.file("scripts/e2e/test_forge_server_attrahite_evidence_v18.py")
val forgeServerContractV18 = rootProject.file("scripts/e2e/forge_server_contract_v18.py")
val forgeServerProfileSnapshotV18 =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile-v18.json")
val forgeAttrahiteBlockRegistryServerEvidenceArchiveV19 =
    forgeRegistryFoundationServerEvidenceRoot.resolve(
        "attrahite-block-registry-server-v19",
    )
val forgeAttrahiteBlockRegistryServerEvidenceVerifierV19 =
    rootProject.file("scripts/e2e/forge_server_attrahite_evidence_v19.py")
val forgeAttrahiteBlockRegistryServerEvidenceTestV19 =
    rootProject.file("scripts/e2e/test_forge_server_attrahite_evidence_v19.py")
val forgeServerContractV19 = rootProject.file("scripts/e2e/forge_server_contract_v19.py")
val forgeServerProfileSnapshotV19 =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile-v19.json")
val forgeServerContractV20 = rootProject.file("scripts/e2e/forge_server_contract_v20.py")
val forgeServerProfileSnapshotV20 =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile-v20.json")
val forgeSlitheriteBlockRegistryServerEvidenceArchiveV20 =
    forgeRegistryFoundationServerEvidenceRoot.resolve(
        "slitherite-block-registry-server-v20",
    )
val forgeSlitheriteBlockRegistryServerEvidenceVerifierV20 =
    rootProject.file("scripts/e2e/forge_server_slitherite_evidence_v20.py")
val forgeSlitheriteBlockRegistryServerEvidenceTestV20 =
    rootProject.file("scripts/e2e/test_forge_server_slitherite_evidence_v20.py")
val forgeServerNativeRunPostponedReason =
    "Forge dedicated-server Slitherite v20 is postponed until all five related " +
        "recipes are present with their real pedestal, alchemy, and lens dependencies"
val forgeRegistryFoundationServerRunner = rootProject.file("scripts/e2e/forge_server.py")
val forgeRegistryFoundationServerRunnerTest =
    rootProject.file("scripts/e2e/test_forge_server.py")
val forgeRegistryFoundationServerRunnerTestV20 =
    rootProject.file("scripts/e2e/test_forge_server_contract_v20.py")
val forgeRegistryFoundationServerProfileManifest =
    rootProject.file("scripts/e2e/forge-server-1.20.1-profile.json")
val forgeRegistryFoundationServerProbeSource = rootProject.file(
    "e2e-harness/forge-server/1.20.1/src/main/java/" +
        "dev/theplumteam/etherology/e2e/server/RegistryFoundationServerProbe.java",
)
val forgeRegistryFoundationServerMemoryHandoffSource = rootProject.file(
    "e2e-harness/forge-server/1.20.1/src/main/java/" +
        "dev/theplumteam/etherology/e2e/server/ServerProbeMemoryHandoff.java",
)
val forgeE2eProfileManifest = rootProject.file("scripts/e2e/forge-1.20.1-profile.json")
val forgeChannelProfileSnapshotV11 =
    rootProject.file("scripts/e2e/forge-1.20.1-profile-v11.json")
val forgeForestLanternEvidenceVerifier =
    rootProject.file("scripts/e2e/forge_forest_lantern_evidence.py")
val forgeForestLanternEvidenceTest =
    rootProject.file("scripts/e2e/test_forge_forest_lantern_evidence.py")
val forgeForestLanternProfileSnapshotV12 =
    rootProject.file("scripts/e2e/forge-1.20.1-profile-v12.json")
val forgeForestLanternProfileSnapshotV13 =
    rootProject.file("scripts/e2e/forge-1.20.1-profile-v13.json")
val forgeForestLanternClientEvidenceArchive =
    forgeChannelEvidenceRoot.resolve("forest-lantern-v13")
val forgeAttrahiteEvidenceVerifierV14 =
    rootProject.file("scripts/e2e/forge_attrahite_evidence_v14.py")
val forgeAttrahiteEvidenceTestV14 =
    rootProject.file("scripts/e2e/test_forge_attrahite_evidence_v14.py")
val forgeAttrahiteProfileSnapshotV14 =
    rootProject.file("scripts/e2e/forge-1.20.1-profile-v14.json")
val forgeAttrahiteEvidenceVerifierV15 =
    rootProject.file("scripts/e2e/forge_attrahite_evidence_v15.py")
val forgeAttrahiteEvidenceTestV15 =
    rootProject.file("scripts/e2e/test_forge_attrahite_evidence_v15.py")
val forgeAttrahiteProfileSnapshotV15 =
    rootProject.file("scripts/e2e/forge-1.20.1-profile-v15.json")
val forgeAttrahiteEvidenceVerifierV16 =
    rootProject.file("scripts/e2e/forge_attrahite_evidence_v16.py")
val forgeAttrahiteEvidenceTestV16 =
    rootProject.file("scripts/e2e/test_forge_attrahite_evidence_v16.py")
val forgeAttrahiteProfileSnapshotV16 =
    rootProject.file("scripts/e2e/forge-1.20.1-profile-v16.json")
val forgeAttrahiteEvidenceVerifier =
    rootProject.file("scripts/e2e/forge_attrahite_evidence_v17.py")
val forgeAttrahiteEvidenceTest =
    rootProject.file("scripts/e2e/test_forge_attrahite_evidence_v17.py")
val forgeAttrahiteProfileSnapshotV17 =
    rootProject.file("scripts/e2e/forge-1.20.1-profile-v17.json")
val forgeAttrahiteClientEvidenceArchive =
    forgeChannelEvidenceRoot.resolve("attrahite-block-registry-v17")
val forgeAttrahiteHarnessSize = 244324L
val forgeAttrahiteHarnessSha256 =
    "9921ec314c9aa411ca1c2f9632faa1a9e05a60b62589d12a416c228bc85170b8"
val forgeSlitheriteEvidenceVerifierV18 =
    rootProject.file("scripts/e2e/forge_slitherite_evidence_v18.py")
val forgeSlitheriteEvidenceTestV18 =
    rootProject.file("scripts/e2e/test_forge_slitherite_evidence_v18.py")
val forgeSlitheriteRunContractV18 =
    rootProject.file("scripts/e2e/forge_slitherite_run_contract_v18.py")
val forgeSlitheriteProfileSnapshotV18 =
    rootProject.file("scripts/e2e/forge-1.20.1-profile-v18.json")
val forgeSlitheriteClientEvidenceArchive =
    forgeChannelEvidenceRoot.resolve("slitherite-block-registry-v19")
val forgeSlitheriteArchiveManifestSize = 2500L
val forgeSlitheriteArchiveManifestSha256 =
    "05e6441d89f4333b503277be59f7385303954ae2586d4d5759ea84ca01209e2e"
val forgeSlitheriteEvidenceVerifier =
    rootProject.file("scripts/e2e/forge_slitherite_evidence_v19.py")
val forgeSlitheriteEvidenceTest =
    rootProject.file("scripts/e2e/test_forge_slitherite_evidence_v19.py")
val forgeSlitheriteRunContractV19 =
    rootProject.file("scripts/e2e/forge_slitherite_run_contract_v19.py")
val forgeSlitheriteProfileSnapshotV19 =
    rootProject.file("scripts/e2e/forge-1.20.1-profile-v19.json")
val slitheriteClientEvidenceContract =
    rootProject.file("scripts/e2e/slitherite_client_evidence_contract_v1.py")
val slitheriteClientEvidenceTestSupport =
    rootProject.file("scripts/e2e/slitherite_client_evidence_test_support_v1.py")
val originalSlitheriteEvidenceVerifier =
    rootProject.file("scripts/baseline/original_slitherite_evidence_v10.py")
val forgeMixinConfig = forgeResourcesRoot.resolve("etherology.forge.mixins.json")

apply(plugin = "dev.architectury.loom")
apply(plugin = "architectury-plugin")
apply(plugin = "com.gradleup.shadow")

fun Project.versionProperty(base: String): String =
    rootProject.property("${base}_${minecraftVersion.replace(".", "_")}") as String

enum class ServerProbeSafetyInterlockFailureKind {
    SEALED_ARCHIVE,
    OWNED_PATH_LINKED,
    RUN_TOKEN,
    RUN_LOCK_MISSING_OR_LINKED,
    RUN_LOCK_INVALID,
    RUN_ATTEMPT_MISSING_OR_LINKED,
    RUN_ATTEMPT_INVALID,
    PROFILE_MARKER_MISSING_OR_LINKED,
    PROFILE_MARKER_MALFORMED,
    PROFILE_MARKER_MISMATCH,
    EVIDENCE_DIRECTORY_INVALID,
}

data class ServerProbeSafetyInterlockFailure(
    val kind: ServerProbeSafetyInterlockFailureKind,
    val message: String,
)

data class ServerProbeSafetyInterlockSpec(
    val sealedArchive: File,
    val ownedPathAnchor: File,
    val runToken: String?,
    val runLock: File,
    val runAttempt: File,
    val profileMarker: File,
    val profileId: String,
    val managedBy: String,
    val taskPath: String,
    val scenarioId: String,
    val evidenceDirectories: List<File>,
)

fun hasServerProbeSymlinkParentComponent(path: File, anchor: File): Boolean {
    val normalizedPath = path.toPath().toAbsolutePath().normalize()
    val normalizedAnchor = anchor.toPath().toAbsolutePath().normalize()
    if (!normalizedPath.startsWith(normalizedAnchor)) {
        return true
    }

    var currentPath = normalizedAnchor
    if (Files.isSymbolicLink(currentPath)) {
        return true
    }
    val relativePath = normalizedAnchor.relativize(normalizedPath)
    for (index in 0 until maxOf(0, relativePath.nameCount - 1)) {
        currentPath = currentPath.resolve(relativePath.getName(index))
        if (Files.isSymbolicLink(currentPath)) {
            return true
        }
    }
    return false
}

fun serverProbeSafetyInterlockFailure(
    spec: ServerProbeSafetyInterlockSpec,
): ServerProbeSafetyInterlockFailure? {
    if (spec.sealedArchive.exists()
        || Files.isSymbolicLink(spec.sealedArchive.toPath())
    ) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.SEALED_ARCHIVE,
            "The dedicated-server probe profile already has sealed evidence and is consumed: " +
            spec.sealedArchive.absolutePath,
        )
    }

    val ownedPaths = listOf(
        spec.runLock,
        spec.runAttempt,
        spec.profileMarker,
        *spec.evidenceDirectories.toTypedArray(),
    )
    if (ownedPaths.any { path ->
            hasServerProbeSymlinkParentComponent(path, spec.ownedPathAnchor)
        }
    ) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.OWNED_PATH_LINKED,
            "The dedicated-server probe owned path resolves through a symlink",
        )
    }

    val runToken = spec.runToken
    if (runToken == null || !Regex("[0-9a-f]{64}").matches(runToken)) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.RUN_TOKEN,
            "The dedicated-server probe runner safety-interlock token is missing or malformed",
        )
    }
    if (!spec.runLock.isFile || Files.isSymbolicLink(spec.runLock.toPath())) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.RUN_LOCK_MISSING_OR_LINKED,
            "The dedicated-server probe runner safety-interlock lock is missing or linked",
        )
    }
    val runLockLines = try {
        spec.runLock.readLines(StandardCharsets.UTF_8)
    } catch (_: Exception) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.RUN_LOCK_INVALID,
            "The dedicated-server probe runner safety-interlock lock is invalid",
        )
    }
    if (runLockLines.size != 2
        || !Regex("pid=[1-9][0-9]*").matches(runLockLines[0])
        || runLockLines[1] != "token=$runToken"
    ) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.RUN_LOCK_INVALID,
            "The dedicated-server probe runner safety-interlock lock is invalid",
        )
    }

    if (!spec.runAttempt.isFile
        || Files.isSymbolicLink(spec.runAttempt.toPath())
    ) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.RUN_ATTEMPT_MISSING_OR_LINKED,
            "The dedicated-server probe launch-attempt marker is missing or linked",
        )
    }
    val runAttemptLines = try {
        spec.runAttempt.readLines(StandardCharsets.UTF_8)
    } catch (_: Exception) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.RUN_ATTEMPT_INVALID,
            "The dedicated-server probe launch-attempt marker is invalid",
        )
    }
    if (runAttemptLines != listOf(
            "profile_id=${spec.profileId}",
            "scenario=${spec.scenarioId}",
            runLockLines[0],
        )
    ) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.RUN_ATTEMPT_INVALID,
            "The dedicated-server probe launch-attempt marker is invalid",
        )
    }

    if (!spec.profileMarker.isFile
        || Files.isSymbolicLink(spec.profileMarker.toPath())
    ) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.PROFILE_MARKER_MISSING_OR_LINKED,
            "The dedicated-server probe profile marker is missing or linked",
        )
    }
    val runtimeMarker = try {
        JsonSlurper().parse(spec.profileMarker)
    } catch (_: Exception) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.PROFILE_MARKER_MALFORMED,
            "The dedicated-server probe runtime marker is malformed",
        )
    }
    if (runtimeMarker !is Map<*, *>) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.PROFILE_MARKER_MALFORMED,
            "The dedicated-server probe runtime marker is malformed",
        )
    }
    val markerLaunch = runtimeMarker["launch"]
    if (runtimeMarker["schema"] != 1
        || runtimeMarker["profile_id"] != spec.profileId
        || runtimeMarker["managed_by"] != spec.managedBy
        || markerLaunch != mapOf(
            "task_path" to spec.taskPath,
            "scenario" to spec.scenarioId,
        )
    ) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.PROFILE_MARKER_MISMATCH,
            "The dedicated-server probe runtime marker does not match the active profile",
        )
    }

    if (spec.evidenceDirectories.any { directory ->
        !directory.isDirectory
            || Files.isSymbolicLink(directory.toPath())
            || directory.listFiles()?.isEmpty() != true
    }) {
        return ServerProbeSafetyInterlockFailure(
            ServerProbeSafetyInterlockFailureKind.EVIDENCE_DIRECTORY_INVALID,
            "The dedicated-server probe evidence directory is not pristine",
        )
    }
    return null
}

fun deleteServerProbeSafetyInterlockFixture(
    fixtureRoot: Path,
    allowedTemporaryRoot: Path,
) {
    val normalizedFixtureRoot = fixtureRoot.toAbsolutePath().normalize()
    val normalizedTemporaryRoot = allowedTemporaryRoot.toAbsolutePath().normalize()
    check(
        normalizedFixtureRoot != normalizedTemporaryRoot
            && normalizedFixtureRoot.startsWith(normalizedTemporaryRoot),
    ) {
        "Refusing to clean a server-probe safety-interlock fixture outside build/tmp: " +
            normalizedFixtureRoot
    }
    if (!Files.exists(normalizedFixtureRoot, LinkOption.NOFOLLOW_LINKS)) {
        return
    }
    Files.walk(normalizedFixtureRoot).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach { path ->
            Files.delete(path)
        }
    }
}

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
            include(
                "data/etherology/loot_tables/blocks/azel_block.json",
                "data/etherology/loot_tables/blocks/ethril_block.json",
                "data/etherology/loot_tables/blocks/ebony_block.json",
                "data/etherology/recipes/azel_block.json",
                "data/etherology/recipes/azel_ingot_from_azel_block.json",
                "data/etherology/recipes/ethril_block.json",
                "data/etherology/recipes/ethril_ingot_from_ethril_block.json",
                "data/etherology/recipes/ebony_block.json",
                "data/etherology/recipes/ebony_ingot_from_ebony_block.json",
                "data/minecraft/tags/blocks/mineable/pickaxe.json",
                "data/minecraft/tags/blocks/needs_iron_tool.json",
                "data/minecraft/tags/blocks/beacon_base_blocks.json",
            )
            canonicalForestLanternDataEntries.forEach { entry ->
                include("data/$entry")
            }
            canonicalAttrahiteBlockDataEntries.forEach { entry ->
                include("data/$entry")
            }
            canonicalSlitheriteDataEntries.forEach { entry ->
                include("data/$entry")
            }
            canonicalWarpCounterDataEntries.forEach { entry ->
                include("data/$entry")
            }
            canonicalPedestalDataEntries.forEach { entry ->
                include("data/$entry")
            }
            canonicalAlchemyRecipeDataEntries.forEach { entry ->
                include("data/$entry")
            }
            canonicalGameEventTagEntries.forEach { entry -> include(entry) }
            include(canonicalEnchantmentTagEntry)
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
        check(packagedDataEntries == acceptedForgeDirectDataEntries) {
            "Forge $minecraftVersion packaged an unaccepted server-data set.\n" +
                "Expected: ${acceptedForgeDirectDataEntries.sorted()}\n" +
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

fun readClassMethodReferences(classBytes: ByteArray): Set<String> {
    val utf8Constants = mutableMapOf<Int, String>()
    val classNameIndexes = mutableMapOf<Int, Int>()
    val nameAndTypeIndexes = mutableMapOf<Int, Pair<Int, Int>>()
    val methodReferences = mutableListOf<Pair<Int, Int>>()
    DataInputStream(ByteArrayInputStream(classBytes)).use { input ->
        check(input.readInt() == 0xCAFEBABE.toInt()) { "Invalid Java class magic" }
        input.readUnsignedShort()
        input.readUnsignedShort()
        val constantPoolCount = input.readUnsignedShort()
        var constantPoolIndex = 1
        while (constantPoolIndex < constantPoolCount) {
            when (val tag = input.readUnsignedByte()) {
                1 -> utf8Constants[constantPoolIndex] = input.readUTF()
                3, 4 -> input.skipBytes(4)
                5, 6 -> {
                    input.skipBytes(8)
                    constantPoolIndex++
                }
                7 -> classNameIndexes[constantPoolIndex] = input.readUnsignedShort()
                8, 16, 19, 20 -> input.skipBytes(2)
                9, 17, 18 -> input.skipBytes(4)
                10, 11 -> methodReferences.add(
                    input.readUnsignedShort() to input.readUnsignedShort(),
                )
                12 -> nameAndTypeIndexes[constantPoolIndex] =
                    input.readUnsignedShort() to input.readUnsignedShort()
                15 -> input.skipBytes(3)
                else -> error("Unsupported Java class constant-pool tag $tag")
            }
            constantPoolIndex++
        }
    }
    return methodReferences.mapTo(mutableSetOf()) { (classIndex, nameAndTypeIndex) ->
        val classNameIndex = requireNotNull(classNameIndexes[classIndex]) {
            "Class method reference has no owner"
        }
        val (methodNameIndex, descriptorIndex) =
            requireNotNull(nameAndTypeIndexes[nameAndTypeIndex]) {
                "Class method reference has no name and type"
            }
        val owner = requireNotNull(utf8Constants[classNameIndex]) {
            "Class method-reference owner has no UTF-8 constant"
        }
        val methodName = requireNotNull(utf8Constants[methodNameIndex]) {
            "Class method reference has no UTF-8 name"
        }
        val descriptor = requireNotNull(utf8Constants[descriptorIndex]) {
            "Class method reference has no UTF-8 descriptor"
        }
        "$owner.$methodName:$descriptor"
    }
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

fun missingForgeGameEventRegistryMilestone(
    commonJarFile: File,
    fabricTransformedCommonJarFile: File,
    forgeTransformedCommonJarFile: File,
    fabricProductionJarFile: File,
    forgeShadowJarFile: File,
): List<String> {
    val missingConditions = mutableListOf<String>()
    val resonanceId = "etherology_resonance"

    canonicalGameEventTagFiles.forEach { (entryPath, sourceFile) ->
        if (!sourceFile.isFile || Files.isSymbolicLink(sourceFile.toPath())) {
            missingConditions.add("canonical game-event tag is missing or linked: $entryPath")
        }
    }

    fun inspectArtifact(
        artifact: File,
        description: String,
        requireTagResources: Boolean,
        requireFabricHook: Boolean,
    ) {
        if (!artifact.isFile || Files.isSymbolicLink(artifact.toPath())) {
            missingConditions.add("$description is missing or linked")
            return
        }

        try {
            ZipFile(artifact).use { zip ->
                val entryNames = zip.entries().asSequence().map { entry -> entry.name }.toList()
                val classEntryNames = entryNames.filter { entry -> entry.endsWith(".class") }
                val sharedOwnerCount = entryNames.count(sharedGameEventRegistryClassEntry::equals)
                if (sharedOwnerCount != 1) {
                    missingConditions.add(
                        "$description must contain one shared game-event registry, " +
                            "found $sharedOwnerCount",
                    )
                } else {
                    val sharedEntry = requireNotNull(
                        zip.getEntry(sharedGameEventRegistryClassEntry),
                    )
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(sharedEntry).use { input -> input.readAllBytes() },
                    )
                    val requiredConstants = setOf(
                        resonanceId,
                        "ru/feytox/etherology/registry/SharedDeferredRegister",
                        "register",
                        "attach",
                    )
                    val missingConstants = requiredConstants - constants
                    if (missingConstants.isNotEmpty()) {
                        missingConditions.add(
                            "$description shared game-event owner lost its deferred contract: " +
                                missingConstants.sorted(),
                        )
                    }
                }

                val legacyOwnerCount = entryNames.count(
                    canonicalFabricGameEventRegistryClassEntry::equals,
                )
                if (legacyOwnerCount != 0) {
                    missingConditions.add(
                        "$description contains $legacyOwnerCount legacy EventsRegistry owner(s)",
                    )
                }

                val fabricHookCount = entryNames.count(fabricGameEventHooksClassEntry::equals)
                val expectedFabricHookCount = if (requireFabricHook) 1 else 0
                if (fabricHookCount != expectedFabricHookCount) {
                    missingConditions.add(
                        "$description Fabric game-event hook count changed: " +
                            "expected=$expectedFabricHookCount, actual=$fabricHookCount",
                    )
                }

                val resonanceRegistrationOwners = mutableSetOf<String>()
                val fabricFrequencyApiOwners = mutableSetOf<String>()
                val directFrequencyMutationOwners = mutableSetOf<String>()
                classEntryNames.forEach { classEntryName ->
                    val classEntry = requireNotNull(zip.getEntry(classEntryName))
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(classEntry).use { input -> input.readAllBytes() },
                    )
                    val referencesRegistrationOwner =
                        "ru/feytox/etherology/registry/SharedDeferredRegister" in constants
                            || "dev/architectury/registry/registries/DeferredRegister" in constants
                            || "net/minecraft/registry/Registry" in constants
                    if (resonanceId in constants
                        && referencesRegistrationOwner
                        && "register" in constants
                    ) {
                        resonanceRegistrationOwners.add(classEntryName)
                    }
                    if ("net/fabricmc/fabric/api/registry/SculkSensorFrequencyRegistry"
                        in constants
                    ) {
                        fabricFrequencyApiOwners.add(classEntryName)
                    }
                    if ("net/minecraft/world/event/Vibrations" in constants
                        && "FREQUENCIES" in constants
                    ) {
                        directFrequencyMutationOwners.add(classEntryName)
                    }
                }
                if (resonanceRegistrationOwners != setOf(sharedGameEventRegistryClassEntry)) {
                    missingConditions.add(
                        "$description resonance registration owners changed: " +
                            resonanceRegistrationOwners.sorted(),
                    )
                }
                val expectedFrequencyOwners = if (requireFabricHook) {
                    setOf(fabricGameEventHooksClassEntry)
                } else {
                    emptySet()
                }
                if (fabricFrequencyApiOwners != expectedFrequencyOwners) {
                    missingConditions.add(
                        "$description Fabric sculk-frequency owners changed: " +
                        fabricFrequencyApiOwners.sorted(),
                    )
                }
                if (directFrequencyMutationOwners.isNotEmpty()) {
                    missingConditions.add(
                        "$description directly mutates vanilla vibration frequencies: " +
                            directFrequencyMutationOwners.sorted(),
                    )
                }

                val bootstrapEntry = zip.getEntry(commonBootstrapClassEntry)
                if (bootstrapEntry == null) {
                    missingConditions.add("$description has no loader-neutral bootstrap")
                } else {
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(bootstrapEntry).use { input -> input.readAllBytes() },
                    )
                    if ("ru/feytox/etherology/registry/misc/SharedGameEvents" !in constants) {
                        missingConditions.add(
                            "$description bootstrap does not attach the shared game-event registry",
                        )
                    }
                }

                if (requireFabricHook) {
                    val initializerEntry = zip.getEntry(canonicalFabricInitializerClassEntry)
                    if (initializerEntry == null) {
                        missingConditions.add("$description has no canonical Fabric initializer")
                    } else {
                        val constants = readClassUtf8Constants(
                            zip.getInputStream(initializerEntry).use { input ->
                                input.readAllBytes()
                            },
                        )
                        if ("ru/feytox/etherology/registry/misc/SharedGameEvents"
                            !in constants
                            || "ru/feytox/etherology/FabricGameEventHooks" !in constants
                            || "ru/feytox/etherology/bootstrap/EtherologyBootstrap" in constants
                        ) {
                            missingConditions.add(
                                "$description initializer does not own the bounded Fabric " +
                                    "game-event attachment and frequency path",
                            )
                        }
                    }
                }

                if (requireTagResources) {
                    canonicalGameEventTagFiles.forEach tag@ { (entryPath, sourceFile) ->
                        val packagedEntryCount = entryNames.count(entryPath::equals)
                        if (packagedEntryCount != 1) {
                            missingConditions.add(
                                "$description must contain one $entryPath, " +
                                    "found $packagedEntryCount",
                            )
                            return@tag
                        }
                        if (!sourceFile.isFile || Files.isSymbolicLink(sourceFile.toPath())) {
                            return@tag
                        }
                        val packagedEntry = requireNotNull(zip.getEntry(entryPath))
                        val packagedBytes = zip.getInputStream(packagedEntry).use { input ->
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
                "$description could not be inspected for game-event ownership: " +
                    "${exception.javaClass.simpleName}: ${exception.message}",
            )
        }
    }

    inspectArtifact(commonJarFile, "common JAR", false, false)
    inspectArtifact(
        fabricTransformedCommonJarFile,
        "Fabric-transformed common JAR",
        false,
        false,
    )
    inspectArtifact(
        forgeTransformedCommonJarFile,
        "Forge-transformed common JAR",
        false,
        false,
    )
    inspectArtifact(
        fabricProductionJarFile,
        "Fabric remapped production JAR",
        true,
        true,
    )
    inspectArtifact(forgeShadowJarFile, "Forge shadow JAR", true, false)
    return missingConditions
}

fun missingForgeLootConditionRegistryMilestone(
    commonJarFile: File,
    fabricTransformedCommonJarFile: File,
    forgeTransformedCommonJarFile: File,
    fabricDevelopmentJarFile: File,
    fabricProductionJarFile: File,
    forgeShadowJarFile: File,
): List<String> {
    val missingConditions = mutableListOf<String>()
    val conditionId = "random_chance_with_fortune"
    val sharedOwner = "ru/feytox/etherology/registry/misc/SharedLootConditions"
    val sharedDeferredRegister = "ru/feytox/etherology/registry/SharedDeferredRegister"

    if (!canonicalAttrahiteLootTable.isFile
        || Files.isSymbolicLink(canonicalAttrahiteLootTable.toPath())
    ) {
        missingConditions.add("canonical attrahite loot table is missing or linked")
    }

    fun inspectArtifact(
        artifact: File,
        description: String,
        requireFabricApplication: Boolean,
        requireAttrahiteLootTable: Boolean,
    ) {
        if (!artifact.isFile || Files.isSymbolicLink(artifact.toPath())) {
            missingConditions.add("$description is missing or linked")
            return
        }

        try {
            ZipFile(artifact).use { zip ->
                val entryNames = zip.entries().asSequence().map { entry -> entry.name }.toList()
                val classEntryNames = entryNames.filter { entry -> entry.endsWith(".class") }
                val exactClassCounts = mapOf(
                    sharedLootConditionRegistryClassEntry to 1,
                    randomChanceWithFortuneConditionClassEntry to 1,
                    randomChanceWithFortuneConditionSerializerClassEntry to 1,
                    canonicalFabricLootConditionRegistryClassEntry to 0,
                )
                exactClassCounts.forEach { (classEntry, expectedCount) ->
                    val actualCount = entryNames.count(classEntry::equals)
                    if (actualCount != expectedCount) {
                        missingConditions.add(
                            "$description has the wrong $classEntry count: " +
                                "expected=$expectedCount, actual=$actualCount",
                        )
                    }
                }

                val sharedEntry = zip.getEntry(sharedLootConditionRegistryClassEntry)
                if (sharedEntry != null) {
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(sharedEntry).use { input -> input.readAllBytes() },
                    )
                    val requiredConstants = setOf(
                        conditionId,
                        sharedDeferredRegister,
                        "register",
                        "attach",
                    )
                    val missingConstants = requiredConstants - constants
                    if (missingConstants.isNotEmpty()) {
                        missingConditions.add(
                            "$description shared loot-condition owner lost its deferred contract: " +
                                "missing=${missingConstants.sorted()}",
                        )
                    }
                }

                val conditionRegistrationOwners = mutableSetOf<String>()
                val sharedSupplierConsumerOwners = mutableSetOf<String>()
                classEntryNames.forEach { classEntryName ->
                    val classEntry = requireNotNull(zip.getEntry(classEntryName))
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(classEntry).use { input -> input.readAllBytes() },
                    )
                    if (conditionId in constants
                        && sharedDeferredRegister in constants
                        && "register" in constants
                    ) {
                        conditionRegistrationOwners.add(classEntryName)
                    }
                    if (classEntryName != sharedLootConditionRegistryClassEntry
                        && sharedOwner in constants
                        && "RANDOM_CHANCE_WITH_FORTUNE" in constants
                    ) {
                        sharedSupplierConsumerOwners.add(classEntryName)
                    }
                }
                if (conditionRegistrationOwners != setOf(
                        sharedLootConditionRegistryClassEntry,
                    )
                ) {
                    missingConditions.add(
                        "$description loot-condition registration owners changed: " +
                            conditionRegistrationOwners.sorted(),
                    )
                }
                if (sharedSupplierConsumerOwners != setOf(
                        randomChanceWithFortuneConditionClassEntry,
                    )
                ) {
                    missingConditions.add(
                        "$description loot-condition supplier consumers changed: " +
                            sharedSupplierConsumerOwners.sorted(),
                    )
                }

                val bootstrapEntry = zip.getEntry(commonBootstrapClassEntry)
                if (bootstrapEntry == null) {
                    missingConditions.add("$description has no loader-neutral bootstrap")
                } else {
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(bootstrapEntry).use { input -> input.readAllBytes() },
                    )
                    if (sharedOwner !in constants || "register" !in constants) {
                        missingConditions.add(
                            "$description bootstrap does not attach SharedLootConditions",
                        )
                    }
                }

                if (requireFabricApplication) {
                    val initializerEntry = zip.getEntry(canonicalFabricInitializerClassEntry)
                    if (initializerEntry == null) {
                        missingConditions.add("$description has no canonical Fabric initializer")
                    } else {
                        val constants = readClassUtf8Constants(
                            zip.getInputStream(initializerEntry).use { input -> input.readAllBytes() },
                        )
                        if (sharedOwner !in constants
                            || "ru/feytox/etherology/bootstrap/EtherologyBootstrap" in constants
                        ) {
                            missingConditions.add(
                                "$description initializer does not directly attach " +
                                    "SharedLootConditions",
                            )
                        }
                    }
                }

                val attrahiteLootTableCount = entryNames.count(
                    "data/etherology/loot_tables/blocks/attrahite.json"::equals,
                )
                val expectedLootTableCount = if (requireAttrahiteLootTable) 1 else 0
                if (attrahiteLootTableCount != expectedLootTableCount) {
                    missingConditions.add(
                        "$description attrahite loot-table count changed: " +
                            "expected=$expectedLootTableCount, actual=$attrahiteLootTableCount",
                    )
                } else if (requireAttrahiteLootTable
                    && canonicalAttrahiteLootTable.isFile
                    && !Files.isSymbolicLink(canonicalAttrahiteLootTable.toPath())
                ) {
                    val packagedEntry = requireNotNull(
                        zip.getEntry("data/etherology/loot_tables/blocks/attrahite.json"),
                    )
                    val packagedBytes = zip.getInputStream(packagedEntry).use { input ->
                        input.readAllBytes()
                    }
                    if (!packagedBytes.contentEquals(canonicalAttrahiteLootTable.readBytes())) {
                        missingConditions.add(
                            "$description attrahite loot table differs from its canonical source",
                        )
                    }
                }
            }
        } catch (exception: Exception) {
            missingConditions.add(
                "$description could not be inspected for loot-condition ownership: " +
                    "${exception.javaClass.simpleName}: ${exception.message}",
            )
        }
    }

    inspectArtifact(commonJarFile, "common JAR", false, false)
    inspectArtifact(
        fabricTransformedCommonJarFile,
        "Fabric-transformed common JAR",
        false,
        false,
    )
    inspectArtifact(
        forgeTransformedCommonJarFile,
        "Forge-transformed common JAR",
        false,
        false,
    )
    inspectArtifact(
        fabricDevelopmentJarFile,
        "Fabric development JAR",
        true,
        true,
    )
    inspectArtifact(
        fabricProductionJarFile,
        "Fabric remapped production JAR",
        true,
        true,
    )
    inspectArtifact(forgeShadowJarFile, "Forge shadow JAR", false, true)
    return missingConditions
}

fun missingForgeEtherSourceReloadMilestone(
    commonJarFile: File,
    fabricTransformedCommonJarFile: File,
    forgeTransformedCommonJarFile: File,
    fabricDevelopmentJarFile: File,
    fabricProductionJarFile: File,
    forgeShadowJarFile: File,
): List<String> {
    val missingConditions = mutableListOf<String>()
    val resourceEntry = "data/$commonEtherSourceDataEntry"
    val resourceReloadersOwner =
        "ru/feytox/etherology/registry/misc/ResourceReloaders"
    val reloadListenerRegistry = "dev/architectury/registry/ReloadListenerRegistry"

    if (!canonicalEtherSourceDefault.isFile
        || Files.isSymbolicLink(canonicalEtherSourceDefault.toPath())
    ) {
        missingConditions.add("canonical Ether-source default is missing or linked")
    } else {
        try {
            val parsed = JsonSlurper().parse(canonicalEtherSourceDefault)
            if (parsed !is Map<*, *>) {
                missingConditions.add("canonical Ether-source default is not a JSON object")
            } else {
                val actualEntries = parsed.entries.map { entry ->
                    entry.key.toString() to entry.value
                }
                val expectedEntries = expectedEtherSourceValues.entries.map { entry ->
                    entry.key to entry.value
                }
                if (actualEntries != expectedEntries) {
                    missingConditions.add(
                        "canonical Ether-source entries changed: " +
                            "expected=$expectedEntries, actual=$actualEntries",
                    )
                }
                if ("etherology:primoshard_rela" in parsed
                    || "etherology:primoshard_rella" !in parsed
                ) {
                    missingConditions.add(
                        "canonical Ether-source primoshard spelling is not exact",
                    )
                }
            }
        } catch (exception: Exception) {
            missingConditions.add(
                "canonical Ether-source default could not be parsed: " +
                    "${exception.javaClass.simpleName}: ${exception.message}",
            )
        }
    }

    legacyFabricEtherSourceOwners.forEach { legacyOwner ->
        if (legacyOwner.exists() || Files.isSymbolicLink(legacyOwner.toPath())) {
            missingConditions.add(
                "legacy Fabric Ether-source owner still exists: " +
                    legacyOwner.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
            )
        }
    }

    fun inspectArtifact(
        artifact: File,
        description: String,
        requireFabricApplication: Boolean,
    ) {
        if (!artifact.isFile || Files.isSymbolicLink(artifact.toPath())) {
            missingConditions.add("$description is missing or linked")
            return
        }

        try {
            ZipFile(artifact).use { zip ->
                val entryNames = zip.entries().asSequence().map { entry -> entry.name }.toList()
                val classEntryNames = entryNames.filter { entry -> entry.endsWith(".class") }
                listOf(
                    resourceReloadersClassEntry,
                    etherSourceLoaderClassEntry,
                    etherSourcesClassEntry,
                    etherSourcesDeserializerClassEntry,
                ).forEach { classEntry ->
                    val actualCount = entryNames.count(classEntry::equals)
                    if (actualCount != 1) {
                        missingConditions.add(
                            "$description has the wrong $classEntry count: " +
                                "expected=1, actual=$actualCount",
                        )
                    }
                }

                val resourceCount = entryNames.count(resourceEntry::equals)
                if (resourceCount != 1) {
                    missingConditions.add(
                        "$description has the wrong $resourceEntry count: " +
                            "expected=1, actual=$resourceCount",
                    )
                } else if (canonicalEtherSourceDefault.isFile
                    && !Files.isSymbolicLink(canonicalEtherSourceDefault.toPath())
                ) {
                    val packagedBytes = zip.getInputStream(
                        requireNotNull(zip.getEntry(resourceEntry)),
                    ).use { input -> input.readAllBytes() }
                    if (!packagedBytes.contentEquals(canonicalEtherSourceDefault.readBytes())) {
                        missingConditions.add(
                            "$description Ether-source default differs from its Common source",
                        )
                    }
                }

                val listenerOwnerEntry = zip.getEntry(resourceReloadersClassEntry)
                if (listenerOwnerEntry != null) {
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(listenerOwnerEntry).use { input ->
                            input.readAllBytes()
                        },
                    )
                    val requiredConstants = setOf(
                        "etherology",
                        "ether_sources",
                        reloadListenerRegistry,
                        "register",
                        "registerServerData",
                    )
                    val missingConstants = requiredConstants - constants
                    val hasServerDataField = "SERVER_DATA" in constants
                        || "field_14190" in constants
                    if (missingConstants.isNotEmpty() || !hasServerDataField) {
                        missingConditions.add(
                            "$description listener owner lost its exact contract: " +
                                "missing=${missingConstants.sorted()}, " +
                                "serverDataField=$hasServerDataField",
                        )
                    }
                }

                val loaderEntry = zip.getEntry(etherSourceLoaderClassEntry)
                if (loaderEntry != null) {
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(loaderEntry).use { input -> input.readAllBytes() },
                    )
                    if ("ether_sources" !in constants) {
                        missingConditions.add(
                            "$description Ether-source loader lost its exact directory",
                        )
                    }
                }

                val registrationOwners = classEntryNames.filter { classEntryName ->
                    val classEntry = requireNotNull(zip.getEntry(classEntryName))
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(classEntry).use { input -> input.readAllBytes() },
                    )
                    reloadListenerRegistry in constants && "register" in constants
                }.toSet()
                if (registrationOwners != setOf(resourceReloadersClassEntry)) {
                    missingConditions.add(
                        "$description reload-listener registration owners changed: " +
                            registrationOwners.sorted(),
                    )
                }

                val bootstrapEntry = zip.getEntry(commonBootstrapClassEntry)
                if (bootstrapEntry == null) {
                    missingConditions.add("$description has no loader-neutral bootstrap")
                } else {
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(bootstrapEntry).use { input -> input.readAllBytes() },
                    )
                    val requiredBootstrapConstants = setOf(
                        resourceReloadersOwner,
                        "registerServerData",
                        "ru/feytox/etherology/registry/misc/SharedLootConditions",
                        "ru/feytox/etherology/bootstrap/BootstrapLifecycle",
                    )
                    val missingBootstrapConstants = requiredBootstrapConstants - constants
                    if (missingBootstrapConstants.isNotEmpty()) {
                        missingConditions.add(
                            "$description bootstrap lost its Ether-source attachment contract: " +
                                missingBootstrapConstants.sorted(),
                        )
                    }
                }

                if (requireFabricApplication) {
                    val initializerEntry = zip.getEntry(canonicalFabricInitializerClassEntry)
                    if (initializerEntry == null) {
                        missingConditions.add("$description has no canonical Fabric initializer")
                    } else {
                        val constants = readClassUtf8Constants(
                            zip.getInputStream(initializerEntry).use { input ->
                                input.readAllBytes()
                            },
                        )
                        if (resourceReloadersOwner !in constants
                            || "registerServerData" !in constants
                            || "ru/feytox/etherology/bootstrap/EtherologyBootstrap" in constants
                        ) {
                            missingConditions.add(
                                "$description initializer lost its direct Ether-source attachment",
                            )
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            missingConditions.add(
                "$description could not be inspected for Ether-source ownership: " +
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
    inspectArtifact(fabricDevelopmentJarFile, "Fabric development JAR", true)
    inspectArtifact(fabricProductionJarFile, "Fabric remapped production JAR", true)
    inspectArtifact(forgeShadowJarFile, "Forge shadow JAR", false)
    return missingConditions
}

fun missingForgeEnchantmentRegistryMilestone(
    commonJarFile: File,
    fabricTransformedCommonJarFile: File,
    forgeTransformedCommonJarFile: File,
    fabricDevelopmentJarFile: File,
    fabricProductionJarFile: File,
    forgeShadowJarFile: File,
): List<String> {
    val missingConditions = mutableListOf<String>()
    val exactTagBytes = (
        "{\n" +
            "  \"replace\": false,\n" +
            "  \"values\": [\n" +
            "    \"etherology:peal\",\n" +
            "    \"etherology:reflection\"\n" +
            "  ]\n" +
            "}"
        ).toByteArray(StandardCharsets.UTF_8)
    val sharedOwner = "ru/feytox/etherology/registry/misc/SharedEnchantments"
    val sharedDeferredRegister =
        "ru/feytox/etherology/registry/SharedDeferredRegister"
    val registrySupplier = "dev/architectury/registry/registries/RegistrySupplier"

    if (!canonicalEnchantmentTagFile.isFile
        || Files.isSymbolicLink(canonicalEnchantmentTagFile.toPath())
    ) {
        missingConditions.add("canonical non-treasure enchantment tag is missing or linked")
    } else if (!canonicalEnchantmentTagFile.readBytes().contentEquals(exactTagBytes)) {
        missingConditions.add("canonical non-treasure enchantment tag bytes changed")
    }

    legacyFabricEnchantmentConcreteOwners.forEach { legacyOwner ->
        if (legacyOwner.exists() || Files.isSymbolicLink(legacyOwner.toPath())) {
            missingConditions.add(
                "legacy Fabric enchantment implementation still exists: " +
                    legacyOwner.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
            )
        }
    }

    fun inspectArtifact(
        artifact: File,
        description: String,
        requireFabricPolicy: Boolean,
        requireCanonicalTag: Boolean,
    ) {
        if (!artifact.isFile || Files.isSymbolicLink(artifact.toPath())) {
            missingConditions.add("$description is missing or linked")
            return
        }

        try {
            ZipFile(artifact).use { zip ->
                val entryNames = zip.entries().asSequence().map { entry -> entry.name }.toList()
                val classEntryNames = entryNames.filter { entry -> entry.endsWith(".class") }
                listOf(
                    sharedEnchantmentRegistryClassEntry,
                    pealEnchantmentClassEntry,
                    reflectionEnchantmentClassEntry,
                ).forEach { classEntry ->
                    val actualCount = entryNames.count(classEntry::equals)
                    if (actualCount != 1) {
                        missingConditions.add(
                            "$description has the wrong $classEntry count: " +
                                "expected=1, actual=$actualCount",
                        )
                    }
                }

                val expectedPolicyCount = if (requireFabricPolicy) 1 else 0
                val policyCount = entryNames.count(
                    canonicalFabricEnchantmentPolicyClassEntry::equals,
                )
                if (policyCount != expectedPolicyCount) {
                    missingConditions.add(
                        "$description has the wrong Fabric enchantment-policy count: " +
                            "expected=$expectedPolicyCount, actual=$policyCount",
                    )
                }

                val expectedTagCount = if (requireCanonicalTag) 1 else 0
                val tagCount = entryNames.count(canonicalEnchantmentTagEntry::equals)
                if (tagCount != expectedTagCount) {
                    missingConditions.add(
                        "$description has the wrong non-treasure tag count: " +
                            "expected=$expectedTagCount, actual=$tagCount",
                    )
                } else if (requireCanonicalTag && tagCount == 1) {
                    val packagedTagBytes = zip.getInputStream(
                        requireNotNull(zip.getEntry(canonicalEnchantmentTagEntry)),
                    ).use { input -> input.readAllBytes() }
                    if (!packagedTagBytes.contentEquals(exactTagBytes)) {
                        missingConditions.add(
                            "$description non-treasure enchantment tag bytes changed",
                        )
                    }
                }

                val sharedEntry = zip.getEntry(sharedEnchantmentRegistryClassEntry)
                if (sharedEntry != null) {
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(sharedEntry).use { input -> input.readAllBytes() },
                    )
                    val requiredConstants = setOf(
                        "ENCHANTMENTS",
                        "PEAL",
                        "REFLECTION",
                        "peal",
                        "reflection",
                        sharedDeferredRegister,
                        "ru/feytox/etherology/registry/misc/PealEnchantment",
                        "ru/feytox/etherology/registry/misc/ReflectionEnchantment",
                        "create",
                        "register",
                        "attach",
                    )
                    val missingConstants = requiredConstants - constants
                    val hasRegistrySupplierType = constants.any { constant ->
                        registrySupplier in constant
                    }
                    val hasEnchantmentType = setOf(
                        "net/minecraft/enchantment/Enchantment",
                        "net/minecraft/class_1887",
                        "net/minecraft/world/item/enchantment/Enchantment",
                    ).any { owner -> constants.any { constant -> owner in constant } }
                    if (missingConstants.isNotEmpty()
                        || !hasRegistrySupplierType
                        || !hasEnchantmentType
                    ) {
                        missingConditions.add(
                            "$description shared enchantment owner lost its exact contract: " +
                                "missing=${missingConstants.sorted()}, " +
                                "registrySupplierType=$hasRegistrySupplierType, " +
                                "enchantmentType=$hasEnchantmentType",
                        )
                    }
                    if ("dev/architectury/registry/registries/DeferredRegister" in constants) {
                        missingConditions.add(
                            "$description shared enchantment owner bypasses its lifecycle wrapper",
                        )
                    }
                }

                val registrationOwners = classEntryNames.filter { classEntryName ->
                    val classEntry = requireNotNull(zip.getEntry(classEntryName))
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(classEntry).use { input -> input.readAllBytes() },
                    )
                    "peal" in constants
                        && "reflection" in constants
                        && sharedDeferredRegister in constants
                        && "register" in constants
                }.toSet()
                if (registrationOwners != setOf(sharedEnchantmentRegistryClassEntry)) {
                    missingConditions.add(
                        "$description enchantment registration owners changed: " +
                            registrationOwners.sorted(),
                    )
                }

                val bootstrapEntry = zip.getEntry(commonBootstrapClassEntry)
                if (bootstrapEntry == null) {
                    missingConditions.add("$description has no loader-neutral bootstrap")
                } else {
                    val constants = readClassUtf8Constants(
                        zip.getInputStream(bootstrapEntry).use { input -> input.readAllBytes() },
                    )
                    if (sharedOwner !in constants || "register" !in constants) {
                        missingConditions.add(
                            "$description bootstrap does not attach shared enchantments",
                        )
                    }
                }

                if (requireFabricPolicy) {
                    val policyEntry = zip.getEntry(canonicalFabricEnchantmentPolicyClassEntry)
                    if (policyEntry != null) {
                        val constants = readClassUtf8Constants(
                            zip.getInputStream(policyEntry).use { input -> input.readAllBytes() },
                        )
                        if (sharedOwner !in constants
                            || registrySupplier !in constants
                            || "PEAL" !in constants
                            || "REFLECTION" !in constants
                        ) {
                            missingConditions.add(
                                "$description Fabric enchantment policy is not a shared consumer",
                            )
                        }
                    }

                    val initializerEntry = zip.getEntry(canonicalFabricInitializerClassEntry)
                    if (initializerEntry == null) {
                        missingConditions.add("$description has no canonical Fabric initializer")
                    } else {
                        val constants = readClassUtf8Constants(
                            zip.getInputStream(initializerEntry).use { input ->
                                input.readAllBytes()
                            },
                        )
                        if (sharedOwner !in constants || "register" !in constants) {
                            missingConditions.add(
                                "$description initializer does not attach shared enchantments",
                            )
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            missingConditions.add(
                "$description could not be inspected for enchantment ownership: " +
                    "${exception.javaClass.simpleName}: ${exception.message}",
            )
        }
    }

    inspectArtifact(commonJarFile, "common JAR", false, false)
    inspectArtifact(
        fabricTransformedCommonJarFile,
        "Fabric-transformed common JAR",
        false,
        false,
    )
    inspectArtifact(
        forgeTransformedCommonJarFile,
        "Forge-transformed common JAR",
        false,
        false,
    )
    inspectArtifact(fabricDevelopmentJarFile, "Fabric development JAR", true, true)
    inspectArtifact(
        fabricProductionJarFile,
        "Fabric remapped production JAR",
        true,
        true,
    )
    inspectArtifact(forgeShadowJarFile, "Forge shadow JAR", false, true)
    return missingConditions
}

fun missingForgeRegistryFoundationServerEvidenceMilestone(): List<String> {
    val missingConditions = mutableListOf<String>()
    if (!forgeRegistryFoundationServerEvidenceVerifier.isFile
        || Files.isSymbolicLink(forgeRegistryFoundationServerEvidenceVerifier.toPath())
    ) {
        missingConditions.add(
            "strict Forge registry-foundation server evidence verifier is missing",
        )
        return missingConditions
    }

    val archiveDirectories = forgeRegistryFoundationServerEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("registry-foundation-server-v[1-9][0-9]*")
                    .matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories != listOf(forgeRegistryFoundationServerEvidenceArchive)) {
        missingConditions.add(
            "the exact frozen Forge registry-foundation server-v4 evidence archive is required",
        )
        return missingConditions
    }

    val command = listOf(
        "python3",
        "-B",
        forgeRegistryFoundationServerEvidenceVerifier.absolutePath,
        "--archive",
        forgeRegistryFoundationServerEvidenceArchive.absolutePath,
    )
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
                "strict Forge registry-foundation server evidence verification failed: " +
                    detail.take(4_000),
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict Forge registry-foundation server evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeEtherSourceReloadServerEvidenceMilestone(): List<String> {
    val missingConditions = mutableListOf<String>()
    if (!forgeEtherSourceReloadServerEvidenceVerifier.isFile
        || Files.isSymbolicLink(forgeEtherSourceReloadServerEvidenceVerifier.toPath())
    ) {
        missingConditions.add(
            "strict Forge Ether-source reload server evidence verifier is missing",
        )
        return missingConditions
    }

    val archiveDirectories = forgeRegistryFoundationServerEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("ether-source-reload-server-v[1-9][0-9]*")
                    .matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories != listOf(forgeEtherSourceReloadServerEvidenceArchive)) {
        missingConditions.add(
            "the exact frozen Forge Ether-source reload server-v6 evidence archive is required",
        )
        return missingConditions
    }

    val command = listOf(
        "python3",
        "-B",
        forgeEtherSourceReloadServerEvidenceVerifier.absolutePath,
        "--archive",
        forgeEtherSourceReloadServerEvidenceArchive.absolutePath,
    )
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
                "strict Forge Ether-source reload server evidence verification failed: " +
                    detail.take(4_000),
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict Forge Ether-source reload server evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeEnchantmentRegistryServerEvidenceMilestone(): List<String> {
    val missingConditions = mutableListOf<String>()
    if (!forgeEnchantmentRegistryServerEvidenceVerifier.isFile
        || Files.isSymbolicLink(forgeEnchantmentRegistryServerEvidenceVerifier.toPath())
    ) {
        missingConditions.add(
            "strict Forge enchantment-registry server evidence verifier is missing",
        )
        return missingConditions
    }

    val archiveDirectories = forgeRegistryFoundationServerEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("enchantment-registry-server-v[1-9][0-9]*")
                    .matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories != listOf(forgeEnchantmentRegistryServerEvidenceArchive)) {
        missingConditions.add(
            "the exact frozen Forge enchantment-registry server-v7 evidence archive is required",
        )
        return missingConditions
    }

    val command = listOf(
        "python3",
        "-B",
        forgeEnchantmentRegistryServerEvidenceVerifier.absolutePath,
        "--archive",
        forgeEnchantmentRegistryServerEvidenceArchive.absolutePath,
    )
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
                "strict Forge enchantment-registry server evidence verification failed: " +
                    detail.take(4_000),
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict Forge enchantment-registry server evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeParticleRegistryServerEvidenceMilestone(): List<String> {
    val missingConditions = mutableListOf<String>()
    if (!forgeParticleRegistryServerEvidenceVerifier.isFile
        || Files.isSymbolicLink(forgeParticleRegistryServerEvidenceVerifier.toPath())
    ) {
        missingConditions.add(
            "strict Forge particle-registry server evidence verifier is missing",
        )
        return missingConditions
    }

    val archiveDirectories = forgeRegistryFoundationServerEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("particle-registry-server-v[1-9][0-9]*")
                    .matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories != listOf(forgeParticleRegistryServerEvidenceArchive)) {
        missingConditions.add(
            "the exact frozen Forge particle-registry server-v10 evidence archive is required",
        )
        return missingConditions
    }

    val command = listOf(
        "python3",
        "-B",
        forgeParticleRegistryServerEvidenceVerifier.absolutePath,
        "--archive",
        forgeParticleRegistryServerEvidenceArchive.absolutePath,
    )
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
                "strict Forge particle-registry server evidence verification failed: " +
                    detail.take(4_000),
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict Forge particle-registry server evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeMaterialItemRegistryServerEvidenceMilestone(): List<String> {
    val missingConditions = mutableListOf<String>()
    if (!forgeMaterialItemRegistryServerEvidenceVerifier.isFile
        || Files.isSymbolicLink(forgeMaterialItemRegistryServerEvidenceVerifier.toPath())
    ) {
        missingConditions.add(
            "strict Forge material-item-registry server evidence verifier is missing",
        )
        return missingConditions
    }

    val archiveDirectories = forgeRegistryFoundationServerEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("material-item-registry-server-v[1-9][0-9]*")
                    .matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories != listOf(forgeMaterialItemRegistryServerEvidenceArchive)) {
        missingConditions.add(
            "the exact frozen Forge material-item-registry server-v11 evidence archive is required",
        )
        return missingConditions
    }

    val command = listOf(
        "python3",
        "-B",
        forgeMaterialItemRegistryServerEvidenceVerifier.absolutePath,
        "--archive",
        forgeMaterialItemRegistryServerEvidenceArchive.absolutePath,
    )
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
                "strict Forge material-item-registry server evidence verification failed: " +
                    detail.take(4_000),
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict Forge material-item-registry server evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeMetalBlockRegistryServerEvidenceMilestone(): List<String> {
    val missingConditions = mutableListOf<String>()
    if (!forgeMetalBlockRegistryServerEvidenceVerifier.isFile
        || Files.isSymbolicLink(forgeMetalBlockRegistryServerEvidenceVerifier.toPath())
    ) {
        missingConditions.add(
            "strict Forge metal-block-registry server evidence verifier is missing",
        )
        return missingConditions
    }

    val archiveDirectories = forgeRegistryFoundationServerEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("metal-block-registry-server-v[1-9][0-9]*")
                    .matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories != listOf(forgeMetalBlockRegistryServerEvidenceArchive)) {
        missingConditions.add(
            "the exact frozen Forge metal-block-registry server-v13 evidence archive is required",
        )
        return missingConditions
    }

    val command = listOf(
        "python3",
        "-B",
        forgeMetalBlockRegistryServerEvidenceVerifier.absolutePath,
        "--archive",
        forgeMetalBlockRegistryServerEvidenceArchive.absolutePath,
    )
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
                "strict Forge metal-block-registry server evidence verification failed: " +
                    detail.take(4_000),
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict Forge metal-block-registry server evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeFoodItemRegistryServerEvidenceMilestone(): List<String> {
    val missingConditions = mutableListOf<String>()
    if (!forgeFoodItemRegistryServerEvidenceVerifier.isFile
        || Files.isSymbolicLink(forgeFoodItemRegistryServerEvidenceVerifier.toPath())
    ) {
        missingConditions.add(
            "strict Forge food-item-registry server evidence verifier is missing",
        )
        return missingConditions
    }

    val archiveDirectories = forgeRegistryFoundationServerEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("food-item-registry-server-v[1-9][0-9]*")
                    .matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories != listOf(forgeFoodItemRegistryServerEvidenceArchive)) {
        missingConditions.add(
            "the exact frozen Forge food-item-registry server-v14 evidence archive is required",
        )
        return missingConditions
    }

    val command = listOf(
        "python3",
        "-B",
        forgeFoodItemRegistryServerEvidenceVerifier.absolutePath,
        "--archive",
        forgeFoodItemRegistryServerEvidenceArchive.absolutePath,
    )
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
                "strict Forge food-item-registry server evidence verification failed: " +
                    detail.take(4_000),
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict Forge food-item-registry server evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeForestLanternServerEvidenceMilestone(): List<String> {
    val missingConditions = mutableListOf<String>()
    if (!forgeForestLanternServerEvidenceVerifier.isFile
        || Files.isSymbolicLink(forgeForestLanternServerEvidenceVerifier.toPath())
    ) {
        missingConditions.add(
            "strict Forge Forest Lantern server evidence verifier is missing",
        )
        return missingConditions
    }

    val archiveDirectories = forgeRegistryFoundationServerEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("forest-lantern-server-v[1-9][0-9]*")
                    .matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories != listOf(forgeForestLanternServerEvidenceArchive)) {
        missingConditions.add(
            "the exact frozen Forge Forest Lantern server-v16 evidence archive is required",
        )
        return missingConditions
    }

    val command = listOf(
        "python3",
        "-B",
        forgeForestLanternServerEvidenceVerifier.absolutePath,
        "--archive",
        forgeForestLanternServerEvidenceArchive.absolutePath,
    )
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
                "strict Forge Forest Lantern server evidence verification failed: " +
                    detail.take(4_000),
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict Forge Forest Lantern server evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeAttrahiteBlockRegistryServerEvidenceMilestone(): List<String> {
    val missingConditions = mutableListOf<String>()
    if (!forgeAttrahiteBlockRegistryServerEvidenceVerifierV19.isFile
        || Files.isSymbolicLink(
            forgeAttrahiteBlockRegistryServerEvidenceVerifierV19.toPath(),
        )
    ) {
        missingConditions.add(
            "strict Forge Attrahite block-registry server evidence verifier is missing",
        )
        return missingConditions
    }

    val archiveDirectories = forgeRegistryFoundationServerEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("attrahite-block-registry-server-v[1-9][0-9]*")
                    .matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories != listOf(forgeAttrahiteBlockRegistryServerEvidenceArchiveV19)) {
        missingConditions.add(
            "the exact frozen Forge Attrahite block-registry server-v19 evidence archive " +
                "is required",
        )
        return missingConditions
    }

    val command = listOf(
        "python3",
        "-B",
        forgeAttrahiteBlockRegistryServerEvidenceVerifierV19.absolutePath,
        "--archive",
        forgeAttrahiteBlockRegistryServerEvidenceArchiveV19.absolutePath,
    )
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
                "strict Forge Attrahite block-registry server evidence verification " +
                    "failed: ${detail.take(4_000)}",
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict Forge Attrahite block-registry server evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeSlitheriteBlockRegistryServerEvidenceMilestone(): List<String> {
    val missingConditions = mutableListOf<String>()
    if (!forgeSlitheriteBlockRegistryServerEvidenceVerifierV20.isFile
        || Files.isSymbolicLink(
            forgeSlitheriteBlockRegistryServerEvidenceVerifierV20.toPath(),
        )
    ) {
        missingConditions.add(
            "strict Forge Slitherite block-registry server evidence verifier is missing",
        )
        return missingConditions
    }

    val archiveDirectories = forgeRegistryFoundationServerEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("slitherite-block-registry-server-v[1-9][0-9]*")
                    .matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories != listOf(forgeSlitheriteBlockRegistryServerEvidenceArchiveV20)) {
        missingConditions.add(
            "the exact frozen Forge Slitherite block-registry server-v20 evidence archive " +
                "is required",
        )
        return missingConditions
    }

    val command = listOf(
        "python3",
        "-B",
        forgeSlitheriteBlockRegistryServerEvidenceVerifierV20.absolutePath,
        "--archive",
        forgeSlitheriteBlockRegistryServerEvidenceArchiveV20.absolutePath,
    )
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
                "strict Forge Slitherite block-registry server evidence verification " +
                    "failed: ${detail.take(4_000)}",
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict Forge Slitherite block-registry server evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeForestLanternClientEvidenceMilestone(): List<String> {
    val missingConditions = mutableListOf<String>()
    if (!forgeForestLanternEvidenceVerifier.isFile
        || Files.isSymbolicLink(forgeForestLanternEvidenceVerifier.toPath())
    ) {
        missingConditions.add(
            "strict Forge Forest Lantern client evidence verifier is missing",
        )
        return missingConditions
    }

    val archiveDirectories = forgeChannelEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("forest-lantern-v[1-9][0-9]*").matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories != listOf(forgeForestLanternClientEvidenceArchive)) {
        missingConditions.add(
            "the exact frozen Forge Forest Lantern client-v13 evidence archive is required",
        )
        return missingConditions
    }

    val command = listOf(
        "python3",
        "-B",
        forgeForestLanternEvidenceVerifier.absolutePath,
        "--archive",
        forgeForestLanternClientEvidenceArchive.absolutePath,
    )
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
                "strict Forge Forest Lantern client evidence verification failed: " +
                    detail.take(4_000),
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict Forge Forest Lantern client evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeAttrahiteBlockRegistryClientEvidenceMilestone(): List<String> {
    val missingConditions = mutableListOf<String>()
    if (!forgeAttrahiteEvidenceVerifier.isFile
        || Files.isSymbolicLink(forgeAttrahiteEvidenceVerifier.toPath())
    ) {
        missingConditions.add(
            "strict Forge Attrahite block-registry client evidence verifier is missing",
        )
        return missingConditions
    }

    val archiveDirectories = forgeChannelEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("attrahite-block-registry-v[1-9][0-9]*")
                    .matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories != listOf(forgeAttrahiteClientEvidenceArchive)) {
        missingConditions.add(
            "the exact frozen Forge Attrahite block-registry client-v17 evidence archive " +
                "is required",
        )
        return missingConditions
    }

    val command = listOf(
        "python3",
        "-B",
        forgeAttrahiteEvidenceVerifier.absolutePath,
        "--archive",
        forgeAttrahiteClientEvidenceArchive.absolutePath,
    )
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
                "strict Forge Attrahite block-registry client evidence verification " +
                    "failed: ${detail.take(4_000)}",
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict Forge Attrahite block-registry client evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeSlitheriteBlockRegistryClientEvidenceMilestone(): List<String> {
    val missingConditions = mutableListOf<String>()
    if (!forgeSlitheriteEvidenceVerifier.isFile
        || Files.isSymbolicLink(forgeSlitheriteEvidenceVerifier.toPath())
    ) {
        missingConditions.add(
            "strict Forge Slitherite block-registry client evidence verifier is missing",
        )
        return missingConditions
    }

    val archiveDirectories = forgeChannelEvidenceRoot.listFiles()
        ?.filter { candidate ->
            candidate.isDirectory
                && !Files.isSymbolicLink(candidate.toPath())
                && Regex("slitherite-block-registry-v[1-9][0-9]*")
                    .matches(candidate.name)
        }
        .orEmpty()
    if (archiveDirectories != listOf(forgeSlitheriteClientEvidenceArchive)) {
        missingConditions.add(
            "the exact frozen Forge Slitherite block-registry client-v19 evidence " +
                "archive is required",
        )
        return missingConditions
    }

    val command = listOf(
        "python3",
        "-B",
        forgeSlitheriteEvidenceVerifier.absolutePath,
        "--archive",
        forgeSlitheriteClientEvidenceArchive.absolutePath,
    )
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
                "strict Forge Slitherite block-registry client evidence verification " +
                    "failed: ${detail.take(4_000)}",
            )
        }
    } catch (exception: Exception) {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        missingConditions.add(
            "strict Forge Slitherite block-registry client evidence verifier could not run: " +
                "${exception.javaClass.simpleName}: ${exception.message}",
        )
    }
    return missingConditions
}

fun missingForgeAuthoritativeRegistrySpineMilestone(): List<String> = listOf(
    "the shared block and item catalogs do not cover every canonical runtime ID",
    "entity, recipe, screen, effect, loot, tree, and " +
        "world-generation registries are not loader-neutral",
    "creative tabs, fuel, lifecycle, trade, brewing, wood, sculk-frequency, and " +
        "command hooks are not accepted on both loaders",
    "the exact Fabric/Forge registry manifest and dedicated-server placement/save smoke are " +
        "not accepted",
)

fun missingForgeAttrahiteNativeEvidenceMilestone(): List<String> =
    missingForgeAttrahiteBlockRegistryServerEvidenceMilestone() +
        missingForgeAttrahiteBlockRegistryClientEvidenceMilestone()

fun missingForgeSlitheriteNativeEvidenceMilestone(): List<String> =
    missingForgeSlitheriteBlockRegistryServerEvidenceMilestone() +
        missingForgeSlitheriteBlockRegistryClientEvidenceMilestone()

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
    fabricDevelopmentJarFile: File,
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

    val missingGameEventRegistry = missingForgeGameEventRegistryMilestone(
        commonJarFile,
        fabricTransformedCommonJarFile,
        forgeTransformedCommonJarFile,
        fabricProductionJarFile,
        forgeShadowJarFile,
    )
    if (missingGameEventRegistry.isNotEmpty()) {
        return "game-event registry and tags" to missingGameEventRegistry
    }

    val missingLootConditionRegistry = missingForgeLootConditionRegistryMilestone(
        commonJarFile,
        fabricTransformedCommonJarFile,
        forgeTransformedCommonJarFile,
        fabricDevelopmentJarFile,
        fabricProductionJarFile,
        forgeShadowJarFile,
    )
    if (missingLootConditionRegistry.isNotEmpty()) {
        return "loot-condition registry" to missingLootConditionRegistry
    }

    val missingRegistryFoundationServerEvidence =
        missingForgeRegistryFoundationServerEvidenceMilestone()
    if (missingRegistryFoundationServerEvidence.isNotEmpty()) {
        return "registry-foundation dedicated-server evidence" to
            missingRegistryFoundationServerEvidence
    }

    val missingEtherSourceReload = missingForgeEtherSourceReloadMilestone(
        commonJarFile,
        fabricTransformedCommonJarFile,
        forgeTransformedCommonJarFile,
        fabricDevelopmentJarFile,
        fabricProductionJarFile,
        forgeShadowJarFile,
    )
    if (missingEtherSourceReload.isNotEmpty()) {
        return "Ether-source server-data reload" to missingEtherSourceReload
    }

    val missingEnchantmentRegistry = missingForgeEnchantmentRegistryMilestone(
        commonJarFile,
        fabricTransformedCommonJarFile,
        forgeTransformedCommonJarFile,
        fabricDevelopmentJarFile,
        fabricProductionJarFile,
        forgeShadowJarFile,
    )
    if (missingEnchantmentRegistry.isNotEmpty()) {
        return "enchantment registry and tag" to missingEnchantmentRegistry
    }

    val missingEnchantmentRegistryServerEvidence =
        missingForgeEnchantmentRegistryServerEvidenceMilestone()
    if (missingEnchantmentRegistryServerEvidence.isNotEmpty()) {
        return "enchantment-registry dedicated-server evidence" to
            missingEnchantmentRegistryServerEvidence
    }

    val missingParticleRegistryServerEvidence =
        missingForgeParticleRegistryServerEvidenceMilestone()
    if (missingParticleRegistryServerEvidence.isNotEmpty()) {
        return "particle-registry dedicated-server evidence" to
            missingParticleRegistryServerEvidence
    }

    val missingMaterialItemRegistryServerEvidence =
        missingForgeMaterialItemRegistryServerEvidenceMilestone()
    if (missingMaterialItemRegistryServerEvidence.isNotEmpty()) {
        return "material-item registry dedicated-server evidence" to
            missingMaterialItemRegistryServerEvidence
    }

    val missingMetalBlockRegistryServerEvidence =
        missingForgeMetalBlockRegistryServerEvidenceMilestone()
    if (missingMetalBlockRegistryServerEvidence.isNotEmpty()) {
        return "metal-block registry dedicated-server evidence" to
            missingMetalBlockRegistryServerEvidence
    }

    val missingFoodItemRegistryServerEvidence =
        missingForgeFoodItemRegistryServerEvidenceMilestone()
    if (missingFoodItemRegistryServerEvidence.isNotEmpty()) {
        return "food-item registry dedicated-server evidence" to
            missingFoodItemRegistryServerEvidence
    }

    val missingForestLanternServerEvidence =
        missingForgeForestLanternServerEvidenceMilestone()
    if (missingForestLanternServerEvidence.isNotEmpty()) {
        return "Forest Lantern dedicated-server evidence" to
            missingForestLanternServerEvidence
    }

    val missingForestLanternClientEvidence =
        missingForgeForestLanternClientEvidenceMilestone()
    if (missingForestLanternClientEvidence.isNotEmpty()) {
        return "Forest Lantern packaged-client evidence" to
            missingForestLanternClientEvidence
    }

    val missingAttrahiteNativeAcceptance =
        missingForgeAttrahiteNativeEvidenceMilestone()
    if (missingAttrahiteNativeAcceptance.isNotEmpty()) {
        return "Attrahite native acceptance" to missingAttrahiteNativeAcceptance
    }

    val missingSlitheriteNativeAcceptance =
        missingForgeSlitheriteNativeEvidenceMilestone()
    if (missingSlitheriteNativeAcceptance.isNotEmpty()) {
        return "Slitherite native acceptance" to missingSlitheriteNativeAcceptance
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
tasks.named<Test>("test").configure {
    exclude("**/GameEventRegistryResourcesTest.class")
    exclude("**/LootConditionRegistryResourcesTest.class")
    exclude("**/EtherSourceReloadResourcesTest.class")
    exclude("**/EnchantmentRegistryResourcesTest.class")
    exclude("**/ParticleRegistryResourcesTest.class")
    exclude("**/MaterialItemRegistryResourcesTest.class")
    exclude("**/FoodItemRegistryResourcesTest.class")
    exclude("**/MetalBlockRegistryResourcesTest.class")
    exclude("**/ForestLanternBlockResourcesTest.class")
    exclude("**/AttrahiteBlockRegistryResourcesTest.class")
    exclude("**/SlitheriteCanonicalResourcesTest.class")
    exclude("**/WarpCounterRegistryResourcesTest.class")
    exclude("**/LensFoundationCrossArtifactTest.class")
    exclude("**/UnadjustedLensRegistryResourcesTest.class")
    exclude("**/AspectFoundationCrossArtifactTest.class")
    exclude("**/PedestalCrossArtifactTest.class")
    exclude("**/AlchemyRecipeFoundationCrossArtifactTest.class")
}
val gameEventRegistryTest = tasks.register<Test>("gameEventRegistryTest") {
    group = "verification"
    description =
        "Runs the bounded cross-loader game-event ownership and packaged-tag tests."
    dependsOn(
        tasks.named("testClasses"),
        commonJar,
        commonTransformProductionFabric,
        commonTransformProductionForge,
        fabricJar,
        fabricRemapJar,
        forgeShadowJar,
    )
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching(
            "ru.feytox.etherology.forge.GameEventRegistryResourcesTest",
        )
    }
    inputs.file(commonJar.flatMap { it.archiveFile })
        .withPropertyName("gameEventCommonJar")
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("gameEventFabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("gameEventForgeTransformedCommonJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        .withPropertyName("gameEventFabricProductionJar")
    inputs.file(fabricJar.flatMap { it.archiveFile })
        .withPropertyName("gameEventFabricDevelopmentJar")
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        .withPropertyName("gameEventForgeShadowJar")
    doFirst {
        systemProperty(
            "etherology.gameEvents.commonJar",
            commonJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.gameEvents.fabricTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionFabric.get(),
                "Fabric common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.gameEvents.forgeTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionForge.get(),
                "Forge common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.gameEvents.fabricProductionJar",
            fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.gameEvents.fabricDevelopmentJar",
            fabricJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.gameEvents.forgeShadowJar",
            forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
    }
}

val lootConditionRegistryTest = tasks.register<Test>("lootConditionRegistryTest") {
    group = "verification"
    description =
        "Runs exact cross-loader loot-condition ownership and resource-isolation tests."
    dependsOn(
        tasks.named("testClasses"),
        commonJar,
        commonTransformProductionFabric,
        commonTransformProductionForge,
        fabricShadowJar,
        fabricRemapJar,
        forgeShadowJar,
    )
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching(
            "ru.feytox.etherology.forge.LootConditionRegistryResourcesTest",
        )
    }
    inputs.file(commonJar.flatMap { it.archiveFile })
        .withPropertyName("lootConditionCommonJar")
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("lootConditionFabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("lootConditionForgeTransformedCommonJar")
    inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        .withPropertyName("lootConditionFabricDevelopmentJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        .withPropertyName("lootConditionFabricProductionJar")
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        .withPropertyName("lootConditionForgeShadowJar")
    inputs.file(canonicalAttrahiteLootTable)
        .withPropertyName("lootConditionCanonicalAttrahiteLootTable")
    doFirst {
        systemProperty(
            "etherology.lootConditions.commonJar",
            commonJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.lootConditions.fabricTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionFabric.get(),
                "Fabric common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.lootConditions.forgeTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionForge.get(),
                "Forge common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.lootConditions.fabricDevelopmentJar",
            fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.lootConditions.fabricProductionJar",
            fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.lootConditions.forgeShadowJar",
            forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.lootConditions.attrahiteLootTable",
            canonicalAttrahiteLootTable.absolutePath,
        )
    }
}

val etherSourceReloadTest = tasks.register<Test>("etherSourceReloadTest") {
    group = "verification"
    description =
        "Runs exact cross-loader Ether-source listener and default-data isolation tests."
    dependsOn(
        tasks.named("testClasses"),
        commonJar,
        commonTransformProductionFabric,
        commonTransformProductionForge,
        fabricShadowJar,
        fabricRemapJar,
        forgeShadowJar,
    )
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching(
            "ru.feytox.etherology.forge.EtherSourceReloadResourcesTest",
        )
    }
    inputs.file(commonJar.flatMap { it.archiveFile })
        .withPropertyName("etherSourceCommonJar")
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("etherSourceFabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("etherSourceForgeTransformedCommonJar")
    inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        .withPropertyName("etherSourceFabricDevelopmentJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        .withPropertyName("etherSourceFabricProductionJar")
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        .withPropertyName("etherSourceForgeShadowJar")
    inputs.file(canonicalEtherSourceDefault)
        .withPropertyName("etherSourceCanonicalDefault")
    inputs.files(legacyFabricEtherSourceOwners)
        .withPropertyName("legacyFabricEtherSourceOwners")
        .optional()
    doFirst {
        systemProperty(
            "etherology.etherSources.commonJar",
            commonJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.etherSources.fabricTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionFabric.get(),
                "Fabric common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.etherSources.forgeTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionForge.get(),
                "Forge common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.etherSources.fabricDevelopmentJar",
            fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.etherSources.fabricProductionJar",
            fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.etherSources.forgeShadowJar",
            forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.etherSources.defaultResource",
            canonicalEtherSourceDefault.absolutePath,
        )
        systemProperty(
            "etherology.etherSources.repositoryRoot",
            rootProject.projectDir.absolutePath,
        )
    }
}

val enchantmentRegistryTest = tasks.register<Test>("enchantmentRegistryTest") {
    group = "verification"
    description =
        "Runs exact cross-loader enchantment ownership and packaged-tag tests."
    dependsOn(
        tasks.named("testClasses"),
        commonJar,
        commonTransformProductionFabric,
        commonTransformProductionForge,
        fabricShadowJar,
        fabricRemapJar,
        forgeShadowJar,
    )
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching(
            "ru.feytox.etherology.forge.EnchantmentRegistryResourcesTest",
        )
    }
    inputs.file(commonJar.flatMap { it.archiveFile })
        .withPropertyName("enchantmentCommonJar")
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("enchantmentFabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("enchantmentForgeTransformedCommonJar")
    inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        .withPropertyName("enchantmentFabricDevelopmentJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        .withPropertyName("enchantmentFabricProductionJar")
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        .withPropertyName("enchantmentForgeShadowJar")
    inputs.file(canonicalEnchantmentTagFile)
        .withPropertyName("enchantmentCanonicalNonTreasureTag")
    inputs.files(legacyFabricEnchantmentConcreteOwners)
        .withPropertyName("legacyFabricEnchantmentConcreteOwners")
        .optional()
    doFirst {
        systemProperty(
            "etherology.enchantments.commonJar",
            commonJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.enchantments.fabricTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionFabric.get(),
                "Fabric common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.enchantments.forgeTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionForge.get(),
                "Forge common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.enchantments.fabricDevelopmentJar",
            fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.enchantments.fabricProductionJar",
            fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.enchantments.forgeShadowJar",
            forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.enchantments.nonTreasureTag",
            canonicalEnchantmentTagFile.absolutePath,
        )
        systemProperty(
            "etherology.enchantments.repositoryRoot",
            rootProject.projectDir.absolutePath,
        )
    }
}

val particleRegistryTest = tasks.register<Test>("particleRegistryTest") {
    group = "verification"
    description =
        "Runs exact cross-loader particle ownership and packaged-texture tests."
    dependsOn(
        tasks.named("testClasses"),
        commonJar,
        commonTransformProductionFabric,
        commonTransformProductionForge,
        fabricShadowJar,
        fabricRemapJar,
        forgeShadowJar,
    )
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching(
            "ru.feytox.etherology.forge.ParticleRegistryResourcesTest",
        )
    }
    inputs.file(commonJar.flatMap { it.archiveFile })
        .withPropertyName("particleCommonJar")
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("particleFabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("particleForgeTransformedCommonJar")
    inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        .withPropertyName("particleFabricDevelopmentJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        .withPropertyName("particleFabricProductionJar")
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        .withPropertyName("particleForgeShadowJar")
    inputs.dir(rootProject.file("src/client/resources/assets/etherology/particles"))
        .withPropertyName("canonicalParticleDefinitions")
    inputs.dir(rootProject.file("src/client/resources/assets/etherology/textures/particle"))
        .withPropertyName("canonicalParticleTextures")
    inputs.files(
        rootProject.fileTree("src/client/resources/assets/etherology/textures/block") {
            include("*_seal.png")
            include("*_seal_light.png")
        },
    ).withPropertyName("canonicalSealTextures")
    inputs.files(
        rootProject.fileTree("src/main/java/ru/feytox/etherology") {
            include("particle/**")
            include("magic/seal/SealType.java")
            include("util/misc/RGBColor.java")
            include("registry/particle/EtherParticleTypes.java")
        },
    ).withPropertyName("legacyFabricParticleOwners").optional()
    doFirst {
        systemProperty(
            "etherology.particles.commonJar",
            commonJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.particles.fabricTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionFabric.get(),
                "Fabric common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.particles.forgeTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionForge.get(),
                "Forge common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.particles.fabricDevelopmentJar",
            fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.particles.fabricProductionJar",
            fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.particles.forgeShadowJar",
            forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.particles.repositoryRoot",
            rootProject.projectDir.absolutePath,
        )
    }
}

val materialItemRegistryTest = tasks.register<Test>("materialItemRegistryTest") {
    group = "verification"
    description =
        "Runs exact cross-loader material-item ownership and packaged-asset tests."
    dependsOn(
        tasks.named("testClasses"),
        commonJar,
        commonTransformProductionFabric,
        commonTransformProductionForge,
        fabricShadowJar,
        fabricRemapJar,
        forgeShadowJar,
    )
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching(
            "ru.feytox.etherology.forge.MaterialItemRegistryResourcesTest",
        )
    }
    inputs.file(commonJar.flatMap { it.archiveFile })
        .withPropertyName("materialItemCommonJar")
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("materialItemFabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("materialItemForgeTransformedCommonJar")
    inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        .withPropertyName("materialItemFabricDevelopmentJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        .withPropertyName("materialItemFabricProductionJar")
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        .withPropertyName("materialItemForgeShadowJar")
    inputs.files(
        rootProject.fileTree("src/main/generated/assets/etherology/models/item") {
            include("*.json")
        },
    ).withPropertyName("canonicalGeneratedItemModels")
    inputs.files(
        rootProject.fileTree("src/client/resources/assets/etherology/textures/item") {
            include("*.png")
        },
    ).withPropertyName("canonicalItemTextures")
    inputs.file(englishLanguageFile)
        .withPropertyName("materialItemEnglishLanguage")
    doFirst {
        systemProperty(
            "etherology.materialItems.commonJar",
            commonJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.materialItems.fabricTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionFabric.get(),
                "Fabric common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.materialItems.forgeTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionForge.get(),
                "Forge common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.materialItems.fabricDevelopmentJar",
            fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.materialItems.fabricProductionJar",
            fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.materialItems.forgeShadowJar",
            forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.materialItems.repositoryRoot",
            rootProject.projectDir.absolutePath,
        )
    }
}

val foodItemRegistryTest = tasks.register<Test>("foodItemRegistryTest") {
    group = "verification"
    description =
        "Runs exact cross-loader food-item ownership and packaged-resource tests."
    dependsOn(
        tasks.named("testClasses"),
        commonJar,
        commonTransformProductionFabric,
        commonTransformProductionForge,
        fabricShadowJar,
        fabricRemapJar,
        forgeShadowJar,
    )
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching(
            "ru.feytox.etherology.forge.FoodItemRegistryResourcesTest",
        )
    }
    inputs.file(commonJar.flatMap { it.archiveFile })
        .withPropertyName("foodItemCommonJar")
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("foodItemFabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("foodItemForgeTransformedCommonJar")
    inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        .withPropertyName("foodItemFabricDevelopmentJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        .withPropertyName("foodItemFabricProductionJar")
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        .withPropertyName("foodItemForgeShadowJar")
    inputs.file(
        rootProject.file(
            "src/main/generated/assets/etherology/models/item/forest_lantern_crumb.json",
        ),
    ).withPropertyName("canonicalForestLanternCrumbModel")
    inputs.file(
        rootProject.file(
            "src/client/resources/assets/etherology/textures/item/forest_lantern_crumb.png",
        ),
    ).withPropertyName("canonicalForestLanternCrumbTexture")
    inputs.file(englishLanguageFile)
        .withPropertyName("foodItemEnglishLanguage")
    inputs.file(
        rootProject.file("src/main/generated/assets/etherology/lang/ru_ru.json"),
    ).withPropertyName("foodItemRussianLanguage")
    inputs.files(
        rootProject.fileTree("src/main/generated/data/etherology") {
            include(
                "recipes/forest_lantern_crumb.json",
                "recipes/forest_lantern_crumb_from_campfire.json",
                "recipes/forest_lantern_crumb_from_smoking.json",
                "advancements/recipes/food/forest_lantern_crumb.json",
                "advancements/recipes/food/forest_lantern_crumb_from_campfire.json",
                "advancements/recipes/food/forest_lantern_crumb_from_smoking.json",
            )
        },
    ).withPropertyName("deferredForestLanternCrumbCookingData")
    doFirst {
        systemProperty(
            "etherology.foodItems.commonJar",
            commonJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.foodItems.fabricTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionFabric.get(),
                "Fabric common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.foodItems.forgeTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionForge.get(),
                "Forge common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.foodItems.fabricDevelopmentJar",
            fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.foodItems.fabricProductionJar",
            fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.foodItems.forgeShadowJar",
            forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.foodItems.repositoryRoot",
            rootProject.projectDir.absolutePath,
        )
    }
}

val forestLanternBlockTest = tasks.register<Test>("forestLanternBlockTest") {
    group = "verification"
    description =
        "Runs exact cross-loader Forest Lantern ownership, mechanics, and resource tests."
    dependsOn(
        tasks.named("testClasses"),
        commonJar,
        commonTransformProductionFabric,
        commonTransformProductionForge,
        fabricShadowJar,
        fabricRemapJar,
        forgeShadowJar,
    )
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching(
            "ru.feytox.etherology.forge.ForestLanternBlockResourcesTest",
        )
    }
    inputs.file(commonJar.flatMap { it.archiveFile })
        .withPropertyName("forestLanternCommonJar")
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("forestLanternFabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("forestLanternForgeTransformedCommonJar")
    inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        .withPropertyName("forestLanternFabricDevelopmentJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        .withPropertyName("forestLanternFabricProductionJar")
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        .withPropertyName("forestLanternForgeShadowJar")
    inputs.files(
        rootProject.fileTree("src/client/resources/assets/etherology") {
            include("blockstates/forest_lantern.json")
            include("models/block/forest_lantern*.json")
            include("textures/block/forest_lantern*.png")
            include("textures/item/forest_lantern.png")
            include("lang/en_us.json")
        },
        rootProject.fileTree("src/main/generated") {
            include("assets/etherology/models/item/forest_lantern.json")
            include("assets/etherology/lang/ru_ru.json")
            canonicalForestLanternDataEntries.forEach { entry ->
                include("data/$entry")
            }
        },
    ).withPropertyName("canonicalForestLanternResources")
    doFirst {
        systemProperty(
            "etherology.forestLantern.commonJar",
            commonJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.forestLantern.fabricTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionFabric.get(),
                "Fabric common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.forestLantern.forgeTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionForge.get(),
                "Forge common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.forestLantern.fabricDevelopmentJar",
            fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.forestLantern.fabricProductionJar",
            fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.forestLantern.forgeShadowJar",
            forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.forestLantern.repositoryRoot",
            rootProject.projectDir.absolutePath,
        )
    }
}

val attrahiteBlockRegistryTest = tasks.register<Test>("attrahiteBlockRegistryTest") {
    group = "verification"
    description =
        "Runs exact cross-loader Attrahite block ownership and packaged-resource tests."
    dependsOn(
        tasks.named("testClasses"),
        commonJar,
        commonTransformProductionFabric,
        commonTransformProductionForge,
        fabricShadowJar,
        fabricRemapJar,
        forgeShadowJar,
    )
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching(
            "ru.feytox.etherology.forge.AttrahiteBlockRegistryResourcesTest",
        )
    }
    inputs.file(commonJar.flatMap { it.archiveFile })
        .withPropertyName("attrahiteBlockCommonJar")
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("attrahiteBlockFabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("attrahiteBlockForgeTransformedCommonJar")
    inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        .withPropertyName("attrahiteBlockFabricDevelopmentJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        .withPropertyName("attrahiteBlockFabricProductionJar")
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        .withPropertyName("attrahiteBlockForgeShadowJar")
    inputs.files(
        rootProject.fileTree("src/main/generated/assets/etherology") {
            include(
                "blockstates/attrahite.json",
                "blockstates/attrahite_bricks.json",
                "blockstates/attrahite_brick_slab.json",
                "blockstates/attrahite_brick_stairs.json",
                "models/block/attrahite.json",
                "models/block/attrahite_bricks.json",
                "models/block/attrahite_brick_slab.json",
                "models/block/attrahite_brick_slab_top.json",
                "models/block/attrahite_brick_stairs.json",
                "models/block/attrahite_brick_stairs_inner.json",
                "models/block/attrahite_brick_stairs_outer.json",
                "models/item/attrahite.json",
                "models/item/attrahite_bricks.json",
                "models/item/attrahite_brick_slab.json",
                "models/item/attrahite_brick_stairs.json",
            )
        },
        rootProject.fileTree("src/client/resources/assets/etherology/textures/block") {
            include("attrahite.png", "attrahite_bricks.png")
        },
        rootProject.fileTree("src/main/generated") {
            canonicalAttrahiteBlockDataEntries.forEach { entry ->
                include("data/$entry")
            }
        },
        englishLanguageFile,
        rootProject.file("src/main/generated/assets/etherology/lang/ru_ru.json"),
    ).withPropertyName("canonicalAttrahiteBlockResources")
    doFirst {
        systemProperty(
            "etherology.attrahiteBlocks.commonJar",
            commonJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.attrahiteBlocks.fabricTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionFabric.get(),
                "Fabric common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.attrahiteBlocks.forgeTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionForge.get(),
                "Forge common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.attrahiteBlocks.fabricDevelopmentJar",
            fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.attrahiteBlocks.fabricProductionJar",
            fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.attrahiteBlocks.forgeShadowJar",
            forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.attrahiteBlocks.repositoryRoot",
            rootProject.projectDir.absolutePath,
        )
    }
}

val slitheriteBlockRegistryTest = tasks.register<Test>("slitheriteBlockRegistryTest") {
    group = "verification"
    description =
        "Runs exact cross-loader Slitherite ownership and packaged-resource tests."
    dependsOn(
        tasks.named("testClasses"),
        commonJar,
        commonTransformProductionFabric,
        commonTransformProductionForge,
        fabricShadowJar,
        fabricRemapJar,
        forgeShadowJar,
    )
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching(
            "ru.feytox.etherology.forge.SlitheriteCanonicalResourcesTest",
        )
    }
    inputs.file(commonJar.flatMap { it.archiveFile })
        .withPropertyName("slitheriteCommonJar")
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("slitheriteFabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("slitheriteForgeTransformedCommonJar")
    inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        .withPropertyName("slitheriteFabricDevelopmentJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        .withPropertyName("slitheriteFabricProductionJar")
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        .withPropertyName("slitheriteForgeShadowJar")
    inputs.files(
        rootProject.fileTree("src/main/generated/assets/etherology") {
            include("blockstates/*slitherite*.json")
            include("models/block/*slitherite*.json")
            include("models/item/*slitherite*.json")
        },
        rootProject.fileTree("src/client/resources/assets/etherology") {
            include("blockstates/*slitherite*.json")
            include("models/block/*slitherite*.json")
            include("models/item/*slitherite*.json")
            include("textures/block/*slitherite*.png")
        },
        rootProject.fileTree("src/main/generated/data") {
            canonicalSlitheriteDataEntries.forEach { entry -> include(entry) }
        },
        englishLanguageFile,
        rootProject.file("src/main/generated/assets/etherology/lang/ru_ru.json"),
    ).withPropertyName("canonicalSlitheriteResources")
    doFirst {
        systemProperty(
            "etherology.slitheriteBlocks.commonJar",
            commonJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.slitheriteBlocks.fabricTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionFabric.get(),
                "Fabric common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.slitheriteBlocks.forgeTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionForge.get(),
                "Forge common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.slitheriteBlocks.fabricDevelopmentJar",
            fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.slitheriteBlocks.fabricProductionJar",
            fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.slitheriteBlocks.forgeShadowJar",
            forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.slitheriteBlocks.repositoryRoot",
            rootProject.projectDir.absolutePath,
        )
    }
}

val warpCounterRegistryTest = tasks.register<Test>("warpCounterRegistryTest") {
    group = "verification"
    description =
        "Runs exact cross-loader Warp Counter ownership and static-resource tests."
    dependsOn(
        tasks.named("testClasses"),
        commonJar,
        commonTransformProductionFabric,
        commonTransformProductionForge,
        fabricShadowJar,
        fabricRemapJar,
        forgeShadowJar,
    )
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching(
            "ru.feytox.etherology.forge.WarpCounterRegistryResourcesTest",
        )
    }
    inputs.file(commonJar.flatMap { it.archiveFile })
        .withPropertyName("warpCounterCommonJar")
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("warpCounterFabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("warpCounterForgeTransformedCommonJar")
    inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        .withPropertyName("warpCounterFabricDevelopmentJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        .withPropertyName("warpCounterFabricProductionJar")
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        .withPropertyName("warpCounterForgeShadowJar")
    inputs.files(
        rootProject.file(
            "src/client/resources/assets/etherology/models/item/warp_counter.json",
        ),
        rootProject.fileTree("src/main/generated/assets/etherology/models/item") {
            include("warp_counter_*.json")
        },
        rootProject.fileTree("src/client/resources/assets/etherology/textures/item") {
            include("warp_counter_*.png")
        },
        englishLanguageFile,
        rootProject.file("src/main/generated/assets/etherology/lang/ru_ru.json"),
        rootProject.fileTree("src/main/generated/data") {
            canonicalWarpCounterDataEntries.forEach { entry -> include(entry) }
        },
    ).withPropertyName("canonicalWarpCounterStaticResources")
    doFirst {
        systemProperty(
            "etherology.warpCounter.commonJar",
            commonJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.warpCounter.fabricTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionFabric.get(),
                "Fabric common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.warpCounter.forgeTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionForge.get(),
                "Forge common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.warpCounter.fabricDevelopmentJar",
            fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.warpCounter.fabricProductionJar",
            fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.warpCounter.forgeShadowJar",
            forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.warpCounter.repositoryRoot",
            rootProject.projectDir.absolutePath,
        )
    }
}

val lensFoundationCrossArtifactTest =
    tasks.register<Test>("lensFoundationCrossArtifactTest") {
        group = "verification"
        description =
            "Runs exact cross-loader lens-foundation ownership and isolation tests."
        dependsOn(
            tasks.named("testClasses"),
            commonJar,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            fabricShadowJar,
            fabricRemapJar,
            forgeShadowJar,
        )
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        filter {
            includeTestsMatching(
                "ru.feytox.etherology.forge.LensFoundationCrossArtifactTest",
            )
        }
        inputs.file(commonJar.flatMap { it.archiveFile })
            .withPropertyName("lensFoundationCommonJar")
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("lensFoundationFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("lensFoundationForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
            .withPropertyName("lensFoundationFabricDevelopmentJar")
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
            .withPropertyName("lensFoundationFabricProductionJar")
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
            .withPropertyName("lensFoundationForgeShadowJar")
        inputs.files(
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/magic/staff/StaffPattern.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/magic/staff/StaffLenses.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/magic/lens/LensComponent.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/magic/lens/LensDataKeys.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/item/LensRuntimeBackend.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/item/LensRuntime.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/item/LensItem.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/item/UnadjustedLens.java",
            ),
            rootProject.file(
                "fabric/src/main/java/ru/feytox/etherology/item/"
                    + "FabricLensRuntimeBackend.java",
            ),
        ).withPropertyName("canonicalLensFoundationSources")
        doFirst {
            systemProperty(
                "etherology.lensFoundation.commonJar",
                commonJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.lensFoundation.fabricTransformedCommonJar",
                taskOutputJar(
                    commonTransformProductionFabric.get(),
                    "Fabric common production transform",
                ).absolutePath,
            )
            systemProperty(
                "etherology.lensFoundation.forgeTransformedCommonJar",
                taskOutputJar(
                    commonTransformProductionForge.get(),
                    "Forge common production transform",
                ).absolutePath,
            )
            systemProperty(
                "etherology.lensFoundation.fabricDevelopmentJar",
                fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.lensFoundation.fabricProductionJar",
                fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.lensFoundation.forgeShadowJar",
                forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.lensFoundation.repositoryRoot",
                rootProject.projectDir.absolutePath,
            )
        }
    }

val unadjustedLensRegistryTest =
    tasks.register<Test>("unadjustedLensRegistryTest") {
        group = "verification"
        description =
            "Runs exact cross-loader unadjusted-lens ownership and static-resource tests."
        dependsOn(
            tasks.named("testClasses"),
            commonJar,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            fabricShadowJar,
            fabricRemapJar,
            forgeShadowJar,
        )
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        filter {
            includeTestsMatching(
                "ru.feytox.etherology.forge.UnadjustedLensRegistryResourcesTest",
            )
        }
        inputs.file(commonJar.flatMap { it.archiveFile })
            .withPropertyName("unadjustedLensCommonJar")
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("unadjustedLensFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("unadjustedLensForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
            .withPropertyName("unadjustedLensFabricDevelopmentJar")
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
            .withPropertyName("unadjustedLensFabricProductionJar")
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
            .withPropertyName("unadjustedLensForgeShadowJar")
        inputs.files(
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/registry/item/"
                    + "SharedLensItems.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/registry/misc/"
                    + "SharedAlchemyRecipes.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/bootstrap/"
                    + "EtherologyBootstrap.java",
            ),
            rootProject.file(
                "src/main/java/ru/feytox/etherology/registry/item/EItems.java",
            ),
            rootProject.file(
                "src/main/java/ru/feytox/etherology/registry/misc/"
                    + "RecipesRegistry.java",
            ),
            rootProject.file(
                "src/main/java/ru/feytox/etherology/Etherology.java",
            ),
            rootProject.file(
                "src/client/resources/assets/etherology/models/item/"
                    + "unadjusted_lens.json",
            ),
            rootProject.file(
                "src/client/resources/assets/etherology/models/item/"
                    + "unadjusted_cracked_lens.json",
            ),
            rootProject.file(
                "src/client/resources/assets/etherology/textures/item/"
                    + "unadjusted_lens.png",
            ),
            rootProject.file(
                "src/client/resources/assets/etherology/textures/item/"
                    + "unadjusted_cracked_lens.png",
            ),
            englishLanguageFile,
            rootProject.file("src/main/generated/assets/etherology/lang/ru_ru.json"),
            rootProject.file(
                "src/main/generated/data/etherology/recipes/unadjusted_lens.json",
            ),
        ).withPropertyName("canonicalUnadjustedLensSourcesAndResources")
        doFirst {
            systemProperty(
                "etherology.unadjustedLens.commonJar",
                commonJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.unadjustedLens.fabricTransformedCommonJar",
                taskOutputJar(
                    commonTransformProductionFabric.get(),
                    "Fabric common production transform",
                ).absolutePath,
            )
            systemProperty(
                "etherology.unadjustedLens.forgeTransformedCommonJar",
                taskOutputJar(
                    commonTransformProductionForge.get(),
                    "Forge common production transform",
                ).absolutePath,
            )
            systemProperty(
                "etherology.unadjustedLens.fabricDevelopmentJar",
                fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.unadjustedLens.fabricProductionJar",
                fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.unadjustedLens.forgeShadowJar",
                forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.unadjustedLens.repositoryRoot",
                rootProject.projectDir.absolutePath,
            )
        }
    }

val aspectFoundationCrossArtifactTest =
    tasks.register<Test>("aspectFoundationCrossArtifactTest") {
        group = "verification"
        description =
            "Runs exact cross-loader aspect-foundation ownership and isolation tests."
        dependsOn(
            tasks.named("testClasses"),
            commonJar,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            fabricShadowJar,
            fabricRemapJar,
            forgeShadowJar,
        )
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        filter {
            includeTestsMatching(
                "ru.feytox.etherology.forge.AspectFoundationCrossArtifactTest",
            )
        }
        inputs.file(commonJar.flatMap { it.archiveFile })
            .withPropertyName("aspectFoundationCommonJar")
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("aspectFoundationFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("aspectFoundationForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
            .withPropertyName("aspectFoundationFabricDevelopmentJar")
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
            .withPropertyName("aspectFoundationFabricProductionJar")
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
            .withPropertyName("aspectFoundationForgeShadowJar")
        inputs.files(
            rootProject.fileTree(
                "common/src/main/java/ru/feytox/etherology/magic/aspects",
            ) {
                include("*.java")
            },
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/data/aspects/AspectsLoader.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/registry/misc/" +
                    "SharedAspectRegistries.java",
            ),
            rootProject.file(
                "src/main/java/ru/feytox/etherology/registry/misc/RegistriesRegistry.java",
            ),
            rootProject.file(
                "forge/src/main/java/ru/feytox/etherology/forge/" +
                    "ForgeAspectRegistryEvents.java",
            ),
            rootProject.file(
                "forge/src/main/java/ru/feytox/etherology/forge/" +
                    "ForgeAspectReloadEvents.java",
            ),
            rootProject.fileTree(
                "common/src/main/resources/data/etherology/etherology/aspects",
            ) {
                include("*.json")
            },
        ).withPropertyName("canonicalAspectFoundationSources")
        doFirst {
            systemProperty(
                "etherology.aspectFoundation.commonJar",
                commonJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.aspectFoundation.fabricTransformedCommonJar",
                taskOutputJar(
                    commonTransformProductionFabric.get(),
                    "Fabric common production transform",
                ).absolutePath,
            )
            systemProperty(
                "etherology.aspectFoundation.forgeTransformedCommonJar",
                taskOutputJar(
                    commonTransformProductionForge.get(),
                    "Forge common production transform",
                ).absolutePath,
            )
            systemProperty(
                "etherology.aspectFoundation.fabricDevelopmentJar",
                fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.aspectFoundation.fabricProductionJar",
                fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.aspectFoundation.forgeShadowJar",
                forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.aspectFoundation.repositoryRoot",
                rootProject.projectDir.absolutePath,
            )
        }
    }

val canonicalPedestalSourcesAndResources = files(
    rootProject.fileTree(
        "common/src/main/java/ru/feytox/etherology/block/pedestal",
    ) {
        include("*.java")
    },
    rootProject.file(
        "common/src/main/java/ru/feytox/etherology/util/inventory/" +
            "ListBackedInventory.java",
    ),
    rootProject.file(
        "common/src/main/java/ru/feytox/etherology/util/misc/UniqueProvider.java",
    ),
    rootProject.file(
        "common/src/main/java/ru/feytox/etherology/registry/block/" +
            "SharedPedestalBlocks.java",
    ),
    rootProject.file(
        "common/src/main/java/ru/feytox/etherology/registry/item/" +
            "SharedPedestalBlockItems.java",
    ),
    rootProject.file(
        "common/src/main/java/ru/feytox/etherology/registry/block/" +
            "SharedPedestalBlockEntities.java",
    ),
    rootProject.file(
        "common/src/main/java/ru/feytox/etherology/bootstrap/EtherologyBootstrap.java",
    ),
    rootProject.file(
        "fabric/src/main/java/ru/feytox/etherology/block/pedestal/" +
            "FabricPedestalBlockEntityRemovalBackend.java",
    ),
    rootProject.file("fabric/src/main/java/ru/feytox/etherology/EtherologyFabric.java"),
    rootProject.file("src/main/java/ru/feytox/etherology/mixin/DispenserBlockMixin.java"),
    rootProject.file(
        "src/main/java/ru/feytox/etherology/network/interaction/" +
            "RemoveBlockEntityS2C.java",
    ),
    rootProject.file(
        "src/client/java/ru/feytox/etherology/client/block/pedestal/" +
            "PedestalRenderer.java",
    ),
    rootProject.file(
        "src/client/java/ru/feytox/etherology/client/registry/" +
            "BlockRenderLayerMapRegistry.java",
    ),
    rootProject.file(
        "src/client/java/ru/feytox/etherology/client/registry/" +
            "BlockRenderersRegistry.java",
    ),
    rootProject.fileTree("forge/src/main/java/ru/feytox/etherology/forge") {
        include("EtherologyForge.java")
        include("block/pedestal/ForgePedestalBlockEntityRemovalBackend.java")
        include("client/ForgeClientEvents.java")
        include("client/ForgePedestalClientRemoval.java")
        include("client/PedestalRenderer.java")
        include("mixin/PedestalDispenserBlockMixin.java")
        include("network/ForgePedestalNetwork.java")
        include("network/RemovePedestalBlockEntityS2C.java")
    },
    rootProject.file("src/main/resources/etherology.mixins.json"),
    rootProject.file("forge/src/main/resources/etherology.forge.mixins.json"),
    rootProject.fileTree("src/client/resources/assets/etherology") {
        include("**/*pedestal*")
    },
    englishLanguageFile,
    rootProject.fileTree("src/main/generated") {
        canonicalPedestalDataEntries.forEach { entry -> include("data/$entry") }
        include("data/minecraft/tags/blocks/mineable/pickaxe.json")
        include("assets/etherology/lang/ru_ru.json")
    },
)

val pedestalCrossArtifactTest =
    tasks.register<Test>("pedestalCrossArtifactTest") {
        group = "verification"
        description =
            "Runs exact cross-loader Pedestal ownership, isolation, and packaged-resource tests."
        dependsOn(
            tasks.named("testClasses"),
            commonJar,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            fabricShadowJar,
            fabricRemapJar,
            forgeShadowJar,
        )
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        filter {
            includeTestsMatching(
                "ru.feytox.etherology.forge.PedestalCrossArtifactTest",
            )
        }
        inputs.file(commonJar.flatMap { it.archiveFile })
            .withPropertyName("pedestalCommonJar")
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("pedestalFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("pedestalForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
            .withPropertyName("pedestalFabricDevelopmentJar")
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
            .withPropertyName("pedestalFabricProductionJar")
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
            .withPropertyName("pedestalForgeShadowJar")
        inputs.files(canonicalPedestalSourcesAndResources)
            .withPropertyName("canonicalPedestalSourcesAndResources")
        doFirst {
            systemProperty(
                "etherology.pedestal.commonJar",
                commonJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.pedestal.fabricTransformedCommonJar",
                taskOutputJar(
                    commonTransformProductionFabric.get(),
                    "Fabric common production transform",
                ).absolutePath,
            )
            systemProperty(
                "etherology.pedestal.forgeTransformedCommonJar",
                taskOutputJar(
                    commonTransformProductionForge.get(),
                    "Forge common production transform",
                ).absolutePath,
            )
            systemProperty(
                "etherology.pedestal.fabricDevelopmentJar",
                fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.pedestal.fabricProductionJar",
                fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.pedestal.forgeShadowJar",
                forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.pedestal.repositoryRoot",
                rootProject.projectDir.absolutePath,
            )
        }
    }

val alchemyRecipeFoundationCrossArtifactTest =
    tasks.register<Test>("alchemyRecipeFoundationCrossArtifactTest") {
        group = "verification"
        description =
            "Runs exact cross-loader alchemy-recipe ownership and isolation tests."
        dependsOn(
            tasks.named("testClasses"),
            commonJar,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            fabricShadowJar,
            fabricRemapJar,
            forgeShadowJar,
        )
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        filter {
            includeTestsMatching(
                "ru.feytox.etherology.forge.AlchemyRecipeFoundationCrossArtifactTest",
            )
        }
        inputs.file(commonJar.flatMap { it.archiveFile })
            .withPropertyName("alchemyRecipeFoundationCommonJar")
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("alchemyRecipeFoundationFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("alchemyRecipeFoundationForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
            .withPropertyName("alchemyRecipeFoundationFabricDevelopmentJar")
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
            .withPropertyName("alchemyRecipeFoundationFabricProductionJar")
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
            .withPropertyName("alchemyRecipeFoundationForgeShadowJar")
        inputs.files(
            rootProject.fileTree(
                "common/src/main/java/ru/feytox/etherology/recipes",
            ) {
                include("FeyInputRecipe.java")
                include("FeyRecipe.java")
                include("FeyRecipeSerializer.java")
                include("FeyRecipeJsonProvider.java")
                include("RecipeResultComponentBackend.java")
                include("RecipeResultComponents.java")
                include("alchemy/AlchemyRecipe.java")
                include("alchemy/AlchemyRecipeInventory.java")
                include("alchemy/AlchemyRecipeSerializer.java")
            },
            rootProject.file(
                "fabric/src/main/java/ru/feytox/etherology/recipes/" +
                    "FabricRecipeResultComponentBackend.java",
            ),
            rootProject.file(
                "fabric/src/main/java/ru/feytox/etherology/EtherologyFabric.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/registry/misc/"
                    + "SharedAlchemyRecipes.java",
            ),
            rootProject.fileTree("src/main/generated") {
                canonicalAlchemyRecipeDataEntries.forEach { entry ->
                    include("data/$entry")
                }
            },
        ).withPropertyName("canonicalAlchemyRecipeFoundationSources")
        doFirst {
            systemProperty(
                "etherology.alchemyRecipeFoundation.commonJar",
                commonJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.alchemyRecipeFoundation.fabricTransformedCommonJar",
                taskOutputJar(
                    commonTransformProductionFabric.get(),
                    "Fabric common production transform",
                ).absolutePath,
            )
            systemProperty(
                "etherology.alchemyRecipeFoundation.forgeTransformedCommonJar",
                taskOutputJar(
                    commonTransformProductionForge.get(),
                    "Forge common production transform",
                ).absolutePath,
            )
            systemProperty(
                "etherology.alchemyRecipeFoundation.fabricDevelopmentJar",
                fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.alchemyRecipeFoundation.fabricProductionJar",
                fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.alchemyRecipeFoundation.forgeShadowJar",
                forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
            )
            systemProperty(
                "etherology.alchemyRecipeFoundation.repositoryRoot",
                rootProject.projectDir.absolutePath,
            )
        }
    }

val metalBlockRegistryTest = tasks.register<Test>("metalBlockRegistryTest") {
    group = "verification"
    description =
        "Runs exact cross-loader metal-block ownership and packaged-resource tests."
    dependsOn(
        tasks.named("testClasses"),
        commonJar,
        commonTransformProductionFabric,
        commonTransformProductionForge,
        fabricShadowJar,
        fabricRemapJar,
        forgeShadowJar,
    )
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching(
            "ru.feytox.etherology.forge.MetalBlockRegistryResourcesTest",
        )
    }
    inputs.file(commonJar.flatMap { it.archiveFile })
        .withPropertyName("metalBlockCommonJar")
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("metalBlockFabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("metalBlockForgeTransformedCommonJar")
    inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        .withPropertyName("metalBlockFabricDevelopmentJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        .withPropertyName("metalBlockFabricProductionJar")
    inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        .withPropertyName("metalBlockForgeShadowJar")
    inputs.files(
        rootProject.fileTree("src/main/generated/assets/etherology") {
            include(
                "blockstates/azel_block.json",
                "blockstates/ethril_block.json",
                "blockstates/ebony_block.json",
                "models/block/azel_block.json",
                "models/block/ethril_block.json",
                "models/block/ebony_block.json",
                "models/item/azel_block.json",
                "models/item/ethril_block.json",
                "models/item/ebony_block.json",
            )
        },
    ).withPropertyName("canonicalGeneratedMetalBlockAssets")
    inputs.files(
        rootProject.fileTree("src/main/generated/data") {
            include(
                "etherology/loot_tables/blocks/azel_block.json",
                "etherology/loot_tables/blocks/ethril_block.json",
                "etherology/loot_tables/blocks/ebony_block.json",
                "etherology/recipes/azel_block.json",
                "etherology/recipes/azel_ingot_from_azel_block.json",
                "etherology/recipes/ethril_block.json",
                "etherology/recipes/ethril_ingot_from_ethril_block.json",
                "etherology/recipes/ebony_block.json",
                "etherology/recipes/ebony_ingot_from_ebony_block.json",
                "minecraft/tags/blocks/mineable/pickaxe.json",
                "minecraft/tags/blocks/needs_iron_tool.json",
                "minecraft/tags/blocks/beacon_base_blocks.json",
            )
        },
    ).withPropertyName("canonicalGeneratedMetalBlockData")
    inputs.files(
        rootProject.fileTree("src/client/resources/assets/etherology/textures/block") {
            include("azel_block.png", "ethril_block.png", "ebony_block.png")
        },
    ).withPropertyName("canonicalMetalBlockTextures")
    inputs.file(englishLanguageFile)
        .withPropertyName("metalBlockEnglishLanguage")
    doFirst {
        systemProperty(
            "etherology.metalBlocks.commonJar",
            commonJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.metalBlocks.fabricTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionFabric.get(),
                "Fabric common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.metalBlocks.forgeTransformedCommonJar",
            taskOutputJar(
                commonTransformProductionForge.get(),
                "Forge common production transform",
            ).absolutePath,
        )
        systemProperty(
            "etherology.metalBlocks.fabricDevelopmentJar",
            fabricShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.metalBlocks.fabricProductionJar",
            fabricRemapJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.metalBlocks.forgeShadowJar",
            forgeShadowJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "etherology.metalBlocks.repositoryRoot",
            rootProject.projectDir.absolutePath,
        )
    }
}

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

val validateForgeGameEventRegistryMilestone =
    tasks.register("validateForgeGameEventRegistryMilestone") {
        group = "verification"
        description =
            "Accepts shared resonance registration and exact cross-loader game-event tags."
        dependsOn(
            validateForgeSoundRegistryMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricRemapJar,
            gameEventRegistryTest,
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
        inputs.files(canonicalGameEventTagFiles.values)
        doLast {
            val commonJarFile = commonJar.get().archiveFile.get().asFile
            val missingConditions = missingForgeGameEventRegistryMilestone(
                commonJarFile,
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
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion shared game-event registry milestone is incomplete:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val forgeRegistryFoundationServerSafetyTest =
    tasks.register<Exec>("forgeRegistryFoundationServerSafetyTest") {
        group = "verification"
        description =
            "Runs the historical Forge registry-foundation archive-verifier safety tests."
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server_evidence.py",
        )
        inputs.files(
            forgeRegistryFoundationServerEvidenceVerifier,
            forgeRegistryFoundationServerEvidenceTest,
        )
    }

val forgeEtherSourceReloadServerSafetyTest =
    tasks.register<Exec>("forgeEtherSourceReloadServerSafetyTest") {
        group = "verification"
        description =
            "Runs the historical Forge Ether-source reload v6 verifier safety tests."
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server_reload_evidence_v6.py",
        )
        inputs.files(
            forgeServerContractV6,
            forgeServerProfileSnapshotV6,
            forgeEtherSourceReloadServerEvidenceVerifier,
            forgeEtherSourceReloadServerEvidenceTest,
        )
    }

val forgeEnchantmentRegistryServerSafetyTest =
    tasks.register<Exec>("forgeEnchantmentRegistryServerSafetyTest") {
        group = "verification"
        description =
            "Runs the historical Forge enchantment-registry v7 verifier safety tests."
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server_enchantment_evidence_v7.py",
        )
        inputs.files(
            forgeServerContractV7,
            forgeServerProfileSnapshotV7,
            forgeEnchantmentRegistryServerEvidenceVerifier,
            forgeEnchantmentRegistryServerEvidenceTest,
        )
    }

val forgeParticleRegistryServerSafetyTest =
    tasks.register<Exec>("forgeParticleRegistryServerSafetyTest") {
        group = "verification"
        description =
            "Runs the historical Forge particle-registry v10 verifier safety tests."
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server_particle_evidence_v10.py",
        )
        inputs.files(
            forgeServerContractV10,
            forgeServerProfileSnapshotV10,
            forgeParticleRegistryServerEvidenceVerifier,
            forgeParticleRegistryServerEvidenceTest,
        )
    }

val forgeMaterialItemRegistryServerSafetyTest =
    tasks.register<Exec>("forgeMaterialItemRegistryServerSafetyTest") {
        group = "verification"
        description =
            "Runs the historical Forge material-item-registry v11 verifier safety tests."
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server_material_item_evidence_v11.py",
        )
        inputs.files(
            forgeServerContractV11,
            forgeServerProfileSnapshotV11,
            forgeMaterialItemRegistryServerEvidenceVerifier,
            forgeMaterialItemRegistryServerEvidenceTest,
        )
    }

val forgeMetalBlockRegistryServerV12SafetyTest =
    tasks.register<Exec>("forgeMetalBlockRegistryServerV12SafetyTest") {
        group = "verification"
        description =
            "Runs the consumed Forge metal-block-registry v12 verifier safety tests."
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server_metal_block_evidence_v12.py",
        )
        inputs.files(
            forgeServerContractV12,
            forgeServerProfileSnapshotV12,
            forgeMetalBlockRegistryServerEvidenceVerifierV12,
            forgeMetalBlockRegistryServerEvidenceTestV12,
        )
    }

val forgeMetalBlockRegistryServerSafetyTest =
    tasks.register<Exec>("forgeMetalBlockRegistryServerSafetyTest") {
        group = "verification"
        description =
            "Runs the historical Forge metal-block-registry v13 verifier safety tests."
        dependsOn(forgeMetalBlockRegistryServerV12SafetyTest)
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server_metal_block_evidence_v13.py",
        )
        inputs.files(
            forgeServerContractV13,
            forgeServerProfileSnapshotV13,
            forgeMetalBlockRegistryServerEvidenceVerifier,
            forgeMetalBlockRegistryServerEvidenceTest,
        )
    }

val serverProbeSafetyInterlockTest =
    tasks.register("serverProbeSafetyInterlockTest") {
        group = "verification"
        description =
            "Executable-tests the Forge server runner safety interlock without launching Minecraft."

        doLast {
            val allowedTemporaryRoot = layout.buildDirectory.dir("tmp")
                .get()
                .asFile
                .toPath()
            val fixtureRoot = temporaryDir.toPath()
            val validToken = "a".repeat(64)
            val mismatchedToken = "b".repeat(64)
            val expectedProfileId = "etherology-server-probe-fixture-v1"
            val expectedManagedBy = "scripts/e2e/forge_server.py"
            val expectedTaskPath =
                ":forge:1.20.1:runRegistryFoundationServerProbe"
            val expectedScenarioId = "safety-interlock-fixture"

            fun writeMarker(path: Path, profileId: String = expectedProfileId) {
                val marker = mapOf(
                    "schema" to 1,
                    "profile_id" to profileId,
                    "managed_by" to expectedManagedBy,
                    "launch" to mapOf(
                        "task_path" to expectedTaskPath,
                        "scenario" to expectedScenarioId,
                    ),
                )
                path.toFile().writeText(
                    JsonOutput.prettyPrint(JsonOutput.toJson(marker)) + "\n",
                    StandardCharsets.UTF_8,
                )
            }

            fun validFixture(): ServerProbeSafetyInterlockSpec {
                deleteServerProbeSafetyInterlockFixture(
                    fixtureRoot,
                    allowedTemporaryRoot,
                )
                Files.createDirectories(fixtureRoot)
                val runLock = fixtureRoot.resolve("run.lock").toFile()
                runLock.writeText(
                    "pid=12345\ntoken=$validToken\n",
                    StandardCharsets.UTF_8,
                )
                val runAttempt = fixtureRoot.resolve("run.attempted").toFile()
                runAttempt.writeText(
                    "profile_id=$expectedProfileId\n" +
                        "scenario=$expectedScenarioId\n" +
                        "pid=12345\n",
                    StandardCharsets.UTF_8,
                )
                val profileMarker = fixtureRoot.resolve("runtime/profile-marker.json")
                Files.createDirectories(profileMarker.parent)
                writeMarker(profileMarker)
                val evidenceRoot = fixtureRoot.resolve("runtime/evidence")
                val evidenceDirectories = listOf(
                    evidenceRoot.resolve("reports").toFile(),
                    evidenceRoot.resolve("logs").toFile(),
                )
                evidenceDirectories.forEach { directory ->
                    Files.createDirectories(directory.toPath())
                }
                return ServerProbeSafetyInterlockSpec(
                    sealedArchive = fixtureRoot.resolve("sealed-archive").toFile(),
                    ownedPathAnchor = fixtureRoot.parent.toFile(),
                    runToken = validToken,
                    runLock = runLock,
                    runAttempt = runAttempt,
                    profileMarker = profileMarker.toFile(),
                    profileId = expectedProfileId,
                    managedBy = expectedManagedBy,
                    taskPath = expectedTaskPath,
                    scenarioId = expectedScenarioId,
                    evidenceDirectories = evidenceDirectories,
                )
            }

            fun requireFailure(
                description: String,
                expectedKind: ServerProbeSafetyInterlockFailureKind,
                spec: ServerProbeSafetyInterlockSpec,
            ) {
                val failure = serverProbeSafetyInterlockFailure(spec)
                check(failure?.kind == expectedKind) {
                    "$description: expected $expectedKind, got " +
                        (failure?.kind?.toString() ?: "accepted")
                }
            }

            try {
                var spec = validFixture()
                Files.createDirectories(spec.sealedArchive.toPath())
                requireFailure(
                    "sealed archive",
                    ServerProbeSafetyInterlockFailureKind.SEALED_ARCHIVE,
                    spec,
                )

                spec = validFixture().copy(runToken = null)
                requireFailure(
                    "missing token",
                    ServerProbeSafetyInterlockFailureKind.RUN_TOKEN,
                    spec,
                )

                spec = validFixture().copy(runToken = "not-a-token")
                requireFailure(
                    "malformed token",
                    ServerProbeSafetyInterlockFailureKind.RUN_TOKEN,
                    spec,
                )

                spec = validFixture()
                Files.delete(spec.runLock.toPath())
                requireFailure(
                    "missing lock",
                    ServerProbeSafetyInterlockFailureKind.RUN_LOCK_MISSING_OR_LINKED,
                    spec,
                )

                spec = validFixture()
                Files.delete(spec.runLock.toPath())
                val linkedLockTarget = fixtureRoot.resolve("linked-lock-target")
                Files.writeString(
                    linkedLockTarget,
                    "pid=12345\ntoken=$validToken\n",
                    StandardCharsets.UTF_8,
                )
                Files.createSymbolicLink(spec.runLock.toPath(), linkedLockTarget)
                requireFailure(
                    "linked lock",
                    ServerProbeSafetyInterlockFailureKind.RUN_LOCK_MISSING_OR_LINKED,
                    spec,
                )

                spec = validFixture()
                spec.runLock.writeText(
                    "pid=0\ntoken=$validToken\n",
                    StandardCharsets.UTF_8,
                )
                requireFailure(
                    "malformed lock",
                    ServerProbeSafetyInterlockFailureKind.RUN_LOCK_INVALID,
                    spec,
                )

                spec = validFixture()
                Files.delete(spec.runAttempt.toPath())
                requireFailure(
                    "missing launch-attempt marker",
                    ServerProbeSafetyInterlockFailureKind.RUN_ATTEMPT_MISSING_OR_LINKED,
                    spec,
                )

                spec = validFixture()
                Files.delete(spec.runAttempt.toPath())
                val linkedAttemptTarget = fixtureRoot.resolve("linked-attempt-target")
                Files.writeString(
                    linkedAttemptTarget,
                    "profile_id=$expectedProfileId\n" +
                        "scenario=$expectedScenarioId\n" +
                        "pid=12345\n",
                    StandardCharsets.UTF_8,
                )
                Files.createSymbolicLink(
                    spec.runAttempt.toPath(),
                    linkedAttemptTarget,
                )
                requireFailure(
                    "linked launch-attempt marker",
                    ServerProbeSafetyInterlockFailureKind.RUN_ATTEMPT_MISSING_OR_LINKED,
                    spec,
                )

                spec = validFixture()
                spec.runAttempt.writeText(
                    "profile_id=$expectedProfileId\n" +
                        "scenario=another-scenario\n" +
                        "pid=12345\n",
                    StandardCharsets.UTF_8,
                )
                requireFailure(
                    "mismatched launch-attempt marker",
                    ServerProbeSafetyInterlockFailureKind.RUN_ATTEMPT_INVALID,
                    spec,
                )

                spec = validFixture()
                spec.runAttempt.writeText(
                    "profile_id=$expectedProfileId\n" +
                        "scenario=$expectedScenarioId\n" +
                        "pid=54321\n",
                    StandardCharsets.UTF_8,
                )
                requireFailure(
                    "launch-attempt PID mismatch",
                    ServerProbeSafetyInterlockFailureKind.RUN_ATTEMPT_INVALID,
                    spec,
                )

                spec = validFixture()
                val realAttemptParent = fixtureRoot.resolve("real-attempt-parent")
                Files.createDirectories(realAttemptParent)
                val linkedAttemptParent = fixtureRoot.resolve("linked-attempt-parent")
                Files.createSymbolicLink(
                    linkedAttemptParent,
                    realAttemptParent.fileName,
                )
                val parentLinkedAttempt = linkedAttemptParent.resolve("run.attempted")
                Files.writeString(
                    realAttemptParent.resolve("run.attempted"),
                    "profile_id=$expectedProfileId\n" +
                        "scenario=$expectedScenarioId\n" +
                        "pid=12345\n",
                    StandardCharsets.UTF_8,
                )
                requireFailure(
                    "linked launch-attempt parent",
                    ServerProbeSafetyInterlockFailureKind.OWNED_PATH_LINKED,
                    spec.copy(runAttempt = parentLinkedAttempt.toFile()),
                )

                spec = validFixture()
                spec.runLock.writeText(
                    "pid=12345\ntoken=$mismatchedToken\n",
                    StandardCharsets.UTF_8,
                )
                requireFailure(
                    "mismatched token and lock",
                    ServerProbeSafetyInterlockFailureKind.RUN_LOCK_INVALID,
                    spec,
                )

                spec = validFixture()
                Files.delete(spec.profileMarker.toPath())
                requireFailure(
                    "missing marker",
                    ServerProbeSafetyInterlockFailureKind.PROFILE_MARKER_MISSING_OR_LINKED,
                    spec,
                )

                spec = validFixture()
                Files.delete(spec.profileMarker.toPath())
                val linkedMarkerTarget = fixtureRoot.resolve("linked-marker-target.json")
                writeMarker(linkedMarkerTarget)
                Files.createSymbolicLink(
                    spec.profileMarker.toPath(),
                    linkedMarkerTarget,
                )
                requireFailure(
                    "linked marker",
                    ServerProbeSafetyInterlockFailureKind.PROFILE_MARKER_MISSING_OR_LINKED,
                    spec,
                )

                spec = validFixture()
                spec.profileMarker.writeText("{\n", StandardCharsets.UTF_8)
                requireFailure(
                    "malformed marker",
                    ServerProbeSafetyInterlockFailureKind.PROFILE_MARKER_MALFORMED,
                    spec,
                )

                spec = validFixture()
                writeMarker(spec.profileMarker.toPath(), "another-profile")
                requireFailure(
                    "mismatched marker",
                    ServerProbeSafetyInterlockFailureKind.PROFILE_MARKER_MISMATCH,
                    spec,
                )

                spec = validFixture()
                Files.delete(spec.evidenceDirectories[0].toPath())
                requireFailure(
                    "missing evidence directory",
                    ServerProbeSafetyInterlockFailureKind.EVIDENCE_DIRECTORY_INVALID,
                    spec,
                )

                spec = validFixture()
                Files.delete(spec.evidenceDirectories[1].toPath())
                val linkedEvidenceTarget = fixtureRoot.resolve("linked-evidence-target")
                Files.createDirectories(linkedEvidenceTarget)
                Files.createSymbolicLink(
                    spec.evidenceDirectories[1].toPath(),
                    linkedEvidenceTarget,
                )
                requireFailure(
                    "linked evidence directory",
                    ServerProbeSafetyInterlockFailureKind.EVIDENCE_DIRECTORY_INVALID,
                    spec,
                )

                spec = validFixture()
                Files.writeString(
                    spec.evidenceDirectories[0].toPath().resolve("unexpected.txt"),
                    "unexpected\n",
                    StandardCharsets.UTF_8,
                )
                requireFailure(
                    "non-pristine evidence directory",
                    ServerProbeSafetyInterlockFailureKind.EVIDENCE_DIRECTORY_INVALID,
                    spec,
                )

                spec = validFixture()
                check(serverProbeSafetyInterlockFailure(spec) == null) {
                    "The all-valid server-probe safety-interlock fixture was rejected"
                }
                logger.lifecycle(
                    "Validated 20 Forge server-probe safety-interlock fixture cases without " +
                        "launching Minecraft.",
                )
            } finally {
                deleteServerProbeSafetyInterlockFixture(
                    fixtureRoot,
                    allowedTemporaryRoot,
                )
            }
        }
    }

val forgeFoodItemRegistryServerSafetyTest =
    tasks.register<Exec>("forgeFoodItemRegistryServerSafetyTest") {
        group = "verification"
        description =
            "Runs the active Forge food-item runner and v14 verifier safety tests."
        dependsOn(
            forgeMetalBlockRegistryServerSafetyTest,
            serverProbeSafetyInterlockTest,
        )
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server.py",
            "scripts/e2e/test_forge_server_food_item_evidence_v14.py",
        )
        inputs.files(
            forgeServerContractV14,
            forgeServerContractV16,
            forgeServerProfileSnapshotV12,
            forgeServerProfileSnapshotV13,
            forgeServerProfileSnapshotV14,
            forgeServerProfileSnapshotV15,
            forgeServerProfileSnapshotV16,
            forgeRegistryFoundationServerRunner,
            forgeRegistryFoundationServerRunnerTest,
            forgeFoodItemRegistryServerEvidenceVerifier,
            forgeFoodItemRegistryServerEvidenceTest,
            forgeRegistryFoundationServerProfileManifest,
            forgeRegistryFoundationServerProbeSource,
            rootProject.file("release/release-matrix.json"),
            rootProject.file("gradle.properties"),
            rootProject.file("gradlew"),
            project.buildFile,
        )
        inputs.dir(
            rootProject.file("e2e-harness/forge-server/1.20.1/src/main/java"),
        ).withPropertyName("forgeFoodItemRegistryServerProbeSources")
        inputs.dir(
            rootProject.file("e2e-harness/forge-server/1.20.1/src/test/java"),
        ).withPropertyName("forgeFoodItemRegistryServerProbeTests")
    }

val forgeForestLanternServerV15SafetyTest =
    tasks.register<Exec>("forgeForestLanternServerV15SafetyTest") {
        group = "verification"
        description =
            "Runs the consumed Forge Forest Lantern v15 verifier safety tests."
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server_forest_lantern_evidence_v15.py",
        )
        inputs.files(
            forgeServerContractV14,
            forgeServerContractV15,
            forgeServerProfileSnapshotV15,
            forgeForestLanternServerEvidenceVerifierV15,
            forgeForestLanternServerEvidenceTestV15,
        )
    }

val forgeForestLanternServerSafetyTest =
    tasks.register<Exec>("forgeForestLanternServerSafetyTest") {
        group = "verification"
        description =
            "Runs the historical Forge Forest Lantern v16 verifier safety tests."
        dependsOn(
            forgeFoodItemRegistryServerSafetyTest,
            forgeForestLanternServerV15SafetyTest,
        )
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server_forest_lantern_evidence_v16.py",
        )
        inputs.files(
            forgeServerContractV14,
            forgeServerContractV16,
            forgeServerProfileSnapshotV16,
            forgeForestLanternServerEvidenceVerifier,
            forgeForestLanternServerEvidenceTest,
        )
    }

val forgeAttrahiteBlockRegistryServerV17SafetyTest =
    tasks.register<Exec>("forgeAttrahiteBlockRegistryServerV17SafetyTest") {
        group = "verification"
        description =
            "Runs the consumed Forge Attrahite block-registry v17 verifier tests."
        dependsOn(forgeForestLanternServerSafetyTest)
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server_attrahite_evidence_v17.py",
        )
        inputs.files(
            forgeServerContractV17,
            forgeServerProfileSnapshotV17,
            forgeAttrahiteBlockRegistryServerEvidenceVerifier,
            forgeAttrahiteBlockRegistryServerEvidenceTest,
        )
    }

val forgeAttrahiteBlockRegistryServerV18SafetyTest =
    tasks.register<Exec>("forgeAttrahiteBlockRegistryServerV18SafetyTest") {
        group = "verification"
        description =
            "Runs the consumed Forge Attrahite block-registry v18 verifier tests."
        dependsOn(forgeAttrahiteBlockRegistryServerV17SafetyTest)
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server_attrahite_evidence_v18.py",
        )
        inputs.files(
            forgeServerContractV17,
            forgeServerContractV18,
            forgeServerProfileSnapshotV17,
            forgeServerProfileSnapshotV18,
            forgeAttrahiteBlockRegistryServerEvidenceVerifierV18,
            forgeAttrahiteBlockRegistryServerEvidenceTestV18,
        )
    }

val forgeAttrahiteBlockRegistryServerV19SafetyTest =
    tasks.register<Exec>("forgeAttrahiteBlockRegistryServerV19SafetyTest") {
        group = "verification"
        description =
            "Runs the consumed Forge Attrahite block-registry v19 verifier tests."
        dependsOn(forgeAttrahiteBlockRegistryServerV18SafetyTest)
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server_attrahite_evidence_v19.py",
        )
        inputs.files(
            forgeServerContractV19,
            forgeServerProfileSnapshotV19,
            forgeAttrahiteBlockRegistryServerEvidenceVerifierV19,
            forgeAttrahiteBlockRegistryServerEvidenceTestV19,
        )
    }

val forgeSlitheriteBlockRegistryServerSafetyTest =
    tasks.register<Exec>("forgeSlitheriteBlockRegistryServerSafetyTest") {
        group = "verification"
        description =
            "Runs the prepared Forge Slitherite block-registry v20 contract and " +
                "sealed-evidence tests."
        dependsOn(
            forgeAttrahiteBlockRegistryServerV19SafetyTest,
            serverProbeSafetyInterlockTest,
        )
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            "-m",
            "unittest",
            "scripts/e2e/test_forge_server.py",
            "scripts/e2e/test_forge_server_contract_v20.py",
            "scripts/e2e/test_forge_server_slitherite_evidence_v20.py",
            "scripts/e2e/test_macos_guarded_java.py",
        )
        inputs.files(
            forgeServerContractV19,
            forgeServerContractV20,
            forgeServerProfileSnapshotV19,
            forgeServerProfileSnapshotV20,
            forgeAttrahiteBlockRegistryServerEvidenceVerifierV19,
            forgeAttrahiteBlockRegistryServerEvidenceTestV19,
            forgeRegistryFoundationServerRunner,
            forgeRegistryFoundationServerRunnerTest,
            forgeRegistryFoundationServerRunnerTestV20,
            forgeSlitheriteBlockRegistryServerEvidenceVerifierV20,
            forgeSlitheriteBlockRegistryServerEvidenceTestV20,
            forgeRegistryFoundationServerProfileManifest,
            forgeRegistryFoundationServerProbeSource,
            forgeRegistryFoundationServerMemoryHandoffSource,
            rootProject.file("scripts/e2e/macos_guarded_java.py"),
            rootProject.file("scripts/e2e/test_macos_guarded_java.py"),
            rootProject.file("scripts/baseline/macos_memory_guard.py"),
            rootProject.file("scripts/baseline/tests/test_macos_memory_guard.py"),
            slitheriteClientEvidenceContract,
            originalSlitheriteEvidenceVerifier,
            rootProject.file("release/release-matrix.json"),
            rootProject.file("gradle.properties"),
            rootProject.file("gradlew"),
            project.buildFile,
        )
        inputs.dir(
            rootProject.file("e2e-harness/forge-server/1.20.1/src/main/java"),
        ).withPropertyName("forgeSlitheriteBlockRegistryServerProbeSources")
        inputs.dir(
            rootProject.file("e2e-harness/forge-server/1.20.1/src/test/java"),
        ).withPropertyName("forgeSlitheriteBlockRegistryServerProbeTests")
    }

val validateForgeRegistryFoundationServerEvidenceArchiveIntegrity =
    tasks.register("validateForgeRegistryFoundationServerEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Forge registry-foundation server-v4 archive."
        dependsOn(forgeRegistryFoundationServerSafetyTest)
        inputs.file(forgeRegistryFoundationServerEvidenceVerifier)
        inputs.dir(forgeRegistryFoundationServerEvidenceArchive)
            .withPropertyName("forgeRegistryFoundationServerEvidenceArchive")
            .optional()
        doLast {
            val missingConditions =
                missingForgeRegistryFoundationServerEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion registry-foundation server evidence is invalid:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeEtherSourceReloadServerEvidenceArchiveIntegrity =
    tasks.register("validateForgeEtherSourceReloadServerEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Forge Ether-source reload server-v6 archive."
        dependsOn(forgeEtherSourceReloadServerSafetyTest)
        inputs.files(
            forgeServerContractV6,
            forgeServerProfileSnapshotV6,
            forgeEtherSourceReloadServerEvidenceVerifier,
        )
        inputs.dir(forgeEtherSourceReloadServerEvidenceArchive)
            .withPropertyName("forgeEtherSourceReloadServerEvidenceArchive")
            .optional()
        doLast {
            val missingConditions = missingForgeEtherSourceReloadServerEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion Ether-source reload server evidence is invalid:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeEnchantmentRegistryServerEvidenceArchiveIntegrity =
    tasks.register("validateForgeEnchantmentRegistryServerEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Forge enchantment-registry server-v7 archive."
        dependsOn(forgeEnchantmentRegistryServerSafetyTest)
        inputs.files(
            forgeServerContractV7,
            forgeServerProfileSnapshotV7,
            forgeEnchantmentRegistryServerEvidenceVerifier,
        )
        inputs.dir(forgeEnchantmentRegistryServerEvidenceArchive)
            .withPropertyName("forgeEnchantmentRegistryServerEvidenceArchive")
            .optional()
        doLast {
            val missingConditions =
                missingForgeEnchantmentRegistryServerEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion enchantment-registry server evidence is invalid:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeParticleRegistryServerEvidenceArchiveIntegrity =
    tasks.register("validateForgeParticleRegistryServerEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Forge particle-registry server-v10 archive."
        dependsOn(forgeParticleRegistryServerSafetyTest)
        inputs.files(
            forgeServerContractV10,
            forgeServerProfileSnapshotV10,
            forgeParticleRegistryServerEvidenceVerifier,
        )
        if (forgeParticleRegistryServerEvidenceArchive.exists()) {
            inputs.dir(forgeParticleRegistryServerEvidenceArchive)
                .withPropertyName("forgeParticleRegistryServerEvidenceArchive")
        }
        doLast {
            val missingConditions =
                missingForgeParticleRegistryServerEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion particle-registry server evidence is invalid:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeMaterialItemRegistryServerEvidenceArchiveIntegrity =
    tasks.register("validateForgeMaterialItemRegistryServerEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Forge material-item-registry server-v11 archive."
        dependsOn(forgeMaterialItemRegistryServerSafetyTest)
        inputs.files(
            forgeServerContractV11,
            forgeServerProfileSnapshotV11,
            forgeMaterialItemRegistryServerEvidenceVerifier,
        )
        if (forgeMaterialItemRegistryServerEvidenceArchive.exists()) {
            inputs.dir(forgeMaterialItemRegistryServerEvidenceArchive)
                .withPropertyName("forgeMaterialItemRegistryServerEvidenceArchive")
        }
        doLast {
            val missingConditions =
                missingForgeMaterialItemRegistryServerEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion material-item-registry server evidence is invalid:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeMetalBlockRegistryServerEvidenceArchiveIntegrity =
    tasks.register("validateForgeMetalBlockRegistryServerEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Forge metal-block-registry server-v13 archive."
        dependsOn(forgeMetalBlockRegistryServerSafetyTest)
        inputs.files(
            forgeServerContractV13,
            forgeServerProfileSnapshotV13,
            forgeMetalBlockRegistryServerEvidenceVerifier,
        )
        if (forgeMetalBlockRegistryServerEvidenceArchive.exists()) {
            inputs.dir(forgeMetalBlockRegistryServerEvidenceArchive)
                .withPropertyName("forgeMetalBlockRegistryServerEvidenceArchive")
        }
        doLast {
            val missingConditions =
                missingForgeMetalBlockRegistryServerEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion metal-block-registry server evidence is invalid:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeFoodItemRegistryServerEvidenceArchiveIntegrity =
    tasks.register("validateForgeFoodItemRegistryServerEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Forge food-item-registry server-v14 archive."
        dependsOn(forgeFoodItemRegistryServerSafetyTest)
        inputs.files(
            forgeServerContractV14,
            forgeServerProfileSnapshotV14,
            forgeFoodItemRegistryServerEvidenceVerifier,
        )
        if (forgeFoodItemRegistryServerEvidenceArchive.exists()) {
            inputs.dir(forgeFoodItemRegistryServerEvidenceArchive)
                .withPropertyName("forgeFoodItemRegistryServerEvidenceArchive")
        }
        doLast {
            val missingConditions =
                missingForgeFoodItemRegistryServerEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion food-item-registry server evidence is invalid:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeForestLanternServerEvidenceArchiveIntegrity =
    tasks.register("validateForgeForestLanternServerEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Forge Forest Lantern dedicated-server v16 archive."
        dependsOn(forgeForestLanternServerSafetyTest)
        inputs.files(
            forgeServerContractV16,
            forgeServerProfileSnapshotV16,
            forgeForestLanternServerEvidenceVerifier,
        )
        if (forgeForestLanternServerEvidenceArchive.exists()) {
            inputs.dir(forgeForestLanternServerEvidenceArchive)
                .withPropertyName("forgeForestLanternServerEvidenceArchive")
        }
        doLast {
            val missingConditions =
                missingForgeForestLanternServerEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion Forest Lantern server evidence is invalid:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeAttrahiteBlockRegistryServerEvidenceArchiveIntegrity =
    tasks.register("validateForgeAttrahiteBlockRegistryServerEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Forge Attrahite block-registry server-v19 archive."
        dependsOn(forgeAttrahiteBlockRegistryServerV19SafetyTest)
        inputs.files(
            forgeServerContractV19,
            forgeServerProfileSnapshotV19,
            forgeAttrahiteBlockRegistryServerEvidenceVerifierV19,
        )
        if (forgeAttrahiteBlockRegistryServerEvidenceArchiveV19.exists()) {
            inputs.dir(forgeAttrahiteBlockRegistryServerEvidenceArchiveV19)
                .withPropertyName("forgeAttrahiteBlockRegistryServerEvidenceArchive")
        }
        doLast {
            val missingConditions =
                missingForgeAttrahiteBlockRegistryServerEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion Attrahite block-registry server evidence is invalid:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeSlitheriteBlockRegistryServerEvidenceArchiveIntegrity =
    tasks.register("validateForgeSlitheriteBlockRegistryServerEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Forge Slitherite block-registry server-v20 archive."
        dependsOn(forgeSlitheriteBlockRegistryServerSafetyTest)
        inputs.files(
            forgeServerContractV20,
            forgeServerProfileSnapshotV20,
            forgeSlitheriteBlockRegistryServerEvidenceVerifierV20,
        )
        if (forgeSlitheriteBlockRegistryServerEvidenceArchiveV20.exists()) {
            inputs.dir(forgeSlitheriteBlockRegistryServerEvidenceArchiveV20)
                .withPropertyName("forgeSlitheriteBlockRegistryServerEvidenceArchiveV20")
        }
        doLast {
            val missingConditions =
                missingForgeSlitheriteBlockRegistryServerEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion Slitherite block-registry server evidence is " +
                    "invalid:\n${
                        missingConditions.joinToString("\n") { condition ->
                            " - $condition"
                        }
                    }"
            }
        }
    }

val validateForgeForestLanternClientEvidenceArchiveIntegrity =
    tasks.register("validateForgeForestLanternClientEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Forge Forest Lantern packaged-client v13 archive."
        inputs.files(
            forgeForestLanternEvidenceVerifier,
            forgeForestLanternProfileSnapshotV12,
            forgeForestLanternProfileSnapshotV13,
            forgeE2eProfileManifest,
        )
        if (forgeForestLanternClientEvidenceArchive.exists()) {
            inputs.dir(forgeForestLanternClientEvidenceArchive)
                .withPropertyName("forgeForestLanternClientEvidenceArchive")
        }
        doLast {
            val missingConditions =
                missingForgeForestLanternClientEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion Forest Lantern client evidence is invalid:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeAttrahiteBlockRegistryClientEvidenceArchiveIntegrity =
    tasks.register("validateForgeAttrahiteBlockRegistryClientEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Forge Attrahite block-registry packaged-client v17 archive."
        inputs.files(
            forgeAttrahiteEvidenceVerifier,
            forgeAttrahiteEvidenceVerifierV14,
            forgeAttrahiteEvidenceTestV14,
            forgeAttrahiteEvidenceVerifierV15,
            forgeAttrahiteEvidenceTestV15,
            forgeAttrahiteEvidenceVerifierV16,
            forgeAttrahiteEvidenceTestV16,
            forgeChannelProfileSnapshotV11,
            forgeForestLanternProfileSnapshotV12,
            forgeForestLanternProfileSnapshotV13,
            forgeAttrahiteProfileSnapshotV14,
            forgeAttrahiteProfileSnapshotV15,
            forgeAttrahiteProfileSnapshotV16,
            forgeAttrahiteProfileSnapshotV17,
            forgeE2eProfileManifest,
        )
        if (forgeAttrahiteClientEvidenceArchive.exists()) {
            inputs.dir(forgeAttrahiteClientEvidenceArchive)
                .withPropertyName("forgeAttrahiteClientEvidenceArchive")
        }
        doLast {
            val missingConditions =
                missingForgeAttrahiteBlockRegistryClientEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion Attrahite block-registry client evidence is invalid:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeSlitheriteBlockRegistryClientEvidenceArchiveIntegrity =
    tasks.register<Exec>(
        "validateForgeSlitheriteBlockRegistryClientEvidenceArchiveIntegrity",
    ) {
        group = "verification"
        description =
            "Validates the immutable Forge Slitherite block-registry packaged-client v19 archive."
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            forgeSlitheriteEvidenceVerifier.absolutePath,
            "--archive",
            forgeSlitheriteClientEvidenceArchive.absolutePath,
        )
        inputs.files(
            forgeSlitheriteEvidenceVerifier,
            forgeSlitheriteRunContractV19,
            forgeSlitheriteProfileSnapshotV19,
            forgeE2eProfileManifest,
            slitheriteClientEvidenceContract,
            originalSlitheriteEvidenceVerifier,
            rootProject.file("scripts/e2e/forge_client.py"),
            rootProject.file("scripts/e2e/forge_evidence.py"),
        )
        inputs.dir(forgeSlitheriteClientEvidenceArchive)
            .withPropertyName("forgeSlitheriteClientEvidenceArchive")

        doFirst {
            val archiveManifest =
                forgeSlitheriteClientEvidenceArchive.resolve("archive-manifest.json")
            val archiveManifestPath = archiveManifest.toPath()
            check(
                Files.isRegularFile(archiveManifestPath, LinkOption.NOFOLLOW_LINKS) &&
                    !Files.isSymbolicLink(archiveManifestPath),
            ) {
                "Forge Slitherite client-v19 archive manifest is missing or linked"
            }
            val archiveManifestBytes = Files.readAllBytes(archiveManifestPath)
            val archiveManifestDigest = MessageDigest.getInstance("SHA-256")
                .digest(archiveManifestBytes)
                .joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
            check(archiveManifestBytes.size.toLong() == forgeSlitheriteArchiveManifestSize) {
                "Forge Slitherite client-v19 archive manifest size changed: " +
                    archiveManifestBytes.size
            }
            check(archiveManifestDigest == forgeSlitheriteArchiveManifestSha256) {
                "Forge Slitherite client-v19 archive manifest SHA-256 changed: " +
                    archiveManifestDigest
            }
        }
    }

val validateForgeGameEventMilestone = tasks.register("validateForgeGameEventMilestone") {
    group = "verification"
    description = "Accepts the shared game event and exact cross-loader tags."
    dependsOn(validateForgeGameEventRegistryMilestone)
}

val validateForgeLootConditionRegistryMilestone =
    tasks.register("validateForgeLootConditionRegistryMilestone") {
        group = "verification"
        description =
            "Accepts the sole shared loot-condition owner across every production boundary."
        dependsOn(
            validateForgeGameEventMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            lootConditionRegistryTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("lootConditionFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("lootConditionForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.file(canonicalAttrahiteLootTable)
        doLast {
            val missingConditions = missingForgeLootConditionRegistryMilestone(
                commonJar.get().archiveFile.get().asFile,
                taskOutputJar(
                    commonTransformProductionFabric.get(),
                    "Fabric common production transform",
                ),
                taskOutputJar(
                    commonTransformProductionForge.get(),
                    "Forge common production transform",
                ),
                fabricShadowJar.get().archiveFile.get().asFile,
                fabricRemapJar.get().archiveFile.get().asFile,
                forgeShadowJar.get().archiveFile.get().asFile,
            )
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion shared loot-condition registry milestone is " +
                    "incomplete:\n${
                        missingConditions.joinToString("\n") { condition -> " - $condition" }
                    }"
            }
        }
    }

val validateForgeRegistryFoundationMilestone =
    tasks.register("validateForgeRegistryFoundationMilestone") {
        group = "verification"
        description =
            "Accepts the shared registry foundation and its frozen Forge server proof."
        dependsOn(
            validateForgeLootConditionRegistryMilestone,
            validateForgeRegistryFoundationServerEvidenceArchiveIntegrity,
        )
    }

val validateForgeEtherSourceReloadStaticMilestone =
    tasks.register("validateForgeEtherSourceReloadStaticMilestone") {
        group = "verification"
        description =
            "Accepts the sole Common Ether-source listener and exact default server data statically."
        dependsOn(
            validateForgeRegistryFoundationMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            etherSourceReloadTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("etherSourceFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("etherSourceForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.file(canonicalEtherSourceDefault)
        inputs.files(legacyFabricEtherSourceOwners)
            .withPropertyName("legacyFabricEtherSourceOwners")
            .optional()
        doLast {
            val missingConditions = missingForgeEtherSourceReloadMilestone(
                commonJar.get().archiveFile.get().asFile,
                taskOutputJar(
                    commonTransformProductionFabric.get(),
                    "Fabric common production transform",
                ),
                taskOutputJar(
                    commonTransformProductionForge.get(),
                    "Forge common production transform",
                ),
                fabricShadowJar.get().archiveFile.get().asFile,
                fabricRemapJar.get().archiveFile.get().asFile,
                forgeShadowJar.get().archiveFile.get().asFile,
            )
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion Ether-source reload milestone is incomplete:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeEtherSourceReloadMilestone =
    tasks.register("validateForgeEtherSourceReloadMilestone") {
        group = "verification"
        description =
            "Accepts static Ether-source ownership and its frozen native reload proof."
        dependsOn(
            validateForgeEtherSourceReloadStaticMilestone,
            validateForgeEtherSourceReloadServerEvidenceArchiveIntegrity,
        )
    }

val validateForgeEnchantmentRegistryStaticMilestone =
    tasks.register("validateForgeEnchantmentRegistryStaticMilestone") {
        group = "verification"
        description =
            "Accepts the sole shared enchantment owner and exact cross-loader tag statically."
        dependsOn(
            validateForgeEtherSourceReloadMilestone,
            validateForgeAcceptedDataSet,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            enchantmentRegistryTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("enchantmentFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("enchantmentForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.file(canonicalEnchantmentTagFile)
        inputs.files(legacyFabricEnchantmentConcreteOwners)
            .withPropertyName("legacyFabricEnchantmentConcreteOwners")
            .optional()
        doLast {
            val missingConditions = missingForgeEnchantmentRegistryMilestone(
                commonJar.get().archiveFile.get().asFile,
                taskOutputJar(
                    commonTransformProductionFabric.get(),
                    "Fabric common production transform",
                ),
                taskOutputJar(
                    commonTransformProductionForge.get(),
                    "Forge common production transform",
                ),
                fabricShadowJar.get().archiveFile.get().asFile,
                fabricRemapJar.get().archiveFile.get().asFile,
                forgeShadowJar.get().archiveFile.get().asFile,
            )
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion enchantment registry milestone is incomplete:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeEnchantmentRegistryMilestone =
    tasks.register("validateForgeEnchantmentRegistryMilestone") {
        group = "verification"
        description =
            "Accepts shared enchantments with their frozen native Forge reload proof."
        dependsOn(
            validateForgeEnchantmentRegistryStaticMilestone,
            validateForgeEnchantmentRegistryServerEvidenceArchiveIntegrity,
        )
    }

val validateForgeParticleRegistryStaticMilestone =
    tasks.register("validateForgeParticleRegistryStaticMilestone") {
        group = "verification"
        description =
            "Validates the bounded shared particle registry and exact packaged assets statically."
        dependsOn(
            validateForgeEnchantmentRegistryMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            particleRegistryTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("particleFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("particleForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.dir(rootProject.file("src/client/resources/assets/etherology/particles"))
            .withPropertyName("canonicalParticleDefinitions")
        inputs.dir(rootProject.file("src/client/resources/assets/etherology/textures/particle"))
            .withPropertyName("canonicalParticleTextures")
        inputs.files(
            rootProject.fileTree("src/client/resources/assets/etherology/textures/block") {
                include("*_seal.png")
                include("*_seal_light.png")
            },
        ).withPropertyName("canonicalSealTextures")
        inputs.files(
            rootProject.fileTree("src/main/java/ru/feytox/etherology") {
                include("particle/**")
                include("magic/seal/SealType.java")
                include("util/misc/RGBColor.java")
                include("registry/particle/EtherParticleTypes.java")
            },
        ).withPropertyName("legacyFabricParticleOwners").optional()
        doLast {
            val artifacts = listOf(
                commonJar.get().archiveFile.get().asFile,
                taskOutputJar(
                    commonTransformProductionFabric.get(),
                    "Fabric common production transform",
                ),
                taskOutputJar(
                    commonTransformProductionForge.get(),
                    "Forge common production transform",
                ),
                fabricShadowJar.get().archiveFile.get().asFile,
                fabricRemapJar.get().archiveFile.get().asFile,
                forgeShadowJar.get().archiveFile.get().asFile,
            )
            artifacts.forEach { artifact ->
                check(artifact.isFile && !Files.isSymbolicLink(artifact.toPath())) {
                    "Particle static milestone artifact is missing or linked: $artifact"
                }
            }

            val legacySources = listOf(
                "src/main/java/ru/feytox/etherology/registry/particle/"
                    + "EtherParticleTypes.java",
                "src/main/java/ru/feytox/etherology/magic/seal/SealType.java",
                "src/main/java/ru/feytox/etherology/util/misc/RGBColor.java",
                "src/main/java/ru/feytox/etherology/particle/effects/"
                    + "ElectricityParticleEffect.java",
                "src/main/java/ru/feytox/etherology/particle/effects/"
                    + "ItemParticleEffect.java",
                "src/main/java/ru/feytox/etherology/particle/effects/"
                    + "LightParticleEffect.java",
                "src/main/java/ru/feytox/etherology/particle/effects/"
                    + "MovingParticleEffect.java",
                "src/main/java/ru/feytox/etherology/particle/effects/"
                    + "ScalableParticleEffect.java",
                "src/main/java/ru/feytox/etherology/particle/effects/"
                    + "SealParticleEffect.java",
                "src/main/java/ru/feytox/etherology/particle/effects/"
                    + "SimpleParticleEffect.java",
                "src/main/java/ru/feytox/etherology/particle/effects/"
                    + "SparkParticleEffect.java",
                "src/main/java/ru/feytox/etherology/particle/effects/misc/"
                    + "FeyParticleEffect.java",
                "src/main/java/ru/feytox/etherology/particle/effects/misc/"
                    + "FeyParticleType.java",
                "src/main/java/ru/feytox/etherology/particle/subtype/"
                    + "ElectricitySubtype.java",
                "src/main/java/ru/feytox/etherology/particle/subtype/"
                    + "LightSubtype.java",
                "src/main/java/ru/feytox/etherology/particle/subtype/"
                    + "SparkSubtype.java",
            )
            legacySources.forEach { legacySource ->
                val sourceFile = rootProject.file(legacySource)
                check(!sourceFile.exists() && !Files.isSymbolicLink(sourceFile.toPath())) {
                    "Legacy Fabric particle owner remains: $legacySource"
                }
            }

            val expectedDefinitions = setOf(
                "alchemy.json",
                "armillary_sphere.json",
                "electricity1.json",
                "electricity2.json",
                "energy_absorption.json",
                "ether_dot.json",
                "ether_star.json",
                "glint_particle.json",
                "haze.json",
                "light.json",
                "lightning_bolt.json",
                "redstone_flash.json",
                "redstone_stream.json",
                "resonation.json",
                "rising.json",
                "scalable_sweep.json",
                "seal.json",
                "shockwave.json",
                "spark.json",
                "steam.json",
                "vital.json",
            )
            val definitionDirectory =
                rootProject.file("src/client/resources/assets/etherology/particles")
            check(
                definitionDirectory.isDirectory
                    && !Files.isSymbolicLink(definitionDirectory.toPath()),
            ) {
                "Canonical particle definition directory is missing or linked"
            }
            val actualDefinitions = definitionDirectory.listFiles()
                ?.filter { file -> file.isFile && file.extension == "json" }
                ?.onEach { file ->
                    check(!Files.isSymbolicLink(file.toPath())) {
                        "Canonical particle definition is linked: $file"
                    }
                }
                ?.map { file -> file.name }
                ?.toSet()
                ?: emptySet()
            check(actualDefinitions == expectedDefinitions) {
                "Canonical particle definitions differ: "
                    .plus("expected=$expectedDefinitions, actual=$actualDefinitions")
            }

            val textureDirectory =
                rootProject.file("src/client/resources/assets/etherology/textures/particle")
            check(
                textureDirectory.isDirectory
                    && !Files.isSymbolicLink(textureDirectory.toPath()),
            ) {
                "Canonical particle texture directory is missing or linked"
            }
            val particleTextures = textureDirectory.walkTopDown()
                .filter { file -> file.isFile && file.extension == "png" }
                .onEach { file ->
                    check(!Files.isSymbolicLink(file.toPath())) {
                        "Canonical particle texture is linked: $file"
                    }
                }
                .toList()
            check(particleTextures.size == 134) {
                "Expected 134 canonical particle textures, found ${particleTextures.size}"
            }

            val sealTextureDirectory =
                rootProject.file("src/client/resources/assets/etherology/textures/block")
            val sealTextureNames = setOf(
                "keta_seal.png",
                "keta_seal_light.png",
                "rella_seal.png",
                "rella_seal_light.png",
                "via_seal.png",
                "via_seal_light.png",
                "clos_seal.png",
                "clos_seal_light.png",
            )
            sealTextureNames.forEach { textureName ->
                val texture = sealTextureDirectory.resolve(textureName)
                check(texture.isFile && !Files.isSymbolicLink(texture.toPath())) {
                    "Canonical seal texture is missing or linked: $texture"
                }
            }
        }
    }

val validateForgeParticleRegistryMilestone =
    tasks.register("validateForgeParticleRegistryMilestone") {
        group = "verification"
        description =
            "Accepts shared particles with their frozen native Forge registry proof."
        dependsOn(
            validateForgeParticleRegistryStaticMilestone,
            validateForgeParticleRegistryServerEvidenceArchiveIntegrity,
        )
    }

val validateForgeMaterialItemRegistryStaticMilestone =
    tasks.register("validateForgeMaterialItemRegistryStaticMilestone") {
        group = "verification"
        description =
            "Validates the bounded shared material-item catalog and exact packaged assets."
        dependsOn(
            validateForgeParticleRegistryMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            materialItemRegistryTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("materialItemFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("materialItemForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.files(
            rootProject.fileTree("src/main/generated/assets/etherology/models/item") {
                include("*.json")
            },
        ).withPropertyName("canonicalGeneratedItemModels")
        inputs.files(
            rootProject.fileTree("src/client/resources/assets/etherology/textures/item") {
                include("*.png")
            },
        ).withPropertyName("canonicalItemTextures")
        inputs.file(englishLanguageFile)
    }

val validateForgeMaterialItemRegistryMilestone =
    tasks.register("validateForgeMaterialItemRegistryMilestone") {
        group = "verification"
        description =
            "Accepts shared material items with their frozen native Forge registry proof."
        dependsOn(
            validateForgeMaterialItemRegistryStaticMilestone,
            validateForgeMaterialItemRegistryServerEvidenceArchiveIntegrity,
        )
    }

val validateForgeMetalBlockRegistryStaticMilestone =
    tasks.register("validateForgeMetalBlockRegistryStaticMilestone") {
        group = "verification"
        description =
            "Validates the bounded shared metal blocks, BlockItems, and exact resources."
        dependsOn(
            validateForgeMaterialItemRegistryMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            metalBlockRegistryTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("metalBlockFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("metalBlockForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.files(
            rootProject.fileTree("src/main/generated") {
                include(
                    "assets/etherology/blockstates/azel_block.json",
                    "assets/etherology/blockstates/ethril_block.json",
                    "assets/etherology/blockstates/ebony_block.json",
                    "assets/etherology/models/block/azel_block.json",
                    "assets/etherology/models/block/ethril_block.json",
                    "assets/etherology/models/block/ebony_block.json",
                    "assets/etherology/models/item/azel_block.json",
                    "assets/etherology/models/item/ethril_block.json",
                    "assets/etherology/models/item/ebony_block.json",
                    "data/etherology/loot_tables/blocks/azel_block.json",
                    "data/etherology/loot_tables/blocks/ethril_block.json",
                    "data/etherology/loot_tables/blocks/ebony_block.json",
                    "data/etherology/recipes/azel_block.json",
                    "data/etherology/recipes/azel_ingot_from_azel_block.json",
                    "data/etherology/recipes/ethril_block.json",
                    "data/etherology/recipes/ethril_ingot_from_ethril_block.json",
                    "data/etherology/recipes/ebony_block.json",
                    "data/etherology/recipes/ebony_ingot_from_ebony_block.json",
                    "data/minecraft/tags/blocks/mineable/pickaxe.json",
                    "data/minecraft/tags/blocks/needs_iron_tool.json",
                    "data/minecraft/tags/blocks/beacon_base_blocks.json",
                )
            },
        ).withPropertyName("canonicalMetalBlockGeneratedResources")
        inputs.files(
            rootProject.fileTree("src/client/resources/assets/etherology/textures/block") {
                include("azel_block.png", "ethril_block.png", "ebony_block.png")
            },
        ).withPropertyName("canonicalMetalBlockTextures")
        inputs.file(englishLanguageFile)
    }

val validateForgeMetalBlockRegistryMilestone =
    tasks.register("validateForgeMetalBlockRegistryMilestone") {
        group = "verification"
        description =
            "Combines current static metal-block checks with immutable capture-time Forge proof."
        dependsOn(
            validateForgeMetalBlockRegistryStaticMilestone,
            validateForgeMetalBlockRegistryServerEvidenceArchiveIntegrity,
        )
    }

val validateForgeFoodItemRegistryStaticMilestone =
    tasks.register("validateForgeFoodItemRegistryStaticMilestone") {
        group = "verification"
        description =
            "Validates the bounded shared food item and exact packaged resources."
        dependsOn(
            validateForgeMetalBlockRegistryMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            foodItemRegistryTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("foodItemFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("foodItemForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.file(
            rootProject.file(
                "src/main/generated/assets/etherology/models/item/forest_lantern_crumb.json",
            ),
        ).withPropertyName("canonicalForestLanternCrumbModel")
        inputs.file(
            rootProject.file(
                "src/client/resources/assets/etherology/textures/item/forest_lantern_crumb.png",
            ),
        ).withPropertyName("canonicalForestLanternCrumbTexture")
        inputs.file(englishLanguageFile)
            .withPropertyName("foodItemEnglishLanguage")
        inputs.file(
            rootProject.file("src/main/generated/assets/etherology/lang/ru_ru.json"),
        ).withPropertyName("foodItemRussianLanguage")
        inputs.files(
            rootProject.fileTree("src/main/generated/data/etherology") {
                include(
                    "recipes/forest_lantern_crumb.json",
                    "recipes/forest_lantern_crumb_from_campfire.json",
                    "recipes/forest_lantern_crumb_from_smoking.json",
                    "advancements/recipes/food/forest_lantern_crumb.json",
                    "advancements/recipes/food/forest_lantern_crumb_from_campfire.json",
                    "advancements/recipes/food/forest_lantern_crumb_from_smoking.json",
                )
            },
        ).withPropertyName("deferredForestLanternCrumbCookingData")
    }

val validateForgeFoodItemRegistryMilestone =
    tasks.register("validateForgeFoodItemRegistryMilestone") {
        group = "verification"
        description =
            "Combines exact static food-item checks with immutable native Forge proof."
        dependsOn(
            validateForgeFoodItemRegistryStaticMilestone,
            validateForgeFoodItemRegistryServerEvidenceArchiveIntegrity,
        )
    }

val validateForgeForestLanternStaticMilestone =
    tasks.register("validateForgeForestLanternStaticMilestone") {
        group = "verification"
        description =
            "Validates the shared Forest Lantern block, behavior bridges, and exact resources."
        dependsOn(
            validateForgeFoodItemRegistryMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            forestLanternBlockTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("forestLanternFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("forestLanternForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.files(
            rootProject.fileTree("src/client/resources/assets/etherology") {
                include("blockstates/forest_lantern.json")
                include("models/block/forest_lantern*.json")
                include("textures/block/forest_lantern*.png")
                include("textures/item/forest_lantern.png")
                include("lang/en_us.json")
            },
            rootProject.fileTree("src/main/generated") {
                include("assets/etherology/models/item/forest_lantern.json")
                include("assets/etherology/lang/ru_ru.json")
                canonicalForestLanternDataEntries.forEach { entry ->
                    include("data/$entry")
                }
            },
        ).withPropertyName("canonicalForestLanternResources")
    }

val validateForgeForestLanternMilestone =
    tasks.register("validateForgeForestLanternMilestone") {
        group = "verification"
        description =
            "Combines exact static Forest Lantern checks with immutable native Forge proof."
        dependsOn(
            validateForgeForestLanternStaticMilestone,
            validateForgeForestLanternServerEvidenceArchiveIntegrity,
            validateForgeForestLanternClientEvidenceArchiveIntegrity,
        )
    }

val validateForgeAttrahiteStaticMilestone =
    tasks.register("validateForgeAttrahiteStaticMilestone") {
        group = "verification"
        description =
            "Validates the shared four-ID Attrahite block family and exact resources."
        dependsOn(
            validateForgeForestLanternMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            attrahiteBlockRegistryTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("attrahiteFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("attrahiteForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.files(
            rootProject.fileTree("src/main/generated/assets/etherology") {
                include("blockstates/attrahite*.json")
                include("models/block/attrahite*.json")
                include("models/item/attrahite*.json")
                include("lang/ru_ru.json")
            },
            rootProject.fileTree("src/client/resources/assets/etherology") {
                include("textures/block/attrahite*.png")
                include("lang/en_us.json")
            },
            rootProject.fileTree("src/main/generated") {
                canonicalAttrahiteBlockDataEntries.forEach { entry ->
                    include("data/$entry")
                }
            },
        ).withPropertyName("canonicalAttrahiteBlockResources")
    }

val validateForgeAttrahiteMilestone =
    tasks.register("validateForgeAttrahiteMilestone") {
        group = "verification"
        description =
            "Blocks the Attrahite slice until static checks and native Forge proof are accepted."
        dependsOn(
            validateForgeAttrahiteStaticMilestone,
            validateForgeAttrahiteBlockRegistryServerEvidenceArchiveIntegrity,
            validateForgeAttrahiteBlockRegistryClientEvidenceArchiveIntegrity,
        )
        doLast {
            val missingConditions = missingForgeAttrahiteNativeEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion Attrahite native acceptance is incomplete:\n${
                    missingConditions.joinToString("\n") { condition -> " - $condition" }
                }"
            }
        }
    }

val validateForgeSlitheriteStaticMilestone =
    tasks.register("validateForgeSlitheriteStaticMilestone") {
        group = "verification"
        description =
            "Validates the shared 17-ID Slitherite family and its exact packaged resources."
        dependsOn(
            validateForgeAttrahiteMilestone,
            validateForgeAcceptedDataSet,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            slitheriteBlockRegistryTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("slitheriteFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("slitheriteForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.files(
            rootProject.fileTree("src/main/generated/assets/etherology") {
                include("blockstates/*slitherite*.json")
                include("models/block/*slitherite*.json")
                include("models/item/*slitherite*.json")
            },
            rootProject.fileTree("src/client/resources/assets/etherology") {
                include("blockstates/*slitherite*.json")
                include("models/block/*slitherite*.json")
                include("models/item/*slitherite*.json")
                include("textures/block/*slitherite*.png")
                include("lang/en_us.json")
            },
            rootProject.fileTree("src/main/generated") {
                canonicalSlitheriteDataEntries.forEach { entry -> include("data/$entry") }
                include("assets/etherology/lang/ru_ru.json")
            },
        ).withPropertyName("canonicalSlitheriteResources")
    }

val validateForgeSlitheriteMilestone =
    tasks.register("validateForgeSlitheriteMilestone") {
        group = "verification"
        description =
            "Blocks the Slitherite slice until static checks and native Forge " +
                "dedicated-server and packaged-client proof are accepted."
        dependsOn(
            validateForgeSlitheriteStaticMilestone,
            validateForgeSlitheriteBlockRegistryServerEvidenceArchiveIntegrity,
            validateForgeSlitheriteBlockRegistryClientEvidenceArchiveIntegrity,
        )
        doLast {
            val missingConditions = missingForgeSlitheriteNativeEvidenceMilestone()
            check(missingConditions.isEmpty()) {
                "Forge $minecraftVersion Slitherite native acceptance is " +
                    "incomplete:\n${
                        missingConditions.joinToString("\n") { condition ->
                            " - $condition"
                        }
                    }"
            }
        }
    }

val validateForgeWarpCounterStaticMilestone =
    tasks.register("validateForgeWarpCounterStaticMilestone") {
        group = "verification"
        description =
            "Validates the shared Warp Counter registration and exact static resources; " +
                "its corruption-driven model predicate remains deferred."
        dependsOn(
            validateForgeSlitheriteMilestone,
            validateForgeAcceptedDataSet,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            warpCounterRegistryTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("warpCounterFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("warpCounterForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.files(
            rootProject.file(
                "src/client/resources/assets/etherology/models/item/warp_counter.json",
            ),
            rootProject.fileTree("src/main/generated/assets/etherology/models/item") {
                include("warp_counter_*.json")
            },
            rootProject.fileTree(
                "src/client/resources/assets/etherology/textures/item",
            ) {
                include("warp_counter_*.png")
            },
            englishLanguageFile,
            rootProject.file("src/main/generated/assets/etherology/lang/ru_ru.json"),
            rootProject.fileTree("src/main/generated/data") {
                canonicalWarpCounterDataEntries.forEach { entry -> include(entry) }
            },
        ).withPropertyName("canonicalWarpCounterStaticResources")
    }

val validateForgeLensFoundationStaticMilestone =
    tasks.register("validateForgeLensFoundationStaticMilestone") {
        group = "verification"
        description =
            "Validates canonical shared lens types, Fabric delegation, and Forge isolation."
        dependsOn(
            validateForgeWarpCounterStaticMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            lensFoundationCrossArtifactTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("lensFoundationFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("lensFoundationForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.files(
            rootProject.fileTree("common/src/main/java/ru/feytox/etherology") {
                include("item/LensItem.java")
                include("item/LensRuntime.java")
                include("item/LensRuntimeBackend.java")
                include("item/UnadjustedLens.java")
                include("magic/lens/LensComponent.java")
                include("magic/lens/LensDataKeys.java")
                include("magic/staff/StaffLenses.java")
                include("magic/staff/StaffPattern.java")
            },
            rootProject.file(
                "fabric/src/main/java/ru/feytox/etherology/item/"
                    + "FabricLensRuntimeBackend.java",
            ),
            rootProject.file(
                "src/main/java/ru/feytox/etherology/registry/misc/ComponentTypes.java",
            ),
            rootProject.file(
                "fabric/src/main/java/ru/feytox/etherology/EtherologyFabric.java",
            ),
        ).withPropertyName("canonicalLensFoundationSources")
    }

val validateForgeUnadjustedLensStaticMilestone =
    tasks.register("validateForgeUnadjustedLensStaticMilestone") {
        group = "verification"
        description =
            "Validates one shared unadjusted-lens registry owner, exact static assets, " +
                "and cross-loader alchemy registration and packaging; native lens behavior " +
                "remains deferred."
        dependsOn(
            validateForgeLensFoundationStaticMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            unadjustedLensRegistryTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("unadjustedLensFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("unadjustedLensForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.files(
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/registry/item/"
                    + "SharedLensItems.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/registry/misc/"
                    + "SharedAlchemyRecipes.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/bootstrap/"
                    + "EtherologyBootstrap.java",
            ),
            rootProject.file(
                "src/main/java/ru/feytox/etherology/registry/item/EItems.java",
            ),
            rootProject.file(
                "src/main/java/ru/feytox/etherology/registry/misc/"
                    + "RecipesRegistry.java",
            ),
            rootProject.file(
                "src/main/java/ru/feytox/etherology/Etherology.java",
            ),
            rootProject.file(
                "src/client/resources/assets/etherology/models/item/"
                    + "unadjusted_lens.json",
            ),
            rootProject.file(
                "src/client/resources/assets/etherology/models/item/"
                    + "unadjusted_cracked_lens.json",
            ),
            rootProject.file(
                "src/client/resources/assets/etherology/textures/item/"
                    + "unadjusted_lens.png",
            ),
            rootProject.file(
                "src/client/resources/assets/etherology/textures/item/"
                    + "unadjusted_cracked_lens.png",
            ),
            englishLanguageFile,
            rootProject.file("src/main/generated/assets/etherology/lang/ru_ru.json"),
            rootProject.file(
                "src/main/generated/data/etherology/recipes/unadjusted_lens.json",
            ),
        ).withPropertyName("canonicalUnadjustedLensSourcesAndResources")
    }

val validateForgeAspectFoundationStaticMilestone =
    tasks.register("validateForgeAspectFoundationStaticMilestone") {
        group = "verification"
        description =
            "Validates canonical shared aspect types, synced datapack bridges, resources, " +
                "and exact serialization/order contracts."
        dependsOn(
            validateForgeUnadjustedLensStaticMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            aspectFoundationCrossArtifactTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("aspectFoundationFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("aspectFoundationForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.files(
            rootProject.fileTree(
                "common/src/main/java/ru/feytox/etherology/magic/aspects",
            ) {
                include("*.java")
            },
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/data/aspects/AspectsLoader.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/registry/misc/" +
                    "SharedAspectRegistries.java",
            ),
            rootProject.file(
                "src/main/java/ru/feytox/etherology/registry/misc/RegistriesRegistry.java",
            ),
            rootProject.file(
                "forge/src/main/java/ru/feytox/etherology/forge/" +
                    "ForgeAspectRegistryEvents.java",
            ),
            rootProject.file(
                "forge/src/main/java/ru/feytox/etherology/forge/" +
                    "ForgeAspectReloadEvents.java",
            ),
            rootProject.fileTree(
                "common/src/main/resources/data/etherology/etherology/aspects",
            ) {
                include("*.json")
            },
        ).withPropertyName("canonicalAspectFoundationSources")
    }

val validateForgePedestalStaticMilestone =
    tasks.register("validateForgePedestalStaticMilestone") {
        group = "verification"
        description =
            "Validates exact shared Pedestal ownership, loader isolation, transformed " +
                "contracts, and packaged resources."
        dependsOn(
            validateForgeAspectFoundationStaticMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            pedestalCrossArtifactTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("pedestalFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("pedestalForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.files(canonicalPedestalSourcesAndResources)
            .withPropertyName("canonicalPedestalSourcesAndResources")
    }

val validateForgeAlchemyRecipeFoundationStaticMilestone =
    tasks.register("validateForgeAlchemyRecipeFoundationStaticMilestone") {
        group = "verification"
        description =
            "Validates the canonical shared alchemy serializer/type registration, recipes, " +
                "and the narrow 1.20.1 component backend."
        dependsOn(
            validateForgePedestalStaticMilestone,
            commonJar,
            commonTest,
            fabricTest,
            fabricShadowJar,
            fabricRemapJar,
            alchemyRecipeFoundationCrossArtifactTest,
            commonTransformProductionFabric,
            commonTransformProductionForge,
            forgeShadowJar,
            tasks.named("test"),
        )
        inputs.file(commonJar.flatMap { it.archiveFile })
        inputs.files(commonTransformProductionFabric)
            .withPropertyName("alchemyRecipeFoundationFabricTransformedCommonJar")
        inputs.files(commonTransformProductionForge)
            .withPropertyName("alchemyRecipeFoundationForgeTransformedCommonJar")
        inputs.file(fabricShadowJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })
        inputs.files(
            rootProject.fileTree(
                "common/src/main/java/ru/feytox/etherology/recipes",
            ) {
                include("FeyInputRecipe.java")
                include("FeyRecipe.java")
                include("FeyRecipeSerializer.java")
                include("FeyRecipeJsonProvider.java")
                include("RecipeResultComponentBackend.java")
                include("RecipeResultComponents.java")
                include("alchemy/AlchemyRecipe.java")
                include("alchemy/AlchemyRecipeInventory.java")
                include("alchemy/AlchemyRecipeSerializer.java")
            },
            rootProject.file(
                "fabric/src/main/java/ru/feytox/etherology/recipes/" +
                    "FabricRecipeResultComponentBackend.java",
            ),
            rootProject.file(
                "fabric/src/main/java/ru/feytox/etherology/EtherologyFabric.java",
            ),
            rootProject.file(
                "common/src/main/java/ru/feytox/etherology/registry/misc/"
                    + "SharedAlchemyRecipes.java",
            ),
            rootProject.fileTree("src/main/generated") {
                canonicalAlchemyRecipeDataEntries.forEach { entry ->
                    include("data/$entry")
                }
            },
        ).withPropertyName("canonicalAlchemyRecipeFoundationSources")
    }

val validateForgeAuthoritativeRegistrySpineMilestone =
    tasks.register("validateForgeAuthoritativeRegistrySpineMilestone") {
        group = "verification"
        description =
            "Blocks broad gameplay until every canonical runtime registry has one shared owner."
        dependsOn(validateForgeAlchemyRecipeFoundationStaticMilestone)
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
        validateForgeGameEventMilestone,
        validateForgeLootConditionRegistryMilestone,
        validateForgeRegistryFoundationMilestone,
        validateForgeEtherSourceReloadMilestone,
        validateForgeEnchantmentRegistryMilestone,
        validateForgeParticleRegistryMilestone,
        validateForgeMaterialItemRegistryMilestone,
        validateForgeMetalBlockRegistryMilestone,
        validateForgeFoodItemRegistryMilestone,
        validateForgeForestLanternMilestone,
        validateForgeAttrahiteMilestone,
        validateForgeSlitheriteMilestone,
        validateForgeWarpCounterStaticMilestone,
        validateForgeLensFoundationStaticMilestone,
        validateForgeUnadjustedLensStaticMilestone,
        validateForgeAspectFoundationStaticMilestone,
        validateForgePedestalStaticMilestone,
        validateForgeAlchemyRecipeFoundationStaticMilestone,
        validateForgeAuthoritativeRegistrySpineMilestone,
        validateForgeReleaseReadinessMilestone,
    )
}

tasks.register("verifyForgePortGateClosed") {
    group = "verification"
    description = "Reports the first incomplete forward milestone without serving as a release gate."
    dependsOn(validateForgeAlchemyRecipeFoundationStaticMilestone)
    inputs.file(commonJar.flatMap { it.archiveFile })
    inputs.dir(forgeMainClasses)
    inputs.files(etherealChannelResources + englishLanguageFile)
    inputs.files(soundManifest, englishLanguageFile)
    inputs.dir(soundDirectory)
    inputs.files(canonicalGameEventTagFiles.values)
    inputs.file(canonicalAttrahiteLootTable)
    inputs.files(
        rootProject.file(
            "src/client/resources/assets/etherology/models/item/warp_counter.json",
        ),
        rootProject.fileTree("src/main/generated/assets/etherology/models/item") {
            include("warp_counter_*.json")
        },
        rootProject.fileTree("src/client/resources/assets/etherology/textures/item") {
            include("warp_counter_*.png")
        },
        rootProject.fileTree("src/main/generated/data") {
            canonicalWarpCounterDataEntries.forEach { entry -> include(entry) }
        },
    ).withPropertyName("canonicalWarpCounterStaticResources")
    inputs.files(
        rootProject.fileTree("src/main/generated/assets/etherology") {
            include("blockstates/attrahite*.json")
            include("models/block/attrahite*.json")
            include("models/item/attrahite*.json")
            include("lang/ru_ru.json")
        },
        rootProject.fileTree("src/client/resources/assets/etherology") {
            include("textures/block/attrahite*.png")
            include("lang/en_us.json")
        },
        rootProject.fileTree("src/main/generated") {
            canonicalAttrahiteBlockDataEntries.forEach { entry ->
                include("data/$entry")
            }
        },
    ).withPropertyName("canonicalAttrahiteBlockResources")
    inputs.file(canonicalEtherSourceDefault)
    inputs.file(canonicalEnchantmentTagFile)
    inputs.files(legacyFabricEtherSourceOwners)
        .withPropertyName("legacyFabricEtherSourceOwners")
        .optional()
    inputs.files(legacyFabricEnchantmentConcreteOwners)
        .withPropertyName("legacyFabricEnchantmentConcreteOwners")
        .optional()
    inputs.files(
        forgeRegistryFoundationServerEvidenceVerifier,
        forgeRegistryFoundationServerProfileManifest,
        forgeServerContractV6,
        forgeServerProfileSnapshotV6,
        forgeEtherSourceReloadServerEvidenceVerifier,
        forgeServerContractV7,
        forgeServerProfileSnapshotV7,
        forgeEnchantmentRegistryServerEvidenceVerifier,
        forgeServerContractV10,
        forgeServerProfileSnapshotV10,
        forgeParticleRegistryServerEvidenceVerifier,
        forgeServerContractV11,
        forgeServerProfileSnapshotV11,
        forgeMaterialItemRegistryServerEvidenceVerifier,
        forgeServerContractV12,
        forgeServerProfileSnapshotV12,
        forgeMetalBlockRegistryServerEvidenceVerifierV12,
        forgeServerContractV13,
        forgeServerProfileSnapshotV13,
        forgeMetalBlockRegistryServerEvidenceVerifier,
        forgeServerContractV14,
        forgeServerProfileSnapshotV14,
        forgeFoodItemRegistryServerEvidenceVerifier,
        forgeServerContractV15,
        forgeServerProfileSnapshotV15,
        forgeForestLanternServerEvidenceVerifierV15,
        forgeForestLanternServerEvidenceTestV15,
        forgeServerContractV16,
        forgeServerProfileSnapshotV16,
        forgeForestLanternServerEvidenceVerifier,
        forgeServerContractV17,
        forgeServerProfileSnapshotV17,
        forgeAttrahiteBlockRegistryServerEvidenceVerifier,
        forgeServerContractV18,
        forgeServerProfileSnapshotV18,
        forgeAttrahiteBlockRegistryServerEvidenceVerifierV18,
        forgeServerContractV19,
        forgeServerProfileSnapshotV19,
        forgeAttrahiteBlockRegistryServerEvidenceVerifierV19,
        forgeServerContractV20,
        forgeServerProfileSnapshotV20,
        forgeSlitheriteBlockRegistryServerEvidenceVerifierV20,
        forgeSlitheriteBlockRegistryServerEvidenceTestV20,
        forgeRegistryFoundationServerRunner,
        forgeRegistryFoundationServerRunnerTest,
        forgeRegistryFoundationServerRunnerTestV20,
        forgeSlitheriteEvidenceVerifier,
        forgeSlitheriteEvidenceTest,
        forgeSlitheriteRunContractV19,
        forgeSlitheriteProfileSnapshotV19,
        forgeE2eProfileManifest,
        slitheriteClientEvidenceContract,
        slitheriteClientEvidenceTestSupport,
        originalSlitheriteEvidenceVerifier,
        rootProject.file("scripts/e2e/forge_client.py"),
        rootProject.file("scripts/e2e/forge_evidence.py"),
        forgeForestLanternEvidenceVerifier,
        forgeForestLanternProfileSnapshotV12,
        forgeForestLanternProfileSnapshotV13,
        forgeAttrahiteEvidenceVerifierV14,
        forgeAttrahiteEvidenceTestV14,
        forgeAttrahiteEvidenceVerifierV15,
        forgeAttrahiteEvidenceTestV15,
        forgeAttrahiteEvidenceVerifierV16,
        forgeAttrahiteEvidenceTestV16,
        forgeAttrahiteEvidenceVerifier,
        forgeAttrahiteEvidenceTest,
        forgeAttrahiteProfileSnapshotV14,
        forgeAttrahiteProfileSnapshotV15,
        forgeAttrahiteProfileSnapshotV16,
        forgeAttrahiteProfileSnapshotV17,
    )
    inputs.dir(
        rootProject.file("e2e-harness/forge-server/1.20.1/src/main/java"),
    ).withPropertyName("forgeSlitheriteServerProbeSources")
    inputs.dir(
        rootProject.file("e2e-harness/forge-server/1.20.1/src/test/java"),
    ).withPropertyName("forgeSlitheriteServerProbeTests")
    inputs.dir(forgeRegistryFoundationServerEvidenceArchive)
        .withPropertyName("forgeRegistryFoundationServerEvidenceArchive")
        .optional()
    inputs.dir(forgeEtherSourceReloadServerEvidenceArchive)
        .withPropertyName("forgeEtherSourceReloadServerEvidenceArchive")
        .optional()
    inputs.dir(forgeEnchantmentRegistryServerEvidenceArchive)
        .withPropertyName("forgeEnchantmentRegistryServerEvidenceArchive")
        .optional()
    if (forgeParticleRegistryServerEvidenceArchive.exists()) {
        inputs.dir(forgeParticleRegistryServerEvidenceArchive)
            .withPropertyName("forgeParticleRegistryServerEvidenceArchive")
    }
    if (forgeMaterialItemRegistryServerEvidenceArchive.exists()) {
        inputs.dir(forgeMaterialItemRegistryServerEvidenceArchive)
            .withPropertyName("forgeMaterialItemRegistryServerEvidenceArchive")
    }
    if (forgeMetalBlockRegistryServerEvidenceArchive.exists()) {
        inputs.dir(forgeMetalBlockRegistryServerEvidenceArchive)
            .withPropertyName("forgeMetalBlockRegistryServerEvidenceArchive")
    }
    if (forgeFoodItemRegistryServerEvidenceArchive.exists()) {
        inputs.dir(forgeFoodItemRegistryServerEvidenceArchive)
            .withPropertyName("forgeFoodItemRegistryServerEvidenceArchive")
    }
    if (forgeForestLanternServerEvidenceArchive.exists()) {
        inputs.dir(forgeForestLanternServerEvidenceArchive)
            .withPropertyName("forgeForestLanternServerEvidenceArchive")
    }
    if (forgeAttrahiteBlockRegistryServerEvidenceArchive.exists()) {
        inputs.dir(forgeAttrahiteBlockRegistryServerEvidenceArchive)
            .withPropertyName("forgeAttrahiteBlockRegistryServerEvidenceArchiveV17")
    }
    if (forgeAttrahiteBlockRegistryServerEvidenceArchiveV18.exists()) {
        inputs.dir(forgeAttrahiteBlockRegistryServerEvidenceArchiveV18)
            .withPropertyName("forgeAttrahiteBlockRegistryServerEvidenceArchiveV18")
    }
    if (forgeAttrahiteBlockRegistryServerEvidenceArchiveV19.exists()) {
        inputs.dir(forgeAttrahiteBlockRegistryServerEvidenceArchiveV19)
            .withPropertyName("forgeAttrahiteBlockRegistryServerEvidenceArchiveV19")
    }
    if (forgeSlitheriteBlockRegistryServerEvidenceArchiveV20.exists()) {
        inputs.dir(forgeSlitheriteBlockRegistryServerEvidenceArchiveV20)
            .withPropertyName("forgeSlitheriteBlockRegistryServerEvidenceArchiveV20")
    }
    if (forgeForestLanternClientEvidenceArchive.exists()) {
        inputs.dir(forgeForestLanternClientEvidenceArchive)
            .withPropertyName("forgeForestLanternClientEvidenceArchive")
    }
    if (forgeAttrahiteClientEvidenceArchive.exists()) {
        inputs.dir(forgeAttrahiteClientEvidenceArchive)
            .withPropertyName("forgeAttrahiteClientEvidenceArchive")
    }
    inputs.dir(forgeSlitheriteClientEvidenceArchive)
        .withPropertyName("forgeSlitheriteClientEvidenceArchive")
    inputs.files(commonTransformProductionFabric)
        .withPropertyName("fabricTransformedCommonJar")
    inputs.files(commonTransformProductionForge)
        .withPropertyName("forgeTransformedCommonJar")
    inputs.file(fabricRemapJar.flatMap { it.archiveFile })
    inputs.file(fabricShadowJar.flatMap { it.archiveFile })
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
            fabricShadowJar.get().archiveFile.get().asFile,
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
    val serverProbeProfileFile =
        rootProject.file("scripts/e2e/forge-server-1.20.1-profile.json")
    check(serverProbeProfileFile.isFile && !Files.isSymbolicLink(serverProbeProfileFile.toPath())) {
        "The Forge dedicated-server probe profile is missing or linked"
    }
    @Suppress("UNCHECKED_CAST")
    val serverProbeProfile = JsonSlurper().parse(serverProbeProfileFile) as Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    val serverProbeProfileIdentity =
        serverProbeProfile.getValue("profile") as Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    val serverProbeLaunch = serverProbeProfile.getValue("launch") as Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    val serverProbeEvidence = serverProbeProfile.getValue("evidence") as Map<String, Any>
    val serverProbeProfileId = serverProbeProfileIdentity.getValue("id").toString()
    val serverProbeRuntimeDirectory =
        serverProbeProfileIdentity.getValue("runtime_directory").toString()
    val serverProbeGameDirectoryName =
        serverProbeProfileIdentity.getValue("game_directory").toString()
    val serverProbeScenarioId = serverProbeLaunch.getValue("scenario").toString()
    val serverProbeProfileVersion = requireNotNull(
        Regex(".*-(v[1-9][0-9]*)").matchEntire(serverProbeProfileId)
            ?.groupValues?.get(1),
    ) {
        "The dedicated-server probe profile has no safe archive version"
    }
    @Suppress("UNCHECKED_CAST")
    val serverProbeForbiddenModIds =
        (serverProbeProfile.getValue("forbidden_mod_ids") as List<Any>)
            .map(Any::toString)
    val serverProbeGameDirectory = rootProject.file(
        "scripts/e2e/.state/runtimes/$serverProbeRuntimeDirectory/" +
            serverProbeGameDirectoryName,
    )
    val serverProbeRuntimeDirectoryRoot = serverProbeGameDirectory.parentFile
    val serverProbeProfileMarker = serverProbeRuntimeDirectoryRoot.resolve(
        ".etherology-forge-server-e2e-profile.json",
    )
    val serverProbeRunLock = rootProject.file(
        "scripts/e2e/.state/$serverProbeProfileId-run.lock",
    )
    val serverProbeRunAttempt = rootProject.file(
        "scripts/e2e/.state/$serverProbeProfileId-run.attempted",
    )
    val serverProbeSealedArchive = forgeRegistryFoundationServerEvidenceRoot.resolve(
        "$serverProbeScenarioId-server-$serverProbeProfileVersion",
    )
    val serverProbeEvidenceRoot = rootProject.file(
        "scripts/e2e/.state/runtimes/$serverProbeRuntimeDirectory/" +
            "${serverProbeEvidence.getValue("directory")}/" +
            serverProbeEvidence.getValue("scenario_directory"),
    )
    val serverProbeJavaVersion = javaVersion

    val serverProbe = sourceSets.create("serverProbe") {
        java.setSrcDirs(
            listOf(rootProject.file("e2e-harness/forge-server/1.20.1/src/main/java")),
        )
        resources.setSrcDirs(
            listOf(rootProject.file("e2e-harness/forge-server/1.20.1/src/main/resources")),
        )
        compileClasspath += sourceSets.main.get().compileClasspath
        runtimeClasspath += output + compileClasspath
    }

    val serverProbeTest = sourceSets.create("serverProbeTest") {
        java.setSrcDirs(
            listOf(rootProject.file("e2e-harness/forge-server/1.20.1/src/test/java")),
        )
        resources.setSrcDirs(emptyList<String>())
        compileClasspath += serverProbe.output + serverProbe.compileClasspath
        runtimeClasspath += output + serverProbe.output + serverProbe.runtimeClasspath
    }
    configurations[serverProbeTest.implementationConfigurationName]
        .extendsFrom(configurations["testImplementation"])
    configurations[serverProbeTest.runtimeOnlyConfigurationName]
        .extendsFrom(configurations["testRuntimeOnly"])

    val expandedServerProbeMetadata = mapOf(
        "version" to project.version.toString(),
        "minecraft_version_range" to releaseArtifact["metadata_range"].toString(),
        "forge_loader_range" to releaseMetadata["loader_api"].toString(),
        "forge_version_range" to releaseMetadata["loader"].toString(),
    )

    tasks.named<ProcessResources>(serverProbe.processResourcesTaskName) {
        inputs.properties(expandedServerProbeMetadata)
        filesMatching("META-INF/mods.toml") {
            expand(expandedServerProbeMetadata)
        }
    }

    extensions.configure<LoomGradleExtensionAPI>("loom") {
        val inheritedServerRun = runConfigs.named("server")
        runConfigs.create("registryFoundationServerProbe") {
            inherit(inheritedServerRun.get())
            displayName.set("Etherology Forge 1.20.1 Slitherite block-registry server probe")
            sourceSet.set(sourceSets.main.get().name)
            runDirectory.set(serverProbeGameDirectory)
            generateRunConfig.set(false)
            systemProperties.put("etherology.serverProbe.profileId", serverProbeProfileId)
            systemProperties.put("etherology.serverProbe.scenario", serverProbeScenarioId)
            systemProperties.put(
                "etherology.serverProbe.runtimeKind",
                serverProbeLaunch.getValue("kind").toString(),
            )
            systemProperties.put(
                "etherology.serverProbe.forbiddenModIds",
                serverProbeForbiddenModIds.joinToString(","),
            )
            systemProperties.put(
                "etherology.serverProbe.evidenceRoot",
                serverProbeEvidenceRoot.absolutePath,
            )
            jvmArguments.add("-Xmx${serverProbeLaunch.getValue("maximum_memory_mb")}m")
            mods {
                create("etherology") {
                    sourceSet(sourceSets.main.get())
                }
                create("etherology_e2e_server_probe") {
                    sourceSet(serverProbe)
                }
            }
        }
    }

    val serverProbeRunTask = tasks.named<JavaExec>("runRegistryFoundationServerProbe") {
        dependsOn(tasks.named("classes"), serverProbe.classesTaskName)
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(serverProbeJavaVersion))
            },
        )
    }

    val serverProbeTestTask = tasks.register<Test>("serverProbeTest") {
        group = "verification"
        description = "Runs focused unit tests for the Forge 1.20.1 server probe."
        dependsOn(serverProbe.classesTaskName)
        testClassesDirs = serverProbeTest.output.classesDirs
        classpath = serverProbeTest.runtimeClasspath
        useJUnitPlatform()
    }

    val serverProbeJar = tasks.register<Jar>("serverProbeJar") {
        group = "e2e"
        description = "Packages the isolated Forge 1.20.1 dedicated-server probe."
        dependsOn(serverProbe.classesTaskName)
        from(serverProbe.output)
        archiveBaseName.set("Etherology-E2E-Server-Probe-Forge-$minecraftVersion")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("dev")
        destinationDirectory.set(layout.buildDirectory.dir("server-probe/devlibs"))
    }

    val validateServerProbeProfile = tasks.register("validateServerProbeProfile") {
        group = "verification"
        description = "Validates the exact isolated Forge 1.20.1 server-probe profile."
        inputs.file(serverProbeProfileFile)

        doLast {
            check(serverProbeProfile.keys == setOf(
                "schema",
                "profile",
                "release",
                "launch",
                "evidence",
                "profile_directories",
                "required_mod_ids",
                "forbidden_mod_ids",
            )) {
                "The dedicated-server probe profile field inventory changed"
            }
            check(serverProbeProfile["schema"] == 1) {
                "The dedicated-server probe profile schema changed"
            }
            check(serverProbeProfileIdentity == mapOf(
                "id" to "etherology-e2e-forge-server-1.20.1-v20",
                "runtime_directory" to "etherology-e2e-forge-server-1.20.1-v20",
                "game_directory" to "game",
            )) {
                "The dedicated-server probe identity changed"
            }
            @Suppress("UNCHECKED_CAST")
            val profileRelease = serverProbeProfile.getValue("release") as Map<String, Any>
            check(profileRelease == mapOf(
                "matrix" to "release/release-matrix.json",
                "artifact_node" to "forge-1.20.1",
                "minecraft" to minecraftVersion,
                "loader" to "forge",
                "loader_version" to versionProperty("forge_version").substringAfter('-'),
                "java" to javaVersion,
            )) {
                "The dedicated-server probe release identity changed"
            }
            check(serverProbeLaunch == mapOf(
                "kind" to "loom-userdev",
                "task_path" to ":forge:1.20.1:runRegistryFoundationServerProbe",
                "scenario" to "slitherite-block-registry",
                "maximum_memory_mb" to 2048,
            )) {
                "The dedicated-server probe launch contract changed"
            }
            check(
                serverProbeSealedArchive ==
                    forgeSlitheriteBlockRegistryServerEvidenceArchiveV20,
            ) {
                "The dedicated-server probe sealed archive is not the exact v20 target"
            }
            check(serverProbeEvidence == mapOf(
                "directory" to "evidence",
                "scenario_directory" to "slitherite-block-registry",
                "report" to "reports/report.json",
                "launcher_result" to "reports/launcher-result.json",
                "completion_marker" to "reports/done.marker",
                "server_log" to "logs/latest.log",
            )) {
                "The dedicated-server probe evidence contract changed"
            }
            check(serverProbeProfile["profile_directories"] == listOf(
                "config",
                "crash-reports",
                "evidence",
                "logs",
                "mods",
                "world",
            )) {
                "The dedicated-server probe directory inventory changed"
            }
            check(serverProbeProfile["required_mod_ids"] == listOf(
                "etherology",
                "etherology_e2e_server_probe",
            )) {
                "The dedicated-server probe required-mod inventory changed"
            }
            check(serverProbeProfile["forbidden_mod_ids"] == listOf(
                "etherology_e2e_harness",
                "quickskin",
                "cpm",
                "ears",
                "modmenu",
                "roughlyenoughitems",
                "emi",
            )) {
                "The dedicated-server probe forbidden-mod inventory changed"
            }
        }
    }

    val validateServerProbeRunConfiguration =
        tasks.register("validateServerProbeRunConfiguration") {
            group = "verification"
            description =
                "Validates the server-only Loom run without launching Minecraft."
            dependsOn(tasks.named("classes"), serverProbe.classesTaskName)

            doLast {
                val loomExtension = project.extensions.getByType<LoomGradleExtensionAPI>()
                val runConfiguration = loomExtension.runConfigs
                    .getByName("registryFoundationServerProbe")
                check(runConfiguration.runtimeEnvironment.get() == "server") {
                    "The dedicated-server probe does not inherit the server runtime"
                }
                check(runConfiguration.forgeTemplate.get() == "server") {
                    "The dedicated-server probe does not inherit the Forge server template"
                }
                check(runConfiguration.sourceSet.get() == sourceSets.main.get().name) {
                    "The dedicated-server probe run has the wrong primary source set"
                }
                check(
                    runConfiguration.runDirectory.get().asFile.canonicalFile ==
                        serverProbeGameDirectory.canonicalFile,
                ) {
                    "The dedicated-server probe run directory escaped its isolated profile"
                }
                check(!runConfiguration.generateRunConfig.get()) {
                    "The dedicated-server probe generated a reusable IDE profile"
                }
                val probeSystemProperties = runConfiguration.systemProperties.get()
                check(
                    probeSystemProperties["etherology.serverProbe.profileId"] ==
                        serverProbeProfileId
                        && probeSystemProperties["etherology.serverProbe.scenario"] ==
                        serverProbeScenarioId
                        && probeSystemProperties["etherology.serverProbe.runtimeKind"] ==
                        serverProbeLaunch.getValue("kind")
                        && probeSystemProperties["etherology.serverProbe.forbiddenModIds"] ==
                        serverProbeForbiddenModIds.joinToString(",")
                        && probeSystemProperties["etherology.serverProbe.evidenceRoot"] ==
                        serverProbeEvidenceRoot.absolutePath,
                ) {
                    "The dedicated-server probe system-property contract changed"
                }
                check(
                    "-Xmx${serverProbeLaunch.getValue("maximum_memory_mb")}m" in
                        runConfiguration.jvmArguments.get(),
                ) {
                    "The dedicated-server probe memory boundary changed"
                }
                check(
                    serverProbeRunTask.get().javaLauncher.get()
                        .metadata.languageVersion.asInt() == serverProbeJavaVersion,
                ) {
                    "The dedicated-server probe Java launcher is not Java $serverProbeJavaVersion"
                }
                val bootstrapClasspath = serverProbeRunTask.get().classpath.files
                val missingProductionOutputs = sourceSets.main.get().output.files
                    .filterNot(bootstrapClasspath::contains)
                check(missingProductionOutputs.isEmpty()) {
                    "The dedicated-server probe bootstrap classpath lost production output: " +
                        missingProductionOutputs.map(File::getAbsolutePath).sorted()
                }
                val devLaunchInjectorJars = bootstrapClasspath.filter { classpathEntry ->
                    classpathEntry.isFile
                        && classpathEntry.extension == "jar"
                        && classpathEntry.name.startsWith("dev-launch-injector-")
                }
                check(devLaunchInjectorJars.size == 1) {
                    "The dedicated-server probe bootstrap classpath must contain exactly " +
                        "one dev-launch-injector JAR: " +
                        devLaunchInjectorJars.map(File::getAbsolutePath).sorted()
                }
                val devLaunchInjectorJar = devLaunchInjectorJars.single()
                check(!Files.isSymbolicLink(devLaunchInjectorJar.toPath())) {
                    "The dedicated-server probe dev-launch-injector JAR is a symbolic link"
                }
                ZipFile(devLaunchInjectorJar).use { devLaunchInjectorZip ->
                    check(
                        devLaunchInjectorZip.getEntry(
                            "net/fabricmc/devlaunchinjector/Main.class",
                        ) != null,
                    ) {
                        "The dedicated-server probe dev-launch-injector JAR has no launcher class"
                    }
                }
                check(runConfiguration.mods.names == setOf(
                    "etherology",
                    "etherology_e2e_server_probe",
                )) {
                    "The dedicated-server probe run mod inventory changed: " +
                        runConfiguration.mods.names.sorted()
                }
                check(
                    runConfiguration.mods.getByName("etherology")
                        .modFiles.files == sourceSets.main.get().output.files,
                ) {
                    "The dedicated-server probe run lost the production source set"
                }
                check(
                    runConfiguration.mods.getByName("etherology_e2e_server_probe")
                        .modFiles.files == serverProbe.output.files,
                ) {
                    "The dedicated-server probe mod does not own only its probe source set"
                }
                loomExtension.runConfigs
                    .filter { configuration ->
                        configuration.name != "registryFoundationServerProbe"
                    }
                    .forEach { configuration ->
                        check("etherology_e2e_server_probe" !in configuration.mods.names) {
                            "The probe mod leaked into run configuration ${configuration.name}"
                        }
                    }
            }
        }

    val verifyServerProbeArtifact = tasks.register("verifyServerProbeArtifact") {
        group = "verification"
        description = "Validates the isolated Forge 1.20.1 dedicated-server probe JAR."
        dependsOn(serverProbeJar)
        inputs.file(serverProbeJar.flatMap { it.archiveFile })

        doLast {
            val probeFile = serverProbeJar.get().archiveFile.get().asFile
            ZipFile(probeFile).use { probeZip ->
                val probeEntries = probeZip.entries().asSequence().map { it.name }.toSet()
                val probeClassEntries = probeEntries.filter { it.endsWith(".class") }.toSet()
                check(probeClassEntries == setOf(
                    "dev/theplumteam/etherology/e2e/server/" +
                        "AttrahiteBlockProbeState\$AttrahiteBlockEntry.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "AttrahiteBlockProbeState\$AttrahiteBlockSpec.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "AttrahiteBlockProbeState\$LoadedData.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "AttrahiteBlockProbeState\$PlacementState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "AttrahiteBlockProbeState\$RecipeCapture.class",
                    "dev/theplumteam/etherology/e2e/server/AttrahiteBlockProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "SlitheriteBlockProbeState\$LoadedData.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "SlitheriteBlockProbeState\$PlacementState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "SlitheriteBlockProbeState\$RecipeSpec.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "SlitheriteBlockProbeState\$SlitheriteBlockEntry.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "SlitheriteBlockProbeState\$SlitheriteBlockSpec.class",
                    "dev/theplumteam/etherology/e2e/server/SlitheriteBlockProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/EnchantmentProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/EtherSourceProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "FoodItemProbeState\$ConsumptionPhase.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "FoodItemProbeState\$FoodConsumptionState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "FoodItemProbeState\$FoodItemEntry.class",
                    "dev/theplumteam/etherology/e2e/server/FoodItemProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$JumpKind.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$JumpResult.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$LoadedData.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$MechanicsPhase.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$PlacementResult.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$PlayerRole.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$RecipeCapture.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$ShearsResult.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$WorldMechanics.class",
                    "dev/theplumteam/etherology/e2e/server/ForestLanternProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/LootConditionProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "MaterialItemProbeState\$MaterialItemEntry.class",
                    "dev/theplumteam/etherology/e2e/server/MaterialItemProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "MetalBlockProbeState\$MetalBlockEntry.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "MetalBlockProbeState\$MetalBlockPlacementState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "MetalBlockProbeState\$MetalBlockSpec.class",
                    "dev/theplumteam/etherology/e2e/server/MetalBlockProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ParticleProbeState\$ParticleEntry.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ParticleProbeState\$ParticleSpec.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ParticleProbeState\$SealTypeEntry.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ParticleProbeState\$SealTypeSpec.class",
                    "dev/theplumteam/etherology/e2e/server/ParticleProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/ReloadDataPackWriter\$WrittenPack.class",
                    "dev/theplumteam/etherology/e2e/server/ReloadDataPackWriter.class",
                    "dev/theplumteam/etherology/e2e/server/RegistryFoundationServerProbe\$1.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "RegistryFoundationServerProbe\$SlitheriteBehaviorPhase.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "RegistryFoundationServerProbe\$SlitheriteBehaviorProbe.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "RegistryFoundationServerProbe\$SlitheriteBehaviorSequence.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "RegistryFoundationServerProbe\$SlitheriteNativePlacementEntry.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "RegistryFoundationServerProbe\$SlitheriteNativePlacementState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "RegistryFoundationServerProbe\$SlitheritePlacementSetup.class",
                    "dev/theplumteam/etherology/e2e/server/RegistryFoundationServerProbe.class",
                    "dev/theplumteam/etherology/e2e/server/ServerProbeModInventory.class",
                    "dev/theplumteam/etherology/e2e/server/ServerProbeMemoryHandoff.class",
                    "dev/theplumteam/etherology/e2e/server/ServerProbeProcessTerminator.class",
                    "dev/theplumteam/etherology/e2e/server/ServerProbeReportWriter.class",
                )) {
                    "The dedicated-server probe class inventory changed: " +
                        probeClassEntries.sorted()
                }
                check(probeEntries.none { it.endsWith(".jar") }) {
                    "The dedicated-server probe JAR contains nested dependencies"
                }
                probeClassEntries.forEach { classEntryName ->
                    val classEntry = requireNotNull(probeZip.getEntry(classEntryName))
                    val constants = readClassUtf8Constants(
                        probeZip.getInputStream(classEntry).use { input -> input.readAllBytes() },
                    )
                    val allowedProductionConstants = setOf(
                        "ru.feytox.etherology.data.ethersource.EtherSourceLoader",
                        "ru.feytox.etherology.registry.misc.PealEnchantment",
                        "ru.feytox.etherology.registry.misc.ReflectionEnchantment",
                        "ru.feytox.etherology.block.forestLantern.ForestLanternBlock",
                        "ru.feytox.etherology.block.forestLantern.ForestLanternBlock" +
                            "|hardness=0.2|blast=0.2|grass_sound=true" +
                            "|tool_required=false|luminance=8|opaque=true" +
                            "|full_cube=false|transparent=true|post_process=true" +
                            "|emissive=true|piston=DESTROY|mature_random_ticks=false" +
                            "|bud_random_ticks=true",
                        "ru.feytox.etherology.util.misc." +
                            "RandomChanceWithFortuneConditionSerializer",
                        "ru/feytox/etherology/magic/seal/SealType",
                        "ru/feytox/etherology/particle/effects/misc/FeyParticleType",
                        "ru/feytox/etherology/util/misc/RGBColor",
                    )
                    val allowedProductionConstantPrefixes = setOf(
                        "ru.feytox.etherology.particle.effects.",
                    )
                    check(constants.none { constant ->
                        (constant.startsWith("net/minecraft/client/")
                            || constant.startsWith("ru/feytox/etherology/")
                            || constant.startsWith("ru.feytox.etherology."))
                            && constant !in allowedProductionConstants
                            && allowedProductionConstantPrefixes.none(constant::startsWith)
                    }) {
                        "Dedicated-server probe class $classEntryName links client or production code"
                    }
                }

                val lootConditionEntry = requireNotNull(
                    probeZip.getEntry(
                        "dev/theplumteam/etherology/e2e/server/" +
                            "LootConditionProbeState.class",
                    ),
                )
                val lootConditionConstants = readClassUtf8Constants(
                    probeZip.getInputStream(lootConditionEntry).use { input ->
                        input.readAllBytes()
                    },
                )
                val requiredLootConditionConstants = setOf(
                    "minecraft:loot_condition_type",
                    "random_chance_with_fortune",
                    "ru.feytox.etherology.util.misc." +
                        "RandomChanceWithFortuneConditionSerializer",
                    "registry_foundation",
                    "minecraft:stone",
                    "minecraft:diamond",
                    "minecraft:gold_ingot",
                )
                check(requiredLootConditionConstants.all(lootConditionConstants::contains)) {
                    "The dedicated-server probe lost its loot-condition contract: " +
                        (requiredLootConditionConstants - lootConditionConstants).sorted()
                }

                val lootTableEntryPath =
                    "data/etherology_e2e_server_probe/loot_tables/registry_foundation.json"
                val lootTableEntry = requireNotNull(probeZip.getEntry(lootTableEntryPath)) {
                    "The dedicated-server probe JAR has no $lootTableEntryPath"
                }
                val lootTableDigest = MessageDigest.getInstance("SHA-256")
                    .digest(
                        probeZip.getInputStream(lootTableEntry).use { input ->
                            input.readAllBytes()
                        },
                    )
                    .joinToString("") { byte ->
                        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
                    }
                check(
                    lootTableDigest ==
                        "45fb3b70ac59949a2c9e2a2b4d2e775b24ff8c21ada187f80027aed95f2975e0",
                ) {
                    "The dedicated-server probe loot table contract changed: $lootTableDigest"
                }

                val etherSourceStateEntry = requireNotNull(
                    probeZip.getEntry(
                        "dev/theplumteam/etherology/e2e/server/EtherSourceProbeState.class",
                    ),
                )
                val etherSourceStateConstants = readClassUtf8Constants(
                    probeZip.getInputStream(etherSourceStateEntry).use { input ->
                        input.readAllBytes()
                    },
                )
                val requiredEtherSourceStateConstants = setOf(
                    "ru.feytox.etherology.data.ethersource.EtherSourceLoader",
                    "INSTANCE",
                    "getEtherItems",
                    "ether_sources",
                    "etherology:primoshard_rella",
                    "minecraft:redstone",
                    "minecraft:diamond",
                )
                check(
                    requiredEtherSourceStateConstants.all(
                        etherSourceStateConstants::contains,
                    ),
                ) {
                    "The server probe lost its Ether-source reflection contract: " +
                        (requiredEtherSourceStateConstants - etherSourceStateConstants).sorted()
                }

                val enchantmentStateEntry = requireNotNull(
                    probeZip.getEntry(
                        "dev/theplumteam/etherology/e2e/server/EnchantmentProbeState.class",
                    ),
                )
                val enchantmentStateConstants = readClassUtf8Constants(
                    probeZip.getInputStream(enchantmentStateEntry).use { input ->
                        input.readAllBytes()
                    },
                )
                val requiredEnchantmentStateConstants = setOf(
                    "minecraft:enchantment",
                    "etherology",
                    "peal",
                    "reflection",
                    "minecraft",
                    "non_treasure",
                    "ru.feytox.etherology.registry.misc.PealEnchantment",
                    "ru.feytox.etherology.registry.misc.ReflectionEnchantment",
                    "getMaxLevel",
                    "getMinPower",
                    "getMaxPower",
                    "getEntry",
                    "isIn",
                )
                check(
                    requiredEnchantmentStateConstants.all(
                        enchantmentStateConstants::contains,
                    ),
                ) {
                    "The server probe lost its enchantment registry contract: " +
                        (requiredEnchantmentStateConstants - enchantmentStateConstants).sorted()
                }

                val particleStateEntry = requireNotNull(
                    probeZip.getEntry(
                        "dev/theplumteam/etherology/e2e/server/ParticleProbeState.class",
                    ),
                )
                val particleStateConstants = readClassUtf8Constants(
                    probeZip.getInputStream(particleStateEntry).use { input ->
                        input.readAllBytes()
                    },
                )
                val requiredParticleStateConstants = setOf(
                    "minecraft:particle_type",
                    "etherology",
                    "alchemy",
                    "armillary_sphere",
                    "electricity1",
                    "electricity2",
                    "energy_absorption",
                    "ether_dot",
                    "ether_star",
                    "glint_particle",
                    "haze",
                    "item",
                    "light",
                    "lightning_bolt",
                    "redstone_flash",
                    "redstone_stream",
                    "resonation",
                    "rising",
                    "scalable_sweep",
                    "seal",
                    "shockwave",
                    "spark",
                    "steam",
                    "vital",
                    "ru/feytox/etherology/magic/seal/SealType",
                    "ru/feytox/etherology/particle/effects/misc/FeyParticleType",
                    "ru/feytox/etherology/util/misc/RGBColor",
                    "getParametersFactory",
                    "getCodec",
                    "shouldAlwaysSpawn",
                    "asString",
                    "write",
                    "CODEC",
                    "getStartColor",
                    "getEndColor",
                    "getTextureId",
                    "getTextureLightId",
                    "KETA",
                    "RELLA",
                    "VIA",
                    "CLOS",
                    "etherology:textures/block/keta_seal.png",
                    "etherology:textures/block/clos_seal_light.png",
                )
                check(
                    requiredParticleStateConstants.all(
                        particleStateConstants::contains,
                    ),
                ) {
                    "The server probe lost its particle registry contract: " +
                        (requiredParticleStateConstants - particleStateConstants).sorted()
                }

                val materialItemStateEntry = requireNotNull(
                    probeZip.getEntry(
                        "dev/theplumteam/etherology/e2e/server/MaterialItemProbeState.class",
                    ),
                )
                val materialItemStateConstants = readClassUtf8Constants(
                    probeZip.getInputStream(materialItemStateEntry).use { input ->
                        input.readAllBytes()
                    },
                )
                val requiredMaterialItemStateConstants = setOf(
                    "minecraft:item",
                    "etherology:attrahite_brick",
                    "etherology:azel_ingot",
                    "etherology:azel_nugget",
                    "etherology:binder",
                    "etherology:ebony",
                    "etherology:ebony_ingot",
                    "etherology:ebony_nugget",
                    "etherology:enriched_attrahite",
                    "etherology:etheroscope",
                    "etherology:ethril_ingot",
                    "etherology:ethril_nugget",
                    "etherology:raw_azel",
                    "etherology:resonating_wand",
                    "etherology:thuja_oil",
                    "net/minecraft/item/Item",
                    "net/minecraft/item/ItemStack",
                    "writeNbt",
                    "fromNbt",
                    "Count",
                    "id",
                )
                check(
                    requiredMaterialItemStateConstants.all(
                        materialItemStateConstants::contains,
                    ),
                ) {
                    "The server probe lost its material-item registry contract: " +
                        (requiredMaterialItemStateConstants - materialItemStateConstants)
                            .sorted()
                }

                val foodItemStateClassEntries = setOf(
                    "dev/theplumteam/etherology/e2e/server/FoodItemProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "FoodItemProbeState\$ConsumptionPhase.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "FoodItemProbeState\$FoodConsumptionState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "FoodItemProbeState\$FoodItemEntry.class",
                )
                val foodItemStateConstants = foodItemStateClassEntries.flatMap { entryName ->
                    val entry = requireNotNull(probeZip.getEntry(entryName))
                    readClassUtf8Constants(
                        probeZip.getInputStream(entry).use { input -> input.readAllBytes() },
                    )
                }.toSet()
                val requiredFoodItemStateConstants = setOf(
                    "minecraft:item",
                    "etherology:forest_lantern_crumb",
                    "net/minecraft/item/Item",
                    "net/minecraft/item/ItemStack",
                    "net/minecraft/item/FoodComponent",
                    "net/minecraft/server/network/ServerPlayerEntity",
                    "com/mojang/authlib/GameProfile",
                    "00000000-0000-0000-0000-00000000e214",
                    "00000000-0000-0000-0000-00000000e215",
                    "EtherFoodStart",
                    "EtherFoodReload",
                    "getFoodComponent",
                    "getHunger",
                    "getSaturationModifier",
                    "isAlwaysEdible",
                    "getStatusEffects",
                    "hasRecipeRemainder",
                    "getRecipeRemainder",
                    "finishUsing",
                    "getHungerManager",
                    "setFoodLevel",
                    "setSaturationLevel",
                    "setExhaustion",
                    "getFoodLevel",
                    "getSaturationLevel",
                    "writeNbt",
                    "fromNbt",
                    "Count",
                    "id",
                )
                check(
                    requiredFoodItemStateConstants.all(
                        foodItemStateConstants::contains,
                    ),
                ) {
                    "The server probe lost its food-item/consumption contract: " +
                        (requiredFoodItemStateConstants - foodItemStateConstants)
                            .sorted()
                }

                val forestLanternStateClassEntries = setOf(
                    "dev/theplumteam/etherology/e2e/server/ForestLanternProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$JumpKind.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$JumpResult.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$LoadedData.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$MechanicsPhase.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$PlacementResult.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$PlayerRole.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$RecipeCapture.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$ShearsResult.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ForestLanternProbeState\$WorldMechanics.class",
                )
                val forestLanternStateConstants =
                    forestLanternStateClassEntries.flatMap { entryName ->
                        val entry = requireNotNull(probeZip.getEntry(entryName))
                        readClassUtf8Constants(
                            probeZip.getInputStream(entry).use { input ->
                                input.readAllBytes()
                            },
                        )
                    }.toSet()
                val requiredForestLanternStateConstants = setOf(
                    "minecraft:block",
                    "minecraft:item",
                    "etherology:forest_lantern",
                    "etherology:peach_logs",
                    "ru.feytox.etherology.block.forestLantern.ForestLanternBlock",
                    "net/minecraft/item/BlockItem",
                    "net/minecraft/item/AutomaticItemPlacementContext",
                    "net/minecraft/item/ItemStack",
                    "net/minecraft/nbt/NbtCompound",
                    "net/minecraft/server/network/ServerPlayerEntity",
                    "net/minecraft/recipe/AbstractCookingRecipe",
                    "net/minecraft/recipe/ShapedRecipe",
                    "etherology:blocks/forest_lantern",
                    "etherology:forest_lantern_crumb",
                    "etherology:forest_lantern_crumb_from_campfire",
                    "etherology:forest_lantern_crumb_from_smoking",
                    "etherology:leather",
                    "etherology:recipes/food/forest_lantern_crumb",
                    "etherology:recipes/food/forest_lantern_crumb_from_campfire",
                    "etherology:recipes/food/forest_lantern_crumb_from_smoking",
                    "etherology:recipes/misc/leather",
                    "getOutlineShape",
                    "getLootTableId",
                    "getBlockBreakingSpeed",
                    "calcBlockBreakingDelta",
                    "writeNbt",
                    "fromNbt",
                    "jump",
                    "setSeed",
                    "Count",
                    "id",
                )
                check(
                    requiredForestLanternStateConstants.all(
                        forestLanternStateConstants::contains,
                    ),
                ) {
                    "The server probe lost its Forest Lantern runtime contract: " +
                        (requiredForestLanternStateConstants -
                            forestLanternStateConstants).sorted()
                }

                val attrahiteBlockStateClassEntries = setOf(
                    "dev/theplumteam/etherology/e2e/server/AttrahiteBlockProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "AttrahiteBlockProbeState\$AttrahiteBlockEntry.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "AttrahiteBlockProbeState\$AttrahiteBlockSpec.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "AttrahiteBlockProbeState\$LoadedData.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "AttrahiteBlockProbeState\$PlacementState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "AttrahiteBlockProbeState\$RecipeCapture.class",
                )
                val attrahiteBlockStateConstants =
                    attrahiteBlockStateClassEntries.flatMap { entryName ->
                        val entry = requireNotNull(probeZip.getEntry(entryName))
                        readClassUtf8Constants(
                            probeZip.getInputStream(entry).use { input ->
                                input.readAllBytes()
                            },
                        )
                    }.toSet()
                val requiredAttrahiteBlockStateConstants = setOf(
                    "minecraft:block",
                    "minecraft:item",
                    "etherology:attrahite",
                    "etherology:attrahite_brick_slab",
                    "etherology:attrahite_brick_stairs",
                    "etherology:attrahite_bricks",
                    "net/minecraft/block/Block",
                    "net/minecraft/block/SlabBlock",
                    "net/minecraft/block/StairsBlock",
                    "net/minecraft/item/BlockItem",
                    "net/minecraft/item/ItemStack",
                    "net/minecraft/nbt/NbtCompound",
                    "net/minecraft/command/argument/BlockArgumentParser",
                    "setBlockState",
                    "getBlockState",
                    "getBlockFromItem",
                    "asItem",
                    "getHardness",
                    "getBlastResistance",
                    "getMapColor",
                    "getSoundGroup",
                    "isToolRequired",
                    "getLuminance",
                    "isOpaque",
                    "isFullCube",
                    "isIn",
                    "PICKAXE_MINEABLE",
                    "NEEDS_STONE_TOOL",
                    "SLABS",
                    "STAIRS",
                    "GILDED_BLACKSTONE",
                    "STONE",
                    "writeNbt",
                    "fromNbt",
                    "Count",
                    "id",
                    "etherology:blocks/attrahite",
                    "etherology:blocks/attrahite_brick_slab",
                    "etherology:blocks/attrahite_brick_stairs",
                    "etherology:blocks/attrahite_bricks",
                    "LOOT_TABLES",
                    "SILK_TOUCH",
                    "FORTUNE",
                    "generateLoot",
                    "etherology:attrahite_brick",
                    "etherology:attrahite_brick_slab_from_attrahite_bricks_stonecutting",
                    "etherology:attrahite_brick_stairs_from_attrahite_bricks_stonecutting",
                    "etherology:azel_ingot",
                    "etherology:azel_ingot_from_blasting",
                    "etherology:raw_azel",
                    "etherology:recipes/building_blocks/attrahite_brick_slab",
                    "etherology:recipes/building_blocks/" +
                        "attrahite_brick_slab_from_attrahite_bricks_stonecutting",
                    "etherology:recipes/building_blocks/attrahite_brick_stairs",
                    "etherology:recipes/building_blocks/" +
                        "attrahite_brick_stairs_from_attrahite_bricks_stonecutting",
                    "etherology:recipes/building_blocks/attrahite_bricks",
                    "etherology:recipes/misc/attrahite_brick",
                    "etherology:recipes/misc/azel_ingot",
                    "etherology:recipes/misc/azel_ingot_from_blasting",
                    "etherology:recipes/misc/raw_azel",
                    "net/minecraft/recipe/AbstractCookingRecipe",
                    "net/minecraft/recipe/ShapedRecipe",
                    "net/minecraft/recipe/StonecuttingRecipe",
                    "matches",
                    "craft",
                    "getAdvancements",
                )
                check(
                    requiredAttrahiteBlockStateConstants.all(
                        attrahiteBlockStateConstants::contains,
                    ),
                ) {
                    "The server probe lost its Attrahite block-registry contract: " +
                        (requiredAttrahiteBlockStateConstants -
                            attrahiteBlockStateConstants).sorted()
                }

                val slitheriteBlockStateClassEntries = setOf(
                    "dev/theplumteam/etherology/e2e/server/SlitheriteBlockProbeState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "SlitheriteBlockProbeState\$LoadedData.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "SlitheriteBlockProbeState\$PlacementState.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "SlitheriteBlockProbeState\$RecipeSpec.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "SlitheriteBlockProbeState\$SlitheriteBlockEntry.class",
                    "dev/theplumteam/etherology/e2e/server/" +
                        "SlitheriteBlockProbeState\$SlitheriteBlockSpec.class",
                )
                val slitheriteBlockStateConstants =
                    slitheriteBlockStateClassEntries.flatMap { entryName ->
                        val entry = requireNotNull(probeZip.getEntry(entryName))
                        readClassUtf8Constants(
                            probeZip.getInputStream(entry).use { input ->
                                input.readAllBytes()
                            },
                        )
                    }.toSet()
                val slitheriteBlockPathConstants = canonicalSlitheriteLootDataEntries
                    .map { entryName ->
                        entryName.substringAfterLast('/').removeSuffix(".json")
                    }
                    .toSet()
                val requiredSlitheriteBlockStateConstants = setOf(
                    "minecraft:block",
                    "minecraft:item",
                    "net/minecraft/block/Block",
                    "net/minecraft/block/BlockState",
                    "net/minecraft/block/ButtonBlock",
                    "net/minecraft/block/PressurePlateBlock",
                    "net/minecraft/block/SlabBlock",
                    "net/minecraft/block/StairsBlock",
                    "net/minecraft/block/WallBlock",
                    "net/minecraft/command/argument/BlockArgumentParser",
                    "net/minecraft/item/BlockItem",
                    "net/minecraft/item/ItemStack",
                    "STATE_IDS",
                    "getRawId",
                    "sorted",
                    "PICKAXE_MINEABLE",
                    "NEEDS_STONE_TOOL",
                    "SLABS",
                    "STAIRS",
                    "WALLS",
                    "STONE_BRICKS",
                    "STONE_PRESSURE_PLATES",
                    "BUTTONS",
                    "LOOT_TABLES",
                    "SLAB_TYPE",
                    "generateLoot",
                    "getRecipeManager",
                    "getAdvancements",
                    "minecraft:crafting",
                    "minecraft:smelting",
                    "minecraft:stonecutting",
                    "etherology:alchemy_recipe",
                    "comparator",
                    "repeater",
                    "stonecutter",
                    "pedestal",
                    "unadjusted_lens",
                ) + slitheriteBlockPathConstants + canonicalSlitheriteRecipeIds
                check(
                    requiredSlitheriteBlockStateConstants.all(
                        slitheriteBlockStateConstants::contains,
                    ),
                ) {
                    "The server probe lost its Slitherite block-registry contract: " +
                        (requiredSlitheriteBlockStateConstants -
                            slitheriteBlockStateConstants).sorted()
                }

                val metalBlockStateEntry = requireNotNull(
                    probeZip.getEntry(
                        "dev/theplumteam/etherology/e2e/server/MetalBlockProbeState.class",
                    ),
                )
                val metalBlockStateConstants = readClassUtf8Constants(
                    probeZip.getInputStream(metalBlockStateEntry).use { input ->
                        input.readAllBytes()
                    },
                )
                val requiredMetalBlockStateConstants = setOf(
                    "minecraft:block",
                    "minecraft:item",
                    "etherology:azel_block",
                    "etherology:ebony_block",
                    "etherology:ethril_block",
                    "net/minecraft/block/Block",
                    "net/minecraft/block/BlockState",
                    "net/minecraft/item/BlockItem",
                    "net/minecraft/item/ItemStack",
                    "net/minecraft/nbt/NbtCompound",
                    "setBlockState",
                    "getBlock",
                    "getBlockFromItem",
                    "asItem",
                    "getHardness",
                    "getBlastResistance",
                    "getMapColor",
                    "getSoundGroup",
                    "isToolRequired",
                    "getLuminance",
                    "isOpaque",
                    "isFullCube",
                    "isIn",
                    "PICKAXE_MINEABLE",
                    "NEEDS_IRON_TOOL",
                    "BEACON_BASE_BLOCKS",
                    "METAL",
                    "writeNbt",
                    "fromNbt",
                    "Count",
                    "id",
                )
                check(
                    requiredMetalBlockStateConstants.all(
                        metalBlockStateConstants::contains,
                    ),
                ) {
                    "The server probe lost its metal-block registry contract: " +
                        (requiredMetalBlockStateConstants - metalBlockStateConstants)
                            .sorted()
                }

                val reloadResourceDigests = mapOf(
                    "probe-inputs/ether-source-reload-pack/pack.mcmeta" to
                        "0ba7dc05c7ce2955fab716f5c4a2a1ca9cde1da6ed0a06b0f06b937c11b69e00",
                    "probe-inputs/ether-source-reload-pack/data/etherology/" +
                        "ether_sources/default.json" to
                        "643ddd08f18b708ae161370759930475f35189ec75329b2b1b1f38620ba08e74",
                    "probe-inputs/ether-source-reload-pack/data/etherology/" +
                        "ether_sources/probe_addition.json" to
                        "ab897fa539e427e652ee9f59a4fe01d5668a4935bee5e5a7fdb25a93174c95e4",
                )
                val actualReloadResourceEntries = probeEntries.filter { entryName ->
                    entryName.startsWith("probe-inputs/") && !entryName.endsWith("/")
                }.toSet()
                check(actualReloadResourceEntries == reloadResourceDigests.keys) {
                    "The server probe reload-resource inventory changed: " +
                        actualReloadResourceEntries.sorted()
                }
                reloadResourceDigests.forEach { (entryName, expectedDigest) ->
                    val reloadResourceEntry = requireNotNull(probeZip.getEntry(entryName))
                    val actualDigest = MessageDigest.getInstance("SHA-256")
                        .digest(
                            probeZip.getInputStream(reloadResourceEntry).use { input ->
                                input.readAllBytes()
                            },
                        )
                        .joinToString("") { byte ->
                            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
                        }
                    check(actualDigest == expectedDigest) {
                        "The server probe reload resource $entryName changed: $actualDigest"
                    }
                }

                val probeEntry = requireNotNull(
                    probeZip.getEntry(
                        "dev/theplumteam/etherology/e2e/server/" +
                            "RegistryFoundationServerProbe.class",
                    ),
                )
                val probeBytes = probeZip.getInputStream(probeEntry).use { input ->
                    input.readAllBytes()
                }
                val probeConstants = readClassUtf8Constants(probeBytes)
                val requiredProbeConstants = setOf(
                    "etherology_e2e_server_probe",
                    "etherology",
                    "etherology_resonance",
                    "DEDICATED_SERVER",
                    "net/minecraftforge/event/TagsUpdatedEvent",
                    "SERVER_DATA_LOAD",
                    "shouldUpdateStaticData",
                    "getIds",
                    "streamTagsAndEntries",
                    "net/minecraftforge/event/server/ServerStartingEvent",
                    "net/minecraftforge/event/server/ServerStartedEvent",
                    "net/minecraftforge/event/server/ServerStoppingEvent",
                    "net/minecraftforge/event/server/ServerStoppedEvent",
                    "stop",
                    "etherology.serverProbe.evidenceRoot",
                    "etherology.serverProbe.runtimeKind",
                    "etherology.serverProbe.forbiddenModIds",
                    "loaded_mod_ids",
                    "forbidden_mod_ids_loaded",
                    "mods_forbidden_intersection_empty",
                    "enchantments",
                    "registry:enchantment:etherology:peal",
                    "registry:enchantment:etherology:reflection",
                    "registry:enchantment_etherology_ids_exact",
                    "enchantment:peal_class",
                    "enchantment:reflection_class",
                    "tag:enchantment_non_treasure_etherology_entries_exact",
                    "enchantments_captured_after_server_data_load",
                    "server_started_enchantments_rechecked",
                    "enchantment_registry_stable_after_reload",
                    "enchantment_properties_stable_after_reload",
                    "enchantment_tag_stable_after_reload",
                    "particles",
                    "registry:particle_type_etherology_ids_exact",
                    "particle_capture_error",
                    "particle_payload_families_exact",
                    "particle_type_classes_exact",
                    "particle_should_always_spawn_false_exact",
                    "particle_codecs_present_exact",
                    "particle_parameters_factories_present_exact",
                    "particle_factory_sample_effect_classes_exact",
                    "particle_factory_sample_types_exact",
                    "particle_factory_sample_as_strings_exact",
                    "particle_packet_round_trips_exact",
                    "particle_codec_round_trips_exact",
                    "seal_type_order_exact",
                    "seal_type_codec_round_trips_exact",
                    "seal_type_colors_exact",
                    "seal_type_textures_exact",
                    "particles_captured_after_server_data_load",
                    "server_started_particles_rechecked",
                    "particle_registry_stable_after_reload",
                    "particle_type_contract_stable_after_reload",
                    "particle_wire_contract_stable_after_reload",
                    "material_items",
                    "registry:material_item_ids_exact",
                    "material_item_capture_error",
                    "material_item_runtime_class_exact",
                    "material_item_max_counts_exact",
                    "material_item_stack_nbt_round_trips_exact",
                    "material_item_save_representations_exact",
                    "material_items_captured_after_server_data_load",
                    "server_started_material_items_rechecked",
                    "material_item_registry_stable_after_reload",
                    "material_item_properties_stable_after_reload",
                    "material_item_stack_nbt_stable_after_reload",
                    "metal_blocks",
                    "registry:metal_block_ids_exact",
                    "registry:metal_block_item_ids_exact",
                    "metal_block_capture_error",
                    "metal_block_runtime_classes_exact",
                    "metal_block_item_mappings_exact",
                    "metal_block_properties_exact",
                    "metal_block_tags_exact",
                    "metal_block_stack_nbt_round_trips_exact",
                    "metal_block_save_representations_exact",
                    "metal_blocks_captured_after_server_data_load",
                    "server_started_metal_blocks_rechecked",
                    "metal_block_placement_positions_exact",
                    "metal_block_placed_ids_exact",
                    "metal_block_placement_exact",
                    "metal_block_registry_stable_after_reload",
                    "metal_block_properties_stable_after_reload",
                    "metal_block_tags_stable_after_reload",
                    "metal_block_stack_nbt_stable_after_reload",
                    "metal_block_placement_stable_after_reload",
                    "attrahite_blocks",
                    "registry:attrahite_block_ids_exact",
                    "registry:attrahite_block_item_ids_exact",
                    "attrahite_block_capture_error",
                    "attrahite_block_runtime_classes_exact",
                    "attrahite_block_item_mappings_exact",
                    "attrahite_block_properties_exact",
                    "attrahite_block_tags_exact",
                    "attrahite_block_stack_nbt_round_trips_exact",
                    "attrahite_block_save_representations_exact",
                    "attrahite_blocks_captured_at_initial_tag_load",
                    "attrahite_blocks_captured_after_server_data_load",
                    "server_started_attrahite_blocks_rechecked",
                    "attrahite_block_placement_positions_exact",
                    "attrahite_block_placed_ids_exact",
                    "attrahite_block_placed_states_exact",
                    "attrahite_block_placement_exact",
                    "attrahite_world_save_failure",
                    "attrahite_world_saved_after_placement",
                    "attrahite_loaded_data_capture_error",
                    "attrahite_loot_table_ids_exact",
                    "attrahite_standard_loot_exact",
                    "attrahite_raw_silk_touch_loot_exact",
                    "attrahite_raw_fortune_scaled_loot_exact",
                    "attrahite_recipe_ids_exact",
                    "attrahite_recipes_exact",
                    "attrahite_recipes_match_and_craft_exact",
                    "attrahite_advancement_ids_exact",
                    "attrahite_advancements_exact",
                    "attrahite_loaded_data_contract_exact",
                    "attrahite_block_registry_stable_after_reload",
                    "attrahite_block_properties_stable_after_reload",
                    "attrahite_block_tags_stable_after_reload",
                    "attrahite_block_stack_nbt_stable_after_reload",
                    "attrahite_loaded_data_stable_after_reload",
                    "attrahite_loaded_data_fresh_after_reload",
                    "attrahite_block_placement_stable_after_reload",
                    "slitherite_blocks",
                    "registry:slitherite_block_ids_exact",
                    "registry:slitherite_block_item_ids_exact",
                    "slitherite_block_capture_error",
                    "slitherite_block_runtime_classes_exact",
                    "slitherite_block_item_mappings_exact",
                    "slitherite_block_properties_exact",
                    "slitherite_aggregate_state_count_exact",
                    "slitherite_aggregate_unique_raw_id_count_exact",
                    "slitherite_state_raw_ids_aggregate_exact",
                    "slitherite_state_network_ids_exact",
                    "slitherite_block_tags_exact",
                    "slitherite_blocks_captured_at_initial_tag_load",
                    "slitherite_blocks_captured_after_server_data_load",
                    "server_started_slitherite_blocks_rechecked",
                    "slitherite_native_placement_capture_error",
                    "slitherite_native_block_item_placements_exact",
                    "slitherite_native_block_item_placement_contract_exact",
                    "direct_block_item_placements_exact",
                    "slitherite_fixture_capture_error",
                    "slitherite_fixture_positions_exact",
                    "slitherite_fixture_support_positions_exact",
                    "slitherite_fixture_placed_ids_exact",
                    "slitherite_fixture_placed_states_exact",
                    "slitherite_fixture_support_ids_exact",
                    "slitherite_fixture_placement_exact",
                    "initial_server_fixture_exact",
                    "slitherite_behavior_capture_error",
                    "slitherite_button_pulse_reset_exact",
                    "slitherite_pressure_plate_entities_exact",
                    "slitherite_behavior_fixture_reset_exact",
                    "slitherite_behavior_contract_exact",
                    "slitherite_world_save_failure",
                    "slitherite_world_saved_after_placement",
                    "forced_world_save",
                    "slitherite_fixture_saved_after_force_save",
                    "slitherite_loaded_data_capture_error",
                    "slitherite_loot_tables_exact",
                    "slitherite_self_drops_exact",
                    "slitherite_double_slab_drops_x1_exact",
                    "slitherite_owned_recipe_ids_exact",
                    "slitherite_owned_recipes_exact",
                    "slitherite_owned_advancements_exact",
                    "slitherite_related_recipe_ids_exact",
                    "slitherite_related_recipes_recorded_not_owned",
                    "slitherite_loaded_data_contract_exact",
                    "slitherite_block_registry_stable_after_reload",
                    "slitherite_block_default_states_stable_after_reload",
                    "slitherite_block_tags_stable_after_reload",
                    "slitherite_loaded_data_stable_after_reload",
                    "slitherite_loaded_data_fresh_after_reload",
                    "slitherite_fixture_stable_after_reload",
                    "slitherite_block_registry_stable",
                    "slitherite_block_default_states_stable",
                    "slitherite_block_tags_stable",
                    "slitherite_block_loaded_data_stable",
                    "slitherite_block_loaded_data_fresh",
                    "slitherite_block_placement_stable",
                    "food_items",
                    "food_consumption",
                    "registry:food_item_ids_exact",
                    "food_item_capture_error",
                    "food_item_runtime_class_exact",
                    "food_item_properties_exact",
                    "food_item_stack_nbt_round_trip_exact",
                    "food_item_save_representation_exact",
                    "food_item_contract_exact",
                    "food_items_captured_after_server_data_load",
                    "server_started_food_items_rechecked",
                    "food_item_registry_stable_after_reload",
                    "food_item_properties_stable_after_reload",
                    "food_item_stack_nbt_stable_after_reload",
                    "server_started_food_consumption_capture_error",
                    "server_started_food_consumption_player_class",
                    "server_started_food_consumption_player_uuid",
                    "server_started_food_consumption_player_name",
                    "server_started_food_consumption_item_id",
                    "server_started_food_consumption_result_item_id",
                    "server_started_food_consumption_initial_hunger",
                    "server_started_food_consumption_initial_saturation",
                    "server_started_food_consumption_initial_stack_count",
                    "server_started_food_consumption_result_hunger",
                    "server_started_food_consumption_result_saturation",
                    "server_started_food_consumption_result_stack_count",
                    "server_started_food_consumption_same_stack_instance",
                    "server_started_food_consumption_exact",
                    "reloaded_food_consumption_capture_error",
                    "reloaded_food_consumption_exact",
                    "food_consumption_fresh_player_after_reload",
                    "food_consumption_stable_after_reload",
                    "loot_condition",
                    "registry:loot_condition:etherology:random_chance_with_fortune",
                    "registry:loot_condition_etherology_ids_exact",
                    "registry:loot_condition_serializer_class",
                    "loot_table:probe_table_loaded",
                    "loot_table:empty_tool_items_exact",
                    "loot_table:fortune_one_items_exact",
                    "loot_condition_captured_after_server_data_load",
                    "server_started_loot_condition_rechecked",
                    "ether_sources",
                    "reload",
                    "ether_source_initial_entries_exact",
                    "ether_source_reloaded_entries_exact",
                    "ether_source_reloaded_legacy_rela_absent",
                    "reload_pack_enabled",
                    "enabled_data_pack_names",
                    "enabled_data_packs_exact",
                    "registry_stable_after_reload",
                    "tags_stable_after_reload",
                    "loot_condition_registry_and_behavior_stable_after_reload",
                    "loot_table_instance_replaced_after_reload",
                    "server_stop_requested_after_reload",
                    "etherology:primoshard_rela",
                    "getCommandManager",
                    "getCommandSource",
                    "executeWithPrefix",
                    "getDataPackManager",
                    "getEnabledNames",
                    "file/etherology-e2e-ether-source-reload",
                    "getMods",
                    "getModId",
                    "loom-userdev",
                    "[EtherologyServerProbe] registry_foundation_checked",
                    "[EtherologyServerProbe] reload_requested",
                    "[EtherologyServerProbe] tags_updated_reload",
                    "[EtherologyServerProbe] reload_command_returned",
                    "[EtherologyServerProbe] stop_requested",
                    "[EtherologyServerProbe] report_published",
                    "dev/theplumteam/etherology/e2e/server/EnchantmentProbeState",
                    "dev/theplumteam/etherology/e2e/server/EtherSourceProbeState",
                    "dev/theplumteam/etherology/e2e/server/LootConditionProbeState",
                    "dev/theplumteam/etherology/e2e/server/ParticleProbeState",
                    "dev/theplumteam/etherology/e2e/server/MaterialItemProbeState",
                    "dev/theplumteam/etherology/e2e/server/MetalBlockProbeState",
                    "dev/theplumteam/etherology/e2e/server/FoodItemProbeState",
                    "dev/theplumteam/etherology/e2e/server/ForestLanternProbeState",
                    "dev/theplumteam/etherology/e2e/server/AttrahiteBlockProbeState",
                    "dev/theplumteam/etherology/e2e/server/SlitheriteBlockProbeState",
                    "net/minecraftforge/event/TickEvent\$ServerTickEvent",
                    "END",
                    "registry:block:etherology:forest_lantern",
                    "registry:block_item:etherology:forest_lantern",
                    "forest_lantern_loaded_data_fresh_after_reload",
                    "forest_lantern_mechanics_stable_after_reload",
                    "forest_lantern_contract_exact",
                    "dev/theplumteam/etherology/e2e/server/ReloadDataPackWriter",
                    "dev/theplumteam/etherology/e2e/server/ServerProbeMemoryHandoff",
                    "publishAndAwaitAcknowledgement",
                    "dev/theplumteam/etherology/e2e/server/ServerProbeProcessTerminator",
                    "java/lang/Thread",
                    "currentThread",
                    "exitStatusForReport",
                    "[EtherologyServerProbe] loom_userdev_exit_scheduled " +
                        "status={} server_thread_join_timeout_ms={}",
                )
                check(requiredProbeConstants.all(probeConstants::contains)) {
                    "The dedicated-server probe lost part of its lifecycle contract: " +
                        (requiredProbeConstants - probeConstants).sorted()
                }

                val probeMemoryHandoffMethodReferences =
                    readClassMethodReferences(probeBytes).filter { methodReference ->
                        methodReference.startsWith(
                            "dev/theplumteam/etherology/e2e/server/" +
                                "ServerProbeMemoryHandoff.",
                        )
                    }.toSet()
                check(probeMemoryHandoffMethodReferences == setOf(
                    "dev/theplumteam/etherology/e2e/server/" +
                        "ServerProbeMemoryHandoff.publishAndAwaitAcknowledgement:()V",
                )) {
                    "The dedicated-server probe memory-handoff call changed: " +
                        probeMemoryHandoffMethodReferences.sorted()
                }

                val memoryHandoffEntry = requireNotNull(
                    probeZip.getEntry(
                        "dev/theplumteam/etherology/e2e/server/" +
                            "ServerProbeMemoryHandoff.class",
                    ),
                )
                val memoryHandoffBytes = probeZip.getInputStream(memoryHandoffEntry)
                    .use { input -> input.readAllBytes() }
                val memoryHandoffConstants = readClassUtf8Constants(memoryHandoffBytes)
                val expectedJavaOptionInjectionVariables = setOf(
                    "JAVA_TOOL_OPTIONS",
                    "JDK_JAVA_OPTIONS",
                    "_JAVA_OPTIONS",
                )
                val actualJavaOptionInjectionVariables =
                    memoryHandoffConstants.filter { constant ->
                        constant == "JAVA_TOOL_OPTIONS"
                            || constant.endsWith("_JAVA_OPTIONS")
                    }.toSet()
                check(
                    actualJavaOptionInjectionVariables ==
                        expectedJavaOptionInjectionVariables,
                ) {
                    "The server probe memory-handoff Java option guards changed: " +
                        actualJavaOptionInjectionVariables.sorted()
                }
                val expectedMemoryHandoffEnvironmentVariables = setOf(
                    "ETHERLOGY_E2E_FORGE_SERVER_MEMORY_ACKNOWLEDGEMENT",
                    "ETHERLOGY_E2E_FORGE_SERVER_MEMORY_HANDOFF",
                    "ETHERLOGY_E2E_FORGE_SERVER_RUN_TOKEN",
                )
                val actualMemoryHandoffEnvironmentVariables =
                    memoryHandoffConstants.filter { constant ->
                        constant.startsWith("ETHERLOGY_E2E_FORGE_SERVER_")
                    }.toSet()
                check(
                    actualMemoryHandoffEnvironmentVariables ==
                        expectedMemoryHandoffEnvironmentVariables,
                ) {
                    "The server probe memory-handoff environment changed: " +
                        actualMemoryHandoffEnvironmentVariables.sorted()
                }
                val expectedMemoryHandoffFileNames = setOf(
                    ".forge-server-java-memory-handoff.json",
                    ".forge-server-java-memory-ready",
                )
                val actualMemoryHandoffFileNames =
                    memoryHandoffConstants.filter { constant ->
                        constant.startsWith(".forge-server-java-memory-")
                    }.toSet()
                check(actualMemoryHandoffFileNames == expectedMemoryHandoffFileNames) {
                    "The server probe memory-handoff file inventory changed: " +
                        actualMemoryHandoffFileNames.sorted()
                }
                val requiredMemoryHandoffConstants = setOf(
                    "EXACT_MAXIMUM_HEAP_ARGUMENT",
                    "EXACT_MAXIMUM_HEAP_BYTES",
                    "-Xmx2048m",
                    "java_feature",
                    "maximum_heap_bytes",
                    "maximum_heap_arguments",
                    "The dedicated server memory handoff requires Java 17",
                )
                check(
                    requiredMemoryHandoffConstants.all(memoryHandoffConstants::contains),
                ) {
                    "The server probe memory-handoff heap contract changed: " +
                        (requiredMemoryHandoffConstants - memoryHandoffConstants).sorted()
                }
                val memoryHandoffMethodReferences =
                    readClassMethodReferences(memoryHandoffBytes)
                val expectedMemoryHandoffIdentityAndHeapMethodReferences = setOf(
                    "java/lang/management/ManagementFactory.getRuntimeMXBean:" +
                        "()Ljava/lang/management/RuntimeMXBean;",
                    "java/lang/management/RuntimeMXBean.getInputArguments:" +
                        "()Ljava/util/List;",
                    "java/lang/ProcessHandle.current:()Ljava/lang/ProcessHandle;",
                    "java/lang/ProcessHandle.info:()Ljava/lang/ProcessHandle\$Info;",
                    "java/lang/ProcessHandle.pid:()J",
                    "java/lang/ProcessHandle\$Info.command:()Ljava/util/Optional;",
                    "java/lang/Runtime.version:()Ljava/lang/Runtime\$Version;",
                    "java/lang/Runtime.getRuntime:()Ljava/lang/Runtime;",
                    "java/lang/Runtime.maxMemory:()J",
                    "java/lang/Runtime\$Version.feature:()I",
                )
                val actualMemoryHandoffIdentityAndHeapMethodReferences =
                    memoryHandoffMethodReferences.filter { methodReference ->
                        methodReference.startsWith(
                            "java/lang/management/ManagementFactory.",
                        ) || methodReference.startsWith(
                            "java/lang/management/RuntimeMXBean.",
                        ) || methodReference.startsWith("java/lang/ProcessHandle.")
                            || methodReference.startsWith("java/lang/ProcessHandle\$Info.")
                            || methodReference.startsWith("java/lang/Runtime.")
                            || methodReference.startsWith("java/lang/Runtime\$Version.")
                    }.toSet()
                check(
                    actualMemoryHandoffIdentityAndHeapMethodReferences ==
                        expectedMemoryHandoffIdentityAndHeapMethodReferences,
                ) {
                    "The server probe memory-handoff identity/heap calls changed: " +
                        actualMemoryHandoffIdentityAndHeapMethodReferences.sorted()
                }
                val expectedMemoryHandoffSystemMethodReferences = setOf(
                    "java/lang/System.getenv:(Ljava/lang/String;)Ljava/lang/String;",
                    "java/lang/System.nanoTime:()J",
                )
                val actualMemoryHandoffSystemMethodReferences =
                    memoryHandoffMethodReferences.filter { methodReference ->
                        methodReference.startsWith("java/lang/System.")
                    }.toSet()
                check(
                    actualMemoryHandoffSystemMethodReferences ==
                        expectedMemoryHandoffSystemMethodReferences,
                ) {
                    "The server probe memory-handoff environment/time calls changed: " +
                        actualMemoryHandoffSystemMethodReferences.sorted()
                }
                val expectedMemoryHandoffArtifactMethodReferences = setOf(
                    "java/nio/file/Files.createLink:" +
                        "(Ljava/nio/file/Path;Ljava/nio/file/Path;)Ljava/nio/file/Path;",
                    "java/nio/file/Files.createTempFile:" +
                        "(Ljava/nio/file/Path;Ljava/lang/String;Ljava/lang/String;" +
                        "[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;",
                    "java/nio/file/Files.deleteIfExists:(Ljava/nio/file/Path;)Z",
                    "java/nio/file/Files.exists:" +
                        "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z",
                    "java/nio/file/Files.isDirectory:" +
                        "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z",
                    "java/nio/file/Files.isRegularFile:" +
                        "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z",
                    "java/nio/file/Files.isSymbolicLink:(Ljava/nio/file/Path;)Z",
                    "java/nio/file/Files.readString:" +
                        "(Ljava/nio/file/Path;Ljava/nio/charset/Charset;)" +
                        "Ljava/lang/String;",
                    "java/nio/file/Files.size:(Ljava/nio/file/Path;)J",
                    "java/nio/file/Files.write:" +
                        "(Ljava/nio/file/Path;[B[Ljava/nio/file/OpenOption;)" +
                        "Ljava/nio/file/Path;",
                )
                val actualMemoryHandoffArtifactMethodReferences =
                    memoryHandoffMethodReferences.filter { methodReference ->
                        methodReference.startsWith("java/nio/file/Files.")
                    }.toSet()
                check(
                    actualMemoryHandoffArtifactMethodReferences ==
                        expectedMemoryHandoffArtifactMethodReferences,
                ) {
                    "The server probe memory-handoff artifact calls changed: " +
                        actualMemoryHandoffArtifactMethodReferences.sorted()
                }
                val memoryHandoffOwner =
                    "dev/theplumteam/etherology/e2e/server/ServerProbeMemoryHandoff."
                val expectedMemoryHandoffPrivateLifecycleMethodReferences = setOf(
                    memoryHandoffOwner +
                        "publishExclusive:(Ljava/nio/file/Path;Ljava/lang/String;)V",
                    memoryHandoffOwner +
                        "awaitAcknowledgement:" +
                        "(Ljava/nio/file/Path;Ljava/lang/String;)V",
                )
                val actualMemoryHandoffPrivateLifecycleMethodReferences =
                    memoryHandoffMethodReferences.filter { methodReference ->
                        methodReference.startsWith(
                            memoryHandoffOwner + "publishExclusive:",
                        ) || methodReference.startsWith(
                            memoryHandoffOwner + "awaitAcknowledgement:",
                        )
                    }.toSet()
                check(
                    actualMemoryHandoffPrivateLifecycleMethodReferences ==
                        expectedMemoryHandoffPrivateLifecycleMethodReferences,
                ) {
                    "The server probe memory-handoff private lifecycle calls changed: " +
                        actualMemoryHandoffPrivateLifecycleMethodReferences.sorted()
                }

                val terminatorEntry = requireNotNull(
                    probeZip.getEntry(
                        "dev/theplumteam/etherology/e2e/server/" +
                            "ServerProbeProcessTerminator.class",
                    ),
                )
                val terminatorConstants = readClassUtf8Constants(
                    probeZip.getInputStream(terminatorEntry).use { input ->
                        input.readAllBytes()
                    },
                )
                val requiredTerminatorConstants = setOf(
                    "loom-userdev",
                    "etherology-e2e-server-probe-exit",
                    "java/lang/System",
                    "exit",
                    "status",
                    "passed",
                    "java/lang/Thread",
                    "isAlive",
                    "join",
                    "setDaemon",
                    "start",
                )
                check(requiredTerminatorConstants.all(terminatorConstants::contains)) {
                    "The dedicated-server probe lost its clean userdev exit contract: " +
                        (requiredTerminatorConstants - terminatorConstants).sorted()
                }
                check(
                    "java/lang/Runtime" !in terminatorConstants
                        && "halt" !in terminatorConstants,
                ) {
                    "The dedicated-server probe bypasses clean JVM shutdown"
                }

                val metadataEntry = requireNotNull(probeZip.getEntry("META-INF/mods.toml")) {
                    "The dedicated-server probe JAR has no META-INF/mods.toml"
                }
                val metadataText = probeZip.getInputStream(metadataEntry)
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }
                check(metadataText.contains("modId=\"etherology_e2e_server_probe\"")) {
                    "The dedicated-server probe metadata has the wrong mod id"
                }
                check(metadataText.contains("side=\"SERVER\"")) {
                    "The dedicated-server probe metadata is not server-only"
                }
                check(metadataText.contains("modId=\"etherology\"")) {
                    "The dedicated-server probe metadata does not require Etherology"
                }
                check(metadataText.contains("versionRange=\"[${project.version}]\"")) {
                    "The dedicated-server probe does not require the exact Etherology version"
                }
            }
        }
    }

    val verifyServerProbeIsolation = tasks.register("verifyServerProbeIsolation") {
        group = "verification"
        description = "Proves the dedicated-server probe stays outside production artifacts."
        dependsOn(serverProbeJar, forgeShadowJar)
        inputs.file(serverProbeJar.flatMap { it.archiveFile })
        inputs.file(forgeShadowJar.flatMap { it.archiveFile })

        doLast {
            val probeFile = serverProbeJar.get().archiveFile.get().asFile
            val productionFile = forgeShadowJar.get().archiveFile.get().asFile
            check(probeFile != productionFile) {
                "The production and dedicated-server probe resolve to the same artifact"
            }
            ZipFile(productionFile).use { productionZip ->
                check(productionZip.entries().asSequence().none { entry ->
                    entry.name.startsWith("dev/theplumteam/etherology/e2e/server/")
                        || entry.name.startsWith("data/etherology_e2e_server_probe/")
                        || entry.name.startsWith("probe-inputs/")
                }) {
                    "The Forge production artifact contains dedicated-server probe content"
                }
            }
        }
    }

    val verifyRegistryFoundationServerProbe =
        tasks.register("verifyRegistryFoundationServerProbe") {
            group = "verification"
            description =
                "Builds and validates the prepared Forge 1.20.1 Slitherite server probe."
            dependsOn(
                validateForgeSlitheriteStaticMilestone,
                forgeSlitheriteBlockRegistryServerSafetyTest,
                serverProbeTestTask,
                validateServerProbeProfile,
                validateServerProbeRunConfiguration,
                verifyServerProbeArtifact,
                verifyServerProbeIsolation,
            )
        }
    serverProbeRunTask.configure {
        dependsOn(verifyRegistryFoundationServerProbe)
        doFirst {
            val evidenceDirectories = listOf(
                serverProbeEvidenceRoot.resolve("reports"),
                serverProbeEvidenceRoot.resolve("logs"),
            )
            val failure = serverProbeSafetyInterlockFailure(
                ServerProbeSafetyInterlockSpec(
                    sealedArchive = serverProbeSealedArchive,
                    ownedPathAnchor = rootProject.file("scripts/e2e"),
                    runToken = System.getenv(
                        "ETHERLOGY_E2E_FORGE_SERVER_RUN_TOKEN",
                    ),
                    runLock = serverProbeRunLock,
                    runAttempt = serverProbeRunAttempt,
                    profileMarker = serverProbeProfileMarker,
                    profileId = serverProbeProfileId,
                    managedBy = "scripts/e2e/forge_server.py",
                    taskPath =
                        ":forge:1.20.1:runRegistryFoundationServerProbe",
                    scenarioId = serverProbeScenarioId,
                    evidenceDirectories = evidenceDirectories,
                ),
            )
            check(failure == null) {
                failure?.message.orEmpty()
            }
        }
        doFirst("blockPostponedSlitheriteV20NativeRun") {
            throw GradleException(forgeServerNativeRunPostponedReason)
        }
    }

    val e2eHarness = sourceSets.create("e2eHarness") {
        java.setSrcDirs(
            listOf(
                rootProject.file("e2e-harness/forge/1.20.1/src/main/java"),
                rootProject.file("e2e-harness/shared/1.20.1/src/main/java"),
            ),
        )
        resources.setSrcDirs(
            listOf(rootProject.file("e2e-harness/forge/1.20.1/src/main/resources")),
        )
        compileClasspath += sourceSets.main.get().compileClasspath
        runtimeClasspath += output + compileClasspath
    }

    val e2eHarnessTest = sourceSets.create("e2eHarnessTest") {
        java.setSrcDirs(
            listOf(
                rootProject.file("e2e-harness/forge/1.20.1/src/test/java"),
                rootProject.file("e2e-harness/shared/1.20.1/src/test/java"),
            ),
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
        inputs.file(forgeE2eProfileManifest)
        testClassesDirs = e2eHarnessTest.output.classesDirs
        classpath = e2eHarnessTest.runtimeClasspath
        systemProperty(
            "etherology.e2e.forge.activeProfile",
            forgeE2eProfileManifest.absolutePath,
        )
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
                forgeChannelProfileSnapshotV11,
                rootProject.file("scripts/e2e/test_forge_channel_evidence.py"),
                rootProject.file("scripts/e2e/forge_client.py"),
                rootProject.file("scripts/e2e/java_installer_supervisor.py"),
                rootProject.file("scripts/e2e/macos_guarded_java.py"),
                rootProject.file("scripts/baseline/macos_memory_guard.py"),
                forgeSlitheriteRunContractV19,
                rootProject.file("scripts/e2e/forge_evidence.py"),
                rootProject.file("release/release-matrix.json"),
                rootProject.file("gradle.properties"),
            )
        }

    val forgeForestLanternEvidenceVerifierTest =
        tasks.register<Exec>("forgeForestLanternEvidenceVerifierTest") {
            group = "verification"
            description =
                "Runs adversarial tests for the Forge Forest Lantern client verifier."
            workingDir(rootProject.projectDir)
            commandLine(
                "python3",
                "-B",
                "-m",
                "unittest",
                "scripts/e2e/test_forge_forest_lantern_evidence.py",
                "scripts/e2e/test_forge_client.py",
                "scripts/e2e/test_java_installer_supervisor.py",
                "scripts/e2e/test_macos_guarded_java.py",
            )
            inputs.files(
                forgeForestLanternEvidenceVerifier,
                forgeForestLanternEvidenceTest,
                forgeE2eProfileManifest,
                forgeChannelProfileSnapshotV11,
                forgeForestLanternProfileSnapshotV12,
                forgeForestLanternProfileSnapshotV13,
                forgeAttrahiteProfileSnapshotV14,
                forgeAttrahiteProfileSnapshotV15,
                forgeAttrahiteProfileSnapshotV16,
                forgeAttrahiteProfileSnapshotV17,
                forgeSlitheriteProfileSnapshotV18,
                forgeSlitheriteProfileSnapshotV19,
                rootProject.file("scripts/e2e/forge_client.py"),
                rootProject.file("scripts/e2e/java_installer_supervisor.py"),
                rootProject.file("scripts/e2e/macos_guarded_java.py"),
                forgeSlitheriteRunContractV18,
                forgeSlitheriteRunContractV19,
                rootProject.file("scripts/e2e/test_forge_client.py"),
                rootProject.file("scripts/e2e/test_java_installer_supervisor.py"),
                rootProject.file("scripts/e2e/test_macos_guarded_java.py"),
                rootProject.file("scripts/baseline/macos_memory_guard.py"),
                rootProject.file("scripts/e2e/forge_evidence.py"),
                rootProject.file("scripts/e2e/test_evidence.py"),
                rootProject.file("release/release-matrix.json"),
                rootProject.file("gradle.properties"),
                rootProject.file("forge/build.gradle.kts"),
                rootProject.file("docs/testing/E2E-CONTRACT.md"),
            )
        }

    val forgeAttrahiteBlockRegistryV14HistorySafetyTest =
        tasks.register<Exec>("forgeAttrahiteBlockRegistryV14HistorySafetyTest") {
            group = "verification"
            description =
                "Runs immutable prepared-history Forge Attrahite client-v14 verifier tests."
            dependsOn(forgeForestLanternEvidenceVerifierTest)
            workingDir(rootProject.projectDir)
            commandLine(
                "python3",
                "-B",
                "-m",
                "unittest",
                "scripts/e2e/test_forge_attrahite_evidence_v14.py",
            )
            inputs.files(
                forgeAttrahiteEvidenceVerifierV14,
                forgeAttrahiteEvidenceTestV14,
                forgeE2eProfileManifest,
                forgeChannelProfileSnapshotV11,
                forgeForestLanternProfileSnapshotV12,
                forgeForestLanternProfileSnapshotV13,
                forgeAttrahiteProfileSnapshotV14,
                rootProject.file("scripts/e2e/forge_client.py"),
                forgeSlitheriteRunContractV19,
                rootProject.file("scripts/e2e/forge_evidence.py"),
                rootProject.file("scripts/e2e/test_forge_evidence.py"),
                rootProject.file("release/release-matrix.json"),
                rootProject.file("gradle.properties"),
            )
            inputs.files(
                rootProject.fileTree("src/main/generated/assets/etherology") {
                    include("blockstates/attrahite*.json")
                    include("models/block/attrahite*.json")
                    include("models/item/attrahite*.json")
                },
                rootProject.fileTree("src/client/resources/assets/etherology") {
                    include("textures/block/attrahite*.png")
                },
            ).withPropertyName("forgeAttrahiteClientV14HistoryResources")
            doFirst {
                val historyPins = listOf(
                    Triple(
                        forgeAttrahiteProfileSnapshotV14,
                        3702L,
                        "d880c523c6987836cfad5dfe9d640b1d4ee807664f3fc335ae5b31b6fbfe1e44",
                    ),
                    Triple(
                        forgeAttrahiteEvidenceVerifierV14,
                        62748L,
                        "17a09e70b3044bfd1db4602bc82eb30cb96bac46713f6646dc234e8ea97da073",
                    ),
                    Triple(
                        forgeAttrahiteEvidenceTestV14,
                        24655L,
                        "96cbf93a079267802c93d5e7c3e92676a10d070d63f7e9a2cf16776a7945bc09",
                    ),
                )
                historyPins.forEach { (file, expectedSize, expectedSha256) ->
                    check(file.isFile && !Files.isSymbolicLink(file.toPath())) {
                        "Forge Attrahite client-v14 history file is missing or linked: $file"
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                        .digest(file.readBytes())
                        .joinToString("") { byte ->
                            "%02x".format(byte.toInt() and 0xff)
                        }
                    check(file.length() == expectedSize && digest == expectedSha256) {
                        "Forge Attrahite client-v14 history bytes changed: $file"
                    }
                }
            }
        }

    val forgeAttrahiteBlockRegistryEvidenceVerifierTest =
        tasks.register<Exec>("forgeAttrahiteBlockRegistryEvidenceVerifierTest") {
            group = "verification"
            description =
                "Runs adversarial tests for the Forge Attrahite block-registry client verifier."
            dependsOn(forgeAttrahiteBlockRegistryV14HistorySafetyTest)
            workingDir(rootProject.projectDir)
            commandLine(
                "python3",
                "-B",
                "-m",
                "unittest",
                "scripts/e2e/test_forge_attrahite_evidence_v17.py",
                "scripts/e2e/test_forge_client.py",
            )
            inputs.files(
                forgeAttrahiteEvidenceVerifierV14,
                forgeAttrahiteEvidenceTestV14,
                forgeAttrahiteEvidenceVerifierV15,
                forgeAttrahiteEvidenceTestV15,
                forgeAttrahiteEvidenceVerifierV16,
                forgeAttrahiteEvidenceTestV16,
                forgeAttrahiteEvidenceVerifier,
                forgeAttrahiteEvidenceTest,
                forgeE2eProfileManifest,
                forgeChannelProfileSnapshotV11,
                forgeForestLanternProfileSnapshotV12,
                forgeForestLanternProfileSnapshotV13,
                forgeAttrahiteProfileSnapshotV14,
                forgeAttrahiteProfileSnapshotV15,
                forgeAttrahiteProfileSnapshotV16,
                forgeAttrahiteProfileSnapshotV17,
                rootProject.file("scripts/e2e/forge_client.py"),
                rootProject.file("scripts/e2e/java_installer_supervisor.py"),
                rootProject.file("scripts/e2e/macos_guarded_java.py"),
                rootProject.file("scripts/baseline/macos_memory_guard.py"),
                forgeSlitheriteRunContractV19,
                rootProject.file("scripts/e2e/test_forge_client.py"),
                rootProject.file("scripts/e2e/forge_evidence.py"),
                rootProject.file("scripts/e2e/test_forge_evidence.py"),
                rootProject.file("scripts/e2e/consumed_history.py"),
                rootProject.file("release/release-matrix.json"),
                rootProject.file("gradle.properties"),
                rootProject.file("forge/build.gradle.kts"),
            )
            inputs.files(
                rootProject.file(
                    "scripts/e2e/.state/" +
                        "etherology-e2e-forge-1.20.1-v15-start.attempted",
                ),
                rootProject.file(
                    "scripts/e2e/.state/logs/forge-1.20.1-20260901T014800Z.log",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v15/game/logs/latest.log",
                ),
            ).withPropertyName("forgeAttrahiteClientV15FailureHistory").optional()
            inputs.files(
                rootProject.file(
                    "scripts/e2e/.state/" +
                        "etherology-e2e-forge-1.20.1-v16-start.attempted",
                ),
                rootProject.file(
                    "scripts/e2e/.state/logs/forge-1.20.1-20260901T021931Z.log",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v16/evidence/" +
                        "attrahite-block-registry/reports/report.json",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v16/evidence/" +
                        "attrahite-block-registry/reports/done.marker",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v16/game/logs/latest.log",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v16/game/mods/" +
                        "etherology-forge-e2e-harness.jar",
                ),
            ).withPropertyName("forgeAttrahiteClientV16FailureHistory").optional()
            inputs.files(
                rootProject.file(
                    "scripts/e2e/.state/" +
                        "etherology-e2e-forge-1.20.1-v17-start.attempted",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v17/" +
                        ".etherology-forge-e2e-profile.json",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v17/forge-artifact-lock.json",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v17/evidence/" +
                        ".etherology-e2e-evidence.json",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v17/evidence/" +
                        "attrahite-block-registry/reports/report.json",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v17/evidence/" +
                        "attrahite-block-registry/reports/done.marker",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v17/evidence/" +
                        "attrahite-block-registry/screenshots/" +
                        "attrahite-block-registry-initial.png",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v17/evidence/" +
                        "attrahite-block-registry/screenshots/" +
                        "attrahite-block-registry-reopened.png",
                ),
                rootProject.file(
                    "scripts/e2e/.state/logs/forge-1.20.1-20260904T071059Z.log",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v17/game/logs/latest.log",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v17/game/mods/" +
                        "etherology-forge-e2e-harness.jar",
                ),
                rootProject.file(
                    "scripts/e2e/.state/runtimes/" +
                        "etherology-e2e-forge-1.20.1-v17/game/mods/" +
                        "etherology-forge-under-test.jar",
                ),
            ).withPropertyName("forgeAttrahiteClientV17AcceptedHistory").optional()
            inputs.dir(forgeAttrahiteClientEvidenceArchive)
                .withPropertyName("forgeAttrahiteClientEvidenceArchiveSafetyFixture")
            inputs.files(
                rootProject.fileTree("src/main/generated/assets/etherology") {
                    include("blockstates/attrahite*.json")
                    include("models/block/attrahite*.json")
                    include("models/item/attrahite*.json")
                },
                rootProject.fileTree("src/client/resources/assets/etherology") {
                    include("textures/block/attrahite*.png")
                },
            ).withPropertyName("forgeAttrahiteClientEvidenceResources")
        }

    val forgeSlitheriteEvidenceVerifierTest =
        tasks.register<Exec>("forgeSlitheriteEvidenceVerifierTest") {
            group = "verification"
            description =
                "Runs adversarial tests for the Forge Slitherite v19 verifier."
            dependsOn(forgeAttrahiteBlockRegistryEvidenceVerifierTest)
            workingDir(rootProject.projectDir)
            commandLine(
                "python3",
                "-B",
                "-m",
                "unittest",
                "scripts/e2e/test_forge_slitherite_evidence_v19.py",
                "scripts/e2e/test_forge_client.py",
                "scripts/e2e/test_java_installer_supervisor.py",
                "scripts/e2e/test_macos_guarded_java.py",
            )
            inputs.files(
                forgeSlitheriteEvidenceVerifier,
                forgeSlitheriteEvidenceTest,
                forgeSlitheriteEvidenceVerifierV18,
                forgeSlitheriteEvidenceTestV18,
                slitheriteClientEvidenceContract,
                slitheriteClientEvidenceTestSupport,
                originalSlitheriteEvidenceVerifier,
                forgeE2eProfileManifest,
                forgeChannelProfileSnapshotV11,
                forgeForestLanternProfileSnapshotV12,
                forgeForestLanternProfileSnapshotV13,
                forgeAttrahiteProfileSnapshotV14,
                forgeAttrahiteProfileSnapshotV15,
                forgeAttrahiteProfileSnapshotV16,
                forgeAttrahiteProfileSnapshotV17,
                forgeSlitheriteProfileSnapshotV18,
                forgeSlitheriteProfileSnapshotV19,
                rootProject.file("scripts/e2e/forge_client.py"),
                rootProject.file("scripts/e2e/java_installer_supervisor.py"),
                rootProject.file("scripts/e2e/macos_guarded_java.py"),
                forgeSlitheriteRunContractV18,
                forgeSlitheriteRunContractV19,
                rootProject.file("scripts/e2e/test_forge_client.py"),
                rootProject.file("scripts/e2e/test_java_installer_supervisor.py"),
                rootProject.file("scripts/e2e/test_macos_guarded_java.py"),
                rootProject.file("scripts/baseline/macos_memory_guard.py"),
                rootProject.file("scripts/e2e/forge_evidence.py"),
                rootProject.file("release/release-matrix.json"),
                rootProject.file("gradle.properties"),
                rootProject.file("forge/build.gradle.kts"),
                rootProject.file("docs/testing/E2E-CONTRACT.md"),
            )
        }

    validateForgeChannelImplementationMilestone.configure {
        dependsOn(e2eHarnessTestTask, forgeChannelEvidenceVerifierTest)
    }

    validateForgeForestLanternStaticMilestone.configure {
        dependsOn(e2eHarnessTestTask, forgeForestLanternEvidenceVerifierTest)
    }

    validateForgeForestLanternClientEvidenceArchiveIntegrity.configure {
        dependsOn(forgeForestLanternEvidenceVerifierTest)
    }

    validateForgeAttrahiteStaticMilestone.configure {
        dependsOn(e2eHarnessTestTask, forgeAttrahiteBlockRegistryEvidenceVerifierTest)
    }

    validateForgeAttrahiteBlockRegistryClientEvidenceArchiveIntegrity.configure {
        dependsOn(forgeAttrahiteBlockRegistryEvidenceVerifierTest)
    }

    validateForgeSlitheriteStaticMilestone.configure {
        dependsOn(e2eHarnessTestTask, forgeSlitheriteEvidenceVerifierTest)
    }

    validateForgeSlitheriteBlockRegistryClientEvidenceArchiveIntegrity.configure {
        dependsOn(forgeSlitheriteEvidenceVerifierTest)
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

    tasks.register("validateForgeSlitheriteClientArtifactFreeze") {
        group = "verification"
        description =
            "Requires the final remapped Forge Slitherite v19 harness to match its run-contract pin."
        dependsOn(remapE2eHarnessJar)
        inputs.file(forgeSlitheriteRunContractV19)
            .withPropertyName("forgeSlitheriteRunContractV19")
        inputs.file(remapE2eHarnessJar.flatMap { it.archiveFile })
            .withPropertyName("forgeSlitheriteFinalRemappedHarness")
        inputs.property(
            "forgeSlitheriteExpectedProfileId",
            "etherology-e2e-forge-1.20.1-v19",
        )
        inputs.property(
            "forgeSlitheriteExpectedScenarioId",
            "slitherite-block-registry",
        )

        doLast {
            val contractPath = forgeSlitheriteRunContractV19.toPath()
            check(
                Files.isRegularFile(contractPath, LinkOption.NOFOLLOW_LINKS) &&
                    !Files.isSymbolicLink(contractPath),
            ) {
                "Forge Slitherite v19 run-contract owner is missing, linked, or irregular"
            }
            val contractText = Files.readString(contractPath, StandardCharsets.UTF_8)
            fun requireSingleAssignment(name: String): String {
                val assignmentPattern = Regex(
                    "(?m)^${Regex.escape(name)}(?:[ \\t]*:[^=\\r\\n]+)?" +
                        "[ \\t]*=[ \\t]*([^#\\r\\n]+?)[ \\t]*(?:#.*)?$",
                )
                val assignments = assignmentPattern.findAll(contractText).toList()
                check(assignments.size == 1) {
                    "Forge Slitherite v19 run contract must assign $name exactly once"
                }
                return assignments.single().groupValues[1].trim()
            }

            check(
                requireSingleAssignment("PROFILE_ID") ==
                    "\"etherology-e2e-forge-1.20.1-v19\"",
            ) {
                "Forge Slitherite v19 run-contract profile id changed"
            }
            check(
                requireSingleAssignment("SCENARIO_ID") ==
                    "\"slitherite-block-registry\"",
            ) {
                "Forge Slitherite v19 run-contract scenario id changed"
            }
            val harnessSizeLiteral = requireSingleAssignment("HARNESS_SIZE")
            val harnessSha256Literal = requireSingleAssignment("HARNESS_SHA256")
            check(harnessSizeLiteral != "None" || harnessSha256Literal != "None") {
                "Forge Slitherite v19 harness size and SHA-256 remain unpinned"
            }
            check(harnessSizeLiteral != "None" && harnessSha256Literal != "None") {
                "Forge Slitherite v19 harness size and SHA-256 must be pinned together"
            }
            check(Regex("[1-9][0-9]*").matches(harnessSizeLiteral)) {
                "Forge Slitherite v19 HARNESS_SIZE must be one positive decimal integer"
            }
            val expectedHarnessSize = harnessSizeLiteral.toLongOrNull()
            check(expectedHarnessSize != null && expectedHarnessSize > 0L) {
                "Forge Slitherite v19 HARNESS_SIZE exceeds the supported integer range"
            }
            val harnessSha256Match = Regex(
                "(?:\"([0-9a-f]{64})\"|'([0-9a-f]{64})')",
            ).matchEntire(harnessSha256Literal)
            check(harnessSha256Match != null) {
                "Forge Slitherite v19 HARNESS_SHA256 must be 64 lowercase hexadecimal characters"
            }
            val expectedHarnessSha256 = harnessSha256Match.groupValues
                .drop(1)
                .single { it.isNotEmpty() }

            val harnessPath = remapE2eHarnessJar.get().archiveFile.get().asFile.toPath()
            check(
                Files.isRegularFile(harnessPath, LinkOption.NOFOLLOW_LINKS) &&
                    !Files.isSymbolicLink(harnessPath),
            ) {
                "Final remapped Forge Slitherite v19 harness is missing, linked, or irregular"
            }
            val harnessBytes = Files.readAllBytes(harnessPath)
            val actualHarnessSha256 = MessageDigest.getInstance("SHA-256")
                .digest(harnessBytes)
                .joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
            check(
                harnessBytes.size.toLong() == expectedHarnessSize &&
                    actualHarnessSha256 == expectedHarnessSha256,
            ) {
                "Final remapped Forge Slitherite v19 harness differs from its run-contract pin"
            }
        }
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
            check(
                "dev/theplumteam/etherology/e2e/forge/" +
                    "ForgeSlitheriteBlockRegistryScenario.class" in harnessEntries,
            ) {
                "Forge E2E harness JAR has no Forge Slitherite adapter"
            }
            check(
                "dev/theplumteam/etherology/e2e/shared/" +
                    "SlitheriteBlockRegistryScenario.class" in harnessEntries,
            ) {
                "Forge E2E harness JAR has no shared Slitherite scenario"
            }
            check(harnessClassEntries.all {
                it.startsWith("dev/theplumteam/etherology/e2e/forge/")
                    || it.startsWith("dev/theplumteam/etherology/e2e/shared/")
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

    val verifyAttrahiteE2eHarnessArtifact =
        tasks.register("verifyAttrahiteE2eHarnessArtifact") {
            group = "verification"
            description =
                "Binds the accepted Forge Attrahite v17 archive to its captured harness bytes."
            val archiveManifest =
                forgeAttrahiteClientEvidenceArchive.resolve("archive-manifest.json")
            inputs.file(archiveManifest)

            doLast {
                val manifest = JsonSlurper().parse(archiveManifest) as? Map<*, *>
                    ?: error("Forge Attrahite archive manifest is not an object")
                val artifacts = manifest["artifacts"] as? Map<*, *>
                    ?: error("Forge Attrahite archive has no artifact inventory")
                val harness = artifacts["harness"] as? Map<*, *>
                    ?: error("Forge Attrahite archive has no harness identity")
                check((harness["size"] as? Number)?.toLong() == forgeAttrahiteHarnessSize) {
                    "Forge Attrahite archived harness size changed"
                }
                check(harness["sha256"] == forgeAttrahiteHarnessSha256) {
                    "Forge Attrahite archived harness SHA-256 changed"
                }
                check(
                    harness["mod_id"] == "etherology_e2e_harness" &&
                        harness["file_name"] == "etherology-forge-e2e-harness.jar",
                ) {
                    "Forge Attrahite archived harness identity changed"
                }
            }
        }
    forgeAttrahiteBlockRegistryEvidenceVerifierTest.configure {
        dependsOn(verifyAttrahiteE2eHarnessArtifact)
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
                check(packagedDataEntries == acceptedForgeArtifactDataEntries) {
                    "Forge production-under-test JAR packaged an unaccepted server-data set.\n" +
                        "Expected: ${acceptedForgeArtifactDataEntries.sorted()}\n" +
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
        dependsOn(
            e2eHarnessTestTask,
            forgeAttrahiteBlockRegistryEvidenceVerifierTest,
            forgeSlitheriteEvidenceVerifierTest,
            verifyE2eHarnessArtifact,
            verifyE2eUnderTestIsolation,
        )
    }
}
