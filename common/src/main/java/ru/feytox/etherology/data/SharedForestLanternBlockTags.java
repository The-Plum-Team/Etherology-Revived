package ru.feytox.etherology.data;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;

/**
 * Owns the loader-neutral block tag required by the Forest Lantern mechanics.
 */
public final class SharedForestLanternBlockTags {

    /** Identifies peach logs that can support an immature Forest Lantern. */
    public static final TagKey<Block> PEACH_LOGS = TagKey.of(
            RegistryKeys.BLOCK,
            Identifier.of(EtherologyBootstrap.MOD_ID, "peach_logs")
    );

    private SharedForestLanternBlockTags() {
    }
}
