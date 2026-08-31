package ru.feytox.etherology.particle.effects;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleType;
import ru.feytox.etherology.particle.effects.misc.FeyParticleEffect;

public class ScalableParticleEffect extends FeyParticleEffect<ScalableParticleEffect> {

    private final Float scale;

    public ScalableParticleEffect(ParticleType<ScalableParticleEffect> type, Float scale) {
        super(type);
        this.scale = scale;
    }

    public ScalableParticleEffect(ParticleType<ScalableParticleEffect> type) {
        this(type, null);
    }

    public Float getScale() {
        return scale;
    }

    @Override
    public Codec<ScalableParticleEffect> createCodec() {
        return Codec.FLOAT.xmap(factory(ScalableParticleEffect::new), ScalableParticleEffect::getScale).fieldOf("scale").codec();
    }

    @Override
    public ScalableParticleEffect read(ParticleType<ScalableParticleEffect> type, StringReader reader) throws CommandSyntaxException {
        reader.expect(' ');
        return new ScalableParticleEffect(type, reader.readFloat());
    }

    @Override
    public ScalableParticleEffect read(ParticleType<ScalableParticleEffect> type, PacketByteBuf buf) {
        return new ScalableParticleEffect(type, buf.readFloat());
    }

    @Override
    public String writeParameters() {
        return Float.toString(scale);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeFloat(scale);
    }

}
