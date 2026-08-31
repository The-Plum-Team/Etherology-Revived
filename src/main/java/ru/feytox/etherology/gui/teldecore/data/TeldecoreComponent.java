package ru.feytox.etherology.gui.teldecore.data;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.component.CopyableComponentState;
import ru.feytox.etherology.component.PersistentComponentState;
import ru.feytox.etherology.network.interaction.EntityComponentC2SType;
import ru.feytox.etherology.registry.misc.EtherologyComponents;
import ru.feytox.etherology.util.misc.EIdentifier;

import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor @Getter
public class TeldecoreComponent implements PersistentComponentState, CopyableComponentState<TeldecoreComponent> {

    // static
    public static final Identifier CHAPTER_MENU = EIdentifier.of("chapter_menu");
    private final PlayerEntity player;
    @Setter
    private Identifier selected = CHAPTER_MENU;
    @Nullable @Setter
    private Identifier tab = null;
    @Setter
    private int page = 0;
    private Set<Identifier> completedQuests = new ObjectOpenHashSet<>();
    @Setter
    private Set<Identifier> openedChapters = new ObjectOpenHashSet<>();

    public void addCompletedQuest(Identifier chapterId) {
        completedQuests.add(chapterId);
        saveOnServer();
    }

    public boolean wasOpened(Identifier chapterId) {
        return openedChapters.contains(chapterId);
    }

    public void addOpened(Identifier chapterId) {
        if (openedChapters.add(chapterId)) {
            sendToServer(EntityComponentC2SType.TELDECORE_OPENED);
        }
    }

    public boolean isCompleted(Identifier chapterId) {
        return completedQuests.contains(chapterId);
    }

    public void turnPage(boolean isLeft) {
        setPage(Math.max(0, page + (isLeft ? -1 : 1)));
    }

    public void switchTab(Identifier tab) {
        setChapterPage(0);
        setSelected(CHAPTER_MENU);
        this.tab = tab;
        sendToServer(EntityComponentC2SType.TELDECORE_TAB);
    }

    public void setChapterPage(int page) {
        this.page = page;
        sendToServer(EntityComponentC2SType.TELDECORE_PAGE);
    }

    public void setSelectedChapter(Identifier selected) {
        setChapterPage(0);
        this.selected = selected;
        sendToServer(EntityComponentC2SType.TELDECORE_SELECTED);
    }

    @Override
    public void copyFrom(TeldecoreComponent other) {
        selected = other.selected;
        page = other.page;
        tab = other.tab;
        completedQuests = new ObjectOpenHashSet<>(other.completedQuests);
        openedChapters = new ObjectOpenHashSet<>(other.openedChapters);
    }

    @Override
    public void readFromNbt(NbtCompound tag) {
        selected = new Identifier(tag.getString("selected"));
        page = tag.getInt("page");
        String tabStr = tag.getString("tab");
        tab = tabStr.isEmpty() ? null : new Identifier(tabStr);

        completedQuests = tag.getList("completed", NbtElement.STRING_TYPE).stream().map(NbtElement::asString)
                .map(Identifier::new).collect(Collectors.toCollection(ObjectOpenHashSet::new));
        openedChapters = tag.getList("opened", NbtElement.STRING_TYPE).stream().map(NbtElement::asString)
                .map(Identifier::new).collect(Collectors.toCollection(ObjectOpenHashSet::new));
    }

    @Override
    public void writeToNbt(NbtCompound tag) {
        tag.putString("selected", selected.toString());
        tag.putInt("page", page);
        if (tab != null) tag.putString("tab", tab.toString());

        writeQuests(tag);
        writeOpenedChapters(tag);
    }

    private void writeQuests(NbtCompound tag) {
        if (completedQuests.isEmpty()) {
            tag.remove("completed");
            return;
        }
        NbtList quests = new NbtList();
        completedQuests.stream().map(Identifier::toString).map(NbtString::of).forEach(quests::add);
        tag.put("completed", quests);
    }

    private void writeOpenedChapters(NbtCompound tag) {
        if (openedChapters.isEmpty()) {
            tag.remove("opened");
            return;
        }
        NbtList quests = new NbtList();
        openedChapters.stream().map(Identifier::toString).map(NbtString::of).forEach(quests::add);
        tag.put("opened", quests);
    }

    private void saveOnServer() {
        EtherologyComponents.TELDECORE.sync(player);
    }

    private void sendToServer(EntityComponentC2SType<TeldecoreComponent, ?> packetType) {
        packetType.sendToServer(this);
    }

    public boolean shouldSyncWith(ServerPlayerEntity player) {
        return this.player.equals(player);
    }

    public void resetAllData() {
        tab = null;
        page = 0;
        selected = CHAPTER_MENU;
        completedQuests.clear();
        openedChapters.clear();
        saveOnServer();
    }
}
