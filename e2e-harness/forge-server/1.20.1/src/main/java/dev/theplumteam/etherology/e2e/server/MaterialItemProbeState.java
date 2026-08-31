package dev.theplumteam.etherology.e2e.server;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

record MaterialItemProbeState(
        String captureError,
        List<String> materialItemIds,
        Map<String, MaterialItemEntry> entries
) {

    static final String ITEM_REGISTRY_ID = "minecraft:item";
    static final String VANILLA_ITEM_CLASS = Item.class.getName();
    static final List<String> EXPECTED_NBT_KEYS = List.of("Count", "id");
    static final Map<String, Integer> EXPECTED_MAX_COUNTS = expectedMaxCounts();
    static final List<String> EXPECTED_ITEM_IDS = List.copyOf(
            EXPECTED_MAX_COUNTS.keySet()
    );

    static MaterialItemProbeState capture() {
        List<String> capturedIds = new ArrayList<>();
        Map<String, MaterialItemEntry> capturedEntries = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        EXPECTED_MAX_COUNTS.forEach((id, expectedMaxCount) -> {
            MaterialItemEntry entry;
            try {
                entry = captureEntry(id, expectedMaxCount);
            } catch (RuntimeException exception) {
                entry = MaterialItemEntry.failed(id, expectedMaxCount);
                errors.add(id + "=" + exception.getClass().getName());
            }
            if (entry.itemIdentity() != null) {
                capturedIds.add(entry.id());
            }
            capturedEntries.put(id, entry);
        });

        capturedIds.sort(String::compareTo);
        return new MaterialItemProbeState(
                String.join(",", errors),
                List.copyOf(capturedIds),
                Collections.unmodifiableMap(capturedEntries)
        );
    }

    static MaterialItemProbeState missing() {
        return new MaterialItemProbeState("not captured", List.of(), Map.of());
    }

    boolean hasExactRegistry() {
        return EXPECTED_ITEM_IDS.equals(materialItemIds)
                && EXPECTED_MAX_COUNTS.keySet().equals(entries.keySet())
                && EXPECTED_ITEM_IDS.stream().allMatch(id -> {
                    MaterialItemEntry entry = entries.get(id);
                    return entry != null
                            && entry.itemIdentity() != null
                            && id.equals(entry.id());
                });
    }

    boolean hasExactRuntimeClass() {
        return allEntriesMatch(entry -> VANILLA_ITEM_CLASS.equals(entry.runtimeClass()));
    }

    boolean hasExactMaxCounts() {
        return EXPECTED_MAX_COUNTS.entrySet().stream().allMatch(expected -> {
            MaterialItemEntry entry = entries.get(expected.getKey());
            return entry != null && expected.getValue() == entry.maxCount();
        });
    }

    boolean hasExactStackNbtRoundTrips() {
        return allEntriesMatch(entry -> entry.roundTripExact()
                && entry.id().equals(entry.serializedId())
                && entry.maxCount() == entry.serializedCount()
                && EXPECTED_NBT_KEYS.equals(entry.serializedKeys()));
    }

    boolean hasExactSaveRepresentations() {
        return expectedCanonicalSaveRepresentations().equals(
                canonicalSaveRepresentations()
        );
    }

    boolean hasExactContract() {
        return captureError.isEmpty()
                && hasExactRuntimeClass()
                && hasExactMaxCounts()
                && hasExactStackNbtRoundTrips()
                && hasExactSaveRepresentations();
    }

    boolean sameStateAtServerStarted(MaterialItemProbeState startedState) {
        return hasSameRegistry(startedState)
                && hasSameProperties(startedState)
                && hasSameStackNbt(startedState);
    }

    boolean hasSameRegistry(MaterialItemProbeState other) {
        if (!captureError.equals(other.captureError)
                || !materialItemIds.equals(other.materialItemIds)
                || !entries.keySet().equals(other.entries.keySet())) {
            return false;
        }
        return EXPECTED_ITEM_IDS.stream().allMatch(id -> {
            MaterialItemEntry entry = entries.get(id);
            MaterialItemEntry otherEntry = other.entries.get(id);
            return entry != null
                    && otherEntry != null
                    && entry.itemIdentity() != null
                    && entry.itemIdentity() == otherEntry.itemIdentity()
                    && entry.id().equals(otherEntry.id());
        });
    }

    boolean hasSameProperties(MaterialItemProbeState other) {
        return EXPECTED_ITEM_IDS.stream().allMatch(id -> {
            MaterialItemEntry entry = entries.get(id);
            MaterialItemEntry otherEntry = other.entries.get(id);
            return entry != null
                    && otherEntry != null
                    && entry.runtimeClass().equals(otherEntry.runtimeClass())
                    && entry.maxCount() == otherEntry.maxCount();
        });
    }

    boolean hasSameStackNbt(MaterialItemProbeState other) {
        return EXPECTED_ITEM_IDS.stream().allMatch(id -> {
            MaterialItemEntry entry = entries.get(id);
            MaterialItemEntry otherEntry = other.entries.get(id);
            return entry != null
                    && otherEntry != null
                    && entry.serializedId().equals(otherEntry.serializedId())
                    && entry.serializedCount() == otherEntry.serializedCount()
                    && entry.serializedKeys().equals(otherEntry.serializedKeys())
                    && entry.roundTripExact() == otherEntry.roundTripExact()
                    && entry.saveRepresentation().equals(
                            otherEntry.saveRepresentation()
                    );
        });
    }

    String runtimeClassSummary() {
        List<String> runtimeClasses = entries.values().stream()
                .map(MaterialItemEntry::runtimeClass)
                .distinct()
                .sorted()
                .toList();
        return String.join(",", runtimeClasses);
    }

    String canonicalMaxCounts() {
        return canonicalMaxCounts(entries.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().maxCount(),
                        (first, second) -> first,
                        TreeMap::new
                )
        ));
    }

    String canonicalSaveRepresentations() {
        return entries.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().saveRepresentation())
                .collect(Collectors.joining(","));
    }

    static String expectedCanonicalMaxCounts() {
        return canonicalMaxCounts(EXPECTED_MAX_COUNTS);
    }

    static String expectedCanonicalSaveRepresentations() {
        return EXPECTED_MAX_COUNTS.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + saveRepresentation(
                        entry.getKey(),
                        VANILLA_ITEM_CLASS,
                        entry.getValue(),
                        entry.getKey(),
                        entry.getValue(),
                        EXPECTED_NBT_KEYS
                ))
                .collect(Collectors.joining(","));
    }

    private static MaterialItemEntry captureEntry(String id, int expectedMaxCount) {
        Identifier expectedId = new Identifier(id);
        Item item = Registries.ITEM.getOrEmpty(expectedId).orElse(null);
        if (item == null) {
            return MaterialItemEntry.failed(id, expectedMaxCount);
        }

        Identifier registeredId = Registries.ITEM.getId(item);
        String actualId = registeredId == null ? "" : registeredId.toString();
        int maxCount = item.getMaxCount();
        ItemStack stack = new ItemStack(item, maxCount);
        NbtCompound serialized = stack.writeNbt(new NbtCompound());
        List<String> serializedKeys = serialized.getKeys().stream().sorted().toList();
        String serializedId = serialized.getString("id");
        int serializedCount = Byte.toUnsignedInt(serialized.getByte("Count"));
        ItemStack restored = ItemStack.fromNbt(serialized.copy());
        Identifier restoredId = Registries.ITEM.getId(restored.getItem());
        boolean roundTripExact = restored.getItem() == item
                && restoredId != null
                && actualId.equals(restoredId.toString())
                && restored.getCount() == maxCount
                && restored.getMaxCount() == maxCount;
        return new MaterialItemEntry(
                item,
                actualId,
                item.getClass().getName(),
                maxCount,
                serializedId,
                serializedCount,
                serializedKeys,
                roundTripExact,
                saveRepresentation(
                        actualId,
                        item.getClass().getName(),
                        maxCount,
                        serializedId,
                        serializedCount,
                        serializedKeys
                )
        );
    }

    private boolean allEntriesMatch(
            java.util.function.Predicate<MaterialItemEntry> predicate
    ) {
        return EXPECTED_ITEM_IDS.stream().allMatch(id -> {
            MaterialItemEntry entry = entries.get(id);
            return entry != null && predicate.test(entry);
        });
    }

    private static String canonicalMaxCounts(Map<String, Integer> maxCounts) {
        return maxCounts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    private static String saveRepresentation(
            String id,
            String runtimeClass,
            int maxCount,
            String serializedId,
            int serializedCount,
            List<String> serializedKeys
    ) {
        return id
                + "|class=" + runtimeClass
                + "|max=" + maxCount
                + "|nbt_id=" + serializedId
                + "|nbt_count=" + serializedCount
                + "|nbt_keys=" + String.join("+", serializedKeys);
    }

    private static Map<String, Integer> expectedMaxCounts() {
        Map<String, Integer> maxCounts = new TreeMap<>();
        maxCounts.put("etherology:etheroscope", 64);
        maxCounts.put("etherology:thuja_oil", 64);
        maxCounts.put("etherology:azel_ingot", 64);
        maxCounts.put("etherology:azel_nugget", 64);
        maxCounts.put("etherology:ethril_ingot", 64);
        maxCounts.put("etherology:ethril_nugget", 64);
        maxCounts.put("etherology:ebony_ingot", 64);
        maxCounts.put("etherology:ebony_nugget", 64);
        maxCounts.put("etherology:enriched_attrahite", 16);
        maxCounts.put("etherology:raw_azel", 64);
        maxCounts.put("etherology:attrahite_brick", 64);
        maxCounts.put("etherology:binder", 64);
        maxCounts.put("etherology:ebony", 64);
        maxCounts.put("etherology:resonating_wand", 64);
        return Collections.unmodifiableMap(new LinkedHashMap<>(maxCounts));
    }

    record MaterialItemEntry(
            Object itemIdentity,
            String id,
            String runtimeClass,
            int maxCount,
            String serializedId,
            int serializedCount,
            List<String> serializedKeys,
            boolean roundTripExact,
            String saveRepresentation
    ) {

        static MaterialItemEntry failed(String id, int expectedMaxCount) {
            return new MaterialItemEntry(
                    null,
                    id,
                    "",
                    expectedMaxCount,
                    "",
                    -1,
                    List.of(),
                    false,
                    ""
            );
        }
    }
}
