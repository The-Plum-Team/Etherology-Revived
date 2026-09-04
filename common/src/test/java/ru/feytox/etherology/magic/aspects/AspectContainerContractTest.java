package ru.feytox.etherology.magic.aspects;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtString;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AspectContainerContractTest {

    @Test
    void constructorsCopyInputPreserveOrderAndKeepIdentityEquality() {
        Map<Aspect, Integer> authored = new LinkedHashMap<>();
        authored.put(Aspect.FRADO, 5);
        authored.put(Aspect.RELLA, 2);
        AspectContainer container = new AspectContainer(authored);

        assertEquals(
                List.of(Aspect.FRADO, Aspect.RELLA),
                new ArrayList<>(container.getAspects().keySet())
        );
        authored.put(Aspect.VIBRA, 3);
        assertFalse(container.getAspects().containsKey(Aspect.VIBRA));
        assertThrows(
                UnsupportedOperationException.class,
                () -> container.getAspects().put(Aspect.NETHA, 4)
        );

        AspectContainer equalState = new AspectContainer(container.getAspects());
        assertNotEquals(container, equalState);
        assertNotSame(container, equalState);
        assertTrue(new AspectContainer().isEmpty());
        assertEquals(Map.of(Aspect.CLOS, 7), AspectContainer.of(Aspect.CLOS, 7)
                .getAspects());
    }

    @Test
    void clearZerosMutatesItsInputBeforeTheClearingConstructorCopiesIt() {
        Map<Aspect, Integer> direct = new LinkedHashMap<>();
        direct.put(Aspect.RELLA, 3);
        direct.put(Aspect.ETHA, 0);
        direct.put(Aspect.DIZORD, -1);
        assertSame(direct, AspectContainer.clearZeros(direct));
        assertEquals(Map.of(Aspect.RELLA, 3), direct);

        Map<Aspect, Integer> constructorInput = new LinkedHashMap<>();
        constructorInput.put(Aspect.VIA, 2);
        constructorInput.put(Aspect.FLIMA, 0);
        constructorInput.put(Aspect.AREA, -2);
        AspectContainer cleared = new AspectContainer(constructorInput, true);

        assertEquals(Map.of(Aspect.VIA, 2), constructorInput);
        assertEquals(Map.of(Aspect.VIA, 2), cleared.getAspects());
        constructorInput.put(Aspect.KETA, 9);
        assertFalse(cleared.getAspects().containsKey(Aspect.KETA));
    }

    @Test
    void arithmeticRetainsZerosAndNegativesWhileMapClearsThem() {
        Map<Aspect, Integer> leftValues = new LinkedHashMap<>();
        leftValues.put(Aspect.RELLA, 4);
        leftValues.put(Aspect.ETHA, 2);
        AspectContainer left = new AspectContainer(leftValues);

        Map<Aspect, Integer> rightValues = new LinkedHashMap<>();
        rightValues.put(Aspect.RELLA, 4);
        rightValues.put(Aspect.ETHA, 5);
        rightValues.put(Aspect.DIZORD, 3);
        AspectContainer right = new AspectContainer(rightValues);

        assertEquals(
                Map.of(Aspect.RELLA, 8, Aspect.ETHA, 7, Aspect.DIZORD, 3),
                left.add(right).getAspects()
        );
        assertEquals(
                Map.of(Aspect.RELLA, 0, Aspect.ETHA, -3, Aspect.DIZORD, 3),
                left.subtract(right).getAspects()
        );
        assertEquals(
                Map.of(Aspect.RELLA, 2),
                left.map(value -> value - 2).getAspects()
        );
        assertEquals(Map.of(Aspect.RELLA, 4, Aspect.ETHA, 2), left.getAspects());

        Object2IntOpenHashMap<Aspect> mutable = left.getMutableAspects();
        mutable.put(Aspect.RELLA, 99);
        assertEquals(4, left.getAspects().get(Aspect.RELLA));
        assertEquals(0, mutable.getInt(Aspect.NETHA));
    }

    @Test
    void extremaSumAndSortKeepTheExactTieAndLimitRules() {
        assertEquals(Optional.empty(), new AspectContainer().max());
        assertEquals(Optional.empty(), new AspectContainer().sum());

        Map<Aspect, Integer> values = new LinkedHashMap<>();
        values.put(Aspect.DIZORD, 2);
        values.put(Aspect.RELLA, 2);
        values.put(Aspect.ETHA, 1);
        AspectContainer container = new AspectContainer(values);
        assertEquals(Optional.of(2), container.max());
        assertEquals(Optional.of(5), container.sum());

        List<Pair<Aspect, Integer>> ascending = List.of(
                Pair.of(Aspect.ETHA, 1),
                Pair.of(Aspect.RELLA, 2),
                Pair.of(Aspect.DIZORD, 2)
        );
        List<Pair<Aspect, Integer>> descending = List.of(
                Pair.of(Aspect.DIZORD, 2),
                Pair.of(Aspect.RELLA, 2),
                Pair.of(Aspect.ETHA, 1)
        );
        assertEquals(ascending, container.sorted(false, -1));
        assertEquals(ascending, container.sorted(false, 20));
        assertEquals(ascending.subList(0, 2), container.sorted(false, 2));
        assertEquals(List.of(), container.sorted(false, 0));
        assertEquals(descending, container.sorted(true, -1));
    }

    @Test
    void nbtUsesTheUppercaseEnumKeysAndReadIgnoresReceiverState() {
        Map<Aspect, Integer> values = new LinkedHashMap<>();
        values.put(Aspect.FRADO, 5);
        values.put(Aspect.HENDALL, -2);
        values.put(Aspect.VIBRA, 0);
        AspectContainer source = new AspectContainer(values);
        NbtCompound nbt = new NbtCompound();
        source.writeNbt(nbt);

        assertEquals("[aspects]", nbt.getKeys().toString());
        NbtCompound encoded = nbt.getCompound("aspects");
        assertEquals(5, encoded.getInt("FRADO"));
        assertEquals(-2, encoded.getInt("HENDALL"));
        assertEquals(0, encoded.getInt("VIBRA"));
        assertFalse(encoded.contains("frado"));

        AspectContainer receiver = AspectContainer.of(Aspect.NOX, 17);
        AspectContainer decoded = receiver.readNbt(nbt);
        assertEquals(source.getAspects(), decoded.getAspects());
        assertEquals(Map.of(Aspect.NOX, 17), receiver.getAspects());
        assertTrue(receiver.readNbt(new NbtCompound()).isEmpty());

        NbtCompound wrongNumericType = new NbtCompound();
        NbtCompound wrongNumericContainer = new NbtCompound();
        wrongNumericContainer.put("RELLA", NbtString.of("not_an_int"));
        wrongNumericType.put("aspects", wrongNumericContainer);
        assertEquals(
                Map.of(Aspect.RELLA, 0),
                receiver.readNbt(wrongNumericType).getAspects()
        );

        NbtCompound unknownAspect = new NbtCompound();
        NbtCompound unknownContainer = new NbtCompound();
        unknownContainer.putInt("UNKNOWN", 1);
        unknownAspect.put("aspects", unknownContainer);
        assertThrows(IllegalArgumentException.class, () -> receiver.readNbt(unknownAspect));
    }

    @Test
    void codecsAndHelpersPreserveAuthoredOrderAndDuplicateMinimum() {
        Map<Aspect, Integer> values = new LinkedHashMap<>();
        values.put(Aspect.FRADO, 5);
        values.put(Aspect.HENDALL, 5);
        values.put(Aspect.VIBRA, 3);
        AspectContainer source = new AspectContainer(values);

        JsonElement encoded = AspectContainer.CODEC.encodeStart(
                JsonOps.INSTANCE,
                source
        ).result().orElseThrow();
        assertJsonOrderAndValues(encoded);
        AspectContainer decoded = AspectContainer.CODEC.parse(
                JsonOps.INSTANCE,
                encoded
        ).result().orElseThrow();
        assertEquals(source.getAspects(), decoded.getAspects());
        assertEquals(
                List.of(Aspect.FRADO, Aspect.HENDALL, Aspect.VIBRA),
                new ArrayList<>(decoded.getAspects().keySet())
        );

        JsonElement mapEncoded = AspectContainer.MAP_CODEC.codec().encodeStart(
                JsonOps.INSTANCE,
                source
        ).result().orElseThrow();
        assertJsonOrderAndValues(mapEncoded);
        assertEquals(
                source.getAspects(),
                AspectContainer.MAP_CODEC.codec()
                        .parse(JsonOps.INSTANCE, mapEncoded)
                        .result()
                        .orElseThrow()
                        .getAspects()
        );

        AspectContainer parsed = AspectContainer.parse(
                JsonOps.INSTANCE,
                Stream.of(
                        Pair.of(new JsonPrimitive("rella"), new JsonPrimitive(7)),
                        Pair.of(new JsonPrimitive("etha"), new JsonPrimitive(5)),
                        Pair.of(new JsonPrimitive("rella"), new JsonPrimitive(3))
                )
        );
        assertEquals(Map.of(Aspect.RELLA, 3, Aspect.ETHA, 5), parsed.getAspects());

        RecordBuilder<JsonElement> builder = JsonOps.INSTANCE.mapBuilder();
        AspectContainer.encodeStart(builder, JsonOps.INSTANCE, source);
        JsonElement helperEncoded = builder.build(JsonOps.INSTANCE.emptyMap())
                .result()
                .orElseThrow();
        assertJsonOrderAndValues(helperEncoded);
    }

    @Test
    void formerLombokConstructorGetterAndNullContractRemainVisible()
            throws ReflectiveOperationException {
        assertTrue(Modifier.isPublic(
                AspectContainer.class.getDeclaredConstructor(Map.class).getModifiers()
        ));
        assertTrue(Modifier.isPublic(
                AspectContainer.class.getDeclaredConstructor(
                        Map.class,
                        boolean.class
                ).getModifiers()
        ));
        assertTrue(Modifier.isPublic(
                AspectContainer.class.getDeclaredConstructor().getModifiers()
        ));
        assertEquals(
                ImmutableMap.class,
                AspectContainer.class.getMethod("getAspects").getReturnType()
        );

        var privateConstructor = AspectContainer.class.getDeclaredConstructor(
                ImmutableMap.class
        );
        assertTrue(Modifier.isPrivate(privateConstructor.getModifiers()));
        privateConstructor.setAccessible(true);
        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> privateConstructor.newInstance((Object) null)
        );
        assertTrue(exception.getCause() instanceof NullPointerException);
        assertEquals(
                "aspects is marked non-null but is null",
                exception.getCause().getMessage()
        );
    }

    private static void assertJsonOrderAndValues(JsonElement encoded) {
        JsonObject object = encoded.getAsJsonObject();
        assertEquals(
                List.of("frado", "hendall", "vibra"),
                new ArrayList<>(object.keySet())
        );
        assertEquals(5, object.get("frado").getAsInt());
        assertEquals(5, object.get("hendall").getAsInt());
        assertEquals(3, object.get("vibra").getAsInt());
    }
}
