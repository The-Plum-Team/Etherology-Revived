package dev.theplumteam.etherology.e2e.server;

import com.mojang.authlib.GameProfile;
import net.minecraft.advancement.Advancement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.AutomaticItemPlacementContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

record ForestLanternProbeState(
        Object blockIdentity,
        Object itemIdentity,
        String captureError,
        String blockId,
        String itemId,
        String blockClass,
        String itemClass,
        boolean blockItemMapsToBlock,
        boolean blockAsItemMatches,
        int itemMaxCount,
        String serializedItemId,
        int serializedItemCount,
        List<String> serializedItemKeys,
        boolean itemNbtRoundTripExact,
        String defaultState,
        List<String> states,
        List<Integer> stateNetworkIds,
        Map<String, String> outlineShapes,
        String properties,
        boolean hoeMineable,
        List<String> peachLogIds,
        LoadedData loadedData
) {

    static final String BLOCK_REGISTRY_ID = "minecraft:block";
    static final String ITEM_REGISTRY_ID = "minecraft:item";
    static final String BLOCK_ID = "etherology:forest_lantern";
    static final String BLOCK_CLASS =
            "ru.feytox.etherology.block.forestLantern.ForestLanternBlock";
    static final String ITEM_CLASS = BlockItem.class.getName();
    static final String PEACH_LOGS_TAG_ID = "etherology:peach_logs";
    static final int EXPECTED_STATE_COUNT = 20;
    static final int EXPECTED_MAX_AGE = 4;
    static final int EXPECTED_ITEM_MAX_COUNT = 64;
    static final List<String> EXPECTED_ITEM_NBT_KEYS = List.of("Count", "id");
    private static final List<Direction> FACINGS = List.of(
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    );
    static final List<String> EXPECTED_STATES = expectedStates();
    static final Map<String, String> EXPECTED_OUTLINE_SHAPES = expectedOutlineShapes();
    static final String EXPECTED_PROPERTIES = BLOCK_CLASS
            + "|hardness=0.2|blast=0.2|grass_sound=true|tool_required=false"
            + "|luminance=8|opaque=true|full_cube=false|transparent=true"
            + "|post_process=true|emissive=true|piston=DESTROY"
            + "|mature_random_ticks=false|bud_random_ticks=true";
    static final String EXPECTED_DEFAULT_STATE = "age=4,facing=north";
    static final long RETAIN_SINGLE_CALLBACK_SEED = 1L;
    static final float RETAIN_FIRST_ROLL = 0.7308782F;
    static final float RETAIN_SECOND_ROLL = 0.100473166F;
    static final long BREAK_SEED = 4096L;
    static final float BREAK_FIRST_ROLL = 0.09789288F;

    private static final Identifier BLOCK_IDENTIFIER = Identifier.of(
            "etherology",
            "forest_lantern"
    );
    static ForestLanternProbeState capture(MinecraftServer server) {
        try {
            Block block = Registries.BLOCK.getOrEmpty(BLOCK_IDENTIFIER).orElse(null);
            Item item = Registries.ITEM.getOrEmpty(BLOCK_IDENTIFIER).orElse(null);
            if (block == null || item == null) {
                return failed("missing block or item");
            }

            BlockItem blockItem = item instanceof BlockItem candidate ? candidate : null;
            BlockState defaultBlockState = block.getDefaultState();
            ItemStack itemStack = new ItemStack(item, item.getMaxCount());
            NbtCompound serializedItem = itemStack.writeNbt(new NbtCompound());
            ItemStack restoredItem = ItemStack.fromNbt(serializedItem.copy());
            List<String> serializedKeys = serializedItem.getKeys()
                    .stream()
                    .sorted()
                    .toList();
            List<BlockState> orderedStates = block.getStateManager().getStates()
                    .stream()
                    .sorted(java.util.Comparator.comparing(
                            ForestLanternProbeState::stateDescription
                    ))
                    .toList();
            List<String> capturedStates = orderedStates.stream()
                    .map(ForestLanternProbeState::stateDescription)
                    .toList();
            List<Integer> stateNetworkIds = orderedStates.stream()
                    .map(Block.STATE_IDS::getRawId)
                    .toList();
            Map<String, String> shapes = new TreeMap<>();
            for (BlockState state : block.getStateManager().getStates()) {
                VoxelShape shape = state.getOutlineShape(
                        EmptyBlockView.INSTANCE,
                        BlockPos.ORIGIN
                );
                shapes.put(stateDescription(state), shapeDescription(shape));
            }
            TagKey<Block> peachLogsTag = TagKey.of(
                    RegistryKeys.BLOCK,
                    Identifier.of("etherology", "peach_logs")
            );
            List<String> peachLogIds = Registries.BLOCK.getIds().stream()
                    .filter(identifier -> Registries.BLOCK.get(identifier)
                            .getDefaultState()
                            .isIn(peachLogsTag))
                    .map(Identifier::toString)
                    .sorted()
                    .toList();
            Identifier blockId = Registries.BLOCK.getId(block);
            Identifier itemId = Registries.ITEM.getId(item);
            return new ForestLanternProbeState(
                    block,
                    item,
                    "",
                    identifierString(blockId),
                    identifierString(itemId),
                    block.getClass().getName(),
                    item.getClass().getName(),
                    blockItem != null && blockItem.getBlock() == block,
                    Block.getBlockFromItem(item) == block && block.asItem() == item,
                    item.getMaxCount(),
                    serializedItem.getString("id"),
                    Byte.toUnsignedInt(serializedItem.getByte("Count")),
                    serializedKeys,
                    restoredItem.getItem() == item
                            && restoredItem.getCount() == item.getMaxCount(),
                    stateDescription(defaultBlockState),
                    capturedStates,
                    stateNetworkIds,
                    Collections.unmodifiableMap(shapes),
                    propertyDescription(defaultBlockState, block),
                    defaultBlockState.isIn(BlockTags.HOE_MINEABLE),
                    peachLogIds,
                    LoadedData.capture(server, block, item)
            );
        } catch (RuntimeException exception) {
            return failed(exception.getClass().getName());
        }
    }

    static ForestLanternProbeState missing() {
        return failed("not captured");
    }

    boolean hasExactRegistry() {
        return blockIdentity != null
                && itemIdentity != null
                && BLOCK_ID.equals(blockId)
                && BLOCK_ID.equals(itemId)
                && BLOCK_CLASS.equals(blockClass)
                && ITEM_CLASS.equals(itemClass)
                && blockItemMapsToBlock
                && blockAsItemMatches
                && itemMaxCount == EXPECTED_ITEM_MAX_COUNT
                && BLOCK_ID.equals(serializedItemId)
                && serializedItemCount == EXPECTED_ITEM_MAX_COUNT
                && EXPECTED_ITEM_NBT_KEYS.equals(serializedItemKeys)
                && itemNbtRoundTripExact;
    }

    boolean hasExactStatesAndProperties() {
        return EXPECTED_DEFAULT_STATE.equals(defaultState)
                && EXPECTED_STATES.equals(states)
                && hasExactStateNetworkIds()
                && EXPECTED_OUTLINE_SHAPES.equals(outlineShapes)
                && EXPECTED_PROPERTIES.equals(properties);
    }

    boolean hasExactStateNetworkIds() {
        return stateNetworkIds.size() == EXPECTED_STATE_COUNT
                && stateNetworkIds.stream().allMatch(rawId -> rawId >= 0)
                && new HashSet<>(stateNetworkIds).size() == EXPECTED_STATE_COUNT;
    }

    boolean hasExactTags() {
        return hoeMineable && peachLogIds.isEmpty();
    }

    boolean hasExactContract() {
        return captureError.isEmpty()
                && hasExactRegistry()
                && hasExactStatesAndProperties()
                && hasExactTags()
                && loadedData.hasExactContract();
    }

    boolean sameStateAtServerStarted(ForestLanternProbeState other) {
        return hasSameRegistry(other)
                && hasSameStatesAndProperties(other)
                && hasSameTags(other)
                && loadedData.sameOutcome(other.loadedData)
                && loadedData.sameInstances(other.loadedData);
    }

    boolean hasSameRegistry(ForestLanternProbeState other) {
        return captureError.equals(other.captureError)
                && blockIdentity != null
                && blockIdentity == other.blockIdentity
                && itemIdentity != null
                && itemIdentity == other.itemIdentity
                && blockId.equals(other.blockId)
                && itemId.equals(other.itemId)
                && blockClass.equals(other.blockClass)
                && itemClass.equals(other.itemClass)
                && blockItemMapsToBlock == other.blockItemMapsToBlock
                && blockAsItemMatches == other.blockAsItemMatches
                && itemMaxCount == other.itemMaxCount
                && serializedItemId.equals(other.serializedItemId)
                && serializedItemCount == other.serializedItemCount
                && serializedItemKeys.equals(other.serializedItemKeys)
                && itemNbtRoundTripExact == other.itemNbtRoundTripExact;
    }

    boolean hasSameStatesAndProperties(ForestLanternProbeState other) {
        return defaultState.equals(other.defaultState)
                && states.equals(other.states)
                && stateNetworkIds.equals(other.stateNetworkIds)
                && outlineShapes.equals(other.outlineShapes)
                && properties.equals(other.properties);
    }

    boolean hasSameTags(ForestLanternProbeState other) {
        return hoeMineable == other.hoeMineable
                && peachLogIds.equals(other.peachLogIds);
    }

    boolean hasReloadedDataOutcome(ForestLanternProbeState other) {
        return loadedData.sameOutcome(other.loadedData);
    }

    boolean hasFreshReloadedData(ForestLanternProbeState other) {
        return loadedData.hasFreshInstances(other.loadedData);
    }

    String canonicalStates() {
        return String.join(",", states);
    }

    String canonicalOutlineShapes() {
        return canonicalStringMap(outlineShapes);
    }

    static String expectedCanonicalStates() {
        return String.join(",", EXPECTED_STATES);
    }

    static String expectedCanonicalOutlineShapes() {
        return canonicalStringMap(EXPECTED_OUTLINE_SHAPES);
    }

    static WorldMechanics exerciseWorld(MinecraftServer server, MechanicsPhase phase) {
        try {
            Block block = Registries.BLOCK.getOrEmpty(BLOCK_IDENTIFIER).orElse(null);
            Item item = Registries.ITEM.getOrEmpty(BLOCK_IDENTIFIER).orElse(null);
            if (block == null || !(item instanceof BlockItem blockItem)) {
                return WorldMechanics.failed(phase, "missing block or block item");
            }
            ServerWorld world = server.getOverworld();
            if (world == null) {
                return WorldMechanics.failed(phase, "missing overworld");
            }

            PlacementResult placement = PlacementResult.capture(
                    world,
                    block,
                    blockItem,
                    phase
            );
            ShearsResult shears = ShearsResult.capture(server, world, block, phase);
            JumpResult retainJump = JumpResult.capture(
                    server,
                    world,
                    block,
                    item,
                    phase,
                    JumpKind.RETAIN
            );
            JumpResult breakJump = JumpResult.capture(
                    server,
                    world,
                    block,
                    item,
                    phase,
                    JumpKind.BREAK
            );
            return new WorldMechanics(
                    phase,
                    "",
                    placement,
                    shears,
                    retainJump,
                    breakJump
            );
        } catch (RuntimeException exception) {
            return WorldMechanics.failed(phase, exception.getClass().getName());
        }
    }

    private static ForestLanternProbeState failed(String error) {
        return new ForestLanternProbeState(
                null,
                null,
                error,
                "",
                "",
                "",
                "",
                false,
                false,
                -1,
                "",
                -1,
                List.of(),
                false,
                "",
                List.of(),
                List.of(),
                Map.of(),
                "",
                false,
                List.of(),
                LoadedData.missing()
        );
    }

    private static String propertyDescription(BlockState state, Block block) {
        BlockState budState = stateWith(block, 0, Direction.NORTH);
        return block.getClass().getName()
                + "|hardness=" + Float.toString(
                        state.getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)
                )
                + "|blast=" + Float.toString(block.getBlastResistance())
                + "|grass_sound=" + (state.getSoundGroup() == BlockSoundGroup.GRASS)
                + "|tool_required=" + state.isToolRequired()
                + "|luminance=" + state.getLuminance()
                + "|opaque=" + state.isOpaque()
                + "|full_cube=" + state.isFullCube(
                        EmptyBlockView.INSTANCE,
                        BlockPos.ORIGIN
                )
                + "|transparent=" + state.isTransparent(
                        EmptyBlockView.INSTANCE,
                        BlockPos.ORIGIN
                )
                + "|post_process=" + state.shouldPostProcess(
                        EmptyBlockView.INSTANCE,
                        BlockPos.ORIGIN
                )
                + "|emissive=" + state.hasEmissiveLighting(
                        EmptyBlockView.INSTANCE,
                        BlockPos.ORIGIN
                )
                + "|piston=" + state.getPistonBehavior().name()
                + "|mature_random_ticks=" + state.hasRandomTicks()
                + "|bud_random_ticks=" + budState.hasRandomTicks();
    }

    private static BlockState stateWith(Block block, int age, Direction facing) {
        IntProperty ageProperty = (IntProperty) block.getStateManager().getProperty("age");
        DirectionProperty facingProperty = (DirectionProperty) block
                .getStateManager()
                .getProperty("facing");
        if (ageProperty == null || facingProperty == null) {
            throw new IllegalStateException("Forest Lantern properties are missing");
        }
        return block.getDefaultState()
                .with(ageProperty, age)
                .with(facingProperty, facing);
    }

    private static String stateDescription(BlockState state) {
        String fullDescription = BlockArgumentParser.stringifyBlockState(state);
        int propertiesStart = fullDescription.indexOf('[');
        if (propertiesStart < 0 || !fullDescription.endsWith("]")) {
            return fullDescription;
        }
        return fullDescription.substring(propertiesStart + 1, fullDescription.length() - 1);
    }

    private static String shapeDescription(VoxelShape shape) {
        Box bounds = shape.getBoundingBox();
        return Double.toString(bounds.minX)
                + "," + Double.toString(bounds.minY)
                + "," + Double.toString(bounds.minZ)
                + "," + Double.toString(bounds.maxX)
                + "," + Double.toString(bounds.maxY)
                + "," + Double.toString(bounds.maxZ);
    }

    private static String identifierString(Identifier identifier) {
        return identifier == null ? "" : identifier.toString();
    }

    static String canonicalStringMap(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    private static List<String> expectedStates() {
        List<String> states = new ArrayList<>();
        List<String> facings = FACINGS.stream()
                .map(Direction::getName)
                .sorted()
                .toList();
        for (int age = 0; age <= EXPECTED_MAX_AGE; age++) {
            for (String facing : facings) {
                states.add("age=" + age + ",facing=" + facing);
            }
        }
        return List.copyOf(states);
    }

    private static Map<String, String> expectedOutlineShapes() {
        Map<String, String> shapes = new TreeMap<>();
        addShapes(shapes, "north", List.of(
                "0.25,0.25,0.8125,0.75,0.75,1.0",
                "0.34375,0.3125,0.6875,0.65625,0.6875,1.0",
                "0.3125,0.3125,0.5625,0.6875,0.75,1.0",
                "0.25,0.3125,0.375,0.75,0.875,1.0",
                "0.125,0.25,0.25,0.875,1.0,1.0"
        ));
        addShapes(shapes, "south", List.of(
                "0.25,0.25,0.0,0.75,0.75,0.1875",
                "0.34375,0.3125,0.0,0.65625,0.6875,0.3125",
                "0.3125,0.3125,0.0,0.6875,0.75,0.4375",
                "0.25,0.3125,0.0,0.75,0.875,0.625",
                "0.125,0.25,0.0,0.875,1.0,0.75"
        ));
        addShapes(shapes, "west", List.of(
                "0.8125,0.25,0.25,1.0,0.75,0.75",
                "0.6875,0.3125,0.34375,1.0,0.6875,0.65625",
                "0.5625,0.3125,0.3125,1.0,0.75,0.6875",
                "0.375,0.3125,0.25,1.0,0.875,0.75",
                "0.25,0.25,0.125,1.0,1.0,0.875"
        ));
        addShapes(shapes, "east", List.of(
                "0.0,0.25,0.25,0.1875,0.75,0.75",
                "0.0,0.3125,0.34375,0.3125,0.6875,0.65625",
                "0.0,0.3125,0.3125,0.4375,0.75,0.6875",
                "0.0,0.3125,0.25,0.625,0.875,0.75",
                "0.0,0.25,0.125,0.75,1.0,0.875"
        ));
        return Collections.unmodifiableMap(shapes);
    }

    private static void addShapes(
            Map<String, String> shapes,
            String facing,
            List<String> ageShapes
    ) {
        for (int age = 0; age < ageShapes.size(); age++) {
            shapes.put("age=" + age + ",facing=" + facing, ageShapes.get(age));
        }
    }

    record LoadedData(
            Object lootTableIdentity,
            Map<String, Object> recipeIdentities,
            Map<String, Object> advancementIdentities,
            String captureError,
            String lootTableId,
            Map<String, String> lootByAge,
            List<String> recipeIds,
            Map<String, String> recipes,
            List<String> advancementIds,
            Map<String, String> advancements,
            boolean recipeMatchesAndCraftsExact
    ) {

        static final List<String> EXPECTED_RECIPE_IDS = List.of(
                "etherology:forest_lantern_crumb",
                "etherology:forest_lantern_crumb_from_campfire",
                "etherology:forest_lantern_crumb_from_smoking",
                "etherology:leather"
        );
        static final List<String> EXPECTED_ADVANCEMENT_IDS = List.of(
                "etherology:recipes/food/forest_lantern_crumb",
                "etherology:recipes/food/forest_lantern_crumb_from_campfire",
                "etherology:recipes/food/forest_lantern_crumb_from_smoking",
                "etherology:recipes/misc/leather"
        );
        static final Map<String, String> EXPECTED_LOOT_BY_AGE = expectedLootByAge();
        static final Map<String, String> EXPECTED_RECIPES = expectedRecipes();
        static final Map<String, String> EXPECTED_ADVANCEMENTS =
                expectedAdvancements();

        static LoadedData capture(MinecraftServer server, Block block, Item item) {
            if (server == null) {
                return missing();
            }
            try {
                Identifier lootTableId = block.getLootTableId();
                Object lootTableIdentity = server.getLootManager().getLootTable(lootTableId);
                Map<String, String> lootByAge = new TreeMap<>();
                for (int age = 0; age <= EXPECTED_MAX_AGE; age++) {
                    List<ItemStack> drops = Block.getDroppedStacks(
                            stateWith(block, age, Direction.NORTH),
                            server.getOverworld(),
                            new BlockPos(-24, 210, age),
                            null,
                            null,
                            ItemStack.EMPTY
                    );
                    lootByAge.put(Integer.toString(age), stackDescription(drops));
                }

                List<String> recipeIds = server.getRecipeManager()
                        .keys()
                        .filter(LoadedData::isForestLanternRecipeId)
                        .map(Identifier::toString)
                        .sorted()
                        .toList();
                Map<String, Object> recipeIdentities = new LinkedHashMap<>();
                Map<String, String> recipes = new TreeMap<>();
                boolean recipesMatchAndCraft = true;
                for (String id : recipeIds) {
                    Identifier identifier = Identifier.parse(id);
                    Recipe<?> recipe = server.getRecipeManager().get(identifier).orElse(null);
                    recipeIdentities.put(id, recipe);
                    RecipeCapture capture = captureRecipe(server, recipe, item);
                    recipes.put(id, capture.description());
                    recipesMatchAndCraft &= capture.matchesAndCrafts();
                }

                List<String> advancementIds = server.getAdvancementLoader()
                        .getAdvancements()
                        .stream()
                        .map(Advancement::getId)
                        .filter(LoadedData::isForestLanternAdvancementId)
                        .map(Identifier::toString)
                        .sorted()
                        .toList();
                Map<String, Object> advancementIdentities = new LinkedHashMap<>();
                Map<String, String> advancements = new TreeMap<>();
                for (String id : advancementIds) {
                    Identifier identifier = Identifier.parse(id);
                    Advancement advancement = server.getAdvancementLoader().get(identifier);
                    advancementIdentities.put(id, advancement);
                    advancements.put(id, advancementDescription(advancement));
                }
                return new LoadedData(
                        lootTableIdentity,
                        Collections.unmodifiableMap(recipeIdentities),
                        Collections.unmodifiableMap(advancementIdentities),
                        "",
                        lootTableId.toString(),
                        Collections.unmodifiableMap(lootByAge),
                        recipeIds,
                        Collections.unmodifiableMap(recipes),
                        advancementIds,
                        Collections.unmodifiableMap(advancements),
                        recipesMatchAndCraft
                );
            } catch (RuntimeException exception) {
                return failed(exception.getClass().getName());
            }
        }

        static LoadedData missing() {
            return failed("not captured");
        }

        boolean hasExactLoot() {
            return lootTableIdentity != null
                    && "etherology:blocks/forest_lantern".equals(lootTableId)
                    && EXPECTED_LOOT_BY_AGE.equals(lootByAge);
        }

        boolean hasExactRecipes() {
            return EXPECTED_RECIPE_IDS.equals(recipeIds)
                    && EXPECTED_RECIPES.equals(recipes)
                    && recipeMatchesAndCraftsExact
                    && recipeIdentities.size() == EXPECTED_RECIPE_IDS.size()
                    && recipeIdentities.values().stream().allMatch(value -> value != null);
        }

        boolean hasExactAdvancements() {
            return EXPECTED_ADVANCEMENT_IDS.equals(advancementIds)
                    && EXPECTED_ADVANCEMENTS.equals(advancements)
                    && advancementIdentities.size() == EXPECTED_ADVANCEMENT_IDS.size()
                    && advancementIdentities.values().stream().allMatch(value -> value != null);
        }

        boolean hasExactContract() {
            return captureError.isEmpty()
                    && hasExactLoot()
                    && hasExactRecipes()
                    && hasExactAdvancements();
        }

        boolean sameOutcome(LoadedData other) {
            return captureError.equals(other.captureError)
                    && lootTableId.equals(other.lootTableId)
                    && lootByAge.equals(other.lootByAge)
                    && recipeIds.equals(other.recipeIds)
                    && recipes.equals(other.recipes)
                    && advancementIds.equals(other.advancementIds)
                    && advancements.equals(other.advancements)
                    && recipeMatchesAndCraftsExact == other.recipeMatchesAndCraftsExact;
        }

        boolean sameInstances(LoadedData other) {
            return lootTableIdentity != null
                    && lootTableIdentity == other.lootTableIdentity
                    && sameIdentityMap(recipeIdentities, other.recipeIdentities, true)
                    && sameIdentityMap(
                            advancementIdentities,
                            other.advancementIdentities,
                            true
                    );
        }

        boolean hasFreshInstances(LoadedData other) {
            return lootTableIdentity != null
                    && other.lootTableIdentity != null
                    && lootTableIdentity != other.lootTableIdentity
                    && sameIdentityMap(recipeIdentities, other.recipeIdentities, false)
                    && sameIdentityMap(
                            advancementIdentities,
                            other.advancementIdentities,
                            false
                    );
        }

        private static LoadedData failed(String error) {
            return new LoadedData(
                    null,
                    Map.of(),
                    Map.of(),
                    error,
                    "",
                    Map.of(),
                    List.of(),
                    Map.of(),
                    List.of(),
                    Map.of(),
                    false
            );
        }

        private static boolean sameIdentityMap(
                Map<String, Object> first,
                Map<String, Object> second,
                boolean requireSame
        ) {
            if (!first.keySet().equals(second.keySet()) || first.isEmpty()) {
                return false;
            }
            return first.keySet().stream().allMatch(key -> {
                Object firstValue = first.get(key);
                Object secondValue = second.get(key);
                return firstValue != null
                        && secondValue != null
                        && (requireSame
                                ? firstValue == secondValue
                                : firstValue != secondValue);
            });
        }

        private static RecipeCapture captureRecipe(
                MinecraftServer server,
                Recipe<?> recipe,
                Item forestLanternItem
        ) {
            if (recipe == null) {
                return new RecipeCapture("", false);
            }
            Identifier typeId = Registries.RECIPE_TYPE.getId(recipe.getType());
            Identifier serializerId = Registries.RECIPE_SERIALIZER.getId(
                    recipe.getSerializer()
            );
            String recipeId = recipe.getId().toString();
            ItemStack declaredOutput = recipe.getOutput(server.getRegistryManager());
            String common = recipeId
                    + "|class=" + recipe.getClass().getName()
                    + "|type=" + identifierString(typeId)
                    + "|serializer=" + identifierString(serializerId)
                    + "|output=" + stackDescription(List.of(declaredOutput))
                    + "|group=" + recipe.getGroup()
                    + "|notification=" + recipe.showNotification();
            if (recipe instanceof AbstractCookingRecipe cookingRecipe) {
                SimpleInventory inventory = new SimpleInventory(
                        new ItemStack(forestLanternItem)
                );
                boolean inputExact = cookingRecipe.getIngredients().size() == 1
                        && cookingRecipe.getIngredients().get(0).test(
                                new ItemStack(forestLanternItem)
                        )
                        && !cookingRecipe.getIngredients().get(0).test(
                                new ItemStack(Items.STONE)
                        );
                boolean matches = cookingRecipe.matches(inventory, server.getOverworld());
                ItemStack crafted = cookingRecipe.craft(
                        inventory,
                        server.getRegistryManager()
                );
                String description = common
                        + "|input_exact=" + inputExact
                        + "|category=" + cookingRecipe.getCategory().asString()
                        + "|cook_time=" + cookingRecipe.getCookTime()
                        + "|experience=" + Float.toString(cookingRecipe.getExperience())
                        + "|matches=" + matches
                        + "|crafted=" + stackDescription(List.of(crafted));
                return new RecipeCapture(
                        description,
                        inputExact && matches && sameStack(declaredOutput, crafted)
                );
            }
            if (recipe instanceof ShapedRecipe shapedRecipe) {
                CraftingInventory inventory = leatherInventory(forestLanternItem);
                boolean matches = shapedRecipe.matches(inventory, server.getOverworld());
                ItemStack crafted = shapedRecipe.craft(
                        inventory,
                        server.getRegistryManager()
                );
                String ingredientPattern = ingredientPattern(
                        shapedRecipe,
                        forestLanternItem
                );
                boolean inputExact = "string,forest_lantern,string,empty,"
                        .concat("forest_lantern,empty,string,forest_lantern,string")
                        .equals(ingredientPattern);
                String description = common
                        + "|input=" + ingredientPattern
                        + "|category=" + shapedRecipe.getCategory().asString()
                        + "|width=" + shapedRecipe.getWidth()
                        + "|height=" + shapedRecipe.getHeight()
                        + "|matches=" + matches
                        + "|crafted=" + stackDescription(List.of(crafted));
                return new RecipeCapture(
                        description,
                        inputExact
                                && shapedRecipe.getWidth() == 3
                                && shapedRecipe.getHeight() == 3
                                && matches
                                && sameStack(declaredOutput, crafted)
                );
            }
            return new RecipeCapture(common, false);
        }

        private static boolean isForestLanternRecipeId(Identifier identifier) {
            if (!"etherology".equals(identifier.getNamespace())) return false;
            String path = identifier.getPath();
            return path.startsWith("forest_lantern_crumb")
                    || path.startsWith("leather");
        }

        private static boolean isForestLanternAdvancementId(Identifier identifier) {
            if (!"etherology".equals(identifier.getNamespace())) return false;
            String path = identifier.getPath();
            return path.startsWith("recipes/food/forest_lantern_crumb")
                    || path.startsWith("recipes/misc/leather");
        }

        private static CraftingInventory leatherInventory(Item forestLanternItem) {
            DefaultedList<ItemStack> stacks = DefaultedList.ofSize(9, ItemStack.EMPTY);
            stacks.set(0, new ItemStack(Items.STRING));
            stacks.set(1, new ItemStack(forestLanternItem));
            stacks.set(2, new ItemStack(Items.STRING));
            stacks.set(4, new ItemStack(forestLanternItem));
            stacks.set(6, new ItemStack(Items.STRING));
            stacks.set(7, new ItemStack(forestLanternItem));
            stacks.set(8, new ItemStack(Items.STRING));
            return new CraftingInventory(null, 3, 3, stacks);
        }

        private static String ingredientPattern(
                ShapedRecipe recipe,
                Item forestLanternItem
        ) {
            return recipe.getIngredients().stream().map(ingredient -> {
                if (ingredient.isEmpty()) return "empty";
                if (ingredient.test(new ItemStack(forestLanternItem))) {
                    return "forest_lantern";
                }
                if (ingredient.test(new ItemStack(Items.STRING))) return "string";
                return "other";
            }).collect(Collectors.joining(","));
        }

        private static String advancementDescription(Advancement advancement) {
            if (advancement == null) return "";
            List<String> criteria = advancement.getCriteria().keySet()
                    .stream()
                    .sorted()
                    .toList();
            List<String> requirements = new ArrayList<>();
            for (String[] requirement : advancement.getRequirements()) {
                requirements.add(java.util.Arrays.stream(requirement)
                        .sorted()
                        .collect(Collectors.joining("+")));
            }
            requirements.sort(String::compareTo);
            List<String> rewardRecipes = java.util.Arrays.stream(
                            advancement.getRewards().getRecipes()
                    )
                    .map(Identifier::toString)
                    .sorted()
                    .toList();
            return advancement.getId()
                    + "|parent=" + (advancement.getParent() == null
                            ? ""
                            : advancement.getParent().getId())
                    + "|criteria=" + String.join("+", criteria)
                    + "|requirements=" + String.join(";", requirements)
                    + "|reward_recipes=" + String.join("+", rewardRecipes)
                    + "|telemetry=" + advancement.sendsTelemetryEvent();
        }

        private static boolean sameStack(ItemStack first, ItemStack second) {
            return first.getItem() == second.getItem()
                    && first.getCount() == second.getCount();
        }

        private static Map<String, String> expectedRecipes() {
            Map<String, String> recipes = new TreeMap<>();
            recipes.put(
                    "etherology:forest_lantern_crumb",
                    cookingRecipeDescription(
                            "etherology:forest_lantern_crumb",
                            "net.minecraft.recipe.SmeltingRecipe",
                            "minecraft:smelting",
                            200
                    )
            );
            recipes.put(
                    "etherology:forest_lantern_crumb_from_campfire",
                    cookingRecipeDescription(
                            "etherology:forest_lantern_crumb_from_campfire",
                            "net.minecraft.recipe.CampfireCookingRecipe",
                            "minecraft:campfire_cooking",
                            600
                    )
            );
            recipes.put(
                    "etherology:forest_lantern_crumb_from_smoking",
                    cookingRecipeDescription(
                            "etherology:forest_lantern_crumb_from_smoking",
                            "net.minecraft.recipe.SmokingRecipe",
                            "minecraft:smoking",
                            100
                    )
            );
            recipes.put(
                    "etherology:leather",
                    "etherology:leather|class=net.minecraft.recipe.ShapedRecipe"
                            + "|type=minecraft:crafting|serializer=minecraft:crafting_shaped"
                            + "|output=minecraft:leatherx1|group=|notification=true"
                            + "|input=string,forest_lantern,string,empty,"
                            + "forest_lantern,empty,string,forest_lantern,string"
                            + "|category=misc|width=3|height=3|matches=true"
                            + "|crafted=minecraft:leatherx1"
            );
            return Collections.unmodifiableMap(recipes);
        }

        private static Map<String, String> expectedLootByAge() {
            Map<String, String> lootByAge = new TreeMap<>();
            lootByAge.put("0", "");
            lootByAge.put("1", "");
            lootByAge.put("2", "");
            lootByAge.put("3", "");
            lootByAge.put("4", BLOCK_ID + "x1");
            return Collections.unmodifiableMap(lootByAge);
        }

        private static String cookingRecipeDescription(
                String id,
                String recipeClass,
                String type,
                int cookTime
        ) {
            return id + "|class=" + recipeClass
                    + "|type=" + type
                    + "|serializer=" + type
                    + "|output=etherology:forest_lantern_crumbx1"
                    + "|group=|notification=true|input_exact=true|category=food"
                    + "|cook_time=" + cookTime + "|experience=0.35|matches=true"
                    + "|crafted=etherology:forest_lantern_crumbx1";
        }

        private static Map<String, String> expectedAdvancements() {
            Map<String, String> advancements = new TreeMap<>();
            for (String advancementId : EXPECTED_ADVANCEMENT_IDS) {
                String recipeId = advancementId
                        .replace("etherology:recipes/food/", "etherology:")
                        .replace("etherology:recipes/misc/", "etherology:");
                advancements.put(
                        advancementId,
                        advancementId
                                + "|parent=minecraft:recipes/root"
                                + "|criteria=has_forest_lantern+has_the_recipe"
                                + "|requirements=has_forest_lantern+has_the_recipe"
                                + "|reward_recipes=" + recipeId
                                + "|telemetry=false"
                );
            }
            return Collections.unmodifiableMap(advancements);
        }
    }

    record RecipeCapture(String description, boolean matchesAndCrafts) {
    }

    record PlacementResult(
            String captureError,
            Map<String, String> placements,
            boolean exact,
            boolean supportsRemoved
    ) {

        static final Map<String, String> EXPECTED_PLACEMENTS = expectedPlacements();

        private static PlacementResult capture(
                ServerWorld world,
                Block block,
                BlockItem blockItem,
                MechanicsPhase phase
        ) {
            Map<String, String> placements = new TreeMap<>();
            boolean exact = true;
            boolean supportsRemoved = true;
            int index = 0;
            for (Direction facing : FACINGS) {
                BlockPos target = phase.placementPosition(
                        world.getSpawnPos(),
                        index
                );
                BlockPos support = target.offset(facing.getOpposite());
                world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                ItemStack stack = new ItemStack(blockItem);
                Direction contextFacing = facing.getOpposite();
                ActionResult action = blockItem.place(new AutomaticItemPlacementContext(
                        world,
                        target,
                        contextFacing,
                        stack,
                        contextFacing
                ));
                BlockState placedState = world.getBlockState(target);
                String actualState = stateDescription(placedState);
                boolean placedExact = action == ActionResult.CONSUME
                        && placedState.isOf(block)
                        && actualState.equals(
                                "age=4,facing=" + facing.getName()
                        )
                        && stack.isEmpty();
                world.setBlockState(
                        support,
                        Blocks.AIR.getDefaultState(),
                        Block.NOTIFY_ALL
                );
                boolean removed = world.getBlockState(target).isAir();
                placements.put(
                        facing.getName(),
                        action.name() + "|" + actualState
                                + "|stack=" + stack.getCount()
                                + "|support_removed=" + removed
                );
                exact &= placedExact;
                supportsRemoved &= removed;
                world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                index++;
            }
            return new PlacementResult(
                    "",
                    Collections.unmodifiableMap(placements),
                    exact,
                    supportsRemoved
            );
        }

        static PlacementResult missing() {
            return new PlacementResult("not captured", Map.of(), false, false);
        }

        boolean sameOutcome(PlacementResult other) {
            return captureError.equals(other.captureError)
                    && placements.equals(other.placements)
                    && exact == other.exact
                    && supportsRemoved == other.supportsRemoved;
        }

        private static Map<String, String> expectedPlacements() {
            Map<String, String> placements = new TreeMap<>();
            for (Direction facing : FACINGS) {
                placements.put(
                        facing.getName(),
                        "CONSUME|age=4,facing=" + facing.getName()
                                + "|stack=0|support_removed=true"
                );
            }
            return Collections.unmodifiableMap(placements);
        }
    }

    record ShearsResult(
            Object playerIdentity,
            String captureError,
            String playerUuid,
            String playerName,
            String toolId,
            boolean onGround,
            boolean canHarvest,
            Map<String, String> speeds,
            Map<String, String> deltas,
            boolean exact
    ) {

        static final Map<String, String> EXPECTED_SPEEDS = expectedSpeeds();
        static final Map<String, String> EXPECTED_DELTAS = expectedDeltas();

        private static ShearsResult capture(
                MinecraftServer server,
                ServerWorld world,
                Block block,
                MechanicsPhase phase
        ) {
            ServerPlayerEntity player = phase.createPlayer(server, world, PlayerRole.SHEARS);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
            player.setOnGround(true);
            BlockPos position = phase.shearsPosition(world.getSpawnPos());
            boolean statesPlaced = true;
            for (Direction facing : FACINGS) {
                BlockPos support = position.offset(facing.getOpposite());
                statesPlaced &= world.setBlockState(
                        support,
                        Blocks.STONE.getDefaultState(),
                        Block.NOTIFY_ALL
                );
            }
            Map<String, String> speeds = new TreeMap<>();
            Map<String, String> deltas = new TreeMap<>();
            boolean canHarvest = true;
            for (int age = 0; age <= EXPECTED_MAX_AGE; age++) {
                for (Direction facing : FACINGS) {
                    BlockState state = stateWith(block, age, facing);
                    statesPlaced &= world.setBlockState(position, state, Block.NOTIFY_ALL)
                            && world.getBlockState(position).equals(state);
                    canHarvest &= player.canHarvest(state);
                    String stateKey = stateDescription(state);
                    speeds.put(
                            stateKey,
                            Float.toString(player.getBlockBreakingSpeed(state))
                    );
                    deltas.put(
                            stateKey,
                            Float.toString(
                                    state.calcBlockBreakingDelta(player, world, position)
                            )
                    );
                }
            }
            world.setBlockState(position, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            for (Direction facing : FACINGS) {
                world.setBlockState(
                        position.offset(facing.getOpposite()),
                        Blocks.AIR.getDefaultState(),
                        Block.NOTIFY_ALL
                );
            }
            Identifier toolId = Registries.ITEM.getId(player.getMainHandStack().getItem());
            return new ShearsResult(
                    player,
                    "",
                    player.getUuid().toString(),
                    player.getGameProfile().getName(),
                    identifierString(toolId),
                    player.isOnGround(),
                    canHarvest,
                    Collections.unmodifiableMap(speeds),
                    Collections.unmodifiableMap(deltas),
                    EXPECTED_SPEEDS.equals(speeds)
                            && EXPECTED_DELTAS.equals(deltas)
                            && statesPlaced
                            && player.isOnGround()
                            && canHarvest
                            && Items.SHEARS == player.getMainHandStack().getItem()
            );
        }

        static ShearsResult missing() {
            return new ShearsResult(
                    null,
                    "not captured",
                    "",
                    "",
                    "",
                    false,
                    false,
                    Map.of(),
                    Map.of(),
                    false
            );
        }

        boolean sameOutcome(ShearsResult other) {
            return captureError.equals(other.captureError)
                    && toolId.equals(other.toolId)
                    && onGround == other.onGround
                    && canHarvest == other.canHarvest
                    && speeds.equals(other.speeds)
                    && deltas.equals(other.deltas)
                    && exact == other.exact;
        }

        boolean freshPlayer(ShearsResult other) {
            return playerIdentity != null
                    && other.playerIdentity != null
                    && playerIdentity != other.playerIdentity
                    && !playerUuid.equals(other.playerUuid)
                    && !playerName.equals(other.playerName);
        }

        private static Map<String, String> expectedSpeeds() {
            Map<String, String> speeds = new TreeMap<>();
            for (String state : EXPECTED_STATES) {
                speeds.put(state, Float.toString(15.0F));
            }
            return Collections.unmodifiableMap(speeds);
        }

        private static Map<String, String> expectedDeltas() {
            Map<String, String> deltas = new TreeMap<>();
            for (String state : EXPECTED_STATES) {
                deltas.put(
                        state,
                        state.startsWith("age=0,") ? "1.0" : "2.5"
                );
            }
            return Collections.unmodifiableMap(deltas);
        }
    }

    record JumpResult(
            Object playerIdentity,
            String captureError,
            String playerUuid,
            String playerName,
            JumpKind kind,
            long seed,
            float predictedFirstRoll,
            float predictedSecondRoll,
            float nextRollAfterJump,
            boolean steppingPositionExact,
            boolean blockRemoved,
            int newItemEntityCount,
            List<String> newDrops,
            boolean exact
    ) {

        private static JumpResult capture(
                MinecraftServer server,
                ServerWorld world,
                Block block,
                Item item,
                MechanicsPhase phase,
                JumpKind kind
        ) {
            PlayerRole role = kind == JumpKind.RETAIN
                    ? PlayerRole.RETAIN
                    : PlayerRole.BREAK;
            ServerPlayerEntity player = phase.createPlayer(server, world, role);
            BlockPos floor = phase.jumpFloorPosition(world.getSpawnPos(), kind);
            world.setBlockState(floor, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            player.refreshPositionAndAngles(
                    floor.getX() + 0.5,
                    floor.getY() + 1.0,
                    floor.getZ() + 0.5,
                    0.0F,
                    0.0F
            );
            player.setOnGround(true);
            BlockPos steppingPosition = player.getSteppingPos();
            BlockPos support = steppingPosition.south();
            world.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(
                    steppingPosition,
                    stateWith(block, EXPECTED_MAX_AGE, Direction.NORTH),
                    Block.NOTIFY_ALL
            );
            boolean steppingExact = steppingPosition.equals(player.getSteppingPos())
                    && world.getBlockState(steppingPosition).isOf(block);
            Box dropBox = new Box(steppingPosition).expand(2.0);
            Set<UUID> existingItemIds = new HashSet<>();
            world.getEntitiesByClass(ItemEntity.class, dropBox, entity -> true)
                    .forEach(entity -> existingItemIds.add(entity.getUuid()));

            long seed = kind == JumpKind.RETAIN
                    ? RETAIN_SINGLE_CALLBACK_SEED
                    : BREAK_SEED;
            Random prediction = Random.create(seed);
            float predictedFirst = prediction.nextFloat();
            float predictedSecond = prediction.nextFloat();
            world.getRandom().setSeed(seed);
            player.jump();
            float nextRoll = kind == JumpKind.RETAIN
                    ? world.getRandom().nextFloat()
                    : Float.NaN;

            List<ItemEntity> newItemEntities = world.getEntitiesByClass(
                    ItemEntity.class,
                    dropBox,
                    entity -> !existingItemIds.contains(entity.getUuid())
            );
            List<String> drops = newItemEntities.stream()
                    .map(ItemEntity::getStack)
                    .map(stack -> stackDescription(List.of(stack)))
                    .sorted()
                    .toList();
            boolean removed = world.getBlockState(steppingPosition).isAir();
            boolean exact = kind == JumpKind.RETAIN
                    ? !removed
                            && newItemEntities.isEmpty()
                            && Float.compare(nextRoll, RETAIN_SECOND_ROLL) == 0
                    : removed
                            && newItemEntities.size() == 1
                            && newItemEntities.get(0).getStack().isOf(item)
                            && newItemEntities.get(0).getStack().getCount() == 1;
            world.setBlockState(
                    steppingPosition,
                    Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_ALL
            );
            world.setBlockState(support, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            return new JumpResult(
                    player,
                    "",
                    player.getUuid().toString(),
                    player.getGameProfile().getName(),
                    kind,
                    seed,
                    predictedFirst,
                    predictedSecond,
                    nextRoll,
                    steppingExact,
                    removed,
                    newItemEntities.size(),
                    drops,
                    steppingExact && exact
            );
        }

        static JumpResult missing(JumpKind kind) {
            return new JumpResult(
                    null,
                    "not captured",
                    "",
                    "",
                    kind,
                    -1L,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    false,
                    false,
                    -1,
                    List.of(),
                    false
            );
        }

        boolean sameOutcome(JumpResult other) {
            return captureError.equals(other.captureError)
                    && kind == other.kind
                    && seed == other.seed
                    && Float.compare(predictedFirstRoll, other.predictedFirstRoll) == 0
                    && Float.compare(predictedSecondRoll, other.predictedSecondRoll) == 0
                    && Float.compare(nextRollAfterJump, other.nextRollAfterJump) == 0
                    && steppingPositionExact == other.steppingPositionExact
                    && blockRemoved == other.blockRemoved
                    && newItemEntityCount == other.newItemEntityCount
                    && newDrops.equals(other.newDrops)
                    && exact == other.exact;
        }

        boolean freshPlayer(JumpResult other) {
            return playerIdentity != null
                    && other.playerIdentity != null
                    && playerIdentity != other.playerIdentity
                    && !playerUuid.equals(other.playerUuid)
                    && !playerName.equals(other.playerName);
        }

        boolean singleCallbackGuardExact() {
            return kind == JumpKind.RETAIN
                    && seed == RETAIN_SINGLE_CALLBACK_SEED
                    && Float.compare(predictedFirstRoll, RETAIN_FIRST_ROLL) == 0
                    && predictedFirstRoll > 0.4F
                    && Float.compare(predictedSecondRoll, RETAIN_SECOND_ROLL) == 0
                    && predictedSecondRoll <= 0.4F
                    && Float.compare(nextRollAfterJump, RETAIN_SECOND_ROLL) == 0
                    && !blockRemoved;
        }
    }

    record WorldMechanics(
            MechanicsPhase phase,
            String captureError,
            PlacementResult placement,
            ShearsResult shears,
            JumpResult retainJump,
            JumpResult breakJump
    ) {

        static WorldMechanics missing(MechanicsPhase phase) {
            return failed(phase, "not captured");
        }

        static WorldMechanics failed(MechanicsPhase phase, String error) {
            return new WorldMechanics(
                    phase,
                    error,
                    PlacementResult.missing(),
                    ShearsResult.missing(),
                    JumpResult.missing(JumpKind.RETAIN),
                    JumpResult.missing(JumpKind.BREAK)
            );
        }

        boolean hasExactContract() {
            return captureError.isEmpty()
                    && placement.captureError().isEmpty()
                    && placement.exact()
                    && placement.supportsRemoved()
                    && shears.captureError().isEmpty()
                    && shears.exact()
                    && retainJump.captureError().isEmpty()
                    && retainJump.exact()
                    && retainJump.singleCallbackGuardExact()
                    && breakJump.captureError().isEmpty()
                    && breakJump.exact();
        }

        boolean sameOutcome(WorldMechanics other) {
            return captureError.equals(other.captureError)
                    && placement.sameOutcome(other.placement)
                    && shears.sameOutcome(other.shears)
                    && retainJump.sameOutcome(other.retainJump)
                    && breakJump.sameOutcome(other.breakJump);
        }

        boolean hasFreshPlayers(WorldMechanics other) {
            return shears.freshPlayer(other.shears)
                    && retainJump.freshPlayer(other.retainJump)
                    && breakJump.freshPlayer(other.breakJump);
        }
    }

    enum MechanicsPhase {
        SERVER_STARTED(
                -20,
                12,
                "00000000-0000-0000-0000-00000000e216",
                "LanternShearA",
                "00000000-0000-0000-0000-00000000e217",
                "LanternKeepA",
                "00000000-0000-0000-0000-00000000e218",
                "LanternBreakA"
        ),
        RELOADED(
                -20,
                32,
                "00000000-0000-0000-0000-00000000e219",
                "LanternShearB",
                "00000000-0000-0000-0000-00000000e21a",
                "LanternKeepB",
                "00000000-0000-0000-0000-00000000e21b",
                "LanternBreakB"
        );

        private final int baseXOffset;
        private final int baseZOffset;
        private final Map<PlayerRole, String> playerNames;
        private final Map<PlayerRole, UUID> playerUuids;

        MechanicsPhase(
                int baseX,
                int baseZ,
                String shearsUuid,
                String shearsName,
                String retainUuid,
                String retainName,
                String breakUuid,
                String breakName
        ) {
            this.baseXOffset = baseX;
            this.baseZOffset = baseZ;
            playerUuids = Map.of(
                    PlayerRole.SHEARS, UUID.fromString(shearsUuid),
                    PlayerRole.RETAIN, UUID.fromString(retainUuid),
                    PlayerRole.BREAK, UUID.fromString(breakUuid)
            );
            playerNames = Map.of(
                    PlayerRole.SHEARS, shearsName,
                    PlayerRole.RETAIN, retainName,
                    PlayerRole.BREAK, breakName
            );
        }

        BlockPos placementPosition(BlockPos spawnPosition, int index) {
            return position(spawnPosition, index * 4, 210, 0);
        }

        BlockPos shearsPosition(BlockPos spawnPosition) {
            return position(spawnPosition, 0, 212, 6);
        }

        BlockPos jumpFloorPosition(BlockPos spawnPosition, JumpKind kind) {
            int xOffset = kind == JumpKind.RETAIN ? 8 : 12;
            return position(spawnPosition, xOffset, 210, 8);
        }

        List<BlockPos> fixturePositions(BlockPos spawnPosition) {
            List<BlockPos> positions = new ArrayList<>();
            for (int index = 0; index < FACINGS.size(); index++) {
                positions.add(placementPosition(spawnPosition, index));
            }
            positions.add(shearsPosition(spawnPosition));
            positions.add(jumpFloorPosition(spawnPosition, JumpKind.RETAIN));
            positions.add(jumpFloorPosition(spawnPosition, JumpKind.BREAK));
            return List.copyOf(positions);
        }

        private BlockPos position(
                BlockPos spawnPosition,
                int xOffset,
                int y,
                int zOffset
        ) {
            return new BlockPos(
                    spawnPosition.getX() + baseXOffset + xOffset,
                    y,
                    spawnPosition.getZ() + baseZOffset + zOffset
            );
        }

        ServerPlayerEntity createPlayer(
                MinecraftServer server,
                ServerWorld world,
                PlayerRole role
        ) {
            return new ServerPlayerEntity(
                    server,
                    world,
                    new GameProfile(playerUuids.get(role), playerNames.get(role))
            );
        }
    }

    enum PlayerRole {
        SHEARS,
        RETAIN,
        BREAK
    }

    enum JumpKind {
        RETAIN,
        BREAK
    }

    private static String stackDescription(List<ItemStack> stacks) {
        return stacks.stream()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> identifierString(Registries.ITEM.getId(stack.getItem()))
                        + "x" + stack.getCount())
                .sorted()
                .collect(Collectors.joining(","));
    }
}
