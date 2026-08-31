package ru.feytox.etherology.block.seal;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SealBlockEntityNbtTest {

    @Test
    void preservesFractionalPointsAcrossNbtRoundTrip() throws ReflectiveOperationException {
        SealBlockEntity seal = allocateWithoutConstructor();
        NbtCompound sourceNbt = new NbtCompound();
        sourceNbt.putFloat("max_points", 256.0f);
        sourceNbt.putInt("radius", 16);
        sourceNbt.putFloat("points", 123.75f);
        sourceNbt.putFloat("pitch", 45.0f);
        sourceNbt.putFloat("yaw", 90.0f);

        seal.readNbt(sourceNbt);
        NbtCompound savedNbt = new NbtCompound();
        seal.writeNbt(savedNbt);

        assertTrue(savedNbt.contains("points", NbtElement.FLOAT_TYPE));
        assertEquals(123.75f, savedNbt.getFloat("points"));
    }

    private static SealBlockEntity allocateWithoutConstructor() throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        return (SealBlockEntity) unsafe.allocateInstance(SealBlockEntity.class);
    }
}
