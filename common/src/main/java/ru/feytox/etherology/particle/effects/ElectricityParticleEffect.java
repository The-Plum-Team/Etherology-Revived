package ru.feytox.etherology.particle.effects;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.math.random.Random;
import ru.feytox.etherology.particle.effects.misc.FeyParticleEffect;
import ru.feytox.etherology.particle.effects.misc.FeyParticleType;
import ru.feytox.etherology.particle.subtype.ElectricitySubtype;
import ru.feytox.etherology.registry.particle.SharedParticleTypes;

public class ElectricityParticleEffect extends FeyParticleEffect<ElectricityParticleEffect> {

    private final ElectricitySubtype electricityType;

    private ElectricityParticleEffect(ParticleType<ElectricityParticleEffect> type, ElectricitySubtype electricityType) {
        super(type);
        this.electricityType = electricityType;
    }

    public ElectricityParticleEffect(ParticleType<ElectricityParticleEffect> type) {
        this(type, null);
    }

    public ElectricitySubtype getElectricityType() {
        return electricityType;
    }

    @Override
    public Codec<ElectricityParticleEffect> createCodec() {
        return ElectricitySubtype.CODEC.xmap(factory(ElectricityParticleEffect::new), ElectricityParticleEffect::getElectricityType)
                .fieldOf("electricity_type").codec();
    }

    @Override
    public ElectricityParticleEffect read(ParticleType<ElectricityParticleEffect> type, StringReader reader) throws CommandSyntaxException {
        reader.expect(' ');
        return new ElectricityParticleEffect(type, readEnum(reader, ElectricitySubtype.class));
    }

    @Override
    public ElectricityParticleEffect read(ParticleType<ElectricityParticleEffect> type, PacketByteBuf buf) {
        return new ElectricityParticleEffect(type, buf.readEnumConstant(ElectricitySubtype.class));
    }

    @Override
    public String writeParameters() {
        return electricityType.name();
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(electricityType);
    }

    public static ElectricityParticleEffect of(Random random, ElectricitySubtype electricityType) {
        return new ElectricityParticleEffect(getRandomType(random), electricityType);
    }

    public static FeyParticleType<ElectricityParticleEffect> getRandomType(Random random) {
        return random.nextBoolean()
                ? SharedParticleTypes.ELECTRICITY1.get()
                : SharedParticleTypes.ELECTRICITY2.get();
    }
}
