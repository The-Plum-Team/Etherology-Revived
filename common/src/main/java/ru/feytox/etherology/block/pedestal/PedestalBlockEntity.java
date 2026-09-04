package ru.feytox.etherology.block.pedestal;

import net.minecraft.block.BlockState;
import net.minecraft.block.DyedCarpetBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.data.aspects.AspectsLoader;
import ru.feytox.etherology.magic.aspects.AspectContainer;
import ru.feytox.etherology.magic.aspects.RevelationAspectProvider;
import ru.feytox.etherology.registry.block.SharedPedestalBlockEntities;
import ru.feytox.etherology.util.inventory.ListBackedInventory;
import ru.feytox.etherology.util.misc.UniqueProvider;

/**
 * Stores the displayed item and optional carpet decoration for one top Pedestal segment.
 */
public class PedestalBlockEntity extends BlockEntity
        implements ListBackedInventory, UniqueProvider, SidedInventory,
        RevelationAspectProvider {

    private static final int DISPLAY_SLOT = 0;
    private static final int CARPET_SLOT = 1;

    private final DefaultedList<ItemStack> items =
            DefaultedList.ofSize(2, ItemStack.EMPTY);

    @Nullable
    private Float cachedUniqueOffset;

    /**
     * Creates a Pedestal block entity at the supplied state.
     *
     * @param pos block position
     * @param state owning Pedestal state
     */
    public PedestalBlockEntity(BlockPos pos, BlockState state) {
        super(SharedPedestalBlockEntities.PEDESTAL.get(), pos, state);
    }

    /** {@inheritDoc} */
    @Override
    public DefaultedList<ItemStack> getItems() {
        return items;
    }

    /**
     * Reports whether the display slot contains an item.
     *
     * @return {@code true} when slot zero is occupied
     */
    public boolean hasItem() {
        return !getStack(DISPLAY_SLOT).isEmpty();
    }

    /**
     * Applies the original ten-branch player interaction contract.
     *
     * @param world authoritative server world
     * @param state current Pedestal state
     * @param player interacting player
     * @param hand interacting hand
     */
    public void interact(
            ServerWorld world,
            BlockState state,
            PlayerEntity player,
            Hand hand
    ) {
        ItemStack handStack = player.getStackInHand(hand);
        ItemStack pedestalStack = getStack(DISPLAY_SLOT);
        ItemStack carpetStack = getStack(CARPET_SLOT);

        if (!handStack.isEmpty()) {
            if (placeCarpet(world, state, player, hand, handStack, carpetStack)) return;

            if (pedestalStack.isEmpty()) {
                setStack(DISPLAY_SLOT, handStack.copyWithCount(1));
                handStack.decrement(1);
                player.setStackInHand(hand, handStack);
                playItemPlaceSound(world, pos);
                return;
            }

            if (ItemStack.canCombine(handStack, pedestalStack)
                    && handStack.getCount() < handStack.getMaxCount()) {
                setStack(DISPLAY_SLOT, ItemStack.EMPTY);
                handStack.increment(1);
                player.setStackInHand(hand, handStack);
                playItemTakeSound(world, pos);
                return;
            }
        }

        if (pedestalStack.isEmpty() && handStack.isEmpty()) {
            if (carpetStack.isEmpty()) return;

            player.setStackInHand(hand, carpetStack);
            setStack(CARPET_SLOT, ItemStack.EMPTY);
            setCarpetColor(world, player, state, DyeColor.WHITE, false);
            return;
        }

        if (handStack.isEmpty()) {
            player.setStackInHand(hand, pedestalStack);
            setStack(DISPLAY_SLOT, ItemStack.EMPTY);
            playItemTakeSound(world, pos);
        }
    }

    private boolean placeCarpet(
            ServerWorld world,
            BlockState state,
            PlayerEntity player,
            Hand hand,
            ItemStack handStack,
            ItemStack carpetStack
    ) {
        if (!(handStack.getItem() instanceof BlockItem blockItem)) return false;
        if (!(blockItem.getBlock() instanceof DyedCarpetBlock carpet)) return false;

        if (ItemStack.canCombine(handStack, carpetStack)
                && handStack.getCount() < handStack.getMaxCount()) {
            setStack(CARPET_SLOT, ItemStack.EMPTY);
            handStack.increment(1);
            player.setStackInHand(hand, handStack);
            setCarpetColor(world, player, state, DyeColor.WHITE, false);
            return true;
        }

        ItemStack copiedStack = handStack.copyWithCount(1);
        if (handStack.getCount() > 1) {
            if (!carpetStack.isEmpty()) return true;

            setStack(CARPET_SLOT, copiedStack);
            handStack.decrement(1);
            player.setStackInHand(hand, handStack);
            setCarpetColor(world, player, state, carpet.getDyeColor(), true);
            return true;
        }

        player.setStackInHand(hand, carpetStack);
        setStack(CARPET_SLOT, copiedStack);
        setCarpetColor(world, player, state, carpet.getDyeColor(), true);
        return true;
    }

    private void setCarpetColor(
            ServerWorld world,
            PlayerEntity player,
            BlockState state,
            DyeColor dyeColor,
            boolean withCarpet
    ) {
        setCarpetColor(
                world,
                player.getHorizontalFacing().getOpposite(),
                state,
                dyeColor,
                withCarpet
        );
    }

    /**
     * Updates the carpet block-state fields and plays the original placement sound.
     *
     * @param world authoritative server world
     * @param direction decoration facing
     * @param state current Pedestal state
     * @param dyeColor carpet color
     * @param withCarpet whether the decoration is present
     */
    public void setCarpetColor(
            ServerWorld world,
            Direction direction,
            BlockState state,
            DyeColor dyeColor,
            boolean withCarpet
    ) {
        world.setBlockState(
                pos,
                state.with(PedestalBlock.CLOTH_COLOR, dyeColor)
                        .with(PedestalBlock.DECORATION, withCarpet)
                        .with(PedestalBlock.FACING, direction)
        );
        playCarpetSound(world);
    }

    private void playCarpetSound(ServerWorld world) {
        world.playSound(
                null,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                SoundEvents.ENTITY_HORSE_SADDLE,
                SoundCategory.BLOCKS,
                0.5f,
                0.9f + 0.2f * world.getRandom().nextFloat()
        );
    }

    /**
     * Plays the original displayed-item removal sound.
     *
     * @param world authoritative server world
     * @param pos sound position
     */
    public static void playItemTakeSound(ServerWorld world, BlockPos pos) {
        world.playSound(
                null,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM,
                SoundCategory.BLOCKS,
                0.5f,
                0.9f + 0.2f * world.getRandom().nextFloat()
        );
    }

    /**
     * Plays the original displayed-item placement sound.
     *
     * @param world authoritative server world
     * @param pos sound position
     */
    public static void playItemPlaceSound(ServerWorld world, BlockPos pos) {
        world.playSound(
                null,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM,
                SoundCategory.BLOCKS,
                0.5f,
                0.9f + 0.2f * world.getRandom().nextFloat()
        );
    }

    /**
     * Marks inventory data dirty and queues a block-entity update packet.
     *
     * @param world authoritative server world
     */
    public void syncData(ServerWorld world) {
        markDirty();
        world.getChunkManager().markForUpdate(pos);
    }

    /** {@inheritDoc} */
    @Override
    protected void writeNbt(NbtCompound nbt) {
        Inventories.writeNbt(nbt, items);
        nbt.putBoolean("removed", removed);
        super.writeNbt(nbt);
    }

    /** {@inheritDoc} */
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        items.clear();
        Inventories.readNbt(nbt, items);
        removed = nbt.getBoolean("removed");
    }

    /** {@inheritDoc} */
    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    /** {@inheritDoc} */
    @Override
    public int getMaxCountPerStack() {
        return 1;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public AspectContainer getRevelationAspects(World world) {
        ItemStack displayedStack = getStack(DISPLAY_SLOT);
        if (displayedStack.isEmpty()) return null;
        return AspectsLoader.getAspects(
                world,
                displayedStack,
                false,
                false
        ).orElse(null);
    }

    /** {@inheritDoc} */
    @Override
    public int[] getAvailableSlots(Direction side) {
        return new int[0];
    }

    /** {@inheritDoc} */
    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void setCachedUniqueOffset(@Nullable Float value) {
        cachedUniqueOffset = value;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public Float getCachedUniqueOffset() {
        return cachedUniqueOffset;
    }
}
