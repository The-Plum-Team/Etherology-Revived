package ru.feytox.etherology.gui.teldecore.data;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

final class TeldecoreComponentCopyTest {

    @Test
    void copiesResearchCollectionsWithoutAliasingThem() {
        Identifier completedQuest = new Identifier("etherology", "completed_quest");
        Identifier openedChapter = new Identifier("etherology", "opened_chapter");
        TeldecoreComponent source = new TeldecoreComponent(null);
        source.getCompletedQuests().add(completedQuest);
        source.getOpenedChapters().add(openedChapter);
        TeldecoreComponent destination = new TeldecoreComponent(null);

        destination.copyFrom(source);

        assertEquals(source.getCompletedQuests(), destination.getCompletedQuests());
        assertEquals(source.getOpenedChapters(), destination.getOpenedChapters());
        assertNotSame(source.getCompletedQuests(), destination.getCompletedQuests());
        assertNotSame(source.getOpenedChapters(), destination.getOpenedChapters());

        source.getCompletedQuests().clear();
        source.getOpenedChapters().clear();
        assertFalse(destination.getCompletedQuests().isEmpty());
        assertFalse(destination.getOpenedChapters().isEmpty());
    }
}
