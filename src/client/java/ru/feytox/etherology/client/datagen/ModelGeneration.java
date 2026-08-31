package ru.feytox.etherology.client.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.block.Block;
import net.minecraft.data.client.*;
import net.minecraft.data.family.BlockFamily;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.Etherology;
import ru.feytox.etherology.client.model.EtherologyModels;
import ru.feytox.etherology.client.model.custom.StaffModel;
import ru.feytox.etherology.magic.staff.StaffPartInfo;
import ru.feytox.etherology.registry.block.BlockFamilyAccess;
import ru.feytox.etherology.registry.block.DecoBlocks;
import ru.feytox.etherology.registry.block.DevBlocks;
import ru.feytox.etherology.registry.block.EBlocks;
import ru.feytox.etherology.registry.item.EItems;
import ru.feytox.etherology.util.misc.EIdentifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static ru.feytox.etherology.registry.block.DecoBlocks.FOREST_LANTERN;
import static ru.feytox.etherology.registry.block.DecoBlocks.STRIPPED_PEACH_LOG;
import static ru.feytox.etherology.registry.block.EBlockFamilies.FAMILIES;
import static ru.feytox.etherology.registry.block.EBlocks.ETHEREAL_CHANNEL;
import static ru.feytox.etherology.registry.block.EBlocks.ETHEREAL_FORK;
import static ru.feytox.etherology.registry.item.ArmorItems.ARMOR_ITEMS;
import static ru.feytox.etherology.registry.item.ArmorItems.PRANA_VISION;
import static ru.feytox.etherology.registry.item.DecoBlockItems.*;
import static ru.feytox.etherology.registry.item.EItems.*;
import static ru.feytox.etherology.registry.item.ToolItems.*;

@SuppressWarnings("SameParameterValue")
public class ModelGeneration extends FabricModelProvider {

    private static final List<String> HAND_AUTHORED_BLOCK_STATE_PATHS = List.of(
            "arcanelight_detector",
            "armillary_sphere",
            "beamer",
            "brewing_cauldron",
            "chiseled_polished_slitherite_bricks",
            "clay_jug",
            "clos_seal",
            "closet_slab",
            "crate",
            "empowerment_table",
            "ethereal_channel",
            "ethereal_fork",
            "ethereal_furnace",
            "ethereal_socket",
            "ethereal_storage",
            "forest_lantern",
            "furniture_slab",
            "inventor_table",
            "jewelry_table",
            "jug",
            "keta_seal",
            "levitator",
            "metronome",
            "pedestal",
            "rella_seal",
            "samovar",
            "shelf_slab",
            "spill_barrel",
            "spinner",
            "thuja",
            "thuja_plant",
            "tuning_fork",
            "via_seal"
    );

    public ModelGeneration(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator generator) {
        // block families
        registerBlockFamilies(generator, FAMILIES);
        // all simple blocks
        registerSimpleBlock(generator, DecoBlocks.AZEL_BLOCK, DecoBlocks.ETHRIL_BLOCK, DecoBlocks.EBONY_BLOCK, EBlocks.CHANNEL_CASE, DecoBlocks.ATTRAHITE);
        registerSimpleBlock(generator, EBlocks.SEDIMENTARY_STONES);
        // peach
        generator.registerSingleton(DecoBlocks.PEACH_LEAVES, TexturedModel.LEAVES);
        generator.registerTintableCross(DecoBlocks.PEACH_SAPLING, BlockStateModelGenerator.TintType.NOT_TINTED);
        generator.registerLog(DecoBlocks.PEACH_LOG).log(DecoBlocks.PEACH_LOG).wood(DecoBlocks.PEACH_WOOD);
        generator.registerLog(DecoBlocks.STRIPPED_PEACH_LOG).log(DecoBlocks.STRIPPED_PEACH_LOG).wood(DecoBlocks.STRIPPED_PEACH_WOOD);
        generator.registerLog(DecoBlocks.WEEPING_PEACH_LOG).log(DecoBlocks.WEEPING_PEACH_LOG);
        // dev blocks
        registerSimpleBlock(generator, DevBlocks.UNLIMITED_ETHER_STORAGE_BLOCK);
        // plants
        registerOnlyPottedPlant(generator, DecoBlocks.BEAMER, DecoBlocks.POTTED_BEAMER, BlockStateModelGenerator.TintType.NOT_TINTED);
        registerOnlyPottedPlant(generator, DecoBlocks.THUJA, DecoBlocks.POTTED_THUJA, BlockStateModelGenerator.TintType.NOT_TINTED);
        registerOnlyPottedPlant(generator, DecoBlocks.PEACH_SAPLING, DecoBlocks.POTTED_PEACH_SAPLING, BlockStateModelGenerator.TintType.NOT_TINTED);
        generator.registerTintableCross(DecoBlocks.LIGHTELET, BlockStateModelGenerator.TintType.TINTED);
        generator.registerHangingSign(STRIPPED_PEACH_LOG, DecoBlocks.PEACH_HANGING_SIGN, DecoBlocks.PEACH_WALL_HANGING_SIGN);

        registerHandAuthoredBlockStates(generator);
    }

