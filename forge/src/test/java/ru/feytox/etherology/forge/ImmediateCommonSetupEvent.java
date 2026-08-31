package ru.feytox.etherology.forge;

import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.concurrent.CompletableFuture;

final class ImmediateCommonSetupEvent extends FMLCommonSetupEvent {

    ImmediateCommonSetupEvent() {
        super(null, null);
    }

    /**
     * Executes queued work immediately so the Forge adapter can be tested without a running game.
     *
     * @param work setup callback under test
     * @return an already-completed queue result
     */
    @Override
    public CompletableFuture<Void> enqueueWork(Runnable work) {
        work.run();
        return CompletableFuture.completedFuture(null);
    }
}
