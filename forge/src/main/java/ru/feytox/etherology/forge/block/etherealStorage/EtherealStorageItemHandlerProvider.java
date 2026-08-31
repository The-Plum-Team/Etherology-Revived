package ru.feytox.etherology.forge.block.etherealStorage;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import ru.feytox.etherology.block.etherealStorage.EtherealStorageFoundationBlockEntity;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;

import java.util.EnumMap;
import java.util.Map;

/**
 * Exposes the storage foundation's three real input slots through Forge item handlers.
 */
@Mod.EventBusSubscriber(
        modid = EtherologyBootstrap.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class EtherealStorageItemHandlerProvider implements ICapabilityProvider {

    private static final Identifier CAPABILITY_ID = Identifier.of(
            EtherologyBootstrap.MOD_ID,
            "ethereal_storage_item_handler"
    );

    private final LazyOptional<IItemHandler> internalItemHandler;
    private final Map<Direction, LazyOptional<IItemHandler>> sidedItemHandlers =
            new EnumMap<>(Direction.class);

    private EtherealStorageItemHandlerProvider(SidedInventory inventory) {
        internalItemHandler = createItemHandler(inventory, null);
        for (Direction direction : Direction.values()) {
            sidedItemHandlers.put(direction, createItemHandler(inventory, direction));
        }
    }

    /**
     * Attaches one non-serializing item-handler provider only to storage foundations.
     *
     * @param event block-entity capability attachment event
     */
    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof EtherealStorageFoundationBlockEntity storage) {
            attachItemHandler(event, storage);
        }
    }

    private static void attachItemHandler(
            AttachCapabilitiesEvent<BlockEntity> event,
            SidedInventory inventory
    ) {
        EtherealStorageItemHandlerProvider provider =
                new EtherealStorageItemHandlerProvider(inventory);
        event.addCapability(CAPABILITY_ID, provider);
        event.addListener(provider::invalidateCaps);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        LazyOptional<IItemHandler> itemHandler = side == null
                ? internalItemHandler
                : sidedItemHandlers.get(side);
        return ForgeCapabilities.ITEM_HANDLER.orEmpty(capability, itemHandler);
    }

    private void invalidateCaps() {
        internalItemHandler.invalidate();
        for (Direction direction : Direction.values()) {
            sidedItemHandlers.get(direction).invalidate();
        }
    }

    private static LazyOptional<IItemHandler> createItemHandler(
            SidedInventory inventory,
            Direction side
    ) {
        return LazyOptional.of(() -> new SidedInvWrapper(inventory, side));
    }
}
