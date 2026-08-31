package ru.feytox.etherology.magic.lens;

import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

final class LensComponentCodecTest {

    @Test
    void roundTripPreservesStateWithoutAliasingMutableLensData() {
        LensPattern.Mutable pattern = LensPattern.empty().asMutable();
        pattern.markSoft(4);
        pattern.markHard(7);
        LensModifiersData.Mutable modifiers = LensModifiersData.empty().asMutable();
        modifiers.setLevel(LensModifier.SAVING, 3);
        LensComponent source = new LensComponent(19, LensMode.CHARGE, pattern, modifiers, 4231L);

        NbtElement encoded = LensComponent.CODEC.encodeStart(NbtOps.INSTANCE, source)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        LensComponent decoded = LensComponent.CODEC.parse(NbtOps.INSTANCE, encoded)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });

        assertEquals(source, decoded);
        assertNotSame(source.pattern(), decoded.pattern());
        assertNotSame(source.modifiers(), decoded.modifiers());

        pattern.markSoft(9);
        modifiers.setLevel(LensModifier.SAVING, 5);
        assertFalse(decoded.pattern().isSoft(9));
        assertEquals(3, decoded.getLevel(LensModifier.SAVING));
    }
}
