package ru.feytox.etherology.magic.lens;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class LensModifiersData {

    public static final Codec<LensModifiersData> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.INT)
            .xmap(LensModifiersData::new, LensModifiersData::getModifiers);

    protected final Map<Identifier, Integer> modifiers;

    public LensModifiersData(Map<Identifier, Integer> modifiers) {
        if (modifiers == null) {
            throw new NullPointerException("modifiers is marked non-null but is null");
        }
        this.modifiers = modifiers;
    }

    public static LensModifiersData empty() {
        return new LensModifiersData(new Object2IntOpenHashMap<>());
    }

    public int getLevel(LensModifier modifier) {
        return modifiers.getOrDefault(modifier.modifierId(), 0);
    }

    public boolean isEmpty() {
        return modifiers.isEmpty();
    }

    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        modifiers.forEach((id, level) -> nbt.putInt(id.toString(), level));
        return nbt;
    }

    public static LensModifiersData readNbt(NbtCompound nbt) {
        Map<Identifier, Integer> modifiers = nbt.getKeys().stream()
                .collect(Collectors.toMap(
                        Identifier::new,
                        nbt::getInt,
                        Integer::max,
                        Object2IntOpenHashMap::new
                ));
        return new LensModifiersData(modifiers);
    }

    public Mutable asMutable() {
        return new Mutable(new Object2IntOpenHashMap<>(modifiers));
    }

    public Map<Identifier, Integer> getModifiers() {
        return modifiers;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof LensModifiersData otherData)) return false;
        if (!otherData.canEqual(this)) return false;
        return Objects.equals(getModifiers(), otherData.getModifiers());
    }

    protected boolean canEqual(Object other) {
        return other instanceof LensModifiersData;
    }

    @Override
    public int hashCode() {
        int result = 1;
        Map<Identifier, Integer> value = getModifiers();
        result = result * 59 + (value == null ? 43 : value.hashCode());
        return result;
    }

    public static class Mutable extends LensModifiersData {

        public Mutable(Map<Identifier, Integer> modifiers) {
            super(modifiers);
        }

        public void setLevel(LensModifier modifier, int level) {
            if (level == 0) removeModifier(modifier);
            else modifiers.put(modifier.modifierId(), level);
        }

        public void removeModifier(LensModifier modifier) {
            if (!modifiers.containsKey(modifier.modifierId())) return;
            modifiers.remove(modifier.modifierId());
        }
    }
}
