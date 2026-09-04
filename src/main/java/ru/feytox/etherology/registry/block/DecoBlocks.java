package ru.feytox.etherology.registry.block;

import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;
import net.fabricmc.fabric.api.object.builder.v1.block.type.BlockSetTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.block.type.WoodTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import ru.feytox.etherology.block.beamer.BeamerBlock;
import ru.feytox.etherology.block.forestLantern.ForestLanternBlock;
import ru.feytox.etherology.block.peach.PeachSaplingBlock;
import ru.feytox.etherology.block.peach.WeepingPeachLogBlock;
import ru.feytox.etherology.block.thuja.ThujaBlock;
import ru.feytox.etherology.block.thuja.ThujaPlantBlock;
import ru.feytox.etherology.mixin.BlockEntityTypeMixin;
import ru.feytox.etherology.util.misc.EBlock;
import ru.feytox.etherology.util.misc.EIdentifier;

import java.util.Map;

import static net.minecraft.block.Blocks.*;

@UtilityClass
public class DecoBlocks {
    // various types registries
    private static final BlockSetType PEACH_TYPE = BlockSetTypeBuilder.copyOf(BlockSetType.OAK).register(EIdentifier.of("peach"));
    public static final WoodType PEACH_WOOD_TYPE = WoodTypeBuilder.copyOf(WoodType.OAK).register(EIdentifier.of("peach"), PEACH_TYPE);

    // peach wood
    public static final Block PEACH_LOG = register("peach_log", createLogBlock(MapColor.OAK_TAN, MapColor.SPRUCE_BROWN)).withItem();
    public static final Block STRIPPED_PEACH_LOG = register("stripped_peach_log", createLogBlock(MapColor.OAK_TAN, MapColor.OAK_TAN)).withItem();
    public static final Block PEACH_WOOD = register("peach_wood", new PillarBlock(copy(PEACH_LOG))).withItem();
    public static final Block STRIPPED_PEACH_WOOD = register("stripped_peach_wood", new PillarBlock(copy(STRIPPED_PEACH_LOG))).withItem();
    public static final Block WEEPING_PEACH_LOG = register("weeping_peach_log", new WeepingPeachLogBlock(MapColor.OAK_TAN, MapColor.SPRUCE_BROWN)).withItem(false);
    public static final Block PEACH_STAIRS = registerStairs("peach_stairs", ExtraBlocksRegistry.PEACH_PLANKS).withItem();
    public static final Block PEACH_SLAB = register("peach_slab", new SlabBlock(copy(OAK_SLAB))).withItem();
    public static final Block PEACH_BUTTON = register("peach_button", createWoodenButtonBlock(PEACH_TYPE)).withItem();
    public static final Block PEACH_DOOR = register("peach_door", new DoorBlock(copy(OAK_DOOR), PEACH_TYPE)).withoutItem();
    public static final Block PEACH_FENCE = register("peach_fence", new FenceBlock(copy(OAK_FENCE))).withItem();
    public static final Block PEACH_FENCE_GATE = register("peach_fence_gate", new FenceGateBlock(copy(OAK_FENCE_GATE), PEACH_WOOD_TYPE)).withItem();
    public static final Block PEACH_PRESSURE_PLATE = register("peach_pressure_plate", new PressurePlateBlock(PressurePlateBlock.ActivationRule.EVERYTHING, copy(OAK_PRESSURE_PLATE), PEACH_TYPE)).withItem();
    public static final Block PEACH_SIGN = register("peach_sign", new SignBlock(copy(OAK_SIGN), PEACH_WOOD_TYPE)).withoutItem();
    public static final Block PEACH_WALL_SIGN = register("peach_wall_sign", new WallSignBlock(copy(OAK_WALL_SIGN).dropsLike(PEACH_SIGN), PEACH_WOOD_TYPE)).withoutItem();
    public static final Block PEACH_HANGING_SIGN = register("peach_hanging_sign", new HangingSignBlock(copy(OAK_HANGING_SIGN), PEACH_WOOD_TYPE)).withoutItem();
    public static final Block PEACH_WALL_HANGING_SIGN = register("peach_wall_hanging_sign", new WallHangingSignBlock(copy(OAK_WALL_HANGING_SIGN).dropsLike(PEACH_HANGING_SIGN), PEACH_WOOD_TYPE)).withoutItem();
    public static final Block PEACH_TRAPDOOR = register("peach_trapdoor", new TrapdoorBlock(copy(OAK_TRAPDOOR), PEACH_TYPE)).withItem();
    public static final Block PEACH_LEAVES = register("peach_leaves", createLeavesBlock(BlockSoundGroup.AZALEA_LEAVES)).withItem(false);

