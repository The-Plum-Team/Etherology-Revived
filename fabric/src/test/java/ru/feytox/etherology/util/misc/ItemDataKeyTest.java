package ru.feytox.etherology.util.misc;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtString;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemDataKeyTest {

    private static final String CLASS_RESOURCE = "ru/feytox/etherology/util/misc/ItemDataKey.class";
    private static final String ITEM_DATA_KEY_OWNER = "ru/feytox/etherology/util/misc/ItemDataKey";
    private static final String ITEM_STACK_OWNER = "net/minecraft/item/ItemStack";

    @Test
    void roundTripsAndCopiesEncodedDataWithoutAliasing() {
        ItemDataKey<Integer> key = new ItemDataKey<>("test_value", Codec.INT);
        NbtCompound original = new NbtCompound();
        original.put("test_value", key.encode(12));

        NbtCompound copy = original.copy();
        original.put("test_value", key.encode(99));

        assertEquals(99, key.decode(original.get("test_value")));
        assertEquals(12, key.decode(copy.get("test_value")));
    }

    @Test
    void rejectsMalformedStoredDataInsteadOfReturningAPartialValue() {
        ItemDataKey<Integer> key = new ItemDataKey<>("test_value", Codec.INT);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> key.decode(NbtString.of("not_an_integer"))
        );

        assertTrue(exception.getMessage().contains("test_value"));
    }

    @Test
    void rejectsOutOfRangePartialData() {
        ItemDataKey<Integer> key = new ItemDataKey<>("test_value", Codec.intRange(0, 10));

        assertThrows(IllegalStateException.class, () -> key.decode(NbtInt.of(11)));
    }

    @Test
    void rejectsAMalformedRootInsteadOfTreatingItAsMissing() {
        ItemDataKey<Integer> key = new ItemDataKey<>("test_value", Codec.INT);
        NbtCompound stackNbt = new NbtCompound();
        stackNbt.put("etherology:components", NbtString.of("not_a_compound"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> key.getExistingComponents(stackNbt)
        );

        assertTrue(exception.getMessage().contains("etherology:components"));
    }

    @Test
    void failedEncodingIsVisibleBeforeTheStackIsAccessed() {
        ItemDataKey<Integer> key = new ItemDataKey<>("test_value", Codec.intRange(0, 10));

        assertThrows(IllegalStateException.class, () -> key.set(null, 11));
    }

    @Test
    void compiledStackOperationsValidateTheRootAndEncodeBeforeMutation() throws Exception {
        InputStream classStream = getClass().getClassLoader().getResourceAsStream(CLASS_RESOURCE);
        assertNotNull(classStream);

        AtomicBoolean encodeFound = new AtomicBoolean();
        AtomicBoolean stackAccessFound = new AtomicBoolean();
        AtomicBoolean stackAccessBeforeEncode = new AtomicBoolean();
        AtomicBoolean getValidatesRoot = new AtomicBoolean();
        AtomicBoolean setValidatesRoot = new AtomicBoolean();
        AtomicBoolean removeValidatesRoot = new AtomicBoolean();
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
                    if (!name.equals("get") && !name.equals("set") && !name.equals("remove")) return null;
                    String stackOperation = name;

                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String calledMethod,
                                String descriptor,
                                boolean isInterface
                        ) {
                            if (owner.equals(ITEM_DATA_KEY_OWNER)
                                    && calledMethod.equals("getExistingComponents")) {
                                switch (stackOperation) {
                                    case "get" -> getValidatesRoot.set(true);
                                    case "set" -> setValidatesRoot.set(true);
                                    case "remove" -> removeValidatesRoot.set(true);
                                }
                            }
                            if (!stackOperation.equals("set")) return;
                            if (owner.equals(ITEM_DATA_KEY_OWNER) && calledMethod.equals("encode")) {
                                encodeFound.set(true);
                            }
                            if (!owner.equals(ITEM_STACK_OWNER)) return;

                            stackAccessFound.set(true);
                            if (!encodeFound.get()) stackAccessBeforeEncode.set(true);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertTrue(encodeFound.get());
        assertTrue(stackAccessFound.get());
        assertFalse(stackAccessBeforeEncode.get());
        assertTrue(getValidatesRoot.get());
        assertTrue(setValidatesRoot.get());
        assertTrue(removeValidatesRoot.get());
    }
}
