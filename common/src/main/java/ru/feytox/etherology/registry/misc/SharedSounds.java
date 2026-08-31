package ru.feytox.etherology.registry.misc;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import ru.feytox.etherology.bootstrap.EtherologyBootstrap;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns loader-neutral sound-event registrations accepted by every active loader.
 */
public final class SharedSounds {

    private static final SharedDeferredRegister<SoundEvent> SOUNDS =
            SharedDeferredRegister.create(RegistryKeys.SOUND_EVENT);

    /**
     * Supplies the electricity sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> ELECTRICITY =
            register("electricity_sound");

    /**
     * Supplies the matrix idle sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> MATRIX_WORK =
            register("matrix_idle_sound");

    /**
     * Supplies the deflection sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> DEFLECT = register("deflect");

    /**
     * Supplies the brewing bubbles sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> BUBBLES = register("bubbles");

    /**
     * Supplies the brewing completion sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> POUF = register("pouf");

    /**
     * Supplies the ratchet sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> RATCHET = register("ratchet");

    /**
     * Supplies the brewing dissolution sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> BREWING_DISSOLUTION =
            register("brewing_dissolution");

    /**
     * Supplies the thunder zap sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> THUNDER_ZAP = register("thunder_zap");

    /**
     * Supplies the tuning mace sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> TUNING_MACE = register("tuning_mace");

    /**
     * Supplies the tuning-fork activation sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> TUNING_FORK_ACTIVATE =
            register("tuning_fork_activate");

    /**
     * Supplies the tuning-fork tuning sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> TUNING_FORK_TUNING =
            register("tuning_fork_tuning");

    /**
     * Supplies the tuning-fork resonance sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> TUNING_FORK_RESONANCE =
            register("tuning_fork_resonance");

    /**
     * Supplies the broadsword sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> BROADSWORD = register("broadsword");

    /**
     * Supplies the warp-counter sound after the active loader registers sound events.
     */
    public static final RegistrySupplier<SoundEvent> WARP_COUNTER = register("warp_counter");

    private SharedSounds() {
    }

    /**
     * Attaches the shared sound-event registry once before loader registry events run.
     */
    public static void register() {
        SOUNDS.attach();
    }

    private static RegistrySupplier<SoundEvent> register(String id) {
        return SOUNDS.register(
                id,
                () -> SoundEvent.of(Identifier.of(EtherologyBootstrap.MOD_ID, id))
        );
    }
}
