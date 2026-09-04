package ru.feytox.etherology.registry.block;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns the loader-neutral registrations for Etherology's Slitherite block family.
 */
public final class SharedSlitheriteBlocks {

    private static final SharedDeferredRegister<Block> BLOCKS =
            SharedDeferredRegister.create(RegistryKeys.BLOCK);
    private static final BlockSetType POLISHED_SLITHERITE_TYPE =
            registerPolishedSlitheriteType();

    /** Supplies the full Slitherite block. */
    public static final RegistrySupplier<Block> SLITHERITE = BLOCKS.register(
            "slitherite",
            () -> new Block(AbstractBlock.Settings.copy(Blocks.STONE))
    );

    /** Supplies the Slitherite stairs after their base block becomes available. */
    public static final RegistrySupplier<StairsBlock> SLITHERITE_STAIRS = BLOCKS.register(
            "slitherite_stairs",
            () -> createStairs(SLITHERITE)
    );

    /** Supplies the Slitherite slab with its canonical stone-stairs settings. */
    public static final RegistrySupplier<SlabBlock> SLITHERITE_SLAB = BLOCKS.register(
            "slitherite_slab",
            () -> new SlabBlock(AbstractBlock.Settings.copy(Blocks.STONE_STAIRS))
    );

    /** Supplies the Slitherite wall with its canonical stone-brick-wall settings. */
    public static final RegistrySupplier<WallBlock> SLITHERITE_WALL = BLOCKS.register(
            "slitherite_wall",
            () -> new WallBlock(AbstractBlock.Settings.copy(Blocks.STONE_BRICK_WALL))
    );

    /** Supplies the full polished Slitherite block. */
    public static final RegistrySupplier<Block> POLISHED_SLITHERITE = BLOCKS.register(
            "polished_slitherite",
            () -> new Block(AbstractBlock.Settings.copy(Blocks.SMOOTH_STONE))
    );

    /** Supplies the polished Slitherite stairs after their base block is available. */
    public static final RegistrySupplier<StairsBlock> POLISHED_SLITHERITE_STAIRS =
            BLOCKS.register(
                    "polished_slitherite_stairs",
                    () -> createStairs(POLISHED_SLITHERITE)
            );

    /** Supplies the polished Slitherite slab. */
    public static final RegistrySupplier<SlabBlock> POLISHED_SLITHERITE_SLAB = BLOCKS.register(
            "polished_slitherite_slab",
            () -> new SlabBlock(AbstractBlock.Settings.copy(Blocks.SMOOTH_STONE_SLAB))
    );

    /** Supplies the polished Slitherite wall. */
    public static final RegistrySupplier<WallBlock> POLISHED_SLITHERITE_WALL = BLOCKS.register(
            "polished_slitherite_wall",
            () -> new WallBlock(AbstractBlock.Settings.copy(Blocks.STONE_BRICK_WALL))
    );

    /** Supplies the polished Slitherite stone button. */
    public static final RegistrySupplier<ButtonBlock> POLISHED_SLITHERITE_BUTTON =
            BLOCKS.register(
                    "polished_slitherite_button",
                    () -> new ButtonBlock(
                            AbstractBlock.Settings.create()
                                    .noCollision()
                                    .strength(0.5F)
                                    .pistonBehavior(PistonBehavior.DESTROY),
                            BlockSetType.STONE,
                            20,
                            false
                    )
            );

    /** Supplies the polished Slitherite mob-sensitive pressure plate. */
    public static final RegistrySupplier<PressurePlateBlock>
            POLISHED_SLITHERITE_PRESSURE_PLATE = BLOCKS.register(
                    "polished_slitherite_pressure_plate",
                    () -> new PressurePlateBlock(
                            PressurePlateBlock.ActivationRule.MOBS,
                            AbstractBlock.Settings.copy(Blocks.STONE_PRESSURE_PLATE),
                            POLISHED_SLITHERITE_TYPE
                    )
            );

    /** Supplies the full polished Slitherite brick block. */
    public static final RegistrySupplier<Block> POLISHED_SLITHERITE_BRICKS = BLOCKS.register(
            "polished_slitherite_bricks",
            () -> new Block(AbstractBlock.Settings.copy(Blocks.STONE_BRICKS))
    );

    /** Supplies the polished Slitherite brick stairs after their base block is available. */
    public static final RegistrySupplier<StairsBlock> POLISHED_SLITHERITE_BRICK_STAIRS =
            BLOCKS.register(
                    "polished_slitherite_brick_stairs",
                    () -> createStairs(POLISHED_SLITHERITE_BRICKS)
            );

    /** Supplies the polished Slitherite brick slab with its original brick settings. */
    public static final RegistrySupplier<SlabBlock> POLISHED_SLITHERITE_BRICK_SLAB =
            BLOCKS.register(
                    "polished_slitherite_brick_slab",
                    () -> new SlabBlock(AbstractBlock.Settings.copy(Blocks.STONE_BRICKS))
            );

    /** Supplies the polished Slitherite brick wall. */
    public static final RegistrySupplier<WallBlock> POLISHED_SLITHERITE_BRICK_WALL =
            BLOCKS.register(
                    "polished_slitherite_brick_wall",
                    () -> new WallBlock(
                            AbstractBlock.Settings.copy(Blocks.STONE_BRICK_WALL)
                    )
            );

    /** Supplies the chiseled polished Slitherite block. */
    public static final RegistrySupplier<Block> CHISELED_POLISHED_SLITHERITE = BLOCKS.register(
            "chiseled_polished_slitherite",
            () -> new Block(AbstractBlock.Settings.copy(Blocks.CHISELED_STONE_BRICKS))
    );

    /** Supplies the chiseled polished Slitherite brick block. */
    public static final RegistrySupplier<Block> CHISELED_POLISHED_SLITHERITE_BRICKS =
            BLOCKS.register(
                    "chiseled_polished_slitherite_bricks",
                    () -> new Block(
                            AbstractBlock.Settings.copy(Blocks.CHISELED_STONE_BRICKS)
                    )
            );

    /** Supplies the cracked polished Slitherite brick block. */
    public static final RegistrySupplier<Block> CRACKED_POLISHED_SLITHERITE_BRICKS =
            BLOCKS.register(
                    "cracked_polished_slitherite_bricks",
                    () -> new Block(
                            AbstractBlock.Settings.copy(Blocks.CRACKED_STONE_BRICKS)
                    )
            );

    private SharedSlitheriteBlocks() {
    }

    private static StairsBlock createStairs(
            RegistrySupplier<? extends Block> baseBlockSupplier
    ) {
        Block baseBlock = baseBlockSupplier.get();
        return new StairsBlock(
                baseBlock.getDefaultState(),
                AbstractBlock.Settings.copy(baseBlock)
        );
    }

    private static BlockSetType registerPolishedSlitheriteType() {
        BlockSetType stoneType = BlockSetType.STONE;
        return BlockSetType.register(new BlockSetType(
                Identifier.of(
                        EtherologyBootstrap.MOD_ID,
                        "polished_slitherite"
                ).toString(),
                stoneType.canOpenByHand(),
                stoneType.soundType(),
                stoneType.doorClose(),
                stoneType.doorOpen(),
                stoneType.trapdoorClose(),
                stoneType.trapdoorOpen(),
                stoneType.pressurePlateClickOff(),
                stoneType.pressurePlateClickOn(),
                stoneType.buttonClickOff(),
                stoneType.buttonClickOn()
        ));
    }

    /**
     * Attaches this block registry exactly once during loader construction.
     */
    public static void register() {
        BLOCKS.attach();
    }
}
