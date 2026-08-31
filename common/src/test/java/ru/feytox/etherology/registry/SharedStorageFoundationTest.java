package ru.feytox.etherology.registry;

import org.junit.jupiter.api.Test;

import java.io.IOException;

final class SharedStorageFoundationTest {

    private static final String SHARED_BLOCKS =
            "/ru/feytox/etherology/registry/block/SharedBlocks.class";
    private static final String SHARED_BLOCK_ENTITIES =
            "/ru/feytox/etherology/registry/block/SharedBlockEntities.class";
    private static final String STORAGE_BLOCK =
            "/ru/feytox/etherology/block/etherealStorage/"
                    + "EtherealStorageFoundationBlock.class";
    private static final String STORAGE_BLOCK_ENTITY =
            "/ru/feytox/etherology/block/etherealStorage/"
                    + "EtherealStorageFoundationBlockEntity.class";
    private static final String STORAGE_SCREEN_HANDLER =
            "/ru/feytox/etherology/block/etherealStorage/"
                    + "EtherealStorageFoundationScreenHandler.class";
    private static final String SHARED_SCREEN_HANDLERS =
            "/ru/feytox/etherology/registry/misc/SharedScreenHandlers.class";
    private static final String GLINT_ETHER_DATA =
            "/ru/feytox/etherology/item/glints/GlintEtherData.class";

    @Test
    void declaresTheBlockAndBlockEntityThroughSharedDeferredRegistries()
            throws IOException {
        ClassFileAssertions.assertContains(
                SHARED_BLOCKS,
                "SharedDeferredRegister",
                "RegistrySupplier",
                "ethereal_storage",
                "EtherealStorageFoundationBlock",
                "register"
        );
        ClassFileAssertions.assertContains(
                SHARED_BLOCK_ENTITIES,
                "SharedDeferredRegister",
                "RegistrySupplier",
                "ethereal_storage_block_entity",
                "EtherealStorageFoundationBlockEntity",
                "SharedBlocks",
                "register"
        );
        ClassFileAssertions.assertContains(
                "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class",
                "SharedBlocks",
                "SharedItems",
                "SharedBlockEntities",
                "SharedScreenHandlers"
        );
    }

    @Test
    void implementsThePersistentFourSlotServerMenuCore()
            throws IOException {
        ClassFileAssertions.assertContains(
                STORAGE_BLOCK,
                "BlockEntityProvider",
                "EtherealStorageFoundationBlockEntity",
                "createBlockEntity",
                "onUse",
                "isClient",
                "openHandledScreen",
                "onStateReplaced",
                "ItemScatterer",
                "HorizontalFacingBlock",
                "FACING",
                "NORTH",
                "getPlacementState",
                "getHorizontalPlayerFacing",
                "getOpposite",
                "getTicker",
                "serverTick"
        );
        ClassFileAssertions.assertContains(
                STORAGE_BLOCK_ENTITY,
                "BlockEntity",
                "SharedBlockEntities",
                "storage_ether",
                "writeNbt",
                "readNbt",
                "Inventories",
                "DefaultedList",
                "SidedInventory",
                "NamedScreenHandlerFactory",
                "createMenu",
                "onOpen",
                "onClose",
                "BLOCK_CHEST_OPEN",
                "BLOCK_CHEST_CLOSE",
                "GlintEtherData",
                "getGlintEther",
                "getTransportableEther",
                "incrementGlint",
                "decrementGlint",
                "chargeGlints",
                "drainEther",
                "isInputSide",
                "getOutputSide",
                "UP",
                "DOWN",
                "GeoBlockEntity",
                "storage_controller",
                "animation.ether_storage.open",
                "animation.ether_storage.close",
                "thenPlayAndHold",
                "thenPlay",
                "createInstanceCache",
                "registerControllers",
                "triggerableAnim",
                "triggerAnim"
        );
        ClassFileAssertions.assertContains(
                STORAGE_BLOCK,
                "ENTITYBLOCK_ANIMATED"
        );
        ClassFileAssertions.assertContains(
                STORAGE_SCREEN_HANDLER,
                "ScreenHandler",
                "SimpleInventory",
                "EtherealStorageInputSlot",
                "EtherealStorageDisplaySlot",
                "quickMove",
                "insertItem",
                "onOpen",
                "onClose"
        );
        ClassFileAssertions.assertContains(
                SHARED_SCREEN_HANDLERS,
                "SharedDeferredRegister",
                "RegistrySupplier",
                "ethereal_storage_screen_handler",
                "EtherealStorageFoundationScreenHandler",
                "register"
        );
        ClassFileAssertions.assertContains(
                GLINT_ETHER_DATA,
                "ItemDataKey",
                "stored_ether",
                "getStoredEther",
                "increment",
                "decrement"
        );
    }

    @Test
    void keepsCustomAnimationPacketsOutOfTheSharedGeckoLifecycle() throws IOException {
        ClassFileAssertions.assertDoesNotContain(
                STORAGE_BLOCK_ENTITY,
                "EtherGlint",
                "ForgeCapabilities",
                "LazyOptional",
                "StartBlockAnimS2C",
                "StopBlockAnimS2C",
                "stopClientAnim"
        );
    }

    @Test
    void doesNotReplaceCanonicalFabricStorageClasses() {
        ClassFileAssertions.assertAbsent(
                "/ru/feytox/etherology/registry/block/EBlocks.class"
        );
        ClassFileAssertions.assertAbsent(
                "/ru/feytox/etherology/block/etherealStorage/EtherealStorageBlock.class"
        );
        ClassFileAssertions.assertAbsent(
                "/ru/feytox/etherology/block/etherealStorage/EtherealStorageBlockEntity.class"
        );
        ClassFileAssertions.assertAbsent(
                "/ru/feytox/etherology/block/etherealStorage/EtherealStorageScreenHandler.class"
        );
        ClassFileAssertions.assertAbsent(
                "/ru/feytox/etherology/registry/misc/ScreenHandlersRegistry.class"
        );
    }
}
