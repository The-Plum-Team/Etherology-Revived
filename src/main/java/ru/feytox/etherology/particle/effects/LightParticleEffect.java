package ru.feytox.etherology.particle.effects;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.math.Vec3d;
import ru.feytox.etherology.particle.effects.misc.FeyParticleEffect;
import ru.feytox.etherology.particle.subtype.LightSubtype;

@Getter
public class LightParticleEffect extends FeyParticleEffect<LightParticleEffect> {

    private final LightSubtype lightType;
    private final Vec3d moveVec;

    public LightParticleEffect(ParticleType<LightParticleEffect> type, LightSubtype lightType, Vec3d moveVec) {
        super(type);
        this.lightType = lightType;
        this.moveVec = moveVec;
    }

    public LightParticleEffect(ParticleType<LightParticleEffect> type) {
        this(type, null, null);
    }

    @Override
    public Codec<LightParticleEffect> createCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                LightSubtype.CODEC.fieldOf("lightType").forGetter(LightParticleEffect::getLightType),
                Vec3d.CODEC.fieldOf("moveVec").forGetter(LightParticleEffect::getMoveVec)
        ).apply(instance, biFactory(LightParticleEffect::new)));
    }

    @Override
    public LightParticleEffect read(ParticleType<LightParticleEffect> type, StringReader reader) throws CommandSyntaxException {
        reader.expect(' ');
        LightSubtype lightType = readEnum(reader, LightSubtype.class);
        reader.expect(' ');
        return new LightParticleEffect(type, lightType, readVec3d(reader));
    }

    @Override
    public LightParticleEffect read(ParticleType<LightParticleEffect> type, PacketByteBuf buf) {
        LightSubtype lightType = buf.readEnumConstant(LightSubtype.class);
        return new LightParticleEffect(type, lightType, readVec3d(buf));
    }

    @Override
    public String writeParameters() {
        return lightType.name() + " " + writeVec3d(moveVec);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(lightType);
        writeVec3d(buf, moveVec);
    }
}
