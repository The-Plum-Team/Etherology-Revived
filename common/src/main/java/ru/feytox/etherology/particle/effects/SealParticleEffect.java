package ru.feytox.etherology.particle.effects;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.math.Vec3d;
import ru.feytox.etherology.magic.seal.SealType;
import ru.feytox.etherology.particle.effects.misc.FeyParticleEffect;

public class SealParticleEffect extends FeyParticleEffect<SealParticleEffect> {

    private final SealType zoneType;
    private final Vec3d endPos;

    public SealParticleEffect(ParticleType<SealParticleEffect> type, SealType zoneType, Vec3d endPos) {
        super(type);
        this.zoneType = zoneType;
        this.endPos = endPos;
    }

    public SealParticleEffect(ParticleType<SealParticleEffect> type) {
        this(type, null, null);
    }

    public SealType getZoneType() {
        return zoneType;
    }

    public Vec3d getEndPos() {
        return endPos;
    }

    @Override
    public Codec<SealParticleEffect> createCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                SealType.CODEC.fieldOf("zoneType").forGetter(SealParticleEffect::getZoneType),
                Vec3d.CODEC.fieldOf("endPos").forGetter(SealParticleEffect::getEndPos)
        ).apply(instance, biFactory(SealParticleEffect::new)));
    }

    @Override
    public SealParticleEffect read(ParticleType<SealParticleEffect> type, StringReader reader) throws CommandSyntaxException {
        reader.expect(' ');
        SealType sealType = readEnum(reader, SealType.class);
        reader.expect(' ');
        return new SealParticleEffect(type, sealType, readVec3d(reader));
    }

    @Override
    public SealParticleEffect read(ParticleType<SealParticleEffect> type, PacketByteBuf buf) {
        SealType sealType = buf.readEnumConstant(SealType.class);
        return new SealParticleEffect(type, sealType, readVec3d(buf));
    }

    @Override
    public String writeParameters() {
        return zoneType.name() + " " + writeVec3d(endPos);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(zoneType);
        writeVec3d(buf, endPos);
    }
}
