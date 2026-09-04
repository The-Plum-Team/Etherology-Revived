package dev.theplumteam.etherology.e2e.server;

import net.minecraft.advancement.Advancement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootDataType;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

record SlitheriteBlockProbeState(
        String captureError,
        List<String> blockIds,
        List<String> blockItemIds,
        Map<String, SlitheriteBlockEntry> entries,
        int aggregateStateCount,
        int aggregateUniqueRawIdCount,
        LoadedData loadedData
) {

    static final String BLOCK_REGISTRY_ID = "minecraft:block";
    static final String ITEM_REGISTRY_ID = "minecraft:item";
    static final String BLOCK_ITEM_CLASS = BlockItem.class.getName();
    static final int EXPECTED_AGGREGATE_STATE_COUNT = 1262;
    static final Map<String, SlitheriteBlockSpec> EXPECTED_BLOCKS = expectedBlocks();
    static final List<String> EXPECTED_BLOCK_IDS = List.copyOf(EXPECTED_BLOCKS.keySet());

    static SlitheriteBlockProbeState captureTagLoaded() {
        return capture(null);
    }

    static SlitheriteBlockProbeState capture(MinecraftServer server) {
        List<String> capturedBlockIds = new ArrayList<>();
        List<String> capturedItemIds = new ArrayList<>();
        Map<String, SlitheriteBlockEntry> capturedEntries = new LinkedHashMap<>();
        Set<Integer> aggregateRawIds = new HashSet<>();
        List<String> errors = new ArrayList<>();
        int capturedStateCount = 0;

        for (String id : EXPECTED_BLOCK_IDS) {
            SlitheriteBlockEntry entry;
            try {
                entry = captureEntry(id);
            } catch (RuntimeException exception) {
                entry = SlitheriteBlockEntry.failed(id);
                errors.add(id + "=" + exception.getClass().getName());
            }
            if (entry.blockIdentity() != null) {
                capturedBlockIds.add(entry.blockId());
            }
            if (entry.itemIdentity() != null) {
                capturedItemIds.add(entry.itemId());
            }
            capturedEntries.put(id, entry);
            capturedStateCount += entry.stateCount();
            aggregateRawIds.addAll(entry.stateRawIds());
        }

        return new SlitheriteBlockProbeState(
                String.join(",", errors),
                List.copyOf(capturedBlockIds),
                List.copyOf(capturedItemIds),
                Collections.unmodifiableMap(capturedEntries),
                capturedStateCount,
                aggregateRawIds.size(),
                LoadedData.capture(server)
        );
    }

    static SlitheriteBlockProbeState missing() {
        return new SlitheriteBlockProbeState(
                "not captured",
                List.of(),
                List.of(),
                Map.of(),
                0,
                0,
                LoadedData.missing()
        );
    }

    boolean hasExactRegistry() {
        return EXPECTED_BLOCK_IDS.equals(blockIds)
                && EXPECTED_BLOCK_IDS.equals(blockItemIds)
                && EXPECTED_BLOCKS.keySet().equals(entries.keySet())
                && EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
                    SlitheriteBlockEntry entry = entries.get(id);
                    return entry != null
                            && entry.blockIdentity() != null
                            && entry.itemIdentity() != null
                            && id.equals(entry.blockId())
                            && id.equals(entry.itemId());
                });
    }

    boolean hasExactRuntimeClasses() {
        return EXPECTED_BLOCKS.entrySet().stream().allMatch(expected -> {
            SlitheriteBlockEntry entry = entries.get(expected.getKey());
            return entry != null
                    && expected.getValue().blockClass().equals(entry.blockClass())
                    && BLOCK_ITEM_CLASS.equals(entry.itemClass());
        });
    }

    boolean hasExactBlockItemMappings() {
        return allEntriesMatch(entry -> entry.blockItem()
                && entry.blockItemMapsToBlock()
                && entry.blockAsItemMatches());
    }

    boolean hasExactDefaultStates() {
        return EXPECTED_BLOCKS.entrySet().stream().allMatch(expected -> {
            SlitheriteBlockEntry entry = entries.get(expected.getKey());
            SlitheriteBlockSpec spec = expected.getValue();
            return entry != null
                    && spec.defaultProperties().equals(entry.defaultProperties())
                    && spec.defaultState().equals(entry.defaultState())
                    && spec.stateCount() == entry.stateCount();
        });
    }

    boolean hasExactRawIds() {
        if (aggregateStateCount != EXPECTED_AGGREGATE_STATE_COUNT
                || aggregateUniqueRawIdCount != EXPECTED_AGGREGATE_STATE_COUNT) {
            return false;
        }
        Set<Integer> rawIds = new HashSet<>();
        for (Map.Entry<String, SlitheriteBlockSpec> expected :
                EXPECTED_BLOCKS.entrySet()) {
            SlitheriteBlockEntry entry = entries.get(expected.getKey());
            if (entry == null || !entry.hasExactRawIds(expected.getValue().stateCount())) {
                return false;
            }
            rawIds.addAll(entry.stateRawIds());
        }
        return rawIds.size() == EXPECTED_AGGREGATE_STATE_COUNT;
    }

    boolean hasExactTagMemberships() {
        return EXPECTED_BLOCKS.entrySet().stream().allMatch(expected -> {
            SlitheriteBlockEntry entry = entries.get(expected.getKey());
            SlitheriteBlockSpec spec = expected.getValue();
            return entry != null
                    && entry.pickaxeMineable()
                    && !entry.needsStoneTool()
                    && spec.slab() == entry.blockSlab()
                    && spec.slab() == entry.itemSlab()
                    && spec.stairs() == entry.blockStairs()
                    && spec.stairs() == entry.itemStairs()
                    && spec.wall() == entry.blockWall()
                    && spec.wall() == entry.itemWall()
                    && spec.stoneBrick() == entry.blockStoneBrick()
                    && spec.pressurePlate() == entry.blockStonePressurePlate()
                    && !entry.itemButton();
        });
    }

    boolean hasExactCoreContract() {
        return captureError.isEmpty()
                && hasExactRegistry()
                && hasExactRuntimeClasses()
                && hasExactBlockItemMappings()
                && hasExactDefaultStates()
                && hasExactRawIds()
                && hasExactTagMemberships()
                && expectedCanonicalProperties().equals(canonicalProperties())
                && expectedCanonicalTags().equals(canonicalTags());
    }

    boolean hasExactContract() {
        return hasExactCoreContract() && loadedData.hasExactContract();
    }

    boolean sameCoreState(SlitheriteBlockProbeState other) {
        return hasSameRegistry(other)
                && hasSameDefaultStatesAndRawIds(other)
                && hasSameTags(other);
    }

    boolean sameStateAtServerStarted(SlitheriteBlockProbeState other) {
        return sameCoreState(other)
                && loadedData.sameOutcome(other.loadedData)
                && loadedData.sameInstances(other.loadedData);
    }

    boolean hasSameRegistry(SlitheriteBlockProbeState other) {
        if (!captureError.equals(other.captureError)
                || !blockIds.equals(other.blockIds)
                || !blockItemIds.equals(other.blockItemIds)
                || !entries.keySet().equals(other.entries.keySet())) {
            return false;
        }
        return EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
            SlitheriteBlockEntry entry = entries.get(id);
            SlitheriteBlockEntry otherEntry = other.entries.get(id);
            return entry != null
                    && otherEntry != null
                    && entry.blockIdentity() == otherEntry.blockIdentity()
                    && entry.itemIdentity() == otherEntry.itemIdentity()
                    && entry.blockId().equals(otherEntry.blockId())
                    && entry.itemId().equals(otherEntry.itemId())
                    && entry.blockClass().equals(otherEntry.blockClass())
                    && entry.itemClass().equals(otherEntry.itemClass())
                    && entry.blockItem() == otherEntry.blockItem()
                    && entry.blockItemMapsToBlock() == otherEntry.blockItemMapsToBlock()
                    && entry.blockAsItemMatches() == otherEntry.blockAsItemMatches();
        });
    }

    boolean hasSameDefaultStatesAndRawIds(SlitheriteBlockProbeState other) {
        if (aggregateStateCount != other.aggregateStateCount
                || aggregateUniqueRawIdCount != other.aggregateUniqueRawIdCount) {
            return false;
        }
        return EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
            SlitheriteBlockEntry entry = entries.get(id);
            SlitheriteBlockEntry otherEntry = other.entries.get(id);
            return entry != null
                    && otherEntry != null
                    && entry.defaultProperties().equals(otherEntry.defaultProperties())
                    && entry.defaultState().equals(otherEntry.defaultState())
                    && entry.stateCount() == otherEntry.stateCount()
                    && entry.defaultStateRawId() == otherEntry.defaultStateRawId()
                    && entry.stateRawIds().equals(otherEntry.stateRawIds());
        });
    }

    boolean hasSameTags(SlitheriteBlockProbeState other) {
        return EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
            SlitheriteBlockEntry entry = entries.get(id);
            SlitheriteBlockEntry otherEntry = other.entries.get(id);
            return entry != null
                    && otherEntry != null
                    && entry.pickaxeMineable() == otherEntry.pickaxeMineable()
                    && entry.needsStoneTool() == otherEntry.needsStoneTool()
                    && entry.blockSlab() == otherEntry.blockSlab()
                    && entry.itemSlab() == otherEntry.itemSlab()
                    && entry.blockStairs() == otherEntry.blockStairs()
                    && entry.itemStairs() == otherEntry.itemStairs()
                    && entry.blockWall() == otherEntry.blockWall()
                    && entry.itemWall() == otherEntry.itemWall()
                    && entry.blockStoneBrick() == otherEntry.blockStoneBrick()
                    && entry.blockStonePressurePlate()
                            == otherEntry.blockStonePressurePlate()
                    && entry.itemButton() == otherEntry.itemButton();
        });
    }

    boolean hasReloadedDataOutcome(SlitheriteBlockProbeState other) {
        return loadedData.sameOutcome(other.loadedData);
    }

    boolean hasFreshReloadedData(SlitheriteBlockProbeState other) {
        return loadedData.hasFreshInstances(other.loadedData);
    }

    String canonicalProperties() {
        return entries.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().propertySummary())
                .collect(Collectors.joining(","));
    }

    String canonicalTags() {
        return entries.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().tagSummary())
                .collect(Collectors.joining(","));
    }

    String canonicalRegistry() {
        return entries.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().registrySummary())
                .collect(Collectors.joining(","));
    }

    static String expectedCanonicalProperties() {
        return EXPECTED_BLOCKS.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + propertySummary(entry.getValue()))
                .collect(Collectors.joining(","));
    }

    static String expectedCanonicalTags() {
        return EXPECTED_BLOCKS.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + tagSummary(entry.getValue()))
                .collect(Collectors.joining(","));
    }

    static PlacementState placeIn(ServerWorld world) {
        if (world == null) {
            return PlacementState.failed("missing overworld");
        }
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < EXPECTED_BLOCK_IDS.size(); index++) {
            String id = EXPECTED_BLOCK_IDS.get(index);
            SlitheriteBlockSpec spec = EXPECTED_BLOCKS.get(id);
            BlockPos position = placementPosition(index);
            BlockPos supportPosition = supportPosition(position, spec);
            Block support = spec.button() ? Blocks.SMOOTH_STONE : Blocks.POLISHED_ANDESITE;
            try {
                Block block = registeredBlock(id);
                world.setBlockState(
                        supportPosition,
                        support.getDefaultState(),
                        Block.NOTIFY_ALL
                );
                world.setBlockState(position, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(position, block.getDefaultState(), Block.NOTIFY_ALL);
                if (world.getBlockState(position).getBlock() != block) {
                    errors.add(id + "=placement_rejected");
                }
            } catch (RuntimeException exception) {
                errors.add(id + "=" + exception.getClass().getName());
            }
        }
        PlacementState captured = capturePlacement(world);
        return new PlacementState(
                joinErrors(errors, captured.captureError()),
                captured.positions(),
                captured.supportPositions(),
                captured.placedBlockIds(),
                captured.placedStates(),
                captured.supportBlockIds()
        );
    }

    static PlacementState capturePlacement(ServerWorld world) {
        if (world == null) {
            return PlacementState.failed("missing overworld");
        }
        Map<String, String> placedIds = new LinkedHashMap<>();
        Map<String, String> placedStates = new LinkedHashMap<>();
        Map<String, String> supportIds = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < EXPECTED_BLOCK_IDS.size(); index++) {
            String id = EXPECTED_BLOCK_IDS.get(index);
            SlitheriteBlockSpec spec = EXPECTED_BLOCKS.get(id);
            BlockPos position = placementPosition(index);
            BlockPos supportPosition = supportPosition(position, spec);
            try {
                BlockState state = world.getBlockState(position);
                BlockState supportState = world.getBlockState(supportPosition);
                placedIds.put(id, identifierString(Registries.BLOCK.getId(state.getBlock())));
                placedStates.put(id, stateDescription(state));
                supportIds.put(
                        id,
                        identifierString(Registries.BLOCK.getId(supportState.getBlock()))
                );
            } catch (RuntimeException exception) {
                placedIds.put(id, "");
                placedStates.put(id, "");
                supportIds.put(id, "");
                errors.add(id + "=" + exception.getClass().getName());
            }
        }
        return new PlacementState(
                String.join(",", errors),
                expectedPlacementPositions(),
                expectedSupportPositions(),
                Collections.unmodifiableMap(placedIds),
                Collections.unmodifiableMap(placedStates),
                Collections.unmodifiableMap(supportIds)
        );
    }

    private boolean allEntriesMatch(Predicate<SlitheriteBlockEntry> predicate) {
        return entries.size() == EXPECTED_BLOCKS.size()
                && entries.values().stream().allMatch(predicate);
    }

    private static SlitheriteBlockEntry captureEntry(String id) {
        Identifier expectedId = Identifier.parse(id);
        Block block = Registries.BLOCK.getOrEmpty(expectedId).orElse(null);
        Item item = Registries.ITEM.getOrEmpty(expectedId).orElse(null);
        if (block == null || item == null) {
            return SlitheriteBlockEntry.failed(id);
        }

        Identifier blockId = Registries.BLOCK.getId(block);
        Identifier itemId = Registries.ITEM.getId(item);
        BlockState state = block.getDefaultState();
        BlockItem blockItem = item instanceof BlockItem candidate ? candidate : null;
        List<Integer> rawIds = block.getStateManager().getStates().stream()
                .map(Block.STATE_IDS::getRawId)
                .sorted()
                .toList();
        ItemStack stack = item.getDefaultStack();
        return new SlitheriteBlockEntry(
                block,
                item,
                identifierString(blockId),
                identifierString(itemId),
                block.getClass().getName(),
                item.getClass().getName(),
                blockItem != null,
                blockItem != null && blockItem.getBlock() == block,
                Block.getBlockFromItem(item) == block && block.asItem() == item,
                defaultProperties(state),
                stateDescription(state),
                block.getStateManager().getStates().size(),
                Block.STATE_IDS.getRawId(state),
                rawIds,
                state.isIn(BlockTags.PICKAXE_MINEABLE),
                state.isIn(BlockTags.NEEDS_STONE_TOOL),
                state.isIn(BlockTags.SLABS),
                stack.isIn(ItemTags.SLABS),
                state.isIn(BlockTags.STAIRS),
                stack.isIn(ItemTags.STAIRS),
                state.isIn(BlockTags.WALLS),
                stack.isIn(ItemTags.WALLS),
                state.isIn(BlockTags.STONE_BRICKS),
                state.isIn(BlockTags.STONE_PRESSURE_PLATES),
                stack.isIn(ItemTags.BUTTONS)
        );
    }

    private static Map<String, String> defaultProperties(BlockState state) {
        Map<String, String> properties = new TreeMap<>();
        for (Property<?> property : state.getProperties()) {
            properties.put(property.getName(), propertyValueName(state, property));
        }
        return Collections.unmodifiableMap(properties);
    }

    private static <T extends Comparable<T>> String propertyValueName(
            BlockState state,
            Property<T> property
    ) {
        return property.name(state.get(property));
    }

    private static String propertySummary(SlitheriteBlockSpec spec) {
        return spec.blockClass() + "|item_class=" + BLOCK_ITEM_CLASS
                + "|default=" + canonicalStringMap(spec.defaultProperties())
                + "|default_state=" + spec.defaultState()
                + "|state_count=" + spec.stateCount();
    }

    private static String tagSummary(SlitheriteBlockSpec spec) {
        return "pickaxe=true|needs_stone=false"
                + "|block_slab=" + spec.slab() + "|item_slab=" + spec.slab()
                + "|block_stairs=" + spec.stairs() + "|item_stairs=" + spec.stairs()
                + "|block_wall=" + spec.wall() + "|item_wall=" + spec.wall()
                + "|stone_brick=" + spec.stoneBrick()
                + "|stone_pressure_plate=" + spec.pressurePlate()
                + "|item_button=false";
    }

    private static String defaultState(String id, Map<String, String> properties) {
        if (properties.isEmpty()) {
            return id;
        }
        return id + "[" + properties.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",")) + "]";
    }

    private static Block registeredBlock(String id) {
        return Registries.BLOCK.getOrEmpty(Identifier.parse(id)).orElseThrow(
                () -> new IllegalStateException("Missing block " + id)
        );
    }

    private static BlockPos placementPosition(int index) {
        return new BlockPos(64 + index * 2, 200, 24);
    }

    private static BlockPos supportPosition(
            BlockPos position,
            SlitheriteBlockSpec spec
    ) {
        return spec.button() ? position.south() : position.down();
    }

    private static Map<String, String> expectedPlacementPositions() {
        Map<String, String> positions = new LinkedHashMap<>();
        for (int index = 0; index < EXPECTED_BLOCK_IDS.size(); index++) {
            positions.put(EXPECTED_BLOCK_IDS.get(index), position(placementPosition(index)));
        }
        return Collections.unmodifiableMap(positions);
    }

    private static Map<String, String> expectedSupportPositions() {
        Map<String, String> positions = new LinkedHashMap<>();
        for (int index = 0; index < EXPECTED_BLOCK_IDS.size(); index++) {
            String id = EXPECTED_BLOCK_IDS.get(index);
            positions.put(
                    id,
                    position(supportPosition(placementPosition(index), EXPECTED_BLOCKS.get(id)))
            );
        }
        return Collections.unmodifiableMap(positions);
    }

    private static Map<String, String> expectedPlacedBlockIds() {
        Map<String, String> ids = new LinkedHashMap<>();
        EXPECTED_BLOCK_IDS.forEach(id -> ids.put(id, id));
        return Collections.unmodifiableMap(ids);
    }

    private static Map<String, String> expectedPlacedStates() {
        Map<String, String> states = new LinkedHashMap<>();
        EXPECTED_BLOCKS.forEach((id, spec) -> states.put(id, spec.defaultState()));
        return Collections.unmodifiableMap(states);
    }

    private static Map<String, String> expectedSupportBlockIds() {
        Map<String, String> ids = new LinkedHashMap<>();
        EXPECTED_BLOCKS.forEach((id, spec) -> ids.put(
                id,
                spec.button() ? "minecraft:smooth_stone" : "minecraft:polished_andesite"
        ));
        return Collections.unmodifiableMap(ids);
    }

    private static String position(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static String stateDescription(BlockState state) {
        return BlockArgumentParser.stringifyBlockState(state);
    }

    private static String identifierString(Identifier identifier) {
        return identifier == null ? "" : identifier.toString();
    }

    private static String joinErrors(List<String> errors, String capturedError) {
        if (!capturedError.isEmpty()) {
            errors.add(capturedError);
        }
        return String.join(",", errors);
    }

    private static Map<String, SlitheriteBlockSpec> expectedBlocks() {
        Map<String, SlitheriteBlockSpec> expected = new LinkedHashMap<>();
        putBlock(expected, "slitherite", Block.class, Map.of(), 1, false, false,
                false, false, false, false);
        putBlock(expected, "slitherite_stairs", StairsBlock.class, stairsState(), 80,
                false, true, false, false, false, false);
        putBlock(expected, "slitherite_slab", SlabBlock.class, slabState(), 6,
                true, false, false, false, false, false);
        putBlock(expected, "slitherite_wall", WallBlock.class, wallState(), 324,
                false, false, true, false, false, false);
        putBlock(expected, "polished_slitherite", Block.class, Map.of(), 1,
                false, false, false, false, false, false);
        putBlock(expected, "polished_slitherite_stairs", StairsBlock.class,
                stairsState(), 80, false, true, false, false, false, false);
        putBlock(expected, "polished_slitherite_slab", SlabBlock.class, slabState(), 6,
                true, false, false, false, false, false);
        putBlock(expected, "polished_slitherite_wall", WallBlock.class, wallState(), 324,
                false, false, true, false, false, false);
        putBlock(expected, "polished_slitherite_button", ButtonBlock.class,
                buttonState(), 24, false, false, false, false, false, true);
        putBlock(expected, "polished_slitherite_pressure_plate", PressurePlateBlock.class,
                poweredState(), 2, false, false, false, false, true, false);
        putBlock(expected, "polished_slitherite_bricks", Block.class, Map.of(), 1,
                false, false, false, true, false, false);
        putBlock(expected, "polished_slitherite_brick_stairs", StairsBlock.class,
                stairsState(), 80, false, true, false, false, false, false);
        putBlock(expected, "polished_slitherite_brick_slab", SlabBlock.class,
                slabState(), 6, true, false, false, false, false, false);
        putBlock(expected, "polished_slitherite_brick_wall", WallBlock.class,
                wallState(), 324, false, false, true, false, false, false);
        putBlock(expected, "chiseled_polished_slitherite", Block.class, Map.of(), 1,
                false, false, false, false, false, false);
        putBlock(expected, "chiseled_polished_slitherite_bricks", Block.class,
                Map.of(), 1, false, false, false, true, false, false);
        putBlock(expected, "cracked_polished_slitherite_bricks", Block.class,
                Map.of(), 1, false, false, false, true, false, false);
        return Collections.unmodifiableMap(expected);
    }

    private static void putBlock(
            Map<String, SlitheriteBlockSpec> expected,
            String path,
            Class<? extends Block> blockClass,
            Map<String, String> defaultProperties,
            int stateCount,
            boolean slab,
            boolean stairs,
            boolean wall,
            boolean stoneBrick,
            boolean pressurePlate,
            boolean button
    ) {
        String id = "etherology:" + path;
        Map<String, String> sortedProperties = Collections.unmodifiableMap(
                new TreeMap<>(defaultProperties)
        );
        expected.put(
                id,
                new SlitheriteBlockSpec(
                        blockClass.getName(),
                        sortedProperties,
                        stateCount,
                        slab,
                        stairs,
                        wall,
                        stoneBrick,
                        pressurePlate,
                        button,
                        defaultState(id, sortedProperties)
                )
        );
    }

    private static Map<String, String> stairsState() {
        return Map.of(
                "facing", "north",
                "half", "bottom",
                "shape", "straight",
                "waterlogged", "false"
        );
    }

    private static Map<String, String> slabState() {
        return Map.of("type", "bottom", "waterlogged", "false");
    }

    private static Map<String, String> wallState() {
        return Map.of(
                "east", "none",
                "north", "none",
                "south", "none",
                "up", "true",
                "waterlogged", "false",
                "west", "none"
        );
    }

    private static Map<String, String> buttonState() {
        return Map.of(
                "face", "wall",
                "facing", "north",
                "powered", "false"
        );
    }

    private static Map<String, String> poweredState() {
        return Map.of("powered", "false");
    }

    record SlitheriteBlockSpec(
            String blockClass,
            Map<String, String> defaultProperties,
            int stateCount,
            boolean slab,
            boolean stairs,
            boolean wall,
            boolean stoneBrick,
            boolean pressurePlate,
            boolean button,
            String defaultState
    ) {
    }

    record SlitheriteBlockEntry(
            Object blockIdentity,
            Object itemIdentity,
            String blockId,
            String itemId,
            String blockClass,
            String itemClass,
            boolean blockItem,
            boolean blockItemMapsToBlock,
            boolean blockAsItemMatches,
            Map<String, String> defaultProperties,
            String defaultState,
            int stateCount,
            int defaultStateRawId,
            List<Integer> stateRawIds,
            boolean pickaxeMineable,
            boolean needsStoneTool,
            boolean blockSlab,
            boolean itemSlab,
            boolean blockStairs,
            boolean itemStairs,
            boolean blockWall,
            boolean itemWall,
            boolean blockStoneBrick,
            boolean blockStonePressurePlate,
            boolean itemButton
    ) {

        static SlitheriteBlockEntry failed(String id) {
            return new SlitheriteBlockEntry(
                    null, null, id, id, "", "", false, false, false, Map.of(), "",
                    0, -1, List.of(), false, false, false, false, false, false,
                    false, false, false, false, false
            );
        }

        boolean hasExactRawIds(int expectedStateCount) {
            return stateCount == expectedStateCount
                    && defaultStateRawId >= 0
                    && stateRawIds.size() == expectedStateCount
                    && stateRawIds.stream().allMatch(rawId -> rawId >= 0)
                    && new HashSet<>(stateRawIds).size() == expectedStateCount
                    && stateRawIds.equals(stateRawIds.stream().sorted().toList())
                    && stateRawIds.contains(defaultStateRawId);
        }

        String propertySummary() {
            return blockClass + "|item_class=" + itemClass
                    + "|default=" + canonicalStringMap(defaultProperties)
                    + "|default_state=" + defaultState
                    + "|state_count=" + stateCount;
        }

        String tagSummary() {
            return "pickaxe=" + pickaxeMineable + "|needs_stone=" + needsStoneTool
                    + "|block_slab=" + blockSlab + "|item_slab=" + itemSlab
                    + "|block_stairs=" + blockStairs + "|item_stairs=" + itemStairs
                    + "|block_wall=" + blockWall + "|item_wall=" + itemWall
                    + "|stone_brick=" + blockStoneBrick
                    + "|stone_pressure_plate=" + blockStonePressurePlate
                    + "|item_button=" + itemButton;
        }

        String registrySummary() {
            return propertySummary()
                    + "|default_raw_id=" + defaultStateRawId
                    + "|raw_ids=" + stateRawIds.size();
        }
    }

    record PlacementState(
            String captureError,
            Map<String, String> positions,
            Map<String, String> supportPositions,
            Map<String, String> placedBlockIds,
            Map<String, String> placedStates,
            Map<String, String> supportBlockIds
    ) {

        static PlacementState missing() {
            return failed("not captured");
        }

        static PlacementState failed(String error) {
            return new PlacementState(
                    error,
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of()
            );
        }

        boolean hasExactPlacement() {
            return captureError.isEmpty()
                    && expectedPlacementPositions().equals(positions)
                    && expectedSupportPositions().equals(supportPositions)
                    && expectedPlacedBlockIds().equals(placedBlockIds)
                    && expectedPlacedStates().equals(placedStates)
                    && expectedSupportBlockIds().equals(supportBlockIds);
        }

        boolean samePlacement(PlacementState other) {
            return captureError.equals(other.captureError)
                    && positions.equals(other.positions)
                    && supportPositions.equals(other.supportPositions)
                    && placedBlockIds.equals(other.placedBlockIds)
                    && placedStates.equals(other.placedStates)
                    && supportBlockIds.equals(other.supportBlockIds);
        }

        String canonicalPositions() {
            return canonicalStringMap(positions);
        }

        String canonicalSupportPositions() {
            return canonicalStringMap(supportPositions);
        }

        String canonicalPlacedBlockIds() {
            return canonicalStringMap(placedBlockIds);
        }

        String canonicalPlacedStates() {
            return canonicalStringMap(placedStates);
        }

        String canonicalSupportBlockIds() {
            return canonicalStringMap(supportBlockIds);
        }

        static Map<String, String> expectedPositions() {
            return expectedPlacementPositions();
        }

        static Map<String, String> expectedSupports() {
            return expectedSupportPositions();
        }

        static Map<String, String> expectedBlockIds() {
            return expectedPlacedBlockIds();
        }

        static Map<String, String> expectedStates() {
            return expectedPlacedStates();
        }

        static Map<String, String> expectedSupportIds() {
            return expectedSupportBlockIds();
        }
    }

    record LoadedData(
            Map<String, Object> lootTableIdentities,
            Map<String, Object> recipeIdentities,
            Map<String, Object> advancementIdentities,
            String captureError,
            List<String> lootTableIds,
            Map<String, String> selfDrops,
            Map<String, String> doubleSlabDrops,
            List<String> recipeIds,
            Map<String, String> recipes,
            List<String> advancementIds,
            List<String> relatedRecipeIds,
            Map<String, String> relatedRecipes
    ) {

        static final List<String> EXPECTED_LOOT_TABLE_IDS = expectedLootTableIds();
        static final Map<String, String> EXPECTED_SELF_DROPS = expectedSelfDrops();
        static final Map<String, String> EXPECTED_DOUBLE_SLAB_DROPS =
                expectedDoubleSlabDrops();
        static final Map<String, RecipeSpec> EXPECTED_RECIPE_SPECS =
                expectedRecipeSpecs();
        static final List<String> EXPECTED_RECIPE_IDS =
                List.copyOf(EXPECTED_RECIPE_SPECS.keySet());
        static final Map<String, String> EXPECTED_RECIPES =
                expectedRecipeDescriptions(EXPECTED_RECIPE_SPECS);
        static final List<String> EXPECTED_ADVANCEMENT_IDS =
                expectedAdvancementIds();
        static final Map<String, RecipeSpec> EXPECTED_RELATED_RECIPE_SPECS =
                expectedRelatedRecipeSpecs();
        static final List<String> EXPECTED_RELATED_RECIPE_IDS =
                List.copyOf(EXPECTED_RELATED_RECIPE_SPECS.keySet());
        static final Map<String, String> EXPECTED_RELATED_RECIPES =
                expectedRecipeDescriptions(EXPECTED_RELATED_RECIPE_SPECS);

        static LoadedData capture(MinecraftServer server) {
            if (server == null) {
                return missing();
            }
            try {
                List<String> lootTableIds = server.getLootManager()
                        .getIds(LootDataType.LOOT_TABLES)
                        .stream()
                        .map(Identifier::toString)
                        .filter(LoadedData::isOwnedSlitheriteLootTableId)
                        .sorted()
                        .toList();
                Map<String, Object> lootIdentities = new LinkedHashMap<>();
                Map<String, String> selfDrops = new TreeMap<>();
                Map<String, String> doubleSlabDrops = new TreeMap<>();
                ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
                for (String id : lootTableIds) {
                    Identifier lootId = Identifier.parse(id);
                    lootIdentities.put(id, server.getLootManager().getLootTable(lootId));
                }
                for (String id : EXPECTED_BLOCK_IDS) {
                    Block block = registeredBlock(id);
                    LootTable lootTable = server.getLootManager().getLootTable(
                            block.getLootTableId()
                    );
                    selfDrops.put(
                            id,
                            generateLoot(server, block.getDefaultState(), lootTable, tool)
                    );
                    SlitheriteBlockSpec spec = EXPECTED_BLOCKS.get(id);
                    if (spec.slab()) {
                        BlockState doubleSlab = block.getDefaultState().with(
                                net.minecraft.state.property.Properties.SLAB_TYPE,
                                SlabType.DOUBLE
                        );
                        doubleSlabDrops.put(
                                id,
                                generateLoot(server, doubleSlab, lootTable, tool)
                        );
                    }
                }

                Set<String> availableRecipeIds = server.getRecipeManager()
                        .keys()
                        .map(Identifier::toString)
                        .collect(Collectors.toSet());
                Map<String, Object> recipeIdentities = new LinkedHashMap<>();
                Map<String, String> recipes = new LinkedHashMap<>();
                Map<String, String> relatedRecipes = new LinkedHashMap<>();
                List<String> recipeIds = new ArrayList<>();
                List<String> availableSlitheriteRecipeIds = availableRecipeIds.stream()
                        .filter(LoadedData::isOwnedSlitheriteRecipeId)
                        .sorted()
                        .toList();
                for (String id : availableSlitheriteRecipeIds) {
                    Recipe<?> recipe = server.getRecipeManager()
                            .get(Identifier.parse(id))
                            .orElse(null);
                    recipeIdentities.put(id, recipe);
                    recipes.put(id, captureRecipe(server, recipe));
                    recipeIds.add(id);
                }
                List<String> relatedRecipeIds = new ArrayList<>();
                for (String id : EXPECTED_RELATED_RECIPE_IDS) {
                    if (!availableRecipeIds.contains(id)) {
                        continue;
                    }
                    Recipe<?> recipe = server.getRecipeManager()
                            .get(Identifier.parse(id))
                            .orElse(null);
                    recipeIdentities.put(id, recipe);
                    relatedRecipes.put(id, captureRecipe(server, recipe));
                    relatedRecipeIds.add(id);
                }

                List<String> advancementIds = server.getAdvancementLoader()
                        .getAdvancements()
                        .stream()
                        .map(Advancement::getId)
                        .filter(id -> "etherology".equals(id.getNamespace()))
                        .filter(id -> id.getPath().contains("slitherite"))
                        .map(Identifier::toString)
                        .sorted()
                        .toList();
                Map<String, Object> advancementIdentities = new LinkedHashMap<>();
                for (String id : advancementIds) {
                    advancementIdentities.put(
                            id,
                            server.getAdvancementLoader().get(Identifier.parse(id))
                    );
                }
                return new LoadedData(
                        Collections.unmodifiableMap(lootIdentities),
                        Collections.unmodifiableMap(recipeIdentities),
                        Collections.unmodifiableMap(advancementIdentities),
                        "",
                        lootTableIds,
                        Collections.unmodifiableMap(selfDrops),
                        Collections.unmodifiableMap(doubleSlabDrops),
                        List.copyOf(recipeIds),
                        Collections.unmodifiableMap(recipes),
                        advancementIds,
                        List.copyOf(relatedRecipeIds),
                        Collections.unmodifiableMap(relatedRecipes)
                );
            } catch (RuntimeException exception) {
                return failed(exception.getClass().getName());
            }
        }

        static LoadedData missing() {
            return failed("not captured");
        }

        boolean hasExactLoot() {
            return EXPECTED_LOOT_TABLE_IDS.equals(lootTableIds)
                    && identityMapExact(lootTableIdentities, EXPECTED_LOOT_TABLE_IDS)
                    && EXPECTED_SELF_DROPS.equals(selfDrops)
                    && EXPECTED_DOUBLE_SLAB_DROPS.equals(doubleSlabDrops);
        }

        boolean hasExactRecipes() {
            List<String> allIds = new ArrayList<>(EXPECTED_RECIPE_IDS);
            allIds.addAll(EXPECTED_RELATED_RECIPE_IDS);
            return EXPECTED_RECIPE_IDS.equals(recipeIds)
                    && EXPECTED_RECIPES.equals(recipes)
                    && EXPECTED_RELATED_RECIPE_IDS.equals(relatedRecipeIds)
                    && EXPECTED_RELATED_RECIPES.equals(relatedRecipes)
                    && identityMapExact(recipeIdentities, allIds);
        }

        boolean hasExactAdvancements() {
            return EXPECTED_ADVANCEMENT_IDS.equals(advancementIds)
                    && identityMapExact(
                            advancementIdentities,
                            EXPECTED_ADVANCEMENT_IDS
                    );
        }

        boolean hasExactContract() {
            return captureError.isEmpty()
                    && hasExactLoot()
                    && hasExactRecipes()
                    && hasExactAdvancements();
        }

        boolean sameOutcome(LoadedData other) {
            return captureError.equals(other.captureError)
                    && lootTableIds.equals(other.lootTableIds)
                    && selfDrops.equals(other.selfDrops)
                    && doubleSlabDrops.equals(other.doubleSlabDrops)
                    && recipeIds.equals(other.recipeIds)
                    && recipes.equals(other.recipes)
                    && advancementIds.equals(other.advancementIds)
                    && relatedRecipeIds.equals(other.relatedRecipeIds)
                    && relatedRecipes.equals(other.relatedRecipes);
        }

        boolean sameInstances(LoadedData other) {
            return sameIdentityMap(lootTableIdentities, other.lootTableIdentities, true)
                    && sameIdentityMap(recipeIdentities, other.recipeIdentities, true)
                    && sameIdentityMap(
                            advancementIdentities,
                            other.advancementIdentities,
                            true
                    );
        }

        boolean hasFreshInstances(LoadedData other) {
            return sameIdentityMap(lootTableIdentities, other.lootTableIdentities, false)
                    && sameIdentityMap(recipeIdentities, other.recipeIdentities, false)
                    && sameIdentityMap(
                            advancementIdentities,
                            other.advancementIdentities,
                            false
                    );
        }

        String canonicalSelfDrops() {
            return canonicalStringMap(selfDrops);
        }

        String canonicalDoubleSlabDrops() {
            return canonicalStringMap(doubleSlabDrops);
        }

        String canonicalRecipes() {
            return String.join(",", recipes.values());
        }

        String canonicalRelatedRecipes() {
            return String.join(",", relatedRecipes.values());
        }

        static String expectedCanonicalSelfDrops() {
            return canonicalStringMap(EXPECTED_SELF_DROPS);
        }

        static String expectedCanonicalDoubleSlabDrops() {
            return canonicalStringMap(EXPECTED_DOUBLE_SLAB_DROPS);
        }

        static String expectedCanonicalRecipes() {
            return String.join(",", EXPECTED_RECIPES.values());
        }

        static String expectedCanonicalRelatedRecipes() {
            return String.join(",", EXPECTED_RELATED_RECIPES.values());
        }

        static boolean isOwnedSlitheriteLootTableId(String id) {
            return id.startsWith("etherology:blocks/")
                    && id.substring("etherology:blocks/".length()).contains("slitherite");
        }

        static boolean isOwnedSlitheriteRecipeId(String id) {
            return id.startsWith("etherology:")
                    && id.substring("etherology:".length()).contains("slitherite");
        }

        private static LoadedData failed(String error) {
            return new LoadedData(
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    error,
                    List.of(),
                    Map.of(),
                    Map.of(),
                    List.of(),
                    Map.of(),
                    List.of(),
                    List.of(),
                    Map.of()
            );
        }

        private static boolean identityMapExact(
                Map<String, Object> identities,
                List<String> expectedIds
        ) {
            return identities.keySet().equals(new java.util.LinkedHashSet<>(expectedIds))
                    && identities.values().stream().allMatch(value -> value != null);
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

        private static String generateLoot(
                MinecraftServer server,
                BlockState state,
                LootTable lootTable,
                ItemStack tool
        ) {
            LootContextParameterSet parameters = new LootContextParameterSet.Builder(
                    server.getOverworld()
            )
                    .add(LootContextParameters.BLOCK_STATE, state)
                    .add(LootContextParameters.ORIGIN, Vec3d.ZERO)
                    .add(LootContextParameters.TOOL, tool)
                    .build(LootContextTypes.BLOCK);
            return stackDescription(lootTable.generateLoot(parameters, 1L));
        }

        private static String captureRecipe(
                MinecraftServer server,
                Recipe<?> recipe
        ) {
            if (recipe == null) {
                return "";
            }
            Identifier typeId = Registries.RECIPE_TYPE.getId(recipe.getType());
            ItemStack output = recipe.getOutput(server.getRegistryManager());
            Identifier resultId = Registries.ITEM.getId(output.getItem());
            return recipe.getId() + "=" + identifierString(typeId) + "->"
                    + identifierString(resultId) + "x" + output.getCount();
        }

        private static String stackDescription(List<ItemStack> stacks) {
            String description = stacks.stream()
                    .filter(stack -> !stack.isEmpty())
                    .map(stack -> identifierString(Registries.ITEM.getId(stack.getItem()))
                            + "x" + stack.getCount())
                    .sorted()
                    .collect(Collectors.joining("+"));
            return description.isEmpty() ? "none" : description;
        }

        private static List<String> expectedLootTableIds() {
            return EXPECTED_BLOCK_IDS.stream()
                    .map(id -> id.replace("etherology:", "etherology:blocks/"))
                    .sorted()
                    .toList();
        }

        private static Map<String, String> expectedSelfDrops() {
            Map<String, String> drops = new TreeMap<>();
            EXPECTED_BLOCK_IDS.forEach(id -> drops.put(id, id + "x1"));
            return Collections.unmodifiableMap(drops);
        }

        private static Map<String, String> expectedDoubleSlabDrops() {
            Map<String, String> drops = new TreeMap<>();
            EXPECTED_BLOCKS.forEach((id, spec) -> {
                if (spec.slab()) {
                    drops.put(id, id + "x1");
                }
            });
            return Collections.unmodifiableMap(drops);
        }

        private static Map<String, RecipeSpec> expectedRecipeSpecs() {
            Map<String, RecipeSpec> recipes = new LinkedHashMap<>();
            putRecipe(recipes, "chiseled_polished_slitherite", "minecraft:crafting",
                    "etherology:chiseled_polished_slitherite", 1, "building_blocks");
            putRecipe(recipes, "chiseled_polished_slitherite_bricks", "minecraft:crafting",
                    "etherology:chiseled_polished_slitherite_bricks", 1,
                    "building_blocks");
            putRecipe(recipes,
                    "chiseled_polished_slitherite_bricks_from_"
                            + "polished_slitherite_bricks_stonecutting",
                    "minecraft:stonecutting",
                    "etherology:chiseled_polished_slitherite_bricks", 1,
                    "building_blocks");
            putRecipe(recipes,
                    "chiseled_polished_slitherite_from_polished_slitherite_stonecutting",
                    "minecraft:stonecutting", "etherology:chiseled_polished_slitherite", 1,
                    "building_blocks");
            putRecipe(recipes, "cracked_polished_slitherite_bricks", "minecraft:smelting",
                    "etherology:cracked_polished_slitherite_bricks", 1,
                    "building_blocks");
            putRecipe(recipes, "polished_slitherite", "minecraft:crafting",
                    "etherology:polished_slitherite", 4, "building_blocks");
            putRecipe(recipes, "polished_slitherite_brick_slab", "minecraft:crafting",
                    "etherology:polished_slitherite_brick_slab", 6, "building_blocks");
            putRecipe(recipes,
                    "polished_slitherite_brick_slab_from_"
                            + "polished_slitherite_bricks_stonecutting",
                    "minecraft:stonecutting", "etherology:polished_slitherite_brick_slab", 2,
                    "building_blocks");
            putRecipe(recipes, "polished_slitherite_brick_stairs", "minecraft:crafting",
                    "etherology:polished_slitherite_brick_stairs", 4, "building_blocks");
            putRecipe(recipes,
                    "polished_slitherite_brick_stairs_from_"
                            + "polished_slitherite_bricks_stonecutting",
                    "minecraft:stonecutting",
                    "etherology:polished_slitherite_brick_stairs", 1,
                    "building_blocks");
            putRecipe(recipes, "polished_slitherite_brick_wall", "minecraft:crafting",
                    "etherology:polished_slitherite_brick_wall", 6, "decorations");
            putRecipe(recipes,
                    "polished_slitherite_brick_wall_from_"
                            + "polished_slitherite_bricks_stonecutting",
                    "minecraft:stonecutting", "etherology:polished_slitherite_brick_wall", 1,
                    "decorations");
            putRecipe(recipes, "polished_slitherite_bricks", "minecraft:crafting",
                    "etherology:polished_slitherite_bricks", 4, "building_blocks");
            putRecipe(recipes,
                    "polished_slitherite_bricks_from_polished_slitherite_stonecutting",
                    "minecraft:stonecutting", "etherology:polished_slitherite_bricks", 1,
                    "building_blocks");
            putRecipe(recipes, "polished_slitherite_button", "minecraft:crafting",
                    "etherology:polished_slitherite_button", 1, "redstone");
            putRecipe(recipes, "polished_slitherite_from_slitherite_stonecutting",
                    "minecraft:stonecutting", "etherology:polished_slitherite", 1,
                    "building_blocks");
            putRecipe(recipes, "polished_slitherite_pressure_plate", "minecraft:crafting",
                    "etherology:polished_slitherite_pressure_plate", 1, "redstone");
            putRecipe(recipes, "polished_slitherite_slab", "minecraft:crafting",
                    "etherology:polished_slitherite_slab", 6, "building_blocks");
            putRecipe(recipes,
                    "polished_slitherite_slab_from_polished_slitherite_stonecutting",
                    "minecraft:stonecutting", "etherology:polished_slitherite_slab", 2,
                    "building_blocks");
            putRecipe(recipes, "polished_slitherite_stairs", "minecraft:crafting",
                    "etherology:polished_slitherite_stairs", 4, "building_blocks");
            putRecipe(recipes,
                    "polished_slitherite_stairs_from_polished_slitherite_stonecutting",
                    "minecraft:stonecutting", "etherology:polished_slitherite_stairs", 1,
                    "building_blocks");
            putRecipe(recipes, "polished_slitherite_wall", "minecraft:crafting",
                    "etherology:polished_slitherite_wall", 6, "decorations");
            putRecipe(recipes,
                    "polished_slitherite_wall_from_polished_slitherite_stonecutting",
                    "minecraft:stonecutting", "etherology:polished_slitherite_wall", 1,
                    "decorations");
            putRecipe(recipes, "slitherite_slab", "minecraft:crafting",
                    "etherology:slitherite_slab", 6, "building_blocks");
            putRecipe(recipes, "slitherite_slab_from_slitherite_stonecutting",
                    "minecraft:stonecutting", "etherology:slitherite_slab", 2,
                    "building_blocks");
            putRecipe(recipes, "slitherite_stairs", "minecraft:crafting",
                    "etherology:slitherite_stairs", 4, "building_blocks");
            putRecipe(recipes, "slitherite_stairs_from_slitherite_stonecutting",
                    "minecraft:stonecutting", "etherology:slitherite_stairs", 1,
                    "building_blocks");
            putRecipe(recipes, "slitherite_wall", "minecraft:crafting",
                    "etherology:slitherite_wall", 6, "decorations");
            putRecipe(recipes, "slitherite_wall_from_slitherite_stonecutting",
                    "minecraft:stonecutting", "etherology:slitherite_wall", 1,
                    "decorations");
            return Collections.unmodifiableMap(recipes);
        }

        private static Map<String, RecipeSpec> expectedRelatedRecipeSpecs() {
            Map<String, RecipeSpec> recipes = new LinkedHashMap<>();
            putRecipe(recipes, "comparator", "minecraft:crafting",
                    "minecraft:comparator", 1, "");
            putRecipe(recipes, "repeater", "minecraft:crafting",
                    "minecraft:repeater", 1, "");
            putRecipe(recipes, "stonecutter", "minecraft:crafting",
                    "minecraft:stonecutter", 1, "");
            putRecipe(recipes, "pedestal", "minecraft:crafting",
                    "etherology:pedestal", 2, "");
            putRecipe(recipes, "unadjusted_lens", "etherology:alchemy_recipe",
                    "etherology:unadjusted_lens", 1, "");
            return Collections.unmodifiableMap(recipes);
        }

        private static void putRecipe(
                Map<String, RecipeSpec> recipes,
                String path,
                String typeId,
                String resultId,
                int resultCount,
                String advancementCategory
        ) {
            recipes.put(
                    "etherology:" + path,
                    new RecipeSpec(typeId, resultId, resultCount, advancementCategory)
            );
        }

        private static Map<String, String> expectedRecipeDescriptions(
                Map<String, RecipeSpec> specs
        ) {
            Map<String, String> descriptions = new LinkedHashMap<>();
            specs.forEach((id, spec) -> descriptions.put(id, spec.description(id)));
            return Collections.unmodifiableMap(descriptions);
        }

        private static List<String> expectedAdvancementIds() {
            return EXPECTED_RECIPE_SPECS.entrySet().stream()
                    .map(entry -> "etherology:recipes/"
                            + entry.getValue().advancementCategory() + "/"
                            + entry.getKey().replace("etherology:", ""))
                    .sorted()
                    .toList();
        }
    }

    record RecipeSpec(
            String typeId,
            String resultId,
            int resultCount,
            String advancementCategory
    ) {

        String description(String id) {
            return id + "=" + typeId + "->" + resultId + "x" + resultCount;
        }
    }

    private static String canonicalStringMap(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }
}
