package ru.feytox.etherology.particle.effects;

import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import ru.feytox.etherology.magic.seal.SealType;
import ru.feytox.etherology.particle.effects.misc.FeyParticleEffect;
import ru.feytox.etherology.particle.effects.misc.FeyParticleType;
import ru.feytox.etherology.particle.subtype.ElectricitySubtype;
import ru.feytox.etherology.particle.subtype.LightSubtype;
import ru.feytox.etherology.particle.subtype.SparkSubtype;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class ParticleEffectContractTest {

    private static final Vec3d VECTOR = new Vec3d(1.25d, -2.5d, 3.75d);

    @Test
    void everyDummyConstructorCreatesANonNullHiddenCodecAndFactory() {
        FeyParticleType<LightParticleEffect> light = type(LightParticleEffect::new);
        FeyParticleType<SimpleParticleEffect> simple = type(SimpleParticleEffect::new);
        FeyParticleType<SparkParticleEffect> spark = type(SparkParticleEffect::new);
        FeyParticleType<ElectricityParticleEffect> electricity = type(
                ElectricityParticleEffect::new
        );
        FeyParticleType<MovingParticleEffect> moving = type(MovingParticleEffect::new);
        FeyParticleType<ScalableParticleEffect> scalable = type(
                ScalableParticleEffect::new
        );
        FeyParticleType<SealParticleEffect> seal = type(SealParticleEffect::new);
        List<FeyParticleType<?>> types = List.of(
                light,
                simple,
                spark,
                electricity,
                moving,
                scalable,
                seal
        );

        for (FeyParticleType<?> particleType : types) {
            assertFalse(particleType.shouldAlwaysSpawn());
            assertNotNull(particleType.getCodec());
            assertNotNull(particleType.getParametersFactory());
        }
    }

    @Test
    void vectorAndEnumPayloadsRetainTheirPublicValuesAndPacketOrder() {
        FeyParticleType<LightParticleEffect> lightType = type(LightParticleEffect::new);
        LightParticleEffect light = new LightParticleEffect(
                lightType,
                LightSubtype.BREWING,
                VECTOR
        );
        assertSame(lightType, light.getType());
        assertSame(LightSubtype.BREWING, light.getLightType());
        assertEquals(VECTOR, light.getMoveVec());
        assertEquals("BREWING 1.25 -2.5 3.75", light.writeParameters());
        assertArrayEquals(enumAndVector(4, VECTOR), bytes(light));
        LightParticleEffect decodedLight = decode(lightType, light);
        assertSame(LightSubtype.BREWING, decodedLight.getLightType());
        assertEquals(VECTOR, decodedLight.getMoveVec());

        FeyParticleType<SparkParticleEffect> sparkType = type(SparkParticleEffect::new);
        SparkParticleEffect spark = new SparkParticleEffect(
                sparkType,
                VECTOR,
                SparkSubtype.CLOS
        );
        assertEquals(VECTOR, spark.getMoveVec());
        assertSame(SparkSubtype.CLOS, spark.getSparkType());
        assertEquals("1.25 -2.5 3.75 CLOS", spark.writeParameters());
        assertArrayEquals(vectorAndEnum(VECTOR, 4), bytes(spark));
        SparkParticleEffect decodedSpark = decode(sparkType, spark);
        assertEquals(VECTOR, decodedSpark.getMoveVec());
        assertSame(SparkSubtype.CLOS, decodedSpark.getSparkType());

        FeyParticleType<SealParticleEffect> sealType = type(SealParticleEffect::new);
        SealParticleEffect seal = new SealParticleEffect(sealType, SealType.VIA, VECTOR);
        assertSame(SealType.VIA, seal.getZoneType());
        assertEquals(VECTOR, seal.getEndPos());
        assertEquals("VIA 1.25 -2.5 3.75", seal.writeParameters());
        assertArrayEquals(enumAndVector(3, VECTOR), bytes(seal));
        SealParticleEffect decodedSeal = decode(sealType, seal);
        assertSame(SealType.VIA, decodedSeal.getZoneType());
        assertEquals(VECTOR, decodedSeal.getEndPos());
    }

    @Test
    void scalarItemSimpleAndElectricityPayloadsRetainTheirWireShape() throws Exception {
        FeyParticleType<MovingParticleEffect> movingType = type(MovingParticleEffect::new);
        MovingParticleEffect moving = new MovingParticleEffect(movingType, VECTOR);
        assertEquals(VECTOR, moving.getMoveVec());
        assertEquals("1.25 -2.5 3.75", moving.writeParameters());
        assertArrayEquals(vectorBytes(VECTOR), bytes(moving));
        assertEquals(VECTOR, decode(movingType, moving).getMoveVec());

        FeyParticleType<ScalableParticleEffect> scalableType = type(
                ScalableParticleEffect::new
        );
        ScalableParticleEffect scalable = new ScalableParticleEffect(
                scalableType,
                Float.valueOf(1.5f)
        );
        assertEquals(Float.valueOf(1.5f), scalable.getScale());
        assertEquals("1.5", scalable.writeParameters());
        assertArrayEquals(ByteBuffer.allocate(4).putFloat(1.5f).array(), bytes(scalable));
        assertEquals(Float.valueOf(1.5f), decode(scalableType, scalable).getScale());

        ParticleType<ItemParticleEffect> itemType = inertItemParticleType();
        ItemParticleEffect item = new ItemParticleEffect(itemType, null, VECTOR);
        assertNull(item.getItem());
        assertEquals(VECTOR, item.getMoveVec());

        FeyParticleType<SimpleParticleEffect> simpleType = type(SimpleParticleEffect::new);
        SimpleParticleEffect simple = new SimpleParticleEffect(simpleType);
        assertEquals("", simple.writeParameters());
        assertArrayEquals(new byte[0], bytes(simple));
        assertSame(simpleType, decode(simpleType, simple).getType());

        FeyParticleType<ElectricityParticleEffect> electricityType = type(
                ElectricityParticleEffect::new
        );
        ElectricityParticleEffect electricity = electricityType
                .getParametersFactory()
                .read(electricityType, new StringReader(" JEWELRY"));
        assertSame(ElectricitySubtype.JEWELRY, electricity.getElectricityType());
        assertEquals("JEWELRY", electricity.writeParameters());
        assertArrayEquals(new byte[]{2}, bytes(electricity));
        assertSame(
                ElectricitySubtype.JEWELRY,
                decode(electricityType, electricity).getElectricityType()
        );
    }

    @Test
    void subtypeNamesAndOrdinalsRemainWireCompatible() {
        assertEquals(
                List.of("SIMPLE", "MATRIX", "JEWELRY"),
                names(ElectricitySubtype.values())
        );
        assertEquals(
                List.of(
                        "SIMPLE",
                        "SPARK",
                        "PUSHING",
                        "ATTRACT",
                        "BREWING",
                        "MATRIX",
                        "GENERATOR",
                        "HAZE"
                ),
                names(LightSubtype.values())
        );
        assertEquals(
                List.of("SIMPLE", "KETA", "RELLA", "VIA", "CLOS", "RISING", "JEWELRY"),
                names(SparkSubtype.values())
        );
        for (ElectricitySubtype subtype : ElectricitySubtype.values()) {
            assertEquals(subtype.name(), subtype.asString());
        }
        for (LightSubtype subtype : LightSubtype.values()) {
            assertEquals(subtype.name(), subtype.asString());
        }
        for (SparkSubtype subtype : SparkSubtype.values()) {
            assertEquals(subtype.name(), subtype.asString());
        }
    }

    private static <T extends ParticleEffect> FeyParticleType<T> type(
            FeyParticleEffect.DummyConstructor<T> constructor
    ) {
        return new FeyParticleType<>(false, constructor);
    }

    private static ParticleType<ItemParticleEffect> inertItemParticleType() {
        return new ParticleType<>(false, null) {
            @Override
            public Codec<ItemParticleEffect> getCodec() {
                return null;
            }

            @Override
            public ParticleEffect.Factory<ItemParticleEffect> getParametersFactory() {
                return null;
            }
        };
    }

    private static <T extends ParticleEffect> T decode(
            FeyParticleType<T> type,
            T effect
    ) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        effect.write(buffer);
        return type.getParametersFactory().read(type, buffer);
    }

    private static byte[] bytes(ParticleEffect effect) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        effect.write(buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(0, bytes);
        return bytes;
    }

    private static byte[] enumAndVector(int ordinal, Vec3d vector) {
        byte[] vectorBytes = vectorBytes(vector);
        byte[] bytes = new byte[vectorBytes.length + 1];
        bytes[0] = (byte) ordinal;
        System.arraycopy(vectorBytes, 0, bytes, 1, vectorBytes.length);
        return bytes;
    }

    private static byte[] vectorAndEnum(Vec3d vector, int ordinal) {
        byte[] vectorBytes = vectorBytes(vector);
        byte[] bytes = new byte[vectorBytes.length + 1];
        System.arraycopy(vectorBytes, 0, bytes, 0, vectorBytes.length);
        bytes[vectorBytes.length] = (byte) ordinal;
        return bytes;
    }

    private static byte[] vectorBytes(Vec3d vector) {
        return ByteBuffer.allocate(24)
                .putDouble(vector.x)
                .putDouble(vector.y)
                .putDouble(vector.z)
                .array();
    }

    private static List<String> names(Enum<?>[] values) {
        return java.util.Arrays.stream(values).map(Enum::name).toList();
    }
}
