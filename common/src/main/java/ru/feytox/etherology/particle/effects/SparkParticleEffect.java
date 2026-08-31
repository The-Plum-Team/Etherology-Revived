package ru.feytox.etherology.particle.effects;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.math.Vec3d;
import ru.feytox.etherology.particle.effects.misc.FeyParticleEffect;
import ru.feytox.etherology.particle.subtype.SparkSubtype;

public class SparkParticleEffect extends FeyParticleEffect<SparkParticleEffect> {

    private final Vec3d moveVec;
    private final SparkSubtype sparkType;

    public SparkParticleEffect(ParticleType<SparkParticleEffect> type, Vec3d moveVec, SparkSubtype sparkType) {
        super(type);
        this.moveVec = moveVec;
        this.sparkType = sparkType;
    }

    public SparkParticleEffect(ParticleType<SparkParticleEffect> type) {
        this(type, null, null);
    }

    public Vec3d getMoveVec() {
        return moveVec;
    }

    public SparkSubtype getSparkType() {
        return sparkType;
    }

    @Override
    public Codec<SparkParticleEffect> createCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                Vec3d.CODEC.fieldOf("moveVec").forGetter(SparkParticleEffect::getMoveVec),
                SparkSubtype.CODEC.fieldOf("sparkType").forGetter(SparkParticleEffect::getSparkType)
        ).apply(instance, biFactory(SparkParticleEffect::new)));
    }

    @Override
    public SparkParticleEffect read(ParticleType<SparkParticleEffect> type, StringReader reader) throws CommandSyntaxException {
        reader.expect(' ');
        Vec3d moveVec = readVec3d(reader);
        reader.expect(' ');
        return new SparkParticleEffect(type, moveVec, readEnum(reader, SparkSubtype.class));
    }

    @Override
    public SparkParticleEffect read(ParticleType<SparkParticleEffect> type, PacketByteBuf buf) {
        Vec3d moveVec = readVec3d(buf);
        return new SparkParticleEffect(type, moveVec, buf.readEnumConstant(SparkSubtype.class));
    }

    @Override
    public String writeParameters() {
        return writeVec3d(moveVec) + " " + sparkType.name();
    }

    @Override
    public void write(PacketByteBuf buf) {
        writeVec3d(buf, moveVec);
        buf.writeEnumConstant(sparkType);
    }
}
