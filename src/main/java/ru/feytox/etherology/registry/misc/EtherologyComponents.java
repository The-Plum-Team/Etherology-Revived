package ru.feytox.etherology.registry.misc;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.chunk.Chunk;
import ru.feytox.etherology.gui.teldecore.data.TeldecoreComponent;
import ru.feytox.etherology.gui.teldecore.data.VisitedComponent;
import ru.feytox.etherology.magic.corruption.CorruptionComponent;
import ru.feytox.etherology.magic.ether.EtherComponent;
import ru.feytox.etherology.util.misc.EIdentifier;

public final class EtherologyComponents {

    /** Provides the corruption state attached to a chunk. */
    public static final ComponentHandle<CorruptionComponent, Chunk> CORRUPTION =
            new ComponentHandle<>(EIdentifier.of("corruption"));

    /** Provides the ether state attached to a living entity. */
    public static final ComponentHandle<EtherComponent, LivingEntity> ETHER =
            new ComponentHandle<>(EIdentifier.of("ether"));

    /** Provides Teldecore progress attached to a player. */
    public static final ComponentHandle<TeldecoreComponent, PlayerEntity> TELDECORE =
            new ComponentHandle<>(EIdentifier.of("teldecore"));

    /** Provides location discoveries attached to a player. */
    public static final ComponentHandle<VisitedComponent, PlayerEntity> VISITED =
            new ComponentHandle<>(EIdentifier.of("visited"));

    private EtherologyComponents() {
    }
}