    // slitherite
    public static final Block SLITHERITE = SharedSlitheriteBlocks.SLITHERITE.get();
    public static final Block SLITHERITE_STAIRS = SharedSlitheriteBlocks.SLITHERITE_STAIRS.get();
    public static final Block SLITHERITE_SLAB = SharedSlitheriteBlocks.SLITHERITE_SLAB.get();
    public static final Block SLITHERITE_WALL = SharedSlitheriteBlocks.SLITHERITE_WALL.get();

    // polished slitherite
    public static final Block POLISHED_SLITHERITE = SharedSlitheriteBlocks.POLISHED_SLITHERITE.get();
    public static final Block POLISHED_SLITHERITE_STAIRS = SharedSlitheriteBlocks.POLISHED_SLITHERITE_STAIRS.get();
    public static final Block POLISHED_SLITHERITE_SLAB = SharedSlitheriteBlocks.POLISHED_SLITHERITE_SLAB.get();
    public static final Block POLISHED_SLITHERITE_WALL = SharedSlitheriteBlocks.POLISHED_SLITHERITE_WALL.get();
    public static final Block POLISHED_SLITHERITE_BUTTON = SharedSlitheriteBlocks.POLISHED_SLITHERITE_BUTTON.get();
    public static final Block POLISHED_SLITHERITE_PRESSURE_PLATE = SharedSlitheriteBlocks.POLISHED_SLITHERITE_PRESSURE_PLATE.get();

    // polished slitherite bricks
    public static final Block POLISHED_SLITHERITE_BRICKS = SharedSlitheriteBlocks.POLISHED_SLITHERITE_BRICKS.get();
    public static final Block POLISHED_SLITHERITE_BRICK_STAIRS = SharedSlitheriteBlocks.POLISHED_SLITHERITE_BRICK_STAIRS.get();
    public static final Block POLISHED_SLITHERITE_BRICK_SLAB = SharedSlitheriteBlocks.POLISHED_SLITHERITE_BRICK_SLAB.get();
    public static final Block POLISHED_SLITHERITE_BRICK_WALL = SharedSlitheriteBlocks.POLISHED_SLITHERITE_BRICK_WALL.get();

    // single slitherite blocks
    public static final Block CHISELED_POLISHED_SLITHERITE = SharedSlitheriteBlocks.CHISELED_POLISHED_SLITHERITE.get();
    public static final Block CHISELED_POLISHED_SLITHERITE_BRICKS = SharedSlitheriteBlocks.CHISELED_POLISHED_SLITHERITE_BRICKS.get();
    public static final Block CRACKED_POLISHED_SLITHERITE_BRICKS = SharedSlitheriteBlocks.CRACKED_POLISHED_SLITHERITE_BRICKS.get();

    static {
        AutoBlockLootTable.markAsAuto(SLITHERITE, null);
        AutoBlockLootTable.markAsAuto(SLITHERITE_STAIRS, null);
        AutoBlockLootTable.markAsAuto(SLITHERITE_SLAB, null);
        AutoBlockLootTable.markAsAuto(SLITHERITE_WALL, null);
        AutoBlockLootTable.markAsAuto(POLISHED_SLITHERITE, null);
        AutoBlockLootTable.markAsAuto(POLISHED_SLITHERITE_STAIRS, null);
        AutoBlockLootTable.markAsAuto(POLISHED_SLITHERITE_SLAB, null);
        AutoBlockLootTable.markAsAuto(POLISHED_SLITHERITE_WALL, null);
        AutoBlockLootTable.markAsAuto(POLISHED_SLITHERITE_BUTTON, null);
        AutoBlockLootTable.markAsAuto(POLISHED_SLITHERITE_PRESSURE_PLATE, null);
        AutoBlockLootTable.markAsAuto(POLISHED_SLITHERITE_BRICKS, null);
        AutoBlockLootTable.markAsAuto(POLISHED_SLITHERITE_BRICK_STAIRS, null);
        AutoBlockLootTable.markAsAuto(POLISHED_SLITHERITE_BRICK_SLAB, null);
        AutoBlockLootTable.markAsAuto(POLISHED_SLITHERITE_BRICK_WALL, null);
        AutoBlockLootTable.markAsAuto(CHISELED_POLISHED_SLITHERITE, null);
        AutoBlockLootTable.markAsAuto(CHISELED_POLISHED_SLITHERITE_BRICKS, null);
        AutoBlockLootTable.markAsAuto(CRACKED_POLISHED_SLITHERITE_BRICKS, null);
    }

    // plants
    public static final BeamerBlock BEAMER = (BeamerBlock) new BeamerBlock().registerBlock();
    public static final ThujaBlock THUJA = (ThujaBlock) new ThujaBlock().registerBlock();
    public static final ThujaPlantBlock THUJA_PLANT = (ThujaPlantBlock) new ThujaPlantBlock().registerBlock();
    public static final ForestLanternBlock FOREST_LANTERN =
            SharedForestLanternBlocks.FOREST_LANTERN.get();
    public static final Block LIGHTELET = register("lightelet", new FernBlock(AbstractBlock.Settings.copy(GRASS).emissiveLighting((a, b, c) -> true))).withItem(false);

