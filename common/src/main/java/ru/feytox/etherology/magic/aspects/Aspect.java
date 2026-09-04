package ru.feytox.etherology.magic.aspects;

import com.mojang.serialization.Codec;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import org.apache.commons.lang3.EnumUtils;

public enum Aspect implements EtherologyAspect, StringIdentifiable {
    RELLA(0, 0, 0),
    ETHA(0, 1, 4),
    DIZORD(0, 2, 8),
    VACUO(0, 3, 12),
    NETHA(0, 4, 16),
    GRAVIA(0, 5, 20),
    MOUNTA(0, 6, 24),
    SOWORDA(0, 7, 28),
    CLOS(1, 0, 1),
    ENN(1, 1, 5),
    ANEMA(1, 2, 9),
    VIBRA(1, 3, 13),
    MATERRA(1, 4, 17),
    SOLISTA(1, 5, 21),
    DEFENTA(1, 6, 25),
    FELKA(1, 7, 29),
    VIA(2, 0, 2),
    FLIMA(2, 1, 6),
    AREA(2, 2, 10),
    CHAOS(2, 3, 14),
    GEMA(2, 4, 18),
    DOGMA(2, 5, 22),
    HENDALL(2, 6, 26),
    STRALFA(2, 7, 30),
    KETA(3, 0, 3),
    MORA(3, 1, 7),
    MEMO(3, 2, 11),
    DEVO(3, 3, 15),
    SECRA(3, 4, 19),
    ISKIL(3, 5, 23),
    ALCHEMA(3, 6, 27),
    GROSEAL(3, 7, 31),
    VITER(4, 0, 32),
    TALO(4, 1, 33),
    AZU(4, 2, 34),
    FRADO(4, 3, 35),
    SOCE(4, 4, 36),
    PLANTA(4, 5, 37),
    LUMOS(4, 6, 38),
    NOX(4, 7, 39);

    public static final Codec<Aspect> CODEC =
            StringIdentifiable.createCodec(Aspect::values);
    public static final Identifier TEXTURE =
            new Identifier("etherology", "textures/gui/aspects.png");

    private final int textureRow;
    private final int textureColumn;
    private final int runeId;

    Aspect(int textureRow, int textureColumn, int runeId) {
        this.textureRow = textureRow;
        this.textureColumn = textureColumn;
        this.runeId = runeId;
    }

    @Override
    public String getAspectName() {
        return name().toLowerCase();
    }

    public Identifier getId() {
        return new Identifier("etherology", getAspectName());
    }

    public static Aspect get(Identifier id) {
        return EnumUtils.getEnumIgnoreCase(Aspect.class, id.getPath());
    }

    @Override
    public String asString() {
        return getAspectName();
    }

    @Override
    public int getTextureRow() {
        return textureRow;
    }

    @Override
    public int getTextureColumn() {
        return textureColumn;
    }

    public int getRuneId() {
        return runeId;
    }
}
