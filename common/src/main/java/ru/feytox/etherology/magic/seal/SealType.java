package ru.feytox.etherology.magic.seal;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;
import ru.feytox.etherology.util.misc.RGBColor;

import java.util.Optional;
import java.util.function.Supplier;

public enum SealType implements StringIdentifiable {
    EMPTY(null, null, null),
    KETA("primoshard_keta", new RGBColor(128, 205, 247), new RGBColor(105, 128, 231)),
    RELLA("primoshard_rella", new RGBColor(177, 229, 106), new RGBColor(106, 182, 81)),
    VIA("primoshard_via", new RGBColor(248, 122, 95), new RGBColor(205, 58, 76)),
    CLOS("primoshard_clos", new RGBColor(106, 182, 81), new RGBColor(208, 158, 89));

    public static final Codec<SealType> CODEC = StringIdentifiable.createCodec(SealType::values);

    @Nullable
    private final Supplier<Item> shardGetter;
    @Nullable
    private final Identifier blockId;
    @Nullable
    private final RGBColor startColor;
    @Nullable
    private final RGBColor endColor;
    @Nullable
    private final Identifier textureId;
    @Nullable
    private final Identifier textureLightId;

    SealType(@Nullable String shardPath, @Nullable RGBColor startColor, @Nullable RGBColor endColor) {
        boolean isSeal = shardPath != null;
        Identifier shardId = isSeal ? id(shardPath) : null;

        this.shardGetter = shardId == null ? null : () -> getRequiredItem(shardId);
        this.blockId = isSeal ? id(asString() + "_seal") : null;
        this.startColor = startColor;
        this.endColor = endColor;
        this.textureId = isSeal ? id("textures/block/%s_seal.png".formatted(asString())) : null;
        this.textureLightId = isSeal
                ? id("textures/block/%s_seal_light.png".formatted(asString()))
                : null;
    }

    @Nullable
    public Supplier<Item> getShardGetter() {
        return shardGetter;
    }

    @Nullable
    public RGBColor getStartColor() {
        return startColor;
    }

    @Nullable
    public RGBColor getEndColor() {
        return endColor;
    }

    @Nullable
    public Identifier getTextureId() {
        return textureId;
    }

    @Nullable
    public Identifier getTextureLightId() {
        return textureLightId;
    }

    public Optional<Item> getPrimoShard() {
        return Optional.ofNullable(shardGetter).map(Supplier::get);
    }

    public boolean isSeal() {
        return this != EMPTY;
    }

    public Block getBlock() {
        if (blockId == null) {
            throw new ArrayIndexOutOfBoundsException("EMPTY has no seal block");
        }
        if (!Registries.BLOCK.containsId(blockId)) {
            throw new IllegalStateException("Missing Etherology seal block: " + blockId);
        }

        Block block = Registries.BLOCK.get(blockId);
        if (!blockId.equals(Registries.BLOCK.getId(block))) {
            throw new IllegalStateException("Wrong Etherology seal block identity: " + blockId);
        }
        return block;
    }

    @Override
    public String asString() {
        return this.name().toLowerCase();
    }

    private static Identifier id(String path) {
        return Identifier.of(EtherologyBootstrap.MOD_ID, path);
    }

    private static Item getRequiredItem(Identifier itemId) {
        if (!Registries.ITEM.containsId(itemId)) {
            throw new IllegalStateException("Missing Etherology primoshard item: " + itemId);
        }

        Item item = Registries.ITEM.get(itemId);
        if (!itemId.equals(Registries.ITEM.getId(item))) {
            throw new IllegalStateException("Wrong Etherology primoshard item identity: " + itemId);
        }
        return item;
    }
}
