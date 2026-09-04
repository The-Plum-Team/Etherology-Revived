package ru.feytox.etherology.util.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CodecUtilContractTest {

    @Test
    void intSetCodecDeduplicatesInputAndKeepsInsertionOrder() {
        JsonArray encoded = new JsonArray();
        encoded.add(4);
        encoded.add(2);
        encoded.add(4);

        IntArraySet decoded = CodecUtil.INT_SET.parse(JsonOps.INSTANCE, encoded)
                .result()
                .orElseThrow();
        assertArrayEquals(new int[]{4, 2}, decoded.toIntArray());

        JsonElement roundTripped = CodecUtil.INT_SET.encodeStart(JsonOps.INSTANCE, decoded)
                .result()
                .orElseThrow();
        assertEquals("[4,2]", roundTripped.toString());
    }

    @Test
    void explicitUtilityClassContractMatchesLombok() throws NoSuchMethodException {
        assertTrue(java.lang.reflect.Modifier.isFinal(CodecUtil.class.getModifiers()));
        Constructor<CodecUtil> constructor = CodecUtil.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                constructor::newInstance
        );
        UnsupportedOperationException cause = assertInstanceOf(
                UnsupportedOperationException.class,
                exception.getCause()
        );
        assertEquals(
                "This is a utility class and cannot be instantiated",
                cause.getMessage()
        );
    }
}
