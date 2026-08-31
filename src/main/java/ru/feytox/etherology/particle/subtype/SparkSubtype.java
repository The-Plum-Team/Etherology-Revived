package ru.feytox.etherology.particle.subtype;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;
import org.apache.commons.lang3.EnumUtils;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.magic.seal.SealType;

public enum SparkSubtype implements StringIdentifiable {
    SIMPLE,
    KETA,
    RELLA,
    VIA,
    CLOS,
    RISING,
    JEWELRY;

    public static final Codec<SparkSubtype> CODEC = StringIdentifiable.createCodec(SparkSubtype::values);

    @Nullable
    public static SparkSubtype of(SealType sealType) {
        return EnumUtils.getEnum(SparkSubtype.class, sealType.name(), null);
    }

    @Override
    public String asString() {
        return name();
    }
}
