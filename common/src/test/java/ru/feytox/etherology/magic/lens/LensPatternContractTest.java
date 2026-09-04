package ru.feytox.etherology.magic.lens;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LensPatternContractTest {

    @Test
    void constructorAliasesCellsWhileMutableConversionCopiesThem() {
        IntArraySet cracks = new IntArraySet();
        IntArraySet softCells = new IntArraySet();
        LensPattern pattern = new LensPattern(cracks, softCells);

        cracks.add(2);
        softCells.add(5);
        assertTrue(pattern.isHard(2));
        assertTrue(pattern.isSoft(5));

        LensPattern.Mutable mutable = pattern.asMutable();
        assertEquals(pattern, mutable);
        assertNotSame(pattern.cracks, mutable.cracks);
        assertNotSame(pattern.softCells, mutable.softCells);

        cracks.add(7);
        softCells.add(8);
        assertFalse(mutable.isHard(7));
        assertFalse(mutable.isSoft(8));
    }

    @Test
    void cellMutationKeepsExclusionAndHardTexturePrecedence() {
        LensPattern.Mutable pattern = LensPattern.empty().asMutable();
        assertFalse(pattern.isCracked());
        assertEquals(0, pattern.getTextureOffset(9));

        assertTrue(pattern.markSoft(9));
        assertFalse(pattern.markSoft(9));
        assertFalse(pattern.markHard(9));
        assertEquals(2, pattern.getTextureOffset(9));

        pattern.unSoft(9);
        assertTrue(pattern.markHard(9));
        assertFalse(pattern.markSoft(9));
        assertEquals(1, pattern.getTextureOffset(9));

        pattern.softCells.add(9);
        assertTrue(pattern.isHard(9));
        assertTrue(pattern.isSoft(9));
        assertEquals(1, pattern.getTextureOffset(9));
    }

    @Test
    void nbtAndCodecRoundTripsPreserveBothCellSets() {
        IntArraySet cracks = new IntArraySet();
        cracks.add(1);
        cracks.add(6);
        IntArraySet softCells = new IntArraySet();
        softCells.add(3);
        LensPattern source = new LensPattern(cracks, softCells);

        NbtCompound nbt = source.writeNbt();
        assertArrayEquals(new int[]{1, 6}, nbt.getIntArray("cracks"));
        assertArrayEquals(new int[]{3}, nbt.getIntArray("soft_cells"));
        LensPattern fromNbt = LensPattern.readNbt(nbt);
        assertEquals(source, fromNbt);
        assertNotNull(fromNbt);
        assertNotSame(source.cracks, fromNbt.cracks);
        assertNotSame(source.softCells, fromNbt.softCells);

        JsonElement encoded = LensPattern.CODEC.encodeStart(JsonOps.INSTANCE, source)
                .result()
                .orElseThrow();
        assertEquals(2, encoded.getAsJsonObject().size());
        assertTrue(encoded.getAsJsonObject().has("cracks"));
        assertTrue(encoded.getAsJsonObject().has("soft_cells"));

        LensPattern fromCodec = LensPattern.CODEC.parse(JsonOps.INSTANCE, encoded)
                .result()
                .orElseThrow();
        assertEquals(source, fromCodec);
        assertNotSame(source.cracks, fromCodec.cracks);
        assertNotSame(source.softCells, fromCodec.softCells);
    }

    @Test
    void malformedNbtRetainsTheNullableReadContract() {
        assertNull(LensPattern.readNbt(new NbtCompound()));

        NbtCompound wrongCracksType = LensPattern.empty().writeNbt();
        wrongCracksType.put("cracks", NbtInt.of(3));
        assertNull(LensPattern.readNbt(wrongCracksType));

        NullPointerException cracksException = assertThrows(
                NullPointerException.class,
                () -> new LensPattern(null, new IntArraySet())
        );
        assertEquals("cracks is marked non-null but is null", cracksException.getMessage());

        NullPointerException softCellsException = assertThrows(
                NullPointerException.class,
                () -> new LensPattern(new IntArraySet(), null)
        );
        assertEquals("softCells is marked non-null but is null", softCellsException.getMessage());
    }

    @Test
    void equalityAndHashCodeKeepTheFormerLombokContract() {
        IntArraySet cracks = new IntArraySet();
        cracks.add(3);
        IntArraySet softCells = new IntArraySet();
        softCells.add(11);
        LensPattern immutable = new LensPattern(cracks, softCells);
        LensPattern.Mutable mutable = new LensPattern.Mutable(cracks.clone(), softCells.clone());

        assertEquals(immutable, mutable);
        assertEquals(mutable, immutable);
        int expectedHash = (59 + cracks.hashCode()) * 59 + softCells.hashCode();
        assertEquals(expectedHash, immutable.hashCode());
    }

    @Test
    void formerLombokConstructorsAndEqualityHookRemainVisible()
            throws NoSuchMethodException {
        assertTrue(java.lang.reflect.Modifier.isPublic(
                LensPattern.class.getDeclaredConstructor(
                        IntArraySet.class,
                        IntArraySet.class
                ).getModifiers()
        ));
        assertTrue(java.lang.reflect.Modifier.isPublic(
                LensPattern.Mutable.class.getDeclaredConstructor(
                        IntArraySet.class,
                        IntArraySet.class
                ).getModifiers()
        ));
        assertTrue(java.lang.reflect.Modifier.isProtected(
                LensPattern.class.getDeclaredMethod("canEqual", Object.class).getModifiers()
        ));
    }
}
