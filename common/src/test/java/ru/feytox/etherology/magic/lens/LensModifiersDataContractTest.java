package ru.feytox.etherology.magic.lens;

import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LensModifiersDataContractTest {

    @Test
    void constructorAndGetterKeepTheOriginalMapAlias() {
        Map<Identifier, Integer> modifiers = new LinkedHashMap<>();
        LensModifiersData data = new LensModifiersData(modifiers);

        assertSame(modifiers, data.getModifiers());
        assertTrue(data.isEmpty());

        modifiers.put(LensModifier.AREA.modifierId(), 2);
        assertEquals(2, data.getLevel(LensModifier.AREA));

        data.getModifiers().put(LensModifier.SAVING.modifierId(), -4);
        assertEquals(-4, modifiers.get(LensModifier.SAVING.modifierId()));
    }

    @Test
    void mutableCopiesStateAndOnlyZeroRemovesAModifier() {
        Map<Identifier, Integer> modifiers = new LinkedHashMap<>();
        modifiers.put(LensModifier.STREAM.modifierId(), 3);
        LensModifiersData original = new LensModifiersData(modifiers);
        LensModifiersData.Mutable mutable = original.asMutable();

        assertEquals(original, mutable);
        assertEquals(original.hashCode(), mutable.hashCode());
        assertNotSame(original.getModifiers(), mutable.getModifiers());

        mutable.setLevel(LensModifier.STREAM, 0);
        assertEquals(0, mutable.getLevel(LensModifier.STREAM));
        assertEquals(3, original.getLevel(LensModifier.STREAM));

        mutable.setLevel(LensModifier.CHARGE, -2);
        assertEquals(-2, mutable.getLevel(LensModifier.CHARGE));
        assertTrue(mutable.getModifiers().containsKey(LensModifier.CHARGE.modifierId()));

        mutable.removeModifier(LensModifier.AREA);
        assertEquals(-2, mutable.getLevel(LensModifier.CHARGE));
    }

    @Test
    void nbtAndCodecRoundTripsPreserveEveryAuthoredLevel() {
        Map<Identifier, Integer> modifiers = new LinkedHashMap<>();
        modifiers.put(LensModifier.FILTERING.modifierId(), 5);
        modifiers.put(LensModifier.REINFORCEMENT.modifierId(), 0);
        modifiers.put(LensModifier.SAVING.modifierId(), -3);
        LensModifiersData source = new LensModifiersData(modifiers);

        NbtCompound nbt = source.writeNbt();
        assertEquals(5, nbt.getInt("etherology:filtering"));
        assertEquals(0, nbt.getInt("etherology:reinforcement"));
        assertEquals(-3, nbt.getInt("etherology:saving"));
        assertEquals(source, LensModifiersData.readNbt(nbt));

        LensModifiersData decoded = LensModifiersData.CODEC.parse(
                        JsonOps.INSTANCE,
                        LensModifiersData.CODEC.encodeStart(JsonOps.INSTANCE, source)
                                .result()
                                .orElseThrow()
                )
                .result()
                .orElseThrow();
        assertEquals(source, decoded);
        assertNotSame(source.getModifiers(), decoded.getModifiers());
    }

    @Test
    void nbtNumericFallbackAndLombokNullContractRemainUnchanged() {
        NbtCompound malformedLevel = new NbtCompound();
        malformedLevel.put("etherology:area", NbtString.of("not_a_number"));

        LensModifiersData decoded = LensModifiersData.readNbt(malformedLevel);
        assertEquals(0, decoded.getLevel(LensModifier.AREA));
        assertTrue(decoded.getModifiers().containsKey(LensModifier.AREA.modifierId()));
        assertFalse(decoded.isEmpty());

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new LensModifiersData(null)
        );
        assertEquals("modifiers is marked non-null but is null", exception.getMessage());
    }

    @Test
    void equalityAndHashCodeKeepTheFormerLombokContract() {
        Map<Identifier, Integer> modifiers = new LinkedHashMap<>();
        modifiers.put(LensModifier.CONCENTRATION.modifierId(), 4);
        LensModifiersData immutable = new LensModifiersData(modifiers);
        LensModifiersData.Mutable mutable = new LensModifiersData.Mutable(
                new LinkedHashMap<>(modifiers)
        );

        assertEquals(immutable, mutable);
        assertEquals(mutable, immutable);
        assertEquals(59 + modifiers.hashCode(), immutable.hashCode());
    }

    @Test
    void formerLombokConstructorGetterAndEqualitySurfaceRemainVisible()
            throws NoSuchMethodException {
        assertTrue(java.lang.reflect.Modifier.isPublic(
                LensModifiersData.class.getDeclaredConstructor(Map.class).getModifiers()
        ));
        assertTrue(java.lang.reflect.Modifier.isPublic(
                LensModifiersData.Mutable.class.getDeclaredConstructor(Map.class).getModifiers()
        ));
        assertEquals(
                Map.class,
                LensModifiersData.class.getMethod("getModifiers").getReturnType()
        );
        assertTrue(java.lang.reflect.Modifier.isProtected(
                LensModifiersData.class.getDeclaredMethod("canEqual", Object.class).getModifiers()
        ));
    }
}
