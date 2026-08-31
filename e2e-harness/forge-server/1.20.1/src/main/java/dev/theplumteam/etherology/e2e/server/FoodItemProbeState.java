package dev.theplumteam.etherology.e2e.server;

import com.mojang.authlib.GameProfile;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

record FoodItemProbeState(
        String captureError,
        List<String> foodItemIds,
        Map<String, FoodItemEntry> entries
) {

    static final String ITEM_REGISTRY_ID = "minecraft:item";
    static final String ITEM_ID = "etherology:forest_lantern_crumb";
    private static final Identifier ITEM_IDENTIFIER = Identifier.of(
            "etherology",
            "forest_lantern_crumb"
    );
    static final String VANILLA_ITEM_CLASS = Item.class.getName();
    static final int EXPECTED_MAX_COUNT = 64;
    static final int EXPECTED_HUNGER = 3;
    static final float EXPECTED_SATURATION_MODIFIER = 2.0F;
    static final List<String> EXPECTED_ITEM_IDS = List.of(ITEM_ID);
    static final List<String> EXPECTED_NBT_KEYS = List.of("Count", "id");

    static FoodItemProbeState capture() {
        List<String> capturedIds = new ArrayList<>();
        Map<String, FoodItemEntry> capturedEntries = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        FoodItemEntry entry;
        try {
            entry = captureEntry();
        } catch (RuntimeException exception) {
            entry = FoodItemEntry.failed();
            errors.add(ITEM_ID + "=" + exception.getClass().getName());
        }
        if (entry.itemIdentity() != null) capturedIds.add(entry.id());
        capturedEntries.put(ITEM_ID, entry);

        return new FoodItemProbeState(
                String.join(",", errors),
                List.copyOf(capturedIds),
                Collections.unmodifiableMap(capturedEntries)
        );
    }

    static FoodItemProbeState missing() {
        return new FoodItemProbeState("not captured", List.of(), Map.of());
    }

    boolean hasExactRegistry() {
        FoodItemEntry entry = entries.get(ITEM_ID);
        return EXPECTED_ITEM_IDS.equals(foodItemIds)
                && EXPECTED_ITEM_IDS.equals(List.copyOf(entries.keySet()))
                && entry != null
                && entry.itemIdentity() != null
                && ITEM_ID.equals(entry.id());
    }

    boolean hasExactProperties() {
        FoodItemEntry entry = entries.get(ITEM_ID);
        return entry != null
                && VANILLA_ITEM_CLASS.equals(entry.runtimeClass())
                && entry.maxCount() == EXPECTED_MAX_COUNT
                && entry.food()
                && entry.hunger() == EXPECTED_HUNGER
                && Float.compare(
                        entry.saturationModifier(),
                        EXPECTED_SATURATION_MODIFIER
                ) == 0
                && !entry.alwaysEdible()
                && entry.statusEffectCount() == 0
                && !entry.hasRecipeRemainder()
                && entry.recipeRemainderId().isEmpty();
    }

    boolean hasExactStackNbtRoundTrip() {
        FoodItemEntry entry = entries.get(ITEM_ID);
        return entry != null
                && entry.roundTripExact()
                && ITEM_ID.equals(entry.serializedId())
                && entry.serializedCount() == EXPECTED_MAX_COUNT
                && EXPECTED_NBT_KEYS.equals(entry.serializedKeys());
    }

    boolean hasExactSaveRepresentation() {
        return expectedCanonicalSaveRepresentations().equals(
                canonicalSaveRepresentations()
        );
    }

    boolean hasExactContract() {
        return captureError.isEmpty()
                && hasExactRegistry()
                && hasExactProperties()
                && hasExactStackNbtRoundTrip()
                && expectedCanonicalProperties().equals(canonicalProperties())
                && hasExactSaveRepresentation();
    }

    boolean sameStateAtServerStarted(FoodItemProbeState startedState) {
        return hasSameRegistry(startedState)
                && hasSameProperties(startedState)
                && hasSameStackNbt(startedState);
    }

    boolean hasSameRegistry(FoodItemProbeState other) {
        if (!captureError.equals(other.captureError)
                || !foodItemIds.equals(other.foodItemIds)
                || !entries.keySet().equals(other.entries.keySet())) {
            return false;
        }
        FoodItemEntry entry = entries.get(ITEM_ID);
        FoodItemEntry otherEntry = other.entries.get(ITEM_ID);
        return entry != null
                && otherEntry != null
                && entry.itemIdentity() != null
                && entry.itemIdentity() == otherEntry.itemIdentity()
                && entry.id().equals(otherEntry.id());
    }

    boolean hasSameProperties(FoodItemProbeState other) {
        FoodItemEntry entry = entries.get(ITEM_ID);
        FoodItemEntry otherEntry = other.entries.get(ITEM_ID);
        return entry != null
                && otherEntry != null
                && entry.runtimeClass().equals(otherEntry.runtimeClass())
                && entry.maxCount() == otherEntry.maxCount()
                && entry.food() == otherEntry.food()
                && entry.hunger() == otherEntry.hunger()
                && Float.compare(
                        entry.saturationModifier(),
                        otherEntry.saturationModifier()
                ) == 0
                && entry.alwaysEdible() == otherEntry.alwaysEdible()
                && entry.statusEffectCount() == otherEntry.statusEffectCount()
                && entry.hasRecipeRemainder() == otherEntry.hasRecipeRemainder()
                && entry.recipeRemainderId().equals(otherEntry.recipeRemainderId());
    }

    boolean hasSameStackNbt(FoodItemProbeState other) {
        FoodItemEntry entry = entries.get(ITEM_ID);
        FoodItemEntry otherEntry = other.entries.get(ITEM_ID);
        return entry != null
                && otherEntry != null
                && entry.serializedId().equals(otherEntry.serializedId())
                && entry.serializedCount() == otherEntry.serializedCount()
                && entry.serializedKeys().equals(otherEntry.serializedKeys())
                && entry.roundTripExact() == otherEntry.roundTripExact()
                && entry.saveRepresentation().equals(
                        otherEntry.saveRepresentation()
                );
    }

    String canonicalProperties() {
        return entries.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().propertySummary())
                .collect(Collectors.joining(","));
    }

    String runtimeClassSummary() {
        return entries.values().stream()
                .map(FoodItemEntry::runtimeClass)
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
    }

    String canonicalSaveRepresentations() {
        return entries.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().saveRepresentation())
                .collect(Collectors.joining(","));
    }

    static String expectedCanonicalProperties() {
        return ITEM_ID + "=" + propertySummary(
                VANILLA_ITEM_CLASS,
                EXPECTED_MAX_COUNT,
                true,
                EXPECTED_HUNGER,
                EXPECTED_SATURATION_MODIFIER,
                false,
                0,
                false,
                ""
        );
    }

    static String expectedCanonicalSaveRepresentations() {
        return ITEM_ID + "=" + saveRepresentation(
                ITEM_ID,
                VANILLA_ITEM_CLASS,
                EXPECTED_MAX_COUNT,
                ITEM_ID,
                EXPECTED_MAX_COUNT,
                EXPECTED_NBT_KEYS
        );
    }

    private static FoodItemEntry captureEntry() {
        Item item = Registries.ITEM.getOrEmpty(ITEM_IDENTIFIER).orElse(null);
        if (item == null) return FoodItemEntry.failed();

        Identifier registeredId = Registries.ITEM.getId(item);
        String actualId = registeredId == null ? "" : registeredId.toString();
        int maxCount = item.getMaxCount();
        boolean food = item.isFood();
        FoodComponent foodComponent = item.getFoodComponent();
        int hunger = foodComponent == null ? -1 : foodComponent.getHunger();
        float saturationModifier = foodComponent == null
                ? -1.0F
                : foodComponent.getSaturationModifier();
        boolean alwaysEdible = foodComponent != null && foodComponent.isAlwaysEdible();
        int statusEffectCount = foodComponent == null
                ? -1
                : foodComponent.getStatusEffects().size();
        boolean hasRecipeRemainder = item.hasRecipeRemainder();
        Identifier recipeRemainderId = hasRecipeRemainder
                ? Registries.ITEM.getId(item.getRecipeRemainder())
                : null;
        String recipeRemainder = recipeRemainderId == null
                ? ""
                : recipeRemainderId.toString();
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
        return new FoodItemEntry(
                item,
                actualId,
                item.getClass().getName(),
                maxCount,
                food,
                hunger,
                saturationModifier,
                alwaysEdible,
                statusEffectCount,
                hasRecipeRemainder,
                recipeRemainder,
                serializedId,
                serializedCount,
                serializedKeys,
                roundTripExact,
                propertySummary(
                        item.getClass().getName(),
                        maxCount,
                        food,
                        hunger,
                        saturationModifier,
                        alwaysEdible,
                        statusEffectCount,
                        hasRecipeRemainder,
                        recipeRemainder
                ),
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

    private static String propertySummary(
            String runtimeClass,
            int maxCount,
            boolean food,
            int hunger,
            float saturationModifier,
            boolean alwaysEdible,
            int statusEffectCount,
            boolean hasRecipeRemainder,
            String recipeRemainderId
    ) {
        return runtimeClass
                + "|max=" + maxCount
                + "|is_food=" + food
                + "|hunger=" + hunger
                + "|saturation=" + saturationModifier
                + "|always_edible=" + alwaysEdible
                + "|status_effects=" + statusEffectCount
                + "|recipe_remainder=" + hasRecipeRemainder
                + "|recipe_remainder_id=" + recipeRemainderId;
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

    record FoodItemEntry(
            Object itemIdentity,
            String id,
            String runtimeClass,
            int maxCount,
            boolean food,
            int hunger,
            float saturationModifier,
            boolean alwaysEdible,
            int statusEffectCount,
            boolean hasRecipeRemainder,
            String recipeRemainderId,
            String serializedId,
            int serializedCount,
            List<String> serializedKeys,
            boolean roundTripExact,
            String propertySummary,
            String saveRepresentation
    ) {

        static FoodItemEntry failed() {
            return new FoodItemEntry(
                    null,
                    ITEM_ID,
                    "",
                    -1,
                    false,
                    -1,
                    -1.0F,
                    false,
                    -1,
                    false,
                    "",
                    "",
                    -1,
                    List.of(),
                    false,
                    "",
                    ""
            );
        }
    }

    record FoodConsumptionState(
            ConsumptionPhase phase,
            Object playerIdentity,
            Object itemIdentity,
            String captureError,
            String playerClass,
            String playerUuid,
            String playerName,
            String itemId,
            String resultItemId,
            int initialHunger,
            float initialSaturation,
            int initialStackCount,
            int resultHunger,
            float resultSaturation,
            int resultStackCount,
            boolean sameStackInstance
    ) {

        private static final int EXPECTED_INITIAL_HUNGER = 10;
        private static final float EXPECTED_INITIAL_SATURATION = 0.0F;
        private static final int EXPECTED_INITIAL_STACK_COUNT = 2;
        private static final int EXPECTED_RESULT_HUNGER = 13;
        private static final float EXPECTED_RESULT_SATURATION = 12.0F;
        private static final int EXPECTED_RESULT_STACK_COUNT = 1;
        static final String PLAYER_CLASS = ServerPlayerEntity.class.getName();

        static FoodConsumptionState consume(
                MinecraftServer server,
                ConsumptionPhase phase
        ) {
            try {
                Item item = Registries.ITEM.getOrEmpty(ITEM_IDENTIFIER).orElse(null);
                ServerWorld world = server.getOverworld();
                if (item == null || world == null) {
                    return failed(phase, "missing item or overworld");
                }
                GameProfile profile = new GameProfile(phase.uuid(), phase.playerName());
                ServerPlayerEntity player = new ServerPlayerEntity(server, world, profile);
                player.getHungerManager().setFoodLevel(EXPECTED_INITIAL_HUNGER);
                player.getHungerManager().setSaturationLevel(
                        EXPECTED_INITIAL_SATURATION
                );
                player.getHungerManager().setExhaustion(0.0F);
                ItemStack stack = new ItemStack(item, EXPECTED_INITIAL_STACK_COUNT);
                int initialHunger = player.getHungerManager().getFoodLevel();
                float initialSaturation = player.getHungerManager().getSaturationLevel();
                int initialStackCount = stack.getCount();
                ItemStack result = item.finishUsing(stack, world, player);
                return new FoodConsumptionState(
                        phase,
                        player,
                        item,
                        "",
                        player.getClass().getName(),
                        player.getGameProfile().getId().toString(),
                        player.getGameProfile().getName(),
                        registeredId(item),
                        registeredId(result.getItem()),
                        initialHunger,
                        initialSaturation,
                        initialStackCount,
                        player.getHungerManager().getFoodLevel(),
                        player.getHungerManager().getSaturationLevel(),
                        result.getCount(),
                        result == stack
                );
            } catch (RuntimeException exception) {
                return failed(phase, exception.getClass().getName());
            }
        }

        static FoodConsumptionState missing(ConsumptionPhase phase) {
            return failed(phase, "not captured");
        }

        boolean hasExactConsumption() {
            return captureError.isEmpty()
                    && playerIdentity != null
                    && itemIdentity != null
                    && PLAYER_CLASS.equals(playerClass)
                    && phase.uuid().toString().equals(playerUuid)
                    && phase.playerName().equals(playerName)
                    && ITEM_ID.equals(itemId)
                    && ITEM_ID.equals(resultItemId)
                    && initialHunger == EXPECTED_INITIAL_HUNGER
                    && Float.compare(
                            initialSaturation,
                            EXPECTED_INITIAL_SATURATION
                    ) == 0
                    && initialStackCount == EXPECTED_INITIAL_STACK_COUNT
                    && resultHunger == EXPECTED_RESULT_HUNGER
                    && Float.compare(
                            resultSaturation,
                            EXPECTED_RESULT_SATURATION
                    ) == 0
                    && resultStackCount == EXPECTED_RESULT_STACK_COUNT
                    && sameStackInstance;
        }

        boolean isFreshPlayerComparedWith(FoodConsumptionState other) {
            return playerIdentity != null
                    && other.playerIdentity != null
                    && playerIdentity != other.playerIdentity
                    && !playerUuid.equals(other.playerUuid)
                    && !playerName.equals(other.playerName);
        }

        boolean hasSameOutcome(FoodConsumptionState other) {
            return captureError.equals(other.captureError)
                    && playerClass.equals(other.playerClass)
                    && itemIdentity != null
                    && itemIdentity == other.itemIdentity
                    && itemId.equals(other.itemId)
                    && resultItemId.equals(other.resultItemId)
                    && initialHunger == other.initialHunger
                    && Float.compare(initialSaturation, other.initialSaturation) == 0
                    && initialStackCount == other.initialStackCount
                    && resultHunger == other.resultHunger
                    && Float.compare(resultSaturation, other.resultSaturation) == 0
                    && resultStackCount == other.resultStackCount
                    && sameStackInstance == other.sameStackInstance;
        }

        private static FoodConsumptionState failed(
                ConsumptionPhase phase,
                String error
        ) {
            return new FoodConsumptionState(
                    phase,
                    null,
                    null,
                    error,
                    "",
                    phase.uuid().toString(),
                    phase.playerName(),
                    "",
                    "",
                    -1,
                    -1.0F,
                    -1,
                    -1,
                    -1.0F,
                    -1,
                    false
            );
        }

        private static String registeredId(Item item) {
            Identifier identifier = Registries.ITEM.getId(item);
            return identifier == null ? "" : identifier.toString();
        }
    }

    enum ConsumptionPhase {
        SERVER_STARTED(
                "00000000-0000-0000-0000-00000000e214",
                "EtherFoodStart"
        ),
        RELOADED(
                "00000000-0000-0000-0000-00000000e215",
                "EtherFoodReload"
        );

        private final String playerName;
        private final UUID uuid;

        ConsumptionPhase(String uuid, String playerName) {
            this.uuid = UUID.fromString(uuid);
            this.playerName = playerName;
        }

        String playerName() {
            return playerName;
        }

        UUID uuid() {
            return uuid;
        }
    }
}
