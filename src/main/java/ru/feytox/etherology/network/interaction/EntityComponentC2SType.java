package ru.feytox.etherology.network.interaction;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.Etherology;
import ru.feytox.etherology.gui.teldecore.data.TeldecoreComponent;
import ru.feytox.etherology.network.util.AbstractC2SPacket;
import ru.feytox.etherology.registry.misc.ComponentHandle;
import ru.feytox.etherology.registry.misc.EtherologyComponents;
import ru.feytox.etherology.util.misc.EtherProxy;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class EntityComponentC2SType<C, V> {

    // TODO: 02.08.2024 use smth else... idk
    public static final EntityComponentC2SType<TeldecoreComponent, Identifier> TELDECORE_SELECTED;
    public static final EntityComponentC2SType<TeldecoreComponent, Integer> TELDECORE_PAGE;
    public static final EntityComponentC2SType<TeldecoreComponent, Identifier> TELDECORE_TAB;
    public static final EntityComponentC2SType<TeldecoreComponent, Set<Identifier>> TELDECORE_OPENED;

    private final PacketType<Packet<V>> packetType;
    private final ComponentHandle<C, PlayerEntity> componentHandle;
    private final String valueName;
    private final Function<C, V> getter;
    private final BiConsumer<C, V> setter;
    private final Function<PacketByteBuf, V> reader;
    private final BiConsumer<PacketByteBuf, V> writer;

    private EntityComponentC2SType(ComponentHandle<C, PlayerEntity> componentHandle, String valueName, Function<C, V> getter,
                                   BiConsumer<C, V> setter, Function<PacketByteBuf, V> reader,
                                   BiConsumer<PacketByteBuf, V> writer) {
        this.componentHandle = componentHandle;
        this.valueName = valueName;
        this.getter = getter;
        this.setter = setter;
        this.reader = reader;
        this.writer = writer;

        Identifier componentId = componentHandle.getId();
        Identifier id = new Identifier(componentId.getNamespace(), componentId.getPath() + "_" + valueName);
        this.packetType = PacketType.create(id, this::readPacket);
    }

    public PacketType<Packet<V>> getPacketType() {
        return packetType;
    }

    public void receive(Packet<V> packet, ServerPlayerEntity player) {
        componentHandle.maybeGet(player).ifPresentOrElse(data -> setter.accept(data, packet.value),
                () -> Etherology.ELOGGER.error("Failed to sync {} data for component {}", valueName, componentHandle.getId()));
    }

    public void sendToServer(C component) {
        EtherProxy.getInstance().sendToServer(new Packet<>(getter.apply(component), packetType, writer));
    }

    public static <C, V> EntityComponentC2SType<C, V> of(
            ComponentHandle<C, PlayerEntity> componentHandle, String valueName, Function<C, V> getter, BiConsumer<C, V> setter,
            Function<PacketByteBuf, V> reader, BiConsumer<PacketByteBuf, V> writer) {
        return new EntityComponentC2SType<>(componentHandle, valueName, getter, setter, reader, writer);
    }

    private Packet<V> readPacket(PacketByteBuf buf) {
        return new Packet<>(reader.apply(buf), packetType, writer);
    }

    public static final class Packet<V> implements AbstractC2SPacket {

        private final V value;
        private final PacketType<Packet<V>> packetType;
        private final BiConsumer<PacketByteBuf, V> writer;

        private Packet(V value, PacketType<Packet<V>> packetType, BiConsumer<PacketByteBuf, V> writer) {
            this.value = value;
            this.packetType = packetType;
            this.writer = writer;
        }

        @Override
        public void write(PacketByteBuf buf) {
            writer.accept(buf, value);
        }

        @Override
        public PacketType<?> getType() {
            return packetType;
        }
    }

    static {
        TELDECORE_SELECTED = of(EtherologyComponents.TELDECORE, "selected", TeldecoreComponent::getSelected,
                TeldecoreComponent::setSelected, PacketByteBuf::readIdentifier, PacketByteBuf::writeIdentifier);
        TELDECORE_PAGE = of(EtherologyComponents.TELDECORE, "page", TeldecoreComponent::getPage,
                TeldecoreComponent::setPage, PacketByteBuf::readVarInt, PacketByteBuf::writeVarInt);
        TELDECORE_TAB = of(EtherologyComponents.TELDECORE, "tab", TeldecoreComponent::getTab,
                TeldecoreComponent::setTab, PacketByteBuf::readIdentifier, PacketByteBuf::writeIdentifier);
        TELDECORE_OPENED = of(EtherologyComponents.TELDECORE, "opened", TeldecoreComponent::getOpenedChapters,
                TeldecoreComponent::setOpenedChapters,
                buf -> buf.readCollection(ObjectOpenHashSet::new, PacketByteBuf::readIdentifier),
                (buf, opened) -> buf.writeCollection(opened, PacketByteBuf::writeIdentifier));
    }
}
