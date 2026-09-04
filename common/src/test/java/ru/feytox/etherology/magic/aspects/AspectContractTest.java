package ru.feytox.etherology.magic.aspects;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AspectContractTest {

    @Test
    void catalogKeepsExactDeclarationOrderCoordinatesAndRuneIds() {
        List<ExpectedAspect> expected = List.of(
                expected(Aspect.RELLA, 0, 0, 0),
                expected(Aspect.ETHA, 0, 1, 4),
                expected(Aspect.DIZORD, 0, 2, 8),
                expected(Aspect.VACUO, 0, 3, 12),
                expected(Aspect.NETHA, 0, 4, 16),
                expected(Aspect.GRAVIA, 0, 5, 20),
                expected(Aspect.MOUNTA, 0, 6, 24),
                expected(Aspect.SOWORDA, 0, 7, 28),
                expected(Aspect.CLOS, 1, 0, 1),
                expected(Aspect.ENN, 1, 1, 5),
                expected(Aspect.ANEMA, 1, 2, 9),
                expected(Aspect.VIBRA, 1, 3, 13),
                expected(Aspect.MATERRA, 1, 4, 17),
                expected(Aspect.SOLISTA, 1, 5, 21),
                expected(Aspect.DEFENTA, 1, 6, 25),
                expected(Aspect.FELKA, 1, 7, 29),
                expected(Aspect.VIA, 2, 0, 2),
                expected(Aspect.FLIMA, 2, 1, 6),
                expected(Aspect.AREA, 2, 2, 10),
                expected(Aspect.CHAOS, 2, 3, 14),
                expected(Aspect.GEMA, 2, 4, 18),
                expected(Aspect.DOGMA, 2, 5, 22),
                expected(Aspect.HENDALL, 2, 6, 26),
                expected(Aspect.STRALFA, 2, 7, 30),
                expected(Aspect.KETA, 3, 0, 3),
                expected(Aspect.MORA, 3, 1, 7),
                expected(Aspect.MEMO, 3, 2, 11),
                expected(Aspect.DEVO, 3, 3, 15),
                expected(Aspect.SECRA, 3, 4, 19),
                expected(Aspect.ISKIL, 3, 5, 23),
                expected(Aspect.ALCHEMA, 3, 6, 27),
                expected(Aspect.GROSEAL, 3, 7, 31),
                expected(Aspect.VITER, 4, 0, 32),
                expected(Aspect.TALO, 4, 1, 33),
                expected(Aspect.AZU, 4, 2, 34),
                expected(Aspect.FRADO, 4, 3, 35),
                expected(Aspect.SOCE, 4, 4, 36),
                expected(Aspect.PLANTA, 4, 5, 37),
                expected(Aspect.LUMOS, 4, 6, 38),
                expected(Aspect.NOX, 4, 7, 39)
        );

        assertEquals(
                expected.stream().map(ExpectedAspect::aspect).toList(),
                Arrays.asList(Aspect.values())
        );
        for (ExpectedAspect value : expected) {
            assertEquals(value.row(), value.aspect().getTextureRow());
            assertEquals(value.column(), value.aspect().getTextureColumn());
            assertEquals(value.runeId(), value.aspect().getRuneId());
            assertEquals(32 + 32 * value.column(), value.aspect().getTextureMinX());
            assertEquals(32 + 32 * value.row(), value.aspect().getTextureMinY());
            assertEquals(32, value.aspect().getWidth());
            assertEquals(32, value.aspect().getHeight());
        }
    }

    @Test
    void namesIdentifiersLookupAndCodecRemainCanonical() {
        assertEquals(
                new Identifier("etherology", "textures/gui/aspects.png"),
                Aspect.TEXTURE
        );
        assertTrue(Aspect.CODEC instanceof StringIdentifiable.Codec<?>);

        for (Aspect aspect : Aspect.values()) {
            String name = aspect.name().toLowerCase();
            assertEquals(name, aspect.getAspectName());
            assertEquals(name, aspect.asString());
            assertEquals(
                    aspect.name().charAt(0) + name.substring(1),
                    aspect.getDisplayName()
            );
            assertEquals(new Identifier("etherology", name), aspect.getId());
            assertSame(aspect, Aspect.get(new Identifier("ignored", name)));
            assertEquals(
                    new JsonPrimitive(name),
                    Aspect.CODEC.encodeStart(JsonOps.INSTANCE, aspect)
                            .result()
                            .orElseThrow()
            );
            assertSame(
                    aspect,
                    Aspect.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(name))
                            .result()
                            .orElseThrow()
            );
        }

        assertNull(Aspect.get(new Identifier("etherology", "unknown")));
        assertTrue(Aspect.CODEC.parse(
                JsonOps.INSTANCE,
                new JsonPrimitive("unknown")
        ).error().isPresent());
    }

    @Test
    void formerLombokGetterAndConstructorSurfaceIsPreserved()
            throws NoSuchMethodException {
        assertEquals(int.class, Aspect.class.getMethod("getTextureRow").getReturnType());
        assertEquals(int.class, Aspect.class.getMethod("getTextureColumn").getReturnType());
        assertEquals(int.class, Aspect.class.getMethod("getRuneId").getReturnType());
        assertTrue(Modifier.isPrivate(Aspect.class.getDeclaredConstructor(
                String.class,
                int.class,
                int.class,
                int.class,
                int.class
        ).getModifiers()));
    }

    private static ExpectedAspect expected(
            Aspect aspect,
            int row,
            int column,
            int runeId
    ) {
        return new ExpectedAspect(aspect, row, column, runeId);
    }

    private record ExpectedAspect(Aspect aspect, int row, int column, int runeId) {
    }
}
