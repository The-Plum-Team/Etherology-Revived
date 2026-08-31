package ru.feytox.etherology.particle.effects;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleType;
import ru.feytox.etherology.particle.effects.misc.FeyParticleEffect;

public class SimpleParticleEffect extends FeyParticleEffect<SimpleParticleEffect> {

    public SimpleParticleEffect(ParticleType<SimpleParticleEffect> type) {
        super(type);
    }

    @Override
    public Codec<SimpleParticleEffect> createCodec() {
        return Codec.unit(new SimpleParticleEffect(type));
    }

    @Override
    public SimpleParticleEffect read(ParticleType<SimpleParticleEffect> type, StringReader reader) throws CommandSyntaxException {
        return new SimpleParticleEffect(type);
    }

    @Override
    public SimpleParticleEffect read(ParticleType<SimpleParticleEffect> type, PacketByteBuf buf) {
        return new SimpleParticleEffect(type);
    }

    @Override
    public String writeParameters() {
        return "";
    }

    @Override
    public void write(PacketByteBuf buf) {
    }
}
