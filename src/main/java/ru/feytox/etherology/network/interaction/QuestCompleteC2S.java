package ru.feytox.etherology.network.interaction;

import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.Etherology;
import ru.feytox.etherology.gui.teldecore.data.Chapter;
import ru.feytox.etherology.network.util.AbstractC2SPacket;
import ru.feytox.etherology.registry.misc.RegistriesRegistry;
import ru.feytox.etherology.util.misc.EIdentifier;

public record QuestCompleteC2S(Identifier chapterId) implements AbstractC2SPacket {

    public static final Identifier ID = EIdentifier.of("quest_complete_c2s");
    public static final PacketType<QuestCompleteC2S> TYPE = PacketType.create(ID, QuestCompleteC2S::new);

    public QuestCompleteC2S(PacketByteBuf buf) {
        this(buf.readIdentifier());
    }

    public static void receive(QuestCompleteC2S packet, ServerPlayerEntity player) {
        Chapter chapter = player.getWorld().getRegistryManager().get(RegistriesRegistry.CHAPTERS).get(packet.chapterId);
        if (chapter != null) chapter.tryCompleteQuest(player, packet.chapterId);
        else Etherology.ELOGGER.error("Could not find chapter {}", packet.chapterId.toString());
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(chapterId);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
