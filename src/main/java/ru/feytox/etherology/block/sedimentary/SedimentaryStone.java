package ru.feytox.etherology.block.sedimentary;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.Etherology;
import ru.feytox.etherology.magic.seal.EssenceConsumer;
import ru.feytox.etherology.magic.seal.SealType;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static ru.feytox.etherology.registry.block.EBlocks.SEDIMENTARY_BLOCK_ENTITY;
import static ru.feytox.etherology.registry.block.EBlocks.SEDIMENTARY_STONE;

@Getter
public class SedimentaryStone extends Block implements BlockEntityProvider {

    private static final String BASE_ID = "sedimentary_stone";
    private static final Map<SedimentaryType, SedimentaryStone> TYPE_TO_STONE = new Object2ObjectOpenHashMap<>();
    public static final BooleanProperty POWERED = Properties.POWERED;

    private final SealType sealType;
    private final EssenceLevel essenceLevel;

    public SedimentaryStone(SealType sealType, EssenceLevel essenceLevel) {
        super(Settings.copy(Blocks.STONE));
        this.sealType = sealType;
        this.essenceLevel = essenceLevel;
        TYPE_TO_STONE.put(new SedimentaryType(sealType, essenceLevel), this);

        setDefaultState(getDefaultState().with(POWERED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.isClient || state.get(POWERED) == world.isReceivingRedstonePower(pos))
            return;
        world.setBlockState(pos, state.cycle(POWERED), NOTIFY_LISTENERS);
        EssenceConsumer.activateSearching(world, pos, SedimentaryStoneBlockEntity.class);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(POWERED, ctx.getWorld().isReceivingRedstonePower(ctx.getBlockPos()));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        super.appendTooltip(stack, world, tooltip, options);
        if (sealType.isSeal()) tooltip.add(1, Text.translatable("lore.etherology.primoshard", StringUtils.capitalize(sealType.asString())).formatted(Formatting.DARK_PURPLE));
    }

    @Override
    public String getTranslationKey() {
        if (this.equals(SEDIMENTARY_STONE)) return super.getTranslationKey();
        return SEDIMENTARY_STONE.getTranslationKey();
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (newState.getBlock() instanceof SedimentaryStone) return;
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(hand);
        if (!essenceLevel.isPresent() || !(stack.getItem() instanceof AxeItem))
            return super.onUse(state, world, pos, player, hand, hit);

        var result = false;
        var blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof SedimentaryStoneBlockEntity sedimentaryBlock) {
            var minFullness = EssenceLevel.getFullnessDelta() * 2;
            var dropItem = essenceLevel.toFullness() >= minFullness;
            result = sedimentaryBlock.onUseAxe(state, world, sealType, hit.getSide().getVector(), dropItem);
        }
        return result ? ActionResult.SUCCESS : super.onUse(state, world, pos, player, hand, hit);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        generateDrop(world, pos, player);
        super.onBreak(world, pos, state, player);
    }

    private void generateDrop(World world, BlockPos pos, PlayerEntity player) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof SedimentaryStoneBlockEntity sedimentary)) return;
        if (world.isClient || player.isCreative()) return;

        ItemStack stack = SEDIMENTARY_STONE.asItem().getDefaultStack();

        if (sealType.isSeal() && EnchantmentHelper.getEquipmentLevel(Enchantments.SILK_TOUCH, player) > 0) {
            stack = asItem().getDefaultStack();
            sedimentary.setStackNbt(stack);
        }

        ItemEntity itemEntity = new ItemEntity(world, (double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5, stack);
        itemEntity.setToDefaultPickupDelay();
        world.spawnEntity(itemEntity);
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        ItemStack itemStack = super.getPickStack(world, pos, state);
        world.getBlockEntity(pos, SEDIMENTARY_BLOCK_ENTITY).ifPresent((blockEntity) ->
                blockEntity.setStackNbt(itemStack));
        return itemStack;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SedimentaryStoneBlockEntity(pos, state, essenceLevel);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return SedimentaryStoneBlockEntity.getTicker(world, type, SEDIMENTARY_BLOCK_ENTITY);
    }

    public static String createId(SealType seal, EssenceLevel essenceLevel) {
        String id = BASE_ID;
        if (seal.isSeal()) id = id.concat("_" + seal.asString());
        if (essenceLevel.isPresent()) id = id.concat("_" + essenceLevel.asString());
        return id;
    }

    public static void executeOnStone(BlockState state, Consumer<SedimentaryStone> stoneConsumer) {
        if (!(state.getBlock() instanceof SedimentaryStone stone)) {
            Etherology.ELOGGER.error("Failed to get sedimentary stone from block state");
            return;
        }

        stoneConsumer.accept(stone);
    }

    public static BlockState transformState(BlockState oldState, SealType seal, EssenceLevel level) {
        SedimentaryStone block = TYPE_TO_STONE.get(new SedimentaryType(seal, level));
        if (oldState.getBlock().equals(block)) return oldState;
        if (block == null) {
            Etherology.ELOGGER.error("Failed to get sedimentary stone for seal {} and level {}", seal, level);
            return oldState;
        }

        return block.getDefaultState().with(POWERED, oldState.get(POWERED));
    }

    private record SedimentaryType(SealType seal, EssenceLevel essenceLevel) {}
}
