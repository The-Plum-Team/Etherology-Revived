package ru.feytox.etherology.particle.effects;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import lombok.Getter;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.math.Vec3d;
import ru.feytox.etherology.particle.effects.misc.FeyParticleEffect;

public class MovingParticleEffect extends FeyParticleEffect<MovingParticleEffect> {

    @Getter
    private final Vec3d moveVec;

    public MovingParticleEffect(ParticleType<MovingParticleEffect> type, Vec3d moveVec) {
        super(type);
        this.moveVec = moveVec;
    }

    public MovingParticleEffect(ParticleType<MovingParticleEffect> type) {
        this(type, null);
    }

    @Override
    public Codec<MovingParticleEffect> createCodec() {
        return Vec3d.CODEC.xmap(factory(MovingParticleEffect::new), MovingParticleEffect::getMoveVec).fieldOf("moveVec").codec();
    }

    @Override
    public MovingParticleEffect read(ParticleType<MovingParticleEffect> type, StringReader reader) throws CommandSyntaxException {
        reader.expect(' ');
        return new MovingParticleEffect(type, readVec3d(reader));
    }

    @Override
    public MovingParticleEffect read(ParticleType<MovingParticleEffect> type, PacketByteBuf buf) {
        return new MovingParticleEffect(type, readVec3d(buf));
    }

    @Override
    public String writeParameters() {
        return writeVec3d(moveVec);
    }

    @Override
    public void write(PacketByteBuf buf) {
        writeVec3d(buf, moveVec);
    }
}
