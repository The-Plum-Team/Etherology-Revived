package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.feytox.etherology.forge.PedestalBytecodeAssertions.readClass;
import static ru.feytox.etherology.forge.PedestalBytecodeAssertions.repositoryRoot;

final class PatternTabletAccessTest {

    @Test
    void bothLoaderAccessDeclarationsExposeOnlyTheVanillaSellFactory() throws IOException {
        var root = repositoryRoot();
        assertEquals(1, Files.readAllLines(root.resolve("src/main/resources/etherology.accesswidener"))
                .stream().filter("accessible class net/minecraft/village/TradeOffers$SellItemFactory"::equals).count());
        assertEquals("public net.minecraft.world.entity.npc.VillagerTrades$ItemsForEmeralds\n",
                Files.readString(root.resolve("forge/src/main/resources/META-INF/accesstransformer.cfg")));
    }

    @Test
    void forgeDevelopmentClasspathActuallyMakesTheVanillaSellFactoryPublic() throws IOException {
        var factory = readClass("net/minecraft/village/TradeOffers$SellItemFactory.class");
        var innerClasses = factory.innerClasses.stream()
                .filter(inner -> inner.name.equals(factory.name)).toList();
        assertEquals(1, innerClasses.size());
        assertTrue((innerClasses.get(0).access & Opcodes.ACC_PUBLIC) != 0);
    }
}
