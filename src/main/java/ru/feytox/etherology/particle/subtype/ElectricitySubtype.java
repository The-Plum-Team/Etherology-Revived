package ru.feytox.etherology.particle.subtype;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

public enum ElectricitySubtype implements StringIdentifiable {
    SIMPLE,
    MATRIX,
    JEWELRY;

    public static final Codec<ElectricitySubtype> CODEC = StringIdentifiable.createCodec(ElectricitySubtype::values);

    @Override
    public String asString() {
        return name();
    }
}
