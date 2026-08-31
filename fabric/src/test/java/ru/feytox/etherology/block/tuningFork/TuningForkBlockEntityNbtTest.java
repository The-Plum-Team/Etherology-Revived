package ru.feytox.etherology.block.tuningFork;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TuningForkBlockEntityNbtTest {

    @Test
    void preservesPendingResonanceAcrossNbtRoundTrip() throws ReflectiveOperationException {
        TuningForkBlockEntity source = allocateWithoutConstructor();
        setIntField(source, "resonatingTicks", 0);
        setIntField(source, "delay", 4);
        setIntField(source, "receivedNote", 17);

        NbtCompound nbt = new NbtCompound();
        source.writeNbt(nbt);

        assertTrue(nbt.contains("delay", NbtElement.INT_TYPE));
        assertTrue(nbt.contains("received_note", NbtElement.INT_TYPE));

        TuningForkBlockEntity restored = allocateWithoutConstructor();
        restored.readNbt(nbt);

        assertEquals(4, getIntField(restored, "delay"));
        assertEquals(17, getIntField(restored, "receivedNote"));
    }

    @Test
    void oldNbtDefaultsToNoPendingResonance() throws ReflectiveOperationException {
        TuningForkBlockEntity restored = allocateWithoutConstructor();

        restored.readNbt(new NbtCompound());

        assertEquals(-1, getIntField(restored, "delay"));
        assertEquals(-1, getIntField(restored, "receivedNote"));
    }

    private static TuningForkBlockEntity allocateWithoutConstructor() throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        return (TuningForkBlockEntity) unsafe.allocateInstance(TuningForkBlockEntity.class);
    }

    private static void setIntField(Object target, String name, int value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static int getIntField(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