    @Override
    public void generateItemModels(ItemModelGenerator generator) {
        // glint
        registerMultipleModels(EItems.GLINT, generator, 1, 17);
        // warp counter
        registerMultipleModels(WARP_COUNTER, generator, 1, 15);
        // simple items
        registerItems(generator, Models.GENERATED, AZEL_INGOT, AZEL_NUGGET, EBONY_INGOT, EBONY_NUGGET, ETHRIL_INGOT, ETHRIL_NUGGET, BEAM_FRUIT, BEAMER_SEEDS, OCULUS, OCULAR, CORRUPTION_BUCKET, THUJA_OIL, THUJA_SEEDS, ETHEROSCOPE, RAW_AZEL, ATTRAHITE_BRICK, ENRICHED_ATTRAHITE, BINDER, ETHEREAL_CHANNEL, ETHEREAL_FORK, PRANA_VISION, EBONY, FOREST_LANTERN, FOREST_LANTERN_CRUMB, PEACH_BOAT, PEACH_CHEST_BOAT);
        registerItems(generator, Models.GENERATED, LENSES);
        registerItems(generator, Models.GENERATED, EBlocks.SEALS);
        // handheld (swords, pickaxe etc.)
        registerItems(generator, Models.HANDHELD, EBONY_AXE, EBONY_PICKAXE, EBONY_HOE, EBONY_SHOVEL, EBONY_SWORD, STREAM_KEY, BROADSWORD);
        registerItems(generator, Models.HANDHELD, BATTLE_PICKAXES);
        registerItems(generator, Models.HANDHELD, RESONATING_WAND);
        // staff parts
        registerStaffParts(generator);
        // pattern tablets
        registerItems(generator, Models.GENERATED, PATTERN_TABLETS);

        // armor
        for (Item etherArmorItem : ARMOR_ITEMS)
            generator.registerArmor((ArmorItem) etherArmorItem);
    }

    private static void registerBlockFamilies(BlockStateModelGenerator generator, List<BlockFamily> blockFamilies) {
        blockFamilies.forEach(family -> {
            if (family instanceof BlockFamilyAccess access && access.etherology$shouldSkipModelGeneration())
                return;
            generator.registerCubeAllModelTexturePool(family.getBaseBlock()).family(family);
        });
    }

    private static void registerItems(ItemModelGenerator generator, Model model, ItemConvertible... items) {
        Arrays.stream(items).map(ItemConvertible::asItem).forEach(item -> generator.register(item, model));
    }

    private static void registerSimpleBlock(BlockStateModelGenerator generator, Block... blocks) {
        Arrays.stream(blocks).forEach(generator::registerSimpleCubeAll);
    }

    private static void registerMultipleModels(Item item, ItemModelGenerator itemModelGenerator, int startInclusive, int endExclusive) {
        for (int i = startInclusive; i < endExclusive; i++) {
            itemModelGenerator.register(item, "_"+i, Models.GENERATED);
        }
    }

    private static void registerStaffParts(ItemModelGenerator generator) {
        StaffPartInfo.generateAll().forEach(partInfo -> {
            TextureMap textures = TextureMap.particle(EIdentifier.of("item/staff_core_oak")).put(EtherologyModels.STYLE, StaffModel.toTextureId(partInfo));

            EtherologyModels.getStaffPartModel(partInfo).upload(StaffModel.toModelId(partInfo).withPrefixedPath("item/"), textures, generator.writer);
        });
    }

    private static void registerOnlyPottedPlant(BlockStateModelGenerator generator, Block plantBlock, Block flowerPotBlock, BlockStateModelGenerator.TintType tintType) {
        TextureMap textureMap = TextureMap.plant(plantBlock);
        Identifier identifier = tintType.getFlowerPotCrossModel().upload(flowerPotBlock, textureMap, generator.modelCollector);
        generator.blockStateCollector.accept(BlockStateModelGenerator.createSingletonBlockState(flowerPotBlock, identifier));
    }

    private static void registerHandAuthoredBlockStates(BlockStateModelGenerator generator) {
        int registeredCount = 0;
        for (Block block : Registries.BLOCK) {
            Identifier id = Registries.BLOCK.getId(block);
            if (!id.getNamespace().equals(Etherology.MOD_ID)) continue;
            if (!HAND_AUTHORED_BLOCK_STATE_PATHS.contains(id.getPath())) continue;

            String resourcePath = "assets/" + Etherology.MOD_ID + "/blockstates/" + id.getPath() + ".json";
            InputStream input = ModelGeneration.class.getClassLoader().getResourceAsStream(resourcePath);
            if (input == null) continue;

            JsonElement blockStateJson;
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                blockStateJson = JsonParser.parseReader(reader);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read hand-authored blockstate " + resourcePath, exception);
            }

            generator.blockStateCollector.accept(new BlockStateSupplier() {
                @Override
                public Block getBlock() {
                    return block;
                }

                @Override
                public JsonElement get() {
                    return blockStateJson;
                }
            });
            if (block.asItem() != Items.AIR)
                generator.excludeFromSimpleItemModelGeneration(block);
            registeredCount++;
        }

        if (registeredCount != HAND_AUTHORED_BLOCK_STATE_PATHS.size()) {
            throw new IllegalStateException("Expected " + HAND_AUTHORED_BLOCK_STATE_PATHS.size()
                    + " hand-authored blockstates, found " + registeredCount);
        }
    }
}