    // saplings
    public static final PeachSaplingBlock PEACH_SAPLING = (PeachSaplingBlock) new PeachSaplingBlock().registerAll();

    // potted blocks
    public static final Block POTTED_BEAMER = register("potted_beamer", createFlowerPotBlock(BEAMER)).withoutItem();
    public static final Block POTTED_THUJA = register("potted_thuja", createFlowerPotBlock(THUJA)).withoutItem();
    public static final Block POTTED_PEACH_SAPLING = register("potted_peach_sapling", createFlowerPotBlock(PEACH_SAPLING)).withoutItem();

    // attrahite
    public static final Block ATTRAHITE = SharedAttrahiteBlocks.ATTRAHITE.get();
    public static final Block ATTRAHITE_BRICKS = SharedAttrahiteBlocks.ATTRAHITE_BRICKS.get();
    public static final Block ATTRAHITE_BRICK_SLAB = SharedAttrahiteBlocks.ATTRAHITE_BRICK_SLAB.get();
    public static final Block ATTRAHITE_BRICK_STAIRS = SharedAttrahiteBlocks.ATTRAHITE_BRICK_STAIRS.get();

    static {
        AutoBlockLootTable.markAsAuto(ATTRAHITE_BRICKS, null);
        AutoBlockLootTable.markAsAuto(ATTRAHITE_BRICK_SLAB, null);
        AutoBlockLootTable.markAsAuto(ATTRAHITE_BRICK_STAIRS, null);
    }

    // signs
    public static final Block[] SIGNS = {PEACH_SIGN, PEACH_WALL_SIGN};
    public static final Block[] HANGING_SIGNS = {PEACH_HANGING_SIGN, PEACH_WALL_HANGING_SIGN};

    public static EBlock register(String id, Block block) {
        Block registredBlock = Registry.register(Registries.BLOCK, EIdentifier.of(id), block);
        return new EBlock(registredBlock);
    }

    private static EBlock registerStairs(String id, Block baseBlock) {
        return register(id, new StairsBlock(baseBlock.getDefaultState(), copy(baseBlock)));
    }

    private static AbstractBlock.Settings copy(AbstractBlock original) {
        return AbstractBlock.Settings.copy(original);
    }

    public static void registerAll() {
        registerSignBlockEntityTypes();
        registerFlammables();
    }

    private static void registerSignBlockEntityTypes() {
        addSupportedBlocks(BlockEntityType.SIGN, SIGNS);
        addSupportedBlocks(BlockEntityType.HANGING_SIGN, HANGING_SIGNS);
    }

    private static void addSupportedBlocks(BlockEntityType<?> blockEntityType, Block... blocks) {
        BlockEntityTypeMixin accessor = (BlockEntityTypeMixin) (Object) blockEntityType;
        accessor.etherology$setBlocks(ImmutableSet.<Block>builder()
                .addAll(accessor.etherology$getBlocks())
                .add(blocks)
                .build());
    }

    public static void registerFlammables() {
        FireBlock fireBlock = (FireBlock) FIRE;
        fireBlock.registerFlammableBlock(PEACH_LOG, 5, 5);
        fireBlock.registerFlammableBlock(STRIPPED_PEACH_LOG, 5, 5);
        fireBlock.registerFlammableBlock(PEACH_WOOD, 5, 5);
        fireBlock.registerFlammableBlock(STRIPPED_PEACH_WOOD, 5, 5);
        fireBlock.registerFlammableBlock(WEEPING_PEACH_LOG, 5, 5);

        fireBlock.registerFlammableBlock(ExtraBlocksRegistry.PEACH_PLANKS, 5, 20);
        fireBlock.registerFlammableBlock(PEACH_STAIRS, 5, 20);
        fireBlock.registerFlammableBlock(PEACH_SLAB, 5, 20);
        fireBlock.registerFlammableBlock(PEACH_BUTTON, 5, 20);
        fireBlock.registerFlammableBlock(PEACH_DOOR, 5, 20);
        fireBlock.registerFlammableBlock(PEACH_FENCE, 5, 20);
        fireBlock.registerFlammableBlock(PEACH_FENCE_GATE, 5, 20);
        fireBlock.registerFlammableBlock(PEACH_LEAVES, 60, 30);
        fireBlock.registerFlammableBlock(LIGHTELET, 60, 100);
    }

    // logs registry
    public static final Map<Block, Block> ETHER_LOGS = Map.of(
            PEACH_LOG, STRIPPED_PEACH_LOG,
            PEACH_WOOD, STRIPPED_PEACH_WOOD,
            WEEPING_PEACH_LOG, STRIPPED_PEACH_LOG
    );
}
