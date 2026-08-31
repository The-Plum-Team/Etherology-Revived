package dev.theplumteam.etherology.e2e.server;

import net.minecraft.util.Identifier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Captures the production Ether-source listener without linking it into the probe artifact.
 */
final class EtherSourceProbeState {

    static final String LOADER_CLASS_NAME =
            "ru.feytox.etherology.data.ethersource.EtherSourceLoader";
    static final String RESOURCE_DIRECTORY = "ether_sources";
    static final Map<String, Float> EXPECTED_INITIAL_ENTRIES = expectedEntries(2.0F, false);
    static final Map<String, Float> EXPECTED_RELOADED_ENTRIES = expectedEntries(9.5F, true);

    private final String captureError;
    private final Map<String, Float> entries;

    private EtherSourceProbeState(String captureError, Map<String, Float> entries) {
        this.captureError = captureError;
        this.entries = entries;
    }

    static EtherSourceProbeState capture() {
        try {
            Class<?> loaderClass = Class.forName(LOADER_CLASS_NAME);
            Object loader = loaderClass.getField("INSTANCE").get(null);
            Method getEtherItems = loaderClass.getDeclaredMethod("getEtherItems");
            if (!getEtherItems.trySetAccessible()) {
                return failed("getEtherItems is inaccessible");
            }
            return fromRawMap(getEtherItems.invoke(loader));
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException
                | NoSuchMethodException exception) {
            return failed(exception.getClass().getName());
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            return failed(cause == null
                    ? exception.getClass().getName()
                    : cause.getClass().getName());
        } catch (RuntimeException exception) {
            return failed(exception.getClass().getName());
        }
    }

    static EtherSourceProbeState fromRawMap(Object rawEntries) {
        if (!(rawEntries instanceof Map<?, ?> rawMap)) {
            return failed("not a map");
        }

        Map<String, Float> normalizedEntries = new TreeMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof Identifier identifier)
                    || !(entry.getValue() instanceof Number number)) {
                return failed("entry type mismatch");
            }
            float value = number.floatValue();
            if (!Float.isFinite(value)) {
                return failed("non-finite value");
            }
            if (normalizedEntries.put(identifier.toString(), value) != null) {
                return failed("duplicate identifier");
            }
        }
        return new EtherSourceProbeState(
                "",
                Collections.unmodifiableMap(normalizedEntries)
        );
    }

    static EtherSourceProbeState failed(String captureError) {
        return new EtherSourceProbeState(captureError, Map.of());
    }

    String captureError() {
        return captureError;
    }

    Map<String, Float> entries() {
        return entries;
    }

    boolean hasExactInitialEntries() {
        return entries.equals(EXPECTED_INITIAL_ENTRIES);
    }

    boolean hasExactReloadedEntries() {
        return entries.equals(EXPECTED_RELOADED_ENTRIES);
    }

    boolean sameEntries(EtherSourceProbeState other) {
        return entries.equals(other.entries);
    }

    String canonicalEntries() {
        return canonicalEntries(entries);
    }

    static String canonicalEntries(Map<String, Float> entries) {
        return entries.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + Float.toString(entry.getValue()))
                .collect(Collectors.joining(","));
    }

    String value(String identifier) {
        Float value = entries.get(identifier);
        return value == null ? "absent" : Float.toString(value);
    }

    private static Map<String, Float> expectedEntries(float redstoneValue, boolean addDiamond) {
        Map<String, Float> entries = new TreeMap<>();
        entries.put("etherology:primoshard_clos", 4.0F);
        entries.put("etherology:primoshard_keta", 4.0F);
        entries.put("etherology:primoshard_rella", 4.0F);
        entries.put("etherology:primoshard_via", 4.0F);
        entries.put("minecraft:ancient_debris", 4.0F);
        entries.put("minecraft:blaze_powder", 2.0F);
        entries.put("minecraft:chorus_fruit", 2.0F);
        entries.put("minecraft:crying_obsidian", 6.0F);
        if (addDiamond) {
            entries.put("minecraft:diamond", 13.0F);
        }
        entries.put("minecraft:echo_shard", 12.0F);
        entries.put("minecraft:ender_eye", 6.0F);
        entries.put("minecraft:ender_pearl", 4.0F);
        entries.put("minecraft:experience_bottle", 8.0F);
        entries.put("minecraft:ghast_tear", 4.0F);
        entries.put("minecraft:glowstone_dust", 1.0F);
        entries.put("minecraft:gunpowder", 1.0F);
        entries.put("minecraft:heart_of_the_sea", 12.0F);
        entries.put("minecraft:honeycomb", 1.0F);
        entries.put("minecraft:lapis_lazuli", 1.0F);
        entries.put("minecraft:magma_cream", 2.0F);
        entries.put("minecraft:prismarine_crystals", 1.0F);
        entries.put("minecraft:quartz", 1.0F);
        entries.put("minecraft:redstone", redstoneValue);
        entries.put("minecraft:sculk", 12.0F);
        return Collections.unmodifiableMap(entries);
    }
}
