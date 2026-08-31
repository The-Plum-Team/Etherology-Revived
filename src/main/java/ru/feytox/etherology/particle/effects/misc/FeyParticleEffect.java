package ru.feytox.etherology.particle.effects.misc;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.command.argument.CoordinateArgument;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.apache.commons.lang3.function.TriFunction;

import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class FeyParticleEffect<T extends ParticleEffect> implements ParticleEffect {

    protected final ParticleType<T> type;

    public FeyParticleEffect(ParticleType<T> type) {
        this.type = type;
    }

    public abstract T read(ParticleType<T> type, StringReader reader) throws CommandSyntaxException;

    public abstract T read(ParticleType<T> type, PacketByteBuf buf);

    public abstract String writeParameters();

    public abstract Codec<T> createCodec();

    @Override
    public String asString() {
        Identifier id = Registries.PARTICLE_TYPE.getId(getType());
        String parameters = writeParameters();
        return parameters.isEmpty() ? id.toString() : id + " " + parameters;
    }

    @Override
    public ParticleType<?> getType() {
        return type;
    }

    public ParticleEffect.Factory<T> createFactory() {
        return new ParticleEffect.Factory<>() {

            @Override
            public T read(ParticleType<T> type, StringReader reader) throws CommandSyntaxException {
                return FeyParticleEffect.this.read(type, reader);
            }

            @Override
            public T read(ParticleType<T> type, PacketByteBuf buf) {
                return FeyParticleEffect.this.read(type, buf);
            }
        };
    }

    public <V> Function<V, T> factory(BiFunction<ParticleType<T>, V, T> biFactory) {
        return value -> biFactory.apply(type, value);
    }

    public <V, M> BiFunction<V, M, T> biFactory(TriFunction<ParticleType<T>, V, M, T> triFactory) {
        return (value1, value2) -> triFactory.apply(type, value1, value2);
    }

    protected static Vec3d readVec3d(StringReader reader) throws CommandSyntaxException {
        double x = CoordinateArgument.parse(reader).toAbsoluteCoordinate(0);
        reader.expect(' ');
        double y = CoordinateArgument.parse(reader).toAbsoluteCoordinate(0);
        reader.expect(' ');
        double z = CoordinateArgument.parse(reader).toAbsoluteCoordinate(0);
        return new Vec3d(x, y, z);
    }

    protected static Vec3d readVec3d(PacketByteBuf buf) {
        return new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    protected static <E extends Enum<E>> E readEnum(StringReader reader, Class<E> enumClass) throws CommandSyntaxException {
        String value = reader.readString();
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException ignored) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
        }
    }

    protected static void writeVec3d(PacketByteBuf buf, Vec3d value) {
        buf.writeDouble(value.x);
        buf.writeDouble(value.y);
        buf.writeDouble(value.z);
    }

    protected static String writeVec3d(Vec3d value) {
        return value.x + " " + value.y + " " + value.z;
    }

    public void spawnParticles(World world, int count, double delta, Vec3d centerPos) {
        spawnParticles(this, world, count, delta, centerPos);
    }

    public void spawnParticles(World world, int count, double deltaX, double deltaY, double deltaZ, Vec3d centerPos) {
        spawnParticles(this, world, count, deltaX, deltaY, deltaZ, centerPos);
    }

    public static void spawnParticles(ParticleEffect effect, World world, int count, double delta, Vec3d centerPos) {
        spawnParticles(effect, world, count, delta, delta, delta, centerPos);
    }

    public static void spawnParticles(ParticleEffect effect, World world, int count, double deltaX, double deltaY, double deltaZ, Vec3d centerPos) {
        Random random = world.getRandom();
        for (int i = 0; i < count; i++) {
            Vec3d start = centerPos.add(getRandomPos(random, deltaX, deltaY, deltaZ));

            if (world.isClient) world.addParticle(effect, start.x, start.y, start.z, 0, 0, 0);
            else ((ServerWorld) world).spawnParticles(effect, start.x, start.y, start.z, 1, 0, 0, 0, 0);
        }
    }

    public static Vec3d getRandomPos(Random random, double deltaX, double deltaY, double deltaZ) {
        return new Vec3d(getRandomCoordinate(random, deltaX), getRandomCoordinate(random, deltaY), getRandomCoordinate(random, deltaZ));
    }

    public static double getRandomCoordinate(Random random, double delta) {
        return (2 * random.nextDouble() - 1) * delta;
    }

    @FunctionalInterface
    public interface DummyConstructor<D extends ParticleEffect> {

        FeyParticleEffect<D> createDummy(ParticleType<D> particleType);
    }
}
