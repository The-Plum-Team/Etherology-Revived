package dev.theplumteam.etherology.e2e.server;

import net.minecraft.advancement.Advancement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootDataType;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

record AttrahiteBlockProbeState(
        String captureError,
        List<String> blockIds,
        List<String> blockItemIds,
        Map<String, AttrahiteBlockEntry> entries,
        LoadedData loadedData
) {

    static final String BLOCK_REGISTRY_ID = "minecraft:block";
    static final String ITEM_REGISTRY_ID = "minecraft:item";
    static final String BLOCK_ITEM_CLASS = BlockItem.class.getName();
    static final List<String> EXPECTED_NBT_KEYS = List.of("Count", "id");
    static final Map<String, AttrahiteBlockSpec> EXPECTED_BLOCKS = expectedBlocks();
    static final List<String> EXPECTED_BLOCK_IDS = List.copyOf(EXPECTED_BLOCKS.keySet());

    static AttrahiteBlockProbeState captureTagLoaded() {
        return capture(null);
    }

    static AttrahiteBlockProbeState capture(MinecraftServer server) {
        List<String> capturedBlockIds = new ArrayList<>();
        List<String> capturedItemIds = new ArrayList<>();
        Map<String, AttrahiteBlockEntry> capturedEntries = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        EXPECTED_BLOCKS.forEach((id, spec) -> {
            AttrahiteBlockEntry entry;
            try {
                entry = captureEntry(id);
            } catch (RuntimeException exception) {
                entry = AttrahiteBlockEntry.failed(id);
                errors.add(id + "=" + exception.getClass().getName());
            }
            if (entry.blockIdentity() != null) {
                capturedBlockIds.add(entry.blockId());
            }
            if (entry.itemIdentity() != null) {
                capturedItemIds.add(entry.itemId());
            }
            capturedEntries.put(id, entry);
        });

        capturedBlockIds.sort(String::compareTo);
        capturedItemIds.sort(String::compareTo);
        return new AttrahiteBlockProbeState(
                String.join(",", errors),
                List.copyOf(capturedBlockIds),
                List.copyOf(capturedItemIds),
                Collections.unmodifiableMap(capturedEntries),
                LoadedData.capture(server)
        );
    }

    static AttrahiteBlockProbeState missing() {
        return new AttrahiteBlockProbeState(
                "not captured",
                List.of(),
                List.of(),
                Map.of(),
                LoadedData.missing()
        );
    }

    boolean hasExactRegistry() {
        return EXPECTED_BLOCK_IDS.equals(blockIds)
                && EXPECTED_BLOCK_IDS.equals(blockItemIds)
                && EXPECTED_BLOCKS.keySet().equals(entries.keySet())
                && EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
                    AttrahiteBlockEntry entry = entries.get(id);
                    return entry != null
                            && entry.blockIdentity() != null
                            && entry.itemIdentity() != null
                            && id.equals(entry.blockId())
                            && id.equals(entry.itemId());
                });
    }

    boolean hasExactRuntimeClasses() {
        return EXPECTED_BLOCKS.entrySet().stream().allMatch(expected -> {
            AttrahiteBlockEntry entry = entries.get(expected.getKey());
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

    boolean hasExactProperties() {
        return EXPECTED_BLOCKS.entrySet().stream().allMatch(expected -> {
            AttrahiteBlockEntry entry = entries.get(expected.getKey());
            AttrahiteBlockSpec spec = expected.getValue();
            return entry != null
                    && Float.compare(spec.hardness(), entry.hardness()) == 0
                    && Float.compare(
                            spec.blastResistance(),
                            entry.blastResistance()
                    ) == 0
                    && spec.mapColorId() == entry.mapColorId()
                    && spec.soundGroup().equals(entry.soundGroup())
                    && entry.toolRequired()
                    && entry.luminance() == 0
                    && entry.opaque()
                    && spec.fullCube() == entry.fullCube()
                    && spec.transparent() == entry.transparent()
                    && "NORMAL".equals(entry.pistonBehavior())
                    && spec.stateCount() == entry.stateCount()
                    && spec.defaultState().equals(entry.defaultState())
                    && entry.maxCount() == 64;
        });
    }

    boolean hasExactTagMemberships() {
        return EXPECTED_BLOCKS.entrySet().stream().allMatch(expected -> {
            AttrahiteBlockEntry entry = entries.get(expected.getKey());
            AttrahiteBlockSpec spec = expected.getValue();
            return entry != null
                    && entry.pickaxeMineable()
                    && spec.needsStoneTool() == entry.needsStoneTool()
                    && spec.blockSlab() == entry.blockSlab()
                    && spec.itemSlab() == entry.itemSlab()
                    && spec.blockStairs() == entry.blockStairs()
                    && spec.itemStairs() == entry.itemStairs();
        });
    }

    boolean hasExactStackNbtRoundTrips() {
        return allEntriesMatch(entry -> entry.roundTripExact()
                && entry.itemId().equals(entry.serializedId())
                && entry.maxCount() == entry.serializedCount()
                && EXPECTED_NBT_KEYS.equals(entry.serializedKeys()));
    }

    boolean hasExactCoreContract() {
        return captureError.isEmpty()
                && hasExactRegistry()
                && hasExactRuntimeClasses()
                && hasExactBlockItemMappings()
                && hasExactProperties()
                && hasExactTagMemberships()
                && hasExactStackNbtRoundTrips()
                && expectedCanonicalProperties().equals(canonicalProperties())
                && expectedCanonicalTags().equals(canonicalTags())
                && expectedCanonicalSaveRepresentations().equals(
                        canonicalSaveRepresentations()
                );
    }

    boolean hasExactContract() {
        return hasExactCoreContract() && loadedData.hasExactContract();
    }

    boolean sameCoreState(AttrahiteBlockProbeState other) {
        return hasSameRegistry(other)
                && hasSameProperties(other)
                && hasSameTags(other)
                && hasSameStackNbt(other);
    }

    boolean sameStateAtServerStarted(AttrahiteBlockProbeState other) {
        return sameCoreState(other)
                && loadedData.sameOutcome(other.loadedData)
                && loadedData.sameInstances(other.loadedData);
    }

    boolean hasSameRegistry(AttrahiteBlockProbeState other) {
        if (!captureError.equals(other.captureError)
                || !blockIds.equals(other.blockIds)
                || !blockItemIds.equals(other.blockItemIds)
                || !entries.keySet().equals(other.entries.keySet())) {
            return false;
        }
        return EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
            AttrahiteBlockEntry entry = entries.get(id);
            AttrahiteBlockEntry otherEntry = other.entries.get(id);
            return entry != null
                    && otherEntry != null
                    && entry.blockIdentity() == otherEntry.blockIdentity()
                    && entry.itemIdentity() == otherEntry.itemIdentity()
                    && entry.blockId().equals(otherEntry.blockId())
                    && entry.itemId().equals(otherEntry.itemId());
        });
    }

    boolean hasSameProperties(AttrahiteBlockProbeState other) {
        return EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
            AttrahiteBlockEntry entry = entries.get(id);
            AttrahiteBlockEntry otherEntry = other.entries.get(id);
            return entry != null
                    && otherEntry != null
                    && entry.blockClass().equals(otherEntry.blockClass())
                    && entry.itemClass().equals(otherEntry.itemClass())
                    && entry.blockItem() == otherEntry.blockItem()
                    && entry.blockItemMapsToBlock() == otherEntry.blockItemMapsToBlock()
                    && entry.blockAsItemMatches() == otherEntry.blockAsItemMatches()
                    && Float.compare(entry.hardness(), otherEntry.hardness()) == 0
                    && Float.compare(
                            entry.blastResistance(),
                            otherEntry.blastResistance()
                    ) == 0
                    && entry.mapColorId() == otherEntry.mapColorId()
                    && entry.soundGroup().equals(otherEntry.soundGroup())
                    && entry.toolRequired() == otherEntry.toolRequired()
                    && entry.luminance() == otherEntry.luminance()
                    && entry.opaque() == otherEntry.opaque()
                    && entry.fullCube() == otherEntry.fullCube()
                    && entry.transparent() == otherEntry.transparent()
                    && entry.pistonBehavior().equals(otherEntry.pistonBehavior())
                    && entry.stateCount() == otherEntry.stateCount()
                    && entry.defaultState().equals(otherEntry.defaultState())
                    && entry.maxCount() == otherEntry.maxCount();
        });
    }

    boolean hasSameTags(AttrahiteBlockProbeState other) {
        return EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
            AttrahiteBlockEntry entry = entries.get(id);
            AttrahiteBlockEntry otherEntry = other.entries.get(id);
            return entry != null
                    && otherEntry != null
                    && entry.pickaxeMineable() == otherEntry.pickaxeMineable()
                    && entry.needsStoneTool() == otherEntry.needsStoneTool()
                    && entry.blockSlab() == otherEntry.blockSlab()
                    && entry.itemSlab() == otherEntry.itemSlab()
                    && entry.blockStairs() == otherEntry.blockStairs()
                    && entry.itemStairs() == otherEntry.itemStairs();
        });
    }

    boolean hasSameStackNbt(AttrahiteBlockProbeState other) {
        return EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
            AttrahiteBlockEntry entry = entries.get(id);
            AttrahiteBlockEntry otherEntry = other.entries.get(id);
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

    boolean hasReloadedDataOutcome(AttrahiteBlockProbeState other) {
        return loadedData.sameOutcome(other.loadedData);
    }

    boolean hasFreshReloadedData(AttrahiteBlockProbeState other) {
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

    String canonicalSaveRepresentations() {
        return entries.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().saveRepresentation())
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

    static String expectedCanonicalSaveRepresentations() {
        return EXPECTED_BLOCK_IDS.stream()
                .map(id -> id + "=" + saveRepresentation(id, 64, EXPECTED_NBT_KEYS))
                .collect(Collectors.joining(","));
    }

    static PlacementState placeIn(ServerWorld world) {
        if (world == null) {
            return PlacementState.failed("missing overworld");
        }
        Map<String, String> positions = new LinkedHashMap<>();
        Map<String, String> placedBlockIds = new LinkedHashMap<>();
        Map<String, String> placedStates = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int index = 0;
        for (String id : EXPECTED_BLOCK_IDS) {
            BlockPos position = placementPosition(index);
            positions.put(id, position(position));
            Block block = registeredBlock(id);
            try {
                boolean changed = block != null && world.setBlockState(
                        position,
                        block.getDefaultState(),
                        Block.NOTIFY_ALL
                );
                BlockState placedState = world.getBlockState(position);
                Identifier placedId = Registries.BLOCK.getId(placedState.getBlock());
                placedBlockIds.put(id, identifierString(placedId));
                placedStates.put(id, stateDescription(placedState));
                if (!changed && placedState.getBlock() != block) {
                    errors.add(id + "=placement_rejected");
                }
            } catch (RuntimeException exception) {
                placedBlockIds.put(id, "");
                placedStates.put(id, "");
                errors.add(id + "=" + exception.getClass().getName());
            }
            index++;
        }
        return new PlacementState(
                String.join(",", errors),
                Collections.unmodifiableMap(positions),
                Collections.unmodifiableMap(placedBlockIds),
                Collections.unmodifiableMap(placedStates)
        );
    }

    static PlacementState capturePlacement(ServerWorld world) {
        if (world == null) {
            return PlacementState.failed("missing overworld");
        }
        Map<String, String> placedBlockIds = new LinkedHashMap<>();
        Map<String, String> placedStates = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int index = 0;
        for (String id : EXPECTED_BLOCK_IDS) {
            BlockPos position = placementPosition(index);
            try {
                BlockState placedState = world.getBlockState(position);
                Identifier placedId = Registries.BLOCK.getId(placedState.getBlock());
                placedBlockIds.put(id, identifierString(placedId));
                placedStates.put(id, stateDescription(placedState));
            } catch (RuntimeException exception) {
                placedBlockIds.put(id, "");
                placedStates.put(id, "");
                errors.add(id + "=" + exception.getClass().getName());
            }
            index++;
        }
        return new PlacementState(
                String.join(",", errors),
                expectedPlacementPositions(),
                Collections.unmodifiableMap(placedBlockIds),
                Collections.unmodifiableMap(placedStates)
        );
    }

    private boolean allEntriesMatch(Predicate<AttrahiteBlockEntry> predicate) {
        return entries.size() == EXPECTED_BLOCKS.size()
                && entries.values().stream().allMatch(predicate);
    }

    private static AttrahiteBlockEntry captureEntry(String id) {
        Identifier expectedId = Identifier.parse(id);
        Block block = Registries.BLOCK.getOrEmpty(expectedId).orElse(null);
        Item item = Registries.ITEM.getOrEmpty(expectedId).orElse(null);
        if (block == null || item == null) {
            return AttrahiteBlockEntry.failed(id);
        }

        Identifier blockId = Registries.BLOCK.getId(block);
        Identifier itemId = Registries.ITEM.getId(item);
        BlockState state = block.getDefaultState();
        BlockItem blockItem = item instanceof BlockItem candidate ? candidate : null;
        ItemStack stack = new ItemStack(item, item.getMaxCount());
        NbtCompound serialized = stack.writeNbt(new NbtCompound());
        List<String> serializedKeys = serialized.getKeys().stream().sorted().toList();
        String serializedId = serialized.getString("id");
        int serializedCount = Byte.toUnsignedInt(serialized.getByte("Count"));
        ItemStack restored = ItemStack.fromNbt(serialized.copy());
        Identifier restoredId = Registries.ITEM.getId(restored.getItem());
        boolean roundTripExact = restored.getItem() == item
                && restoredId != null
                && itemId != null
                && itemId.equals(restoredId)
                && restored.getCount() == item.getMaxCount();
        String actualItemId = identifierString(itemId);
        return new AttrahiteBlockEntry(
                block,
                item,
                identifierString(blockId),
                actualItemId,
                block.getClass().getName(),
                item.getClass().getName(),
                blockItem != null,
                blockItem != null && blockItem.getBlock() == block,
                Block.getBlockFromItem(item) == block && block.asItem() == item,
                state.getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN),
                block.getBlastResistance(),
                state.getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).id,
                soundGroupName(state.getSoundGroup()),
                state.isToolRequired(),
                state.getLuminance(),
                state.isOpaque(),
                state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN),
                state.isTransparent(EmptyBlockView.INSTANCE, BlockPos.ORIGIN),
                state.getPistonBehavior().name(),
                block.getStateManager().getStates().size(),
                stateDescription(state),
                state.isIn(BlockTags.PICKAXE_MINEABLE),
                state.isIn(BlockTags.NEEDS_STONE_TOOL),
                state.isIn(BlockTags.SLABS),
                stack.isIn(ItemTags.SLABS),
                state.isIn(BlockTags.STAIRS),
                stack.isIn(ItemTags.STAIRS),
                item.getMaxCount(),
                serializedId,
                serializedCount,
                serializedKeys,
                roundTripExact,
                saveRepresentation(actualItemId, item.getMaxCount(), serializedKeys)
        );
    }

    private static String soundGroupName(BlockSoundGroup soundGroup) {
        if (soundGroup == BlockSoundGroup.GILDED_BLACKSTONE) {
            return "gilded_blackstone";
        }
        if (soundGroup == BlockSoundGroup.STONE) {
            return "stone";
        }
        return "other";
    }

    private static String propertySummary(AttrahiteBlockSpec spec) {
        return spec.blockClass() + "|item_class=" + BLOCK_ITEM_CLASS
                + "|hardness=" + Float.toString(spec.hardness())
                + "|blast=" + Float.toString(spec.blastResistance())
                + "|map_color=" + spec.mapColorId()
                + "|sound=" + spec.soundGroup()
                + "|tool_required=true|luminance=0|opaque=true"
                + "|full_cube=" + spec.fullCube()
                + "|transparent=" + spec.transparent()
                + "|piston=NORMAL|state_count=" + spec.stateCount()
                + "|default_state=" + spec.defaultState()
                + "|max=64";
    }

    private static String tagSummary(AttrahiteBlockSpec spec) {
        return "pickaxe=true|needs_stone=" + spec.needsStoneTool()
                + "|block_slab=" + spec.blockSlab()
                + "|item_slab=" + spec.itemSlab()
                + "|block_stairs=" + spec.blockStairs()
                + "|item_stairs=" + spec.itemStairs();
    }

    private static String saveRepresentation(
            String id,
            int maxCount,
            List<String> serializedKeys
    ) {
        return id + "|item_class=" + BLOCK_ITEM_CLASS + "|max=" + maxCount
                + "|nbt_id=" + id + "|nbt_count=" + maxCount
                + "|nbt_keys=" + String.join("+", serializedKeys);
    }

    private static Block registeredBlock(String id) {
        return Registries.BLOCK.getOrEmpty(Identifier.parse(id)).orElse(null);
    }

    private static BlockPos placementPosition(int index) {
        return new BlockPos(40 + index, 200, 16);
    }

    private static Map<String, String> expectedPlacementPositions() {
        Map<String, String> positions = new LinkedHashMap<>();
        int index = 0;
        for (String id : EXPECTED_BLOCK_IDS) {
            positions.put(id, position(placementPosition(index)));
            index++;
        }
        return Collections.unmodifiableMap(positions);
    }

    private static Map<String, String> expectedPlacedStates() {
        Map<String, String> states = new LinkedHashMap<>();
        EXPECTED_BLOCKS.forEach((id, spec) -> states.put(id, spec.defaultState()));
        return Collections.unmodifiableMap(states);
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

    private static Map<String, AttrahiteBlockSpec> expectedBlocks() {
        Map<String, AttrahiteBlockSpec> expected = new TreeMap<>();
        expected.put(
                "etherology:attrahite",
                new AttrahiteBlockSpec(
                        Block.class.getName(),
                        1.5F,
                        6.0F,
                        11,
                        "gilded_blackstone",
                        true,
                        false,
                        1,
                        "etherology:attrahite",
                        true,
                        false,
                        false,
                        false,
                        false
                )
        );
        expected.put(
                "etherology:attrahite_bricks",
                new AttrahiteBlockSpec(
                        Block.class.getName(),
                        1.5F,
                        6.0F,
                        11,
                        "stone",
                        true,
                        false,
                        1,
                        "etherology:attrahite_bricks",
                        false,
                        false,
                        false,
                        false,
                        false
                )
        );
        expected.put(
                "etherology:attrahite_brick_slab",
                new AttrahiteBlockSpec(
                        SlabBlock.class.getName(),
                        2.0F,
                        6.0F,
                        11,
                        "stone",
                        false,
                        true,
                        6,
                        "etherology:attrahite_brick_slab[type=bottom,waterlogged=false]",
                        false,
                        true,
                        true,
                        false,
                        false
                )
        );
        expected.put(
                "etherology:attrahite_brick_stairs",
                new AttrahiteBlockSpec(
                        StairsBlock.class.getName(),
                        1.5F,
                        6.0F,
                        11,
                        "stone",
                        false,
                        true,
                        80,
                        "etherology:attrahite_brick_stairs"
                                + "[facing=north,half=bottom,shape=straight,waterlogged=false]",
                        false,
                        false,
                        false,
                        true,
                        true
                )
        );
        return Collections.unmodifiableMap(expected);
    }

    record AttrahiteBlockSpec(
            String blockClass,
            float hardness,
            float blastResistance,
            int mapColorId,
            String soundGroup,
            boolean fullCube,
            boolean transparent,
            int stateCount,
            String defaultState,
            boolean needsStoneTool,
            boolean blockSlab,
            boolean itemSlab,
            boolean blockStairs,
            boolean itemStairs
    ) {
    }

    record AttrahiteBlockEntry(
            Object blockIdentity,
            Object itemIdentity,
            String blockId,
            String itemId,
            String blockClass,
            String itemClass,
            boolean blockItem,
            boolean blockItemMapsToBlock,
            boolean blockAsItemMatches,
            float hardness,
            float blastResistance,
            int mapColorId,
            String soundGroup,
            boolean toolRequired,
            int luminance,
            boolean opaque,
            boolean fullCube,
            boolean transparent,
            String pistonBehavior,
            int stateCount,
            String defaultState,
            boolean pickaxeMineable,
            boolean needsStoneTool,
            boolean blockSlab,
            boolean itemSlab,
            boolean blockStairs,
            boolean itemStairs,
            int maxCount,
            String serializedId,
            int serializedCount,
            List<String> serializedKeys,
            boolean roundTripExact,
            String saveRepresentation
    ) {

        static AttrahiteBlockEntry failed(String id) {
            return new AttrahiteBlockEntry(
                    null,
                    null,
                    id,
                    id,
                    "",
                    "",
                    false,
                    false,
                    false,
                    -1.0F,
                    -1.0F,
                    -1,
                    "",
                    false,
                    -1,
                    false,
                    false,
                    false,
                    "",
                    -1,
                    "",
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    -1,
                    "",
                    -1,
                    List.of(),
                    false,
                    ""
            );
        }

        String propertySummary() {
            return blockClass + "|item_class=" + itemClass
                    + "|hardness=" + Float.toString(hardness)
                    + "|blast=" + Float.toString(blastResistance)
                    + "|map_color=" + mapColorId + "|sound=" + soundGroup
                    + "|tool_required=" + toolRequired + "|luminance=" + luminance
                    + "|opaque=" + opaque + "|full_cube=" + fullCube
                    + "|transparent=" + transparent + "|piston=" + pistonBehavior
                    + "|state_count=" + stateCount + "|default_state=" + defaultState
                    + "|max=" + maxCount;
        }

        String tagSummary() {
            return "pickaxe=" + pickaxeMineable + "|needs_stone=" + needsStoneTool
                    + "|block_slab=" + blockSlab + "|item_slab=" + itemSlab
                    + "|block_stairs=" + blockStairs + "|item_stairs=" + itemStairs;
        }
    }

    record PlacementState(
            String captureError,
            Map<String, String> positions,
            Map<String, String> placedBlockIds,
            Map<String, String> placedStates
    ) {

        static PlacementState missing() {
            return failed("not captured");
        }

        static PlacementState failed(String error) {
            return new PlacementState(error, Map.of(), Map.of(), Map.of());
        }

        boolean hasExactPlacement() {
            return captureError.isEmpty()
                    && expectedPlacementPositions().equals(positions)
                    && expectedPlacedBlockIds().equals(placedBlockIds)
                    && expectedPlacedStates().equals(placedStates);
        }

        boolean samePlacement(PlacementState other) {
            return captureError.equals(other.captureError)
                    && positions.equals(other.positions)
                    && placedBlockIds.equals(other.placedBlockIds)
                    && placedStates.equals(other.placedStates);
        }

        String canonicalPositions() {
            return canonicalStringMap(positions);
        }

        String canonicalPlacedBlockIds() {
            return canonicalStringMap(placedBlockIds);
        }

        String canonicalPlacedStates() {
            return canonicalStringMap(placedStates);
        }

        static String expectedCanonicalPositions() {
            return canonicalStringMap(expectedPlacementPositions());
        }

        static String expectedCanonicalPlacedBlockIds() {
            return canonicalStringMap(expectedPlacedBlockIds());
        }

        static String expectedCanonicalPlacedStates() {
            return canonicalStringMap(expectedPlacedStates());
        }

        private static Map<String, String> expectedPlacedBlockIds() {
            Map<String, String> ids = new LinkedHashMap<>();
            EXPECTED_BLOCK_IDS.forEach(id -> ids.put(id, id));
            return Collections.unmodifiableMap(ids);
        }
    }

    record LoadedData(
            Map<String, Object> lootTableIdentities,
            Map<String, Object> recipeIdentities,
            Map<String, Object> advancementIdentities,
            String captureError,
            List<String> lootTableIds,
            Map<String, String> standardLoot,
            String rawSilkTouchLoot,
            Map<String, String> rawFortuneLoot,
            List<String> recipeIds,
            Map<String, String> recipes,
            List<String> advancementIds,
            Map<String, String> advancements,
            boolean recipeMatchesAndCraftsExact
    ) {

        static final List<String> EXPECTED_LOOT_TABLE_IDS = List.of(
                "etherology:blocks/attrahite",
                "etherology:blocks/attrahite_brick_slab",
                "etherology:blocks/attrahite_brick_stairs",
                "etherology:blocks/attrahite_bricks"
        );
        static final List<String> EXPECTED_RECIPE_IDS = List.of(
                "etherology:attrahite_brick",
                "etherology:attrahite_brick_slab",
                "etherology:attrahite_brick_slab_from_attrahite_bricks_stonecutting",
                "etherology:attrahite_brick_stairs",
                "etherology:attrahite_brick_stairs_from_attrahite_bricks_stonecutting",
                "etherology:attrahite_bricks",
                "etherology:azel_ingot",
                "etherology:azel_ingot_from_blasting",
                "etherology:raw_azel"
        );
        static final List<String> EXPECTED_ADVANCEMENT_IDS = List.of(
                "etherology:recipes/building_blocks/attrahite_brick_slab",
                "etherology:recipes/building_blocks/"
                        + "attrahite_brick_slab_from_attrahite_bricks_stonecutting",
                "etherology:recipes/building_blocks/attrahite_brick_stairs",
                "etherology:recipes/building_blocks/"
                        + "attrahite_brick_stairs_from_attrahite_bricks_stonecutting",
                "etherology:recipes/building_blocks/attrahite_bricks",
                "etherology:recipes/misc/attrahite_brick",
                "etherology:recipes/misc/azel_ingot",
                "etherology:recipes/misc/azel_ingot_from_blasting",
                "etherology:recipes/misc/raw_azel"
        );
        static final Map<String, String> EXPECTED_STANDARD_LOOT = expectedStandardLoot();
        static final String EXPECTED_RAW_SILK_TOUCH_LOOT = "etherology:attrahitex1";
        static final Map<String, String> EXPECTED_RAW_FORTUNE_LOOT =
                expectedRawFortuneLoot();
        static final Map<String, String> EXPECTED_RECIPES = expectedRecipes();
        static final Map<String, String> EXPECTED_ADVANCEMENTS = expectedAdvancements();
        private static final List<Long> RAW_LOOT_SEEDS = List.of(
                1L,
                4096L,
                4224L,
                4640L,
                7168L
        );

        static LoadedData capture(MinecraftServer server) {
            if (server == null) {
                return missing();
            }
            try {
                List<String> lootTableIds = server.getLootManager()
                        .getIds(LootDataType.LOOT_TABLES)
                        .stream()
                        .filter(LoadedData::isAttrahiteLootTableId)
                        .map(Identifier::toString)
                        .sorted()
                        .toList();
                Map<String, Object> lootTableIdentities = new LinkedHashMap<>();
                for (String id : lootTableIds) {
                    Identifier identifier = Identifier.parse(id);
                    lootTableIdentities.put(
                            id,
                            server.getLootManager().getLootTable(identifier)
                    );
                }

                Map<String, String> standardLoot = new TreeMap<>();
                for (String blockId : EXPECTED_BLOCK_IDS) {
                    if ("etherology:attrahite".equals(blockId)) {
                        continue;
                    }
                    Block block = requiredBlock(blockId);
                    LootTable lootTable = server.getLootManager().getLootTable(
                            block.getLootTableId()
                    );
                    standardLoot.put(
                            blockId,
                            generateLoot(
                                    server,
                                    block,
                                    lootTable,
                                    new ItemStack(Items.IRON_PICKAXE),
                                    1L
                            )
                    );
                }

                Block rawAttrahite = requiredBlock("etherology:attrahite");
                LootTable rawLootTable = server.getLootManager().getLootTable(
                        rawAttrahite.getLootTableId()
                );
                ItemStack silkTouchTool = new ItemStack(Items.IRON_PICKAXE);
                silkTouchTool.addEnchantment(Enchantments.SILK_TOUCH, 1);
                String rawSilkTouchLoot = generateLoot(
                        server,
                        rawAttrahite,
                        rawLootTable,
                        silkTouchTool,
                        1L
                );
                Map<String, String> rawFortuneLoot = new TreeMap<>();
                for (int fortuneLevel = 0; fortuneLevel <= 3; fortuneLevel++) {
                    ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
                    if (fortuneLevel > 0) {
                        tool.addEnchantment(Enchantments.FORTUNE, fortuneLevel);
                    }
                    List<String> seededLoot = new ArrayList<>();
                    for (long seed : RAW_LOOT_SEEDS) {
                        seededLoot.add(seed + "=" + generateLoot(
                                server,
                                rawAttrahite,
                                rawLootTable,
                                tool,
                                seed
                        ));
                    }
                    rawFortuneLoot.put(
                            Integer.toString(fortuneLevel),
                            String.join(",", seededLoot)
                    );
                }

                List<String> recipeIds = server.getRecipeManager()
                        .keys()
                        .map(Identifier::toString)
                        .filter(EXPECTED_RECIPE_IDS::contains)
                        .sorted()
                        .toList();
                Map<String, Object> recipeIdentities = new LinkedHashMap<>();
                Map<String, String> recipes = new TreeMap<>();
                boolean recipesMatchAndCraft = true;
                for (String id : recipeIds) {
                    Recipe<?> recipe = server.getRecipeManager()
                            .get(Identifier.parse(id))
                            .orElse(null);
                    recipeIdentities.put(id, recipe);
                    RecipeCapture capture = captureRecipe(server, recipe);
                    recipes.put(id, capture.description());
                    recipesMatchAndCraft &= capture.matchesAndCrafts();
                }

                List<String> advancementIds = server.getAdvancementLoader()
                        .getAdvancements()
                        .stream()
                        .map(Advancement::getId)
                        .map(Identifier::toString)
                        .filter(EXPECTED_ADVANCEMENT_IDS::contains)
                        .sorted()
                        .toList();
                Map<String, Object> advancementIdentities = new LinkedHashMap<>();
                Map<String, String> advancements = new TreeMap<>();
                for (String id : advancementIds) {
                    Advancement advancement = server.getAdvancementLoader().get(
                            Identifier.parse(id)
                    );
                    advancementIdentities.put(id, advancement);
                    advancements.put(id, advancementDescription(advancement));
                }
                return new LoadedData(
                        Collections.unmodifiableMap(lootTableIdentities),
                        Collections.unmodifiableMap(recipeIdentities),
                        Collections.unmodifiableMap(advancementIdentities),
                        "",
                        lootTableIds,
                        Collections.unmodifiableMap(standardLoot),
                        rawSilkTouchLoot,
                        Collections.unmodifiableMap(rawFortuneLoot),
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
            return EXPECTED_LOOT_TABLE_IDS.equals(lootTableIds)
                    && lootTableIdentities.size() == EXPECTED_LOOT_TABLE_IDS.size()
                    && lootTableIdentities.values().stream().allMatch(value -> value != null)
                    && EXPECTED_STANDARD_LOOT.equals(standardLoot)
                    && EXPECTED_RAW_SILK_TOUCH_LOOT.equals(rawSilkTouchLoot)
                    && EXPECTED_RAW_FORTUNE_LOOT.equals(rawFortuneLoot);
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
                    && lootTableIds.equals(other.lootTableIds)
                    && standardLoot.equals(other.standardLoot)
                    && rawSilkTouchLoot.equals(other.rawSilkTouchLoot)
                    && rawFortuneLoot.equals(other.rawFortuneLoot)
                    && recipeIds.equals(other.recipeIds)
                    && recipes.equals(other.recipes)
                    && advancementIds.equals(other.advancementIds)
                    && advancements.equals(other.advancements)
                    && recipeMatchesAndCraftsExact == other.recipeMatchesAndCraftsExact;
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

        String canonicalStandardLoot() {
            return canonicalStringMap(standardLoot);
        }

        String canonicalRawFortuneLoot() {
            return canonicalStringMap(rawFortuneLoot);
        }

        String canonicalRecipes() {
            return canonicalStringMap(recipes);
        }

        String canonicalAdvancements() {
            return canonicalStringMap(advancements);
        }

        static String expectedCanonicalStandardLoot() {
            return canonicalStringMap(EXPECTED_STANDARD_LOOT);
        }

        static String expectedCanonicalRawFortuneLoot() {
            return canonicalStringMap(EXPECTED_RAW_FORTUNE_LOOT);
        }

        static String expectedCanonicalRecipes() {
            return canonicalStringMap(EXPECTED_RECIPES);
        }

        static String expectedCanonicalAdvancements() {
            return canonicalStringMap(EXPECTED_ADVANCEMENTS);
        }

        private static LoadedData failed(String error) {
            return new LoadedData(
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    error,
                    List.of(),
                    Map.of(),
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

        private static boolean isAttrahiteLootTableId(Identifier identifier) {
            return "etherology".equals(identifier.getNamespace())
                    && identifier.getPath().startsWith("blocks/attrahite");
        }

        private static String generateLoot(
                MinecraftServer server,
                Block block,
                LootTable lootTable,
                ItemStack tool,
                long seed
        ) {
            LootContextParameterSet parameters = new LootContextParameterSet.Builder(
                    server.getOverworld()
            )
                    .add(LootContextParameters.BLOCK_STATE, block.getDefaultState())
                    .add(LootContextParameters.ORIGIN, Vec3d.ZERO)
                    .add(LootContextParameters.TOOL, tool)
                    .build(LootContextTypes.BLOCK);
            return stackDescription(lootTable.generateLoot(parameters, seed));
        }

        private static RecipeCapture captureRecipe(
                MinecraftServer server,
                Recipe<?> recipe
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
                Item input = expectedCookingInput(recipeId);
                SimpleInventory inventory = new SimpleInventory(new ItemStack(input));
                boolean inputExact = cookingRecipe.getIngredients().size() == 1
                        && cookingRecipe.getIngredients().get(0).test(new ItemStack(input))
                        && !cookingRecipe.getIngredients().get(0).test(
                                new ItemStack(Items.STONE)
                        );
                boolean matches = cookingRecipe.matches(
                        inventory,
                        server.getOverworld()
                );
                ItemStack crafted = cookingRecipe.craft(
                        inventory,
                        server.getRegistryManager()
                );
                String description = common
                        + "|input=" + itemId(input)
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
                CraftingInventory inventory = craftingInventory(recipeId);
                boolean matches = shapedRecipe.matches(inventory, server.getOverworld());
                ItemStack crafted = shapedRecipe.craft(
                        inventory,
                        server.getRegistryManager()
                );
                String ingredients = shapedRecipe.getIngredients().stream()
                        .map(LoadedData::ingredientDescription)
                        .collect(Collectors.joining(","));
                boolean inputExact = expectedIngredientPattern(recipeId).equals(ingredients);
                String description = common
                        + "|input=" + ingredients
                        + "|input_exact=" + inputExact
                        + "|category=" + shapedRecipe.getCategory().asString()
                        + "|width=" + shapedRecipe.getWidth()
                        + "|height=" + shapedRecipe.getHeight()
                        + "|matches=" + matches
                        + "|crafted=" + stackDescription(List.of(crafted));
                return new RecipeCapture(
                        description,
                        inputExact
                                && shapedRecipe.getWidth() == inventory.getWidth()
                                && shapedRecipe.getHeight() == inventory.getHeight()
                                && matches
                                && sameStack(declaredOutput, crafted)
                );
            }
            if (recipe instanceof StonecuttingRecipe stonecuttingRecipe) {
                Item input = requiredItem("etherology:attrahite_bricks");
                SimpleInventory inventory = new SimpleInventory(new ItemStack(input));
                boolean inputExact = stonecuttingRecipe.getIngredients().size() == 1
                        && stonecuttingRecipe.getIngredients().get(0).test(
                                new ItemStack(input)
                        )
                        && !stonecuttingRecipe.getIngredients().get(0).test(
                                new ItemStack(Items.STONE)
                        );
                boolean matches = stonecuttingRecipe.matches(
                        inventory,
                        server.getOverworld()
                );
                ItemStack crafted = stonecuttingRecipe.craft(
                        inventory,
                        server.getRegistryManager()
                );
                String description = common
                        + "|input=" + itemId(input)
                        + "|input_exact=" + inputExact
                        + "|matches=" + matches
                        + "|crafted=" + stackDescription(List.of(crafted));
                return new RecipeCapture(
                        description,
                        inputExact && matches && sameStack(declaredOutput, crafted)
                );
            }
            return new RecipeCapture(common, false);
        }

        private static CraftingInventory craftingInventory(String recipeId) {
            List<String> ingredientIds = expectedIngredientIds(recipeId);
            int width = expectedRecipeWidth(recipeId);
            int height = expectedRecipeHeight(recipeId);
            DefaultedList<ItemStack> stacks = DefaultedList.ofSize(
                    width * height,
                    ItemStack.EMPTY
            );
            for (int index = 0; index < ingredientIds.size(); index++) {
                String ingredientId = ingredientIds.get(index);
                if (!"empty".equals(ingredientId)) {
                    stacks.set(index, new ItemStack(requiredItem(ingredientId)));
                }
            }
            return new CraftingInventory(null, width, height, stacks);
        }

        private static String ingredientDescription(
                net.minecraft.recipe.Ingredient ingredient
        ) {
            if (ingredient.isEmpty()) {
                return "empty";
            }
            for (String id : List.of(
                    "etherology:attrahite_brick",
                    "etherology:attrahite_bricks",
                    "etherology:enriched_attrahite",
                    "minecraft:calcite"
            )) {
                if (ingredient.test(new ItemStack(requiredItem(id)))) {
                    return id;
                }
            }
            return "other";
        }

        private static Item expectedCookingInput(String recipeId) {
            if ("etherology:attrahite_brick".equals(recipeId)) {
                return requiredItem("etherology:attrahite");
            }
            return requiredItem("etherology:raw_azel");
        }

        private static String expectedIngredientPattern(String recipeId) {
            return String.join(",", expectedIngredientIds(recipeId));
        }

        private static List<String> expectedIngredientIds(String recipeId) {
            return switch (recipeId) {
                case "etherology:attrahite_bricks" -> List.of(
                        "etherology:attrahite_brick",
                        "etherology:attrahite_brick",
                        "etherology:attrahite_brick",
                        "etherology:attrahite_brick"
                );
                case "etherology:attrahite_brick_slab" -> List.of(
                        "etherology:attrahite_bricks",
                        "etherology:attrahite_bricks",
                        "etherology:attrahite_bricks"
                );
                case "etherology:attrahite_brick_stairs" -> List.of(
                        "etherology:attrahite_bricks",
                        "empty",
                        "empty",
                        "etherology:attrahite_bricks",
                        "etherology:attrahite_bricks",
                        "empty",
                        "etherology:attrahite_bricks",
                        "etherology:attrahite_bricks",
                        "etherology:attrahite_bricks"
                );
                case "etherology:raw_azel" -> List.of(
                        "etherology:enriched_attrahite",
                        "minecraft:calcite",
                        "minecraft:calcite",
                        "etherology:enriched_attrahite"
                );
                default -> throw new IllegalArgumentException(
                        "Unexpected shaped recipe " + recipeId
                );
            };
        }

        private static int expectedRecipeWidth(String recipeId) {
            return "etherology:attrahite_brick_slab".equals(recipeId) ? 3 :
                    "etherology:attrahite_brick_stairs".equals(recipeId) ? 3 : 2;
        }

        private static int expectedRecipeHeight(String recipeId) {
            return "etherology:attrahite_brick_slab".equals(recipeId) ? 1 :
                    "etherology:attrahite_brick_stairs".equals(recipeId) ? 3 : 2;
        }

        private static String advancementDescription(Advancement advancement) {
            if (advancement == null) {
                return "";
            }
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

        private static Block requiredBlock(String id) {
            Block block = registeredBlock(id);
            if (block == null) {
                throw new IllegalStateException("Missing block " + id);
            }
            return block;
        }

        private static Item requiredItem(String id) {
            return Registries.ITEM.getOrEmpty(Identifier.parse(id)).orElseThrow(
                    () -> new IllegalStateException("Missing item " + id)
            );
        }

        private static String itemId(Item item) {
            return identifierString(Registries.ITEM.getId(item));
        }

        private static boolean sameStack(ItemStack first, ItemStack second) {
            return first.getItem() == second.getItem()
                    && first.getCount() == second.getCount();
        }

        private static String stackDescription(List<ItemStack> stacks) {
            if (stacks.isEmpty()) {
                return "none";
            }
            return stacks.stream()
                    .filter(stack -> !stack.isEmpty())
                    .map(stack -> itemId(stack.getItem()) + "x" + stack.getCount())
                    .sorted()
                    .collect(Collectors.joining("+"));
        }

        private static Map<String, String> expectedStandardLoot() {
            Map<String, String> loot = new TreeMap<>();
            loot.put(
                    "etherology:attrahite_brick_slab",
                    "etherology:attrahite_brick_slabx1"
            );
            loot.put(
                    "etherology:attrahite_brick_stairs",
                    "etherology:attrahite_brick_stairsx1"
            );
            loot.put(
                    "etherology:attrahite_bricks",
                    "etherology:attrahite_bricksx1"
            );
            return Collections.unmodifiableMap(loot);
        }

        private static Map<String, String> expectedRawFortuneLoot() {
            String none = "none";
            String enriched = "etherology:enriched_attrahitex1";
            Map<String, String> loot = new TreeMap<>();
            loot.put(
                    "0",
                    "1=" + none + ",4096=" + none + ",4224=" + none
                            + ",4640=" + enriched + ",7168=" + none
            );
            loot.put(
                    "1",
                    "1=" + none + ",4096=" + enriched + ",4224=" + none
                            + ",4640=" + enriched + ",7168=" + none
            );
            loot.put(
                    "2",
                    "1=" + none + ",4096=" + enriched + ",4224=" + enriched
                            + ",4640=" + enriched + ",7168=" + none
            );
            loot.put(
                    "3",
                    "1=" + none + ",4096=" + enriched + ",4224=" + enriched
                            + ",4640=" + enriched + ",7168=" + enriched
            );
            return Collections.unmodifiableMap(loot);
        }

        private static Map<String, String> expectedRecipes() {
            Map<String, String> recipes = new TreeMap<>();
            recipes.put(
                    "etherology:attrahite_brick",
                    cookingRecipeDescription(
                            "etherology:attrahite_brick",
                            "net.minecraft.recipe.SmeltingRecipe",
                            "minecraft:smelting",
                            "etherology:attrahite",
                            "etherology:attrahite_brickx1",
                            200,
                            0.1F
                    )
            );
            recipes.put(
                    "etherology:attrahite_brick_slab",
                    shapedRecipeDescription(
                            "etherology:attrahite_brick_slab",
                            "etherology:attrahite_brick_slabx6",
                            "building",
                            3,
                            1
                    )
            );
            recipes.put(
                    "etherology:attrahite_brick_slab_from_attrahite_bricks_stonecutting",
                    stonecuttingRecipeDescription(
                            "etherology:attrahite_brick_slab_from_attrahite_bricks_stonecutting",
                            "etherology:attrahite_brick_slabx2"
                    )
            );
            recipes.put(
                    "etherology:attrahite_brick_stairs",
                    shapedRecipeDescription(
                            "etherology:attrahite_brick_stairs",
                            "etherology:attrahite_brick_stairsx4",
                            "building",
                            3,
                            3
                    )
            );
            recipes.put(
                    "etherology:attrahite_brick_stairs_from_attrahite_bricks_stonecutting",
                    stonecuttingRecipeDescription(
                            "etherology:attrahite_brick_stairs_from_attrahite_bricks_stonecutting",
                            "etherology:attrahite_brick_stairsx1"
                    )
            );
            recipes.put(
                    "etherology:attrahite_bricks",
                    shapedRecipeDescription(
                            "etherology:attrahite_bricks",
                            "etherology:attrahite_bricksx1",
                            "building",
                            2,
                            2
                    )
            );
            recipes.put(
                    "etherology:azel_ingot",
                    cookingRecipeDescription(
                            "etherology:azel_ingot",
                            "net.minecraft.recipe.SmeltingRecipe",
                            "minecraft:smelting",
                            "etherology:raw_azel",
                            "etherology:azel_ingotx1",
                            200,
                            0.3F
                    )
            );
            recipes.put(
                    "etherology:azel_ingot_from_blasting",
                    cookingRecipeDescription(
                            "etherology:azel_ingot_from_blasting",
                            "net.minecraft.recipe.BlastingRecipe",
                            "minecraft:blasting",
                            "etherology:raw_azel",
                            "etherology:azel_ingotx1",
                            100,
                            0.3F
                    )
            );
            recipes.put(
                    "etherology:raw_azel",
                    shapedRecipeDescription(
                            "etherology:raw_azel",
                            "etherology:raw_azelx1",
                            "misc",
                            2,
                            2
                    )
            );
            return Collections.unmodifiableMap(recipes);
        }

        private static String cookingRecipeDescription(
                String id,
                String recipeClass,
                String type,
                String input,
                String output,
                int cookTime,
                float experience
        ) {
            return id + "|class=" + recipeClass + "|type=" + type
                    + "|serializer=" + type + "|output=" + output
                    + "|group=|notification=true|input=" + input
                    + "|input_exact=true|category=misc|cook_time=" + cookTime
                    + "|experience=" + Float.toString(experience)
                    + "|matches=true|crafted=" + output;
        }

        private static String shapedRecipeDescription(
                String id,
                String output,
                String category,
                int width,
                int height
        ) {
            return id + "|class=net.minecraft.recipe.ShapedRecipe"
                    + "|type=minecraft:crafting"
                    + "|serializer=minecraft:crafting_shaped"
                    + "|output=" + output + "|group=|notification=true"
                    + "|input=" + expectedIngredientPattern(id)
                    + "|input_exact=true|category=" + category
                    + "|width=" + width + "|height=" + height
                    + "|matches=true|crafted=" + output;
        }

        private static String stonecuttingRecipeDescription(
                String id,
                String output
        ) {
            return id + "|class=net.minecraft.recipe.StonecuttingRecipe"
                    + "|type=minecraft:stonecutting"
                    + "|serializer=minecraft:stonecutting"
                    + "|output=" + output + "|group=|notification=true"
                    + "|input=etherology:attrahite_bricks|input_exact=true"
                    + "|matches=true|crafted=" + output;
        }

        private static Map<String, String> expectedAdvancements() {
            Map<String, String> advancements = new TreeMap<>();
            for (String advancementId : EXPECTED_ADVANCEMENT_IDS) {
                String recipeId = advancementId
                        .replace("etherology:recipes/building_blocks/", "etherology:")
                        .replace("etherology:recipes/misc/", "etherology:");
                String ingredientCriterion = advancementId.contains("brick_slab")
                        || advancementId.contains("brick_stairs")
                        ? "has_attrahite_bricks"
                        : "has_attrahite";
                advancements.put(
                        advancementId,
                        advancementId
                                + "|parent=minecraft:recipes/root"
                                + "|criteria=" + ingredientCriterion + "+has_the_recipe"
                                + "|requirements=" + ingredientCriterion
                                + "+has_the_recipe"
                                + "|reward_recipes=" + recipeId
                                + "|telemetry=false"
                );
            }
            return Collections.unmodifiableMap(advancements);
        }
    }

    record RecipeCapture(String description, boolean matchesAndCrafts) {
    }

    private static String canonicalStringMap(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }
}
