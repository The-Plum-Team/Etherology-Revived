package ru.feytox.etherology.registry.item;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import ru.feytox.etherology.item.PatternTabletItem;
import ru.feytox.etherology.magic.staff.StaffStyles;
import ru.feytox.etherology.registry.SharedDeferredRegister;

/**
 * Owns the seven staff-pattern tablets for both loaders.
 */
public final class SharedPatternTabletItems {

    private static final SharedDeferredRegister<Item> ITEMS =
            SharedDeferredRegister.create(RegistryKeys.ITEM);

    /** Supplies the pattern found in woodland-mansion chests. */
    public static final RegistrySupplier<PatternTabletItem> ARISTOCRAT_PATTERN_TABLET =
            ITEMS.register("aristocrat_pattern_tablet",
                    () -> new PatternTabletItem(StaffStyles.ARISTOCRAT));
    /** Supplies the pattern found in jungle-temple chests. */
    public static final RegistrySupplier<PatternTabletItem> ASTRONOMY_PATTERN_TABLET =
            ITEMS.register("astronomy_pattern_tablet",
                    () -> new PatternTabletItem(StaffStyles.ASTRONOMY));
    /** Supplies the pattern found in shipwreck treasure chests. */
    public static final RegistrySupplier<PatternTabletItem> HEAVENLY_PATTERN_TABLET =
            ITEMS.register("heavenly_pattern_tablet",
                    () -> new PatternTabletItem(StaffStyles.HEAVENLY));
    /** Supplies the pattern found in End-city treasure chests. */
    public static final RegistrySupplier<PatternTabletItem> OCULAR_PATTERN_TABLET =
            ITEMS.register("ocular_pattern_tablet",
                    () -> new PatternTabletItem(StaffStyles.OCULAR));
    /** Supplies the pattern found in desert-pyramid chests. */
    public static final RegistrySupplier<PatternTabletItem> RITUAL_PATTERN_TABLET =
            ITEMS.register("ritual_pattern_tablet",
                    () -> new PatternTabletItem(StaffStyles.RITUAL));
    /** Supplies the pattern found in the four bastion chest variants. */
    public static final RegistrySupplier<PatternTabletItem> ROYAL_PATTERN_TABLET =
            ITEMS.register("royal_pattern_tablet",
                    () -> new PatternTabletItem(StaffStyles.ROYAL));
    /** Supplies the pattern sold by apprentice toolsmiths after registration. */
    public static final RegistrySupplier<PatternTabletItem> TRADITIONAL_PATTERN_TABLET =
            ITEMS.register("traditional_pattern_tablet",
                    () -> new PatternTabletItem(StaffStyles.TRADITIONAL));

    private SharedPatternTabletItems() {
    }

    /** Attaches the declarations without resolving an item before its registry event. */
    public static void register() {
        ITEMS.attach();
    }
}
