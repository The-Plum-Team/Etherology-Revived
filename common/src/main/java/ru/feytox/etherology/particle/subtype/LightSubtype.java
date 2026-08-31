package ru.feytox.etherology.particle.subtype;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

public enum LightSubtype implements StringIdentifiable {
    SIMPLE,
    SPARK,
    PUSHING,
    ATTRACT,
    BREWING,
    MATRIX,
    GENERATOR,
    HAZE;

    public static final Codec<LightSubtype> CODEC = StringIdentifiable.createCodec(LightSubtype::values);

    @Override
    public String asString() {
        return name();
    }
}
