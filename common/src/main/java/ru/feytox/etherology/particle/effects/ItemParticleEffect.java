package ru.feytox.etherology.particle.effects;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import ru.feytox.etherology.particle.effects.misc.FeyParticleEffect;

public class ItemParticleEffect extends FeyParticleEffect<ItemParticleEffect> {

    private final Item item;
    private final Vec3d moveVec;

    public ItemParticleEffect(ParticleType<ItemParticleEffect> type, Item item, Vec3d moveVec) {
        super(type);
        this.item = item;
        this.moveVec = moveVec;
    }

    public ItemParticleEffect(ParticleType<ItemParticleEffect> type) {
        this(type, null, null);
    }

    public Item getItem() {
        return item;
    }

    public Vec3d getMoveVec() {
        return moveVec;
    }

    @Override
    public Codec<ItemParticleEffect> createCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                Registries.ITEM.getCodec().fieldOf("item").forGetter(ItemParticleEffect::getItem),
                Vec3d.CODEC.fieldOf("moveVec").forGetter(ItemParticleEffect::getMoveVec)
                ).apply(instance, biFactory(ItemParticleEffect::new)));
    }

    @Override
    public ItemParticleEffect read(ParticleType<ItemParticleEffect> type, StringReader reader) throws CommandSyntaxException {
        reader.expect(' ');
        Identifier itemId = Identifier.fromCommandInput(reader);
        if (!Registries.ITEM.containsId(itemId)) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
        }
        reader.expect(' ');
        return new ItemParticleEffect(type, Registries.ITEM.get(itemId), readVec3d(reader));
    }

    @Override
    public ItemParticleEffect read(ParticleType<ItemParticleEffect> type, PacketByteBuf buf) {
        Item item = Registries.ITEM.get(buf.readIdentifier());
        return new ItemParticleEffect(type, item, readVec3d(buf));
    }

    @Override
    public String writeParameters() {
        return Registries.ITEM.getId(item) + " " + writeVec3d(moveVec);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(Registries.ITEM.getId(item));
        writeVec3d(buf, moveVec);
    }
}
