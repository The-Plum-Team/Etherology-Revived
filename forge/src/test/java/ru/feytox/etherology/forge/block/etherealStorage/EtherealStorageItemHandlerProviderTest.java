package ru.feytox.etherology.forge.block.etherealStorage;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;
import ru.feytox.etherology.item.EtherealStorageInputItem;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherealStorageItemHandlerProviderTest {

    private static final Identifier CAPABILITY_ID = Identifier.of(
            EtherologyBootstrap.MOD_ID,
            "ethereal_storage_item_handler"
    );

    @Test
    void exposesThreeCachedHandlersInternallyAndOnEveryFace()
            throws ReflectiveOperationException {
        EtherealStorageItemHandlerProvider provider =
                attachedProvider(attachTestInventory(new TestStorageInventory()));
        List<LazyOptional<?>> firstRead = cachedItemHandlers(provider);
        List<LazyOptional<?>> secondRead = cachedItemHandlers(provider);
        Set<LazyOptional<?>> distinctHandlers =
                Collections.newSetFromMap(new IdentityHashMap<>());

        assertEquals(7, firstRead.size());
        for (int index = 0; index < firstRead.size(); index++) {
            LazyOptional<?> itemHandler = firstRead.get(index);
            assertSame(itemHandler, secondRead.get(index));
            assertEquals(3, ((IItemHandler) itemHandler.orElseThrow(
                    AssertionError::new
            )).getSlots());
            distinctHandlers.add(itemHandler);
        }
        assertEquals(7, distinctHandlers.size());
    }

    @Test
    void delegatesDirtRejectionGlintAcceptanceAndSimulationToSidedInventory()
            throws IOException {
        assertFalse(TestStorageInventory.acceptsItemType(0, BlockItem.class));
        assertTrue(TestStorageInventory.acceptsItemType(
                0,
                EtherealStorageInputItem.class
        ));
        assertFalse(TestStorageInventory.acceptsItemType(
                3,
                EtherealStorageInputItem.class
        ));

        MethodNode insertItem = readMethod(
                SidedInvWrapper.class,
                "insertItem",
                "(ILnet/minecraft/item/ItemStack;Z)Lnet/minecraft/item/ItemStack;"
        );
        assertEquals(
                2,
                countInvocations(
                        insertItem,
                        "net/minecraft/inventory/SidedInventory",
                        "canInsert"
                )
        );
        assertEquals(
                2,
                countInvocations(
                        insertItem,
                        "net/minecraft/inventory/SidedInventory",
                        "isValid"
                )
        );

        int guardedMutations = 0;
        for (AbstractInsnNode instruction : insertItem.instructions) {
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.owner.equals(
                            "net/minecraftforge/items/wrapper/SidedInvWrapper"
                    )
                    && invocation.name.equals("setInventorySlotContents")) {
                assertSkippedWhenSimulationIsTrue(insertItem, invocation);
                guardedMutations++;
            }
        }
        assertEquals(4, guardedMutations);

        MethodNode extractItem = readMethod(
                SidedInvWrapper.class,
                "extractItem",
                "(IIZ)Lnet/minecraft/item/ItemStack;"
        );
        assertEquals(
                1,
                countInvocations(
                        extractItem,
                        "net/minecraft/inventory/SidedInventory",
                        "canExtract"
                )
        );
        assertExtractionRequiresSidedPermission(extractItem);
    }

    @Test
    void attachmentOwnsOneProviderAndInvalidatesAllSevenCachedOptionals()
            throws ReflectiveOperationException {
        AttachCapabilitiesEvent<BlockEntity> event = attachTestInventory(
                new TestStorageInventory()
        );

        assertEquals(Set.of(CAPABILITY_ID), event.getCapabilities().keySet());
        assertEquals(1, event.getListeners().size());
        ICapabilityProvider attachedProvider = event.getCapabilities().get(CAPABILITY_ID);
        assertTrue(attachedProvider instanceof EtherealStorageItemHandlerProvider);
        assertFalse(INBTSerializable.class.isAssignableFrom(attachedProvider.getClass()));

        List<LazyOptional<?>> handlers = cachedItemHandlers(
                (EtherealStorageItemHandlerProvider) attachedProvider
        );
        assertEquals(7, handlers.size());

        AtomicInteger invalidations = new AtomicInteger();
        handlers.forEach(handler -> handler.addListener(ignored ->
                invalidations.incrementAndGet()
        ));

        event.getListeners().get(0).run();

        assertEquals(7, invalidations.get());
        handlers.forEach(handler -> assertFalse(handler.isPresent()));
        event.getListeners().get(0).run();
        assertEquals(7, invalidations.get());
    }

    @Test
    void subscriberFiltersForTheStorageFoundationType() throws IOException {
        ClassNode providerClass = readClass(EtherealStorageItemHandlerProvider.class);
        AnnotationNode subscriber = requireAnnotation(
                providerClass.visibleAnnotations,
                "Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;"
        );
        assertEquals(EtherologyBootstrap.MOD_ID, annotationValue(subscriber, "modid"));
        String[] eventBus = (String[]) annotationValue(subscriber, "bus");
        assertEquals("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber$Bus;", eventBus[0]);
        assertEquals("FORGE", eventBus[1]);

        MethodNode attachmentMethod = requireMethod(
                providerClass,
                "attachCapabilities",
                "(Lnet/minecraftforge/event/AttachCapabilitiesEvent;)V"
        );
        requireAnnotation(
                attachmentMethod.visibleAnnotations,
                "Lnet/minecraftforge/eventbus/api/SubscribeEvent;"
        );
        assertEquals(
                1,
                countTypeChecks(
                        attachmentMethod,
                        "ru/feytox/etherology/block/etherealStorage/" +
                                "EtherealStorageFoundationBlockEntity"
                )
        );
        MethodNode capabilityLookup = requireMethod(
                providerClass,
                "getCapability",
                "(Lnet/minecraftforge/common/capabilities/Capability;" +
                        "Lnet/minecraft/util/math/Direction;)" +
                        "Lnet/minecraftforge/common/util/LazyOptional;"
        );
        assertEquals(1, countFieldReads(capabilityLookup, "internalItemHandler"));
        assertEquals(1, countFieldReads(capabilityLookup, "sidedItemHandlers"));
        assertEquals(
                1,
                countFieldAccesses(
                        capabilityLookup,
                        "net/minecraftforge/common/capabilities/ForgeCapabilities",
                        "ITEM_HANDLER",
                        Opcodes.GETSTATIC
                )
        );
        assertEquals(
                1,
                countInvocations(capabilityLookup, "java/util/Map", "get")
        );
        assertEquals(
                1,
                countInvocations(
                        capabilityLookup,
                        "net/minecraftforge/common/capabilities/Capability",
                        "orEmpty"
                )
        );
        assertEquals(
                0,
                countInvocations(
                        capabilityLookup,
                        "net/minecraftforge/common/util/LazyOptional",
                        "of"
                )
        );

        AttachCapabilitiesEvent<BlockEntity> unrelatedEvent = new AttachCapabilitiesEvent<>(
                BlockEntity.class,
                null
        );
        EtherealStorageItemHandlerProvider.attachCapabilities(unrelatedEvent);
        assertTrue(unrelatedEvent.getCapabilities().isEmpty());
        assertTrue(unrelatedEvent.getListeners().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static List<LazyOptional<?>> cachedItemHandlers(
            EtherealStorageItemHandlerProvider provider
    ) throws ReflectiveOperationException {
        Field internalField = EtherealStorageItemHandlerProvider.class.getDeclaredField(
                "internalItemHandler"
        );
        Field sidedField = EtherealStorageItemHandlerProvider.class.getDeclaredField(
                "sidedItemHandlers"
        );
        internalField.setAccessible(true);
        sidedField.setAccessible(true);

        List<LazyOptional<?>> handlers = new ArrayList<>();
        handlers.add((LazyOptional<?>) internalField.get(provider));
        Map<Direction, LazyOptional<?>> sidedHandlers =
                (Map<Direction, LazyOptional<?>>) sidedField.get(provider);
        assertEquals(Set.of(Direction.values()), sidedHandlers.keySet());
        for (Direction direction : Direction.values()) {
            handlers.add(sidedHandlers.get(direction));
        }
        return handlers;
    }

    private static AttachCapabilitiesEvent<BlockEntity> attachTestInventory(
            SidedInventory inventory
    ) throws ReflectiveOperationException {
        AttachCapabilitiesEvent<BlockEntity> event = new AttachCapabilitiesEvent<>(
                BlockEntity.class,
                null
        );
        Method attachment = EtherealStorageItemHandlerProvider.class.getDeclaredMethod(
                "attachItemHandler",
                AttachCapabilitiesEvent.class,
                SidedInventory.class
        );
        attachment.setAccessible(true);
        attachment.invoke(null, event, inventory);
        return event;
    }

    private static EtherealStorageItemHandlerProvider attachedProvider(
            AttachCapabilitiesEvent<BlockEntity> event
    ) {
        return (EtherealStorageItemHandlerProvider) event.getCapabilities().get(CAPABILITY_ID);
    }

    private static MethodNode readMethod(
            Class<?> owner,
            String methodName,
            String descriptor
    ) throws IOException {
        return requireMethod(readClass(owner), methodName, descriptor);
    }

    private static ClassNode readClass(Class<?> owner) throws IOException {
        InputStream classStream = owner.getResourceAsStream(owner.getSimpleName() + ".class");
        assertNotNull(classStream);
        try (classStream) {
            ClassNode classNode = new ClassNode();
            new ClassReader(classStream).accept(
                    classNode,
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
            );
            return classNode;
        }
    }

    private static MethodNode requireMethod(
            ClassNode owner,
            String methodName,
            String descriptor
    ) {
        return owner.methods.stream()
                .filter(method -> method.name.equals(methodName))
                .filter(method -> method.desc.equals(descriptor))
                .findFirst()
                .orElseThrow(AssertionError::new);
    }

    private static AnnotationNode requireAnnotation(
            List<AnnotationNode> annotations,
            String descriptor
    ) {
        assertNotNull(annotations);
        return annotations.stream()
                .filter(annotation -> annotation.desc.equals(descriptor))
                .findFirst()
                .orElseThrow(AssertionError::new);
    }

    private static Object annotationValue(AnnotationNode annotation, String name) {
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (annotation.values.get(index).equals(name)) {
                return annotation.values.get(index + 1);
            }
        }
        throw new AssertionError("Missing annotation value " + name);
    }

    private static int countInvocations(
            MethodNode method,
            String owner,
            String methodName
    ) {
        int invocations = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.owner.equals(owner)
                    && invocation.name.equals(methodName)) {
                invocations++;
            }
        }
        return invocations;
    }

    private static int countFieldReads(MethodNode method, String fieldName) {
        return countFieldAccesses(
                method,
                "ru/feytox/etherology/forge/block/etherealStorage/" +
                        "EtherealStorageItemHandlerProvider",
                fieldName,
                Opcodes.GETFIELD
        );
    }

    private static int countFieldAccesses(
            MethodNode method,
            String owner,
            String fieldName,
            int opcode
    ) {
        int fieldAccesses = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == opcode
                    && field.owner.equals(owner)
                    && field.name.equals(fieldName)) {
                fieldAccesses++;
            }
        }
        return fieldAccesses;
    }

    private static int countTypeChecks(MethodNode method, String type) {
        int typeChecks = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode typeInstruction
                    && typeInstruction.getOpcode() == Opcodes.INSTANCEOF
                    && typeInstruction.desc.equals(type)) {
                typeChecks++;
            }
        }
        return typeChecks;
    }

    private static void assertSkippedWhenSimulationIsTrue(
            MethodNode method,
            MethodInsnNode mutation
    ) {
        int mutationIndex = method.instructions.indexOf(mutation);
        for (AbstractInsnNode instruction = mutation.getPrevious();
             instruction != null;
             instruction = instruction.getPrevious()) {
            if (!(instruction instanceof JumpInsnNode jump)
                    || jump.getOpcode() != Opcodes.IFNE) {
                continue;
            }

            AbstractInsnNode previous = previousInstruction(jump);
            if (previous instanceof VarInsnNode variable
                    && variable.getOpcode() == Opcodes.ILOAD
                    && variable.var == 3) {
                int branchTargetIndex = method.instructions.indexOf(jump.label);
                assertTrue(method.instructions.indexOf(jump) < mutationIndex);
                assertTrue(mutationIndex < branchTargetIndex);
                return;
            }
        }
        throw new AssertionError("Inventory mutation is not guarded by the simulation argument");
    }

    private static void assertExtractionRequiresSidedPermission(MethodNode extractItem) {
        MethodInsnNode permissionCheck = requireInvocation(
                extractItem,
                "net/minecraft/inventory/SidedInventory",
                "canExtract"
        );
        AbstractInsnNode permissionBranch = nextInstruction(permissionCheck);
        assertTrue(permissionBranch instanceof JumpInsnNode);
        JumpInsnNode allowedBranch = (JumpInsnNode) permissionBranch;
        assertEquals(Opcodes.IFNE, allowedBranch.getOpcode());

        AbstractInsnNode deniedResult = nextInstruction(allowedBranch);
        assertTrue(deniedResult instanceof FieldInsnNode);
        FieldInsnNode emptyStack = (FieldInsnNode) deniedResult;
        assertEquals("net/minecraft/item/ItemStack", emptyStack.owner);
        assertEquals("EMPTY", emptyStack.name);
        assertEquals(Opcodes.ARETURN, nextInstruction(emptyStack).getOpcode());

        MethodInsnNode removal = requireInvocation(
                extractItem,
                "net/minecraft/inventory/SidedInventory",
                "removeStack"
        );
        int allowedTargetIndex = extractItem.instructions.indexOf(allowedBranch.label);
        assertTrue(allowedTargetIndex < extractItem.instructions.indexOf(removal));
    }

    private static MethodInsnNode requireInvocation(
            MethodNode method,
            String owner,
            String methodName
    ) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.owner.equals(owner)
                    && invocation.name.equals(methodName)) {
                return invocation;
            }
        }
        throw new AssertionError("Missing invocation " + owner + "." + methodName);
    }

    private static AbstractInsnNode previousInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) {
            previous = previous.getPrevious();
        }
        return previous;
    }

    private static AbstractInsnNode nextInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next;
    }

    private static final class TestStorageInventory implements SidedInventory {

        private static final int INPUT_SLOT_COUNT = 3;
        static boolean acceptsItemType(int slot, Class<? extends Item> itemType) {
            return slot >= 0
                    && slot < INPUT_SLOT_COUNT
                    && EtherealStorageInputItem.class.isAssignableFrom(itemType);
        }

        @Override
        public int size() {
            return 4;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public ItemStack getStack(int slot) {
            throw new AssertionError("Unexpected stack read");
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            throw new AssertionError("Unexpected stack removal");
        }

        @Override
        public ItemStack removeStack(int slot) {
            throw new AssertionError("Unexpected stack removal");
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            throw new AssertionError("Unexpected stack write");
        }

        @Override
        public void markDirty() {
        }

        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return true;
        }

        @Override
        public void clear() {
        }

        @Override
        public boolean isValid(int slot, ItemStack stack) {
            return acceptsItemType(slot, stack.getItem().getClass());
        }

        @Override
        public int[] getAvailableSlots(Direction side) {
            return new int[]{0, 1, 2};
        }

        @Override
        public boolean canInsert(int slot, ItemStack stack, Direction direction) {
            return isValid(slot, stack);
        }

        @Override
        public boolean canExtract(int slot, ItemStack stack, Direction direction) {
            return false;
        }
    }
}
