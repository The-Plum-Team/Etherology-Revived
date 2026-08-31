package ru.feytox.etherology.block.machine;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import ru.feytox.etherology.block.brewingCauldron.BrewingCauldronBlockEntity;
import ru.feytox.etherology.block.empowerTable.EmpowerTableBlockEntity;
import ru.feytox.etherology.block.etherealFurnace.EtherealFurnaceBlockEntity;
import ru.feytox.etherology.block.etherealStorage.EtherealStorageBlockEntity;
import ru.feytox.etherology.block.levitator.LevitatorBlockEntity;
import ru.feytox.etherology.block.pedestal.PedestalBlockEntity;
import ru.feytox.etherology.item.glints.GlintItem;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MachineBlockEntityStateTest {

    @Test
    void machineNbtMethodsRoundTripEveryPersistentScalar() throws IOException {
        assertNbtPair(BrewingCauldronBlockEntity.class, "temperature", "temperature", "Int");
        assertNbtPair(BrewingCauldronBlockEntity.class, "wasWithAspects", "wasWithAspects", "Boolean");

        assertNbtPair(EtherealFurnaceBlockEntity.class, "storedEther", "stored_ether", "Float");
        assertNbtPair(EtherealFurnaceBlockEntity.class, "fuel", "fuel", "Int");
        assertNbtPair(EtherealFurnaceBlockEntity.class, "cookTime", "cook_time", "Int");
        assertNbtPair(EtherealFurnaceBlockEntity.class, "totalCookTime", "total_cook_time", "Int");

        assertNbtPair(EmpowerTableBlockEntity.class, "hasResult", "has_result", "Boolean");
        assertNbtPair(EmpowerTableBlockEntity.class, "cachedRela", "cached_rela", "Int");
        assertNbtPair(EmpowerTableBlockEntity.class, "cachedVia", "cached_via", "Int");
        assertNbtPair(EmpowerTableBlockEntity.class, "cachedClos", "cached_clos", "Int");
        assertNbtPair(EmpowerTableBlockEntity.class, "cachedKeta", "cached_keta", "Int");

        assertNbtPair(PedestalBlockEntity.class, "removed", "removed", "Boolean");
        assertNbtPair(LevitatorBlockEntity.class, "fuel", "fuel", "Int");
        assertNbtPair(LevitatorBlockEntity.class, "storedEther", "stored_ether", "Float");
        assertNbtPair(EtherealStorageBlockEntity.class, "storageEther", "storage_ether", "Float");
    }

    @Test
    void machineInventoriesAndAspectsUseNbtRoundTrips() throws IOException {
        assertInventoryNbtPair(BrewingCauldronBlockEntity.class);
        assertInventoryNbtPair(EtherealFurnaceBlockEntity.class);
        assertInventoryNbtPair(EmpowerTableBlockEntity.class);
        assertInventoryNbtPair(PedestalBlockEntity.class);
        assertInventoryNbtPair(EtherealStorageBlockEntity.class);
        assertInvokesOwnedMethod(
                BrewingCauldronBlockEntity.class,
                "writeNbt",
                "ru/feytox/etherology/magic/aspects/AspectContainer",
                "writeNbt"
        );
        assertInvokesOwnedMethod(
                BrewingCauldronBlockEntity.class,
                "readNbt",
                "ru/feytox/etherology/magic/aspects/AspectContainer",
                "readNbt"
        );
    }

    @Test
    void furnaceCapacityReadsTheCookInputSlot() throws IOException {
        assertTrue(loadsIntBeforeCall(EtherealFurnaceBlockEntity.class, "isEnoughSpace", 1, "getStack"));
        assertFalse(loadsIntBeforeCall(EtherealFurnaceBlockEntity.class, "isEnoughSpace", 0, "getStack"));
    }

    @Test
    void furnaceOnlyDirtiesChangedEtherState() throws ReflectiveOperationException {
        EtherealFurnaceBlockEntity furnace = allocate(EtherealFurnaceBlockEntity.class);
        setFloatField(furnace, "storedEther", 4.0f);
        setBooleanField(furnace, "isUpdated", false);

        furnace.setStoredEther(5.0f);

        assertTrue(getBooleanField(furnace, "isUpdated"));

        setBooleanField(furnace, "isUpdated", false);
        furnace.setStoredEther(5.0f);

        assertFalse(getBooleanField(furnace, "isUpdated"));
    }

    @Test
    void storageFallsBackAcrossItsBufferAndGlints() throws IOException {
        assertEquals(2, countMethodCalls(EtherealStorageBlockEntity.class, "decrement", "decrementGlint"));
    }

    @Test
    void storageAutomationChecksForGlintItems() throws IOException {
        assertTrue(containsTypeCheck(EtherealStorageBlockEntity.class, "canInsert", GlintItem.class));
    }

    @Test
    void etherMutatorsInvokeTheDirtyHook() throws IOException {
        assertInvokesMethod(EtherealFurnaceBlockEntity.class, "setStoredEther", "markDirty");
        assertInvokesMethod(LevitatorBlockEntity.class, "setStoredEther", "markDirty");
        assertInvokesMethod(EtherealStorageBlockEntity.class, "incrementGlint", "markDirty");
        assertInvokesMethod(EtherealStorageBlockEntity.class, "decrementGlint", "markDirty");
    }

    private static void assertNbtPair(Class<?> type, String fieldName, String nbtKey, String nbtType)
            throws IOException {
        assertTrue(
                hasNbtFieldAccess(type, "writeNbt", fieldName, nbtKey, "put" + nbtType, Opcodes.GETFIELD),
                () -> type.getSimpleName() + ".writeNbt must persist " + fieldName + " as " + nbtKey
        );
        assertTrue(
                hasNbtFieldAccess(type, "readNbt", fieldName, nbtKey, "get" + nbtType, Opcodes.PUTFIELD),
                () -> type.getSimpleName() + ".readNbt must restore " + fieldName + " from " + nbtKey
        );
    }

    private static boolean hasNbtFieldAccess(
            Class<?> type,
            String methodName,
            String fieldName,
            String nbtKey,
            String nbtMethodName,
            int fieldOpcode
    ) throws IOException {
        boolean isWrite = fieldOpcode == Opcodes.GETFIELD;
        AtomicInteger sequence = new AtomicInteger();
        AtomicBoolean found = new AtomicBoolean();

        visitMethod(type, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitLdcInsn(Object value) {
                if (value.equals(nbtKey)) sequence.set(1);
            }

            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface
            ) {
                if (!owner.equals("net/minecraft/nbt/NbtCompound") || !name.equals(nbtMethodName)) return;

                if (isWrite && sequence.get() == 2) found.set(true);
                if (!isWrite && sequence.get() == 1) sequence.set(2);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                if (opcode != fieldOpcode || !name.equals(fieldName)) return;

                if (isWrite && sequence.get() == 1) sequence.set(2);
                if (!isWrite && sequence.get() == 2) found.set(true);
            }
        });

        return found.get();
    }

    private static void assertInventoryNbtPair(Class<?> type) throws IOException {
        assertInvokesOwnedMethod(
                type,
                "writeNbt",
                "net/minecraft/inventory/Inventories",
                "writeNbt"
        );
        assertInvokesOwnedMethod(
                type,
                "readNbt",
                "net/minecraft/inventory/Inventories",
                "readNbt"
        );
    }

    private static void assertInvokesMethod(Class<?> type, String methodName, String invokedMethodName)
            throws IOException {
        assertTrue(
                countMethodCalls(type, methodName, invokedMethodName) > 0,
                () -> type.getSimpleName() + "." + methodName + " must invoke " + invokedMethodName
        );
    }

    private static int countMethodCalls(Class<?> type, String methodName, String invokedMethodName)
            throws IOException {
        AtomicInteger count = new AtomicInteger();
        visitMethod(type, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface
            ) {
                if (name.equals(invokedMethodName)) count.incrementAndGet();
            }
        });
        return count.get();
    }

    private static void assertInvokesOwnedMethod(
            Class<?> type,
            String methodName,
            String invokedOwner,
            String invokedMethodName
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethod(type, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface
            ) {
                if (owner.equals(invokedOwner) && name.equals(invokedMethodName)) found.set(true);
            }
        });

        assertTrue(
                found.get(),
                () -> type.getSimpleName() + "." + methodName + " must invoke " + invokedOwner + "." + invokedMethodName
        );
    }

    private static boolean loadsIntBeforeCall(
            Class<?> type,
            String methodName,
            int expectedValue,
            String invokedMethodName
    ) throws IOException {
        AtomicInteger lastInt = new AtomicInteger(Integer.MIN_VALUE);
        AtomicBoolean found = new AtomicBoolean();
        visitMethod(type, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitInsn(int opcode) {
                if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                    lastInt.set(opcode - Opcodes.ICONST_0);
                }
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) lastInt.set(operand);
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Integer intValue) lastInt.set(intValue);
            }

            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface
            ) {
                if (name.equals(invokedMethodName) && lastInt.get() == expectedValue) found.set(true);
                lastInt.set(Integer.MIN_VALUE);
            }
        });
        return found.get();
    }

    private static boolean containsTypeCheck(Class<?> type, String methodName, Class<?> checkedType)
            throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        String internalName = checkedType.getName().replace('.', '/');
        visitMethod(type, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitTypeInsn(int opcode, String typeName) {
                if (opcode == Opcodes.INSTANCEOF && typeName.equals(internalName)) found.set(true);
            }
        });
        return found.get();
    }

    private static void visitMethod(Class<?> type, String methodName, MethodVisitor methodVisitor)
            throws IOException {
        String resourceName = type.getName().replace('.', '/') + ".class";
        InputStream classStream = MachineBlockEntityStateTest.class.getClassLoader().getResourceAsStream(resourceName);
        assertNotNull(classStream);

        try (classStream) {
            ClassReader reader = new ClassReader(classStream);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    return name.equals(methodName) ? methodVisitor : null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        return type.cast(getUnsafe().allocateInstance(type));
    }

    private static Unsafe getUnsafe() throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }

    private static Field findField(Object target, String name) throws NoSuchFieldException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }

        throw new NoSuchFieldException(name);
    }

    private static boolean getBooleanField(Object target, String name) throws ReflectiveOperationException {
        return findField(target, name).getBoolean(target);
    }

    private static void setBooleanField(Object target, String name, boolean value) throws ReflectiveOperationException {
        findField(target, name).setBoolean(target, value);
    }

    private static void setFloatField(Object target, String name, float value) throws ReflectiveOperationException {
        findField(target, name).setFloat(target, value);
    }
}
