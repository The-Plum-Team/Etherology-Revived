package dev.theplumteam.etherology.e2e.server;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

record MetalBlockProbeState(
        String captureError,
        List<String> metalBlockIds,
        List<String> metalBlockItemIds,
        Map<String, MetalBlockEntry> entries
) {

    static final String BLOCK_REGISTRY_ID = "minecraft:block";
    static final String ITEM_REGISTRY_ID = "minecraft:item";
    static final String VANILLA_BLOCK_CLASS = Block.class.getName();
    static final String BLOCK_ITEM_CLASS = BlockItem.class.getName();
    static final List<String> EXPECTED_NBT_KEYS = List.of("Count", "id");
    static final Map<String, MetalBlockSpec> EXPECTED_METAL_BLOCKS = expectedMetalBlocks();
    static final List<String> EXPECTED_BLOCK_IDS = List.copyOf(
            EXPECTED_METAL_BLOCKS.keySet()
    );

    static MetalBlockProbeState capture() {
        List<String> blockIds = new ArrayList<>();
        List<String> itemIds = new ArrayList<>();
        Map<String, MetalBlockEntry> entries = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        EXPECTED_METAL_BLOCKS.forEach((id, spec) -> {
            MetalBlockEntry entry;
            try {
                entry = captureEntry(id);
            } catch (RuntimeException exception) {
                entry = MetalBlockEntry.failed(id);
                errors.add(id + "=" + exception.getClass().getName());
            }
            if (entry.blockIdentity() != null) blockIds.add(entry.blockId());
            if (entry.itemIdentity() != null) itemIds.add(entry.itemId());
            entries.put(id, entry);
        });

        blockIds.sort(String::compareTo);
        itemIds.sort(String::compareTo);
        return new MetalBlockProbeState(
                String.join(",", errors),
                List.copyOf(blockIds),
                List.copyOf(itemIds),
                Collections.unmodifiableMap(entries)
        );
    }

    static MetalBlockProbeState missing() {
        return new MetalBlockProbeState("not captured", List.of(), List.of(), Map.of());
    }

    boolean hasExactRegistry() {
        return EXPECTED_BLOCK_IDS.equals(metalBlockIds)
                && EXPECTED_BLOCK_IDS.equals(metalBlockItemIds)
                && EXPECTED_METAL_BLOCKS.keySet().equals(entries.keySet())
                && EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
                    MetalBlockEntry entry = entries.get(id);
                    return entry != null
                            && entry.blockIdentity() != null
                            && entry.itemIdentity() != null
                            && id.equals(entry.blockId())
                            && id.equals(entry.itemId());
                });
    }

    boolean hasExactRuntimeClasses() {
        return allEntriesMatch(entry -> VANILLA_BLOCK_CLASS.equals(entry.blockClass())
                && BLOCK_ITEM_CLASS.equals(entry.itemClass()));
    }

    boolean hasExactBlockItemMappings() {
        return allEntriesMatch(entry -> entry.blockItem()
                && entry.blockItemMapsToBlock()
                && entry.blockAsItemMatches());
    }

    boolean hasExactProperties() {
        return EXPECTED_METAL_BLOCKS.entrySet().stream().allMatch(expected -> {
            MetalBlockEntry entry = entries.get(expected.getKey());
            MetalBlockSpec spec = expected.getValue();
            return entry != null
                    && Float.compare(spec.hardness(), entry.hardness()) == 0
                    && Float.compare(spec.blastResistance(), entry.blastResistance()) == 0
                    && spec.mapColorId() == entry.mapColorId()
                    && entry.metalSoundGroup()
                    && entry.toolRequired()
                    && entry.luminance() == 0
                    && entry.opaque()
                    && entry.fullCube()
                    && entry.maxCount() == 64;
        });
    }

    boolean hasExactTagMemberships() {
        return EXPECTED_METAL_BLOCKS.entrySet().stream().allMatch(expected -> {
            MetalBlockEntry entry = entries.get(expected.getKey());
            return entry != null
                    && entry.pickaxeMineable()
                    && entry.needsIronTool()
                    && entry.beaconBase() == expected.getValue().beaconBase();
        });
    }

    boolean hasExactStackNbtRoundTrips() {
        return allEntriesMatch(entry -> entry.roundTripExact()
                && entry.itemId().equals(entry.serializedId())
                && entry.maxCount() == entry.serializedCount()
                && EXPECTED_NBT_KEYS.equals(entry.serializedKeys()));
    }

    boolean hasExactContract() {
        return captureError.isEmpty()
                && hasExactRegistry()
                && hasExactRuntimeClasses()
                && hasExactBlockItemMappings()
                && hasExactProperties()
                && hasExactTagMemberships()
                && hasExactStackNbtRoundTrips()
                && expectedCanonicalProperties().equals(canonicalProperties())
                && expectedCanonicalSaveRepresentations().equals(
                        canonicalSaveRepresentations()
                );
    }

    boolean sameStateAtServerStarted(MetalBlockProbeState startedState) {
        return hasSameRegistry(startedState)
                && hasSameProperties(startedState)
                && hasSameTags(startedState)
                && hasSameStackNbt(startedState);
    }

    boolean hasSameRegistry(MetalBlockProbeState other) {
        if (!captureError.equals(other.captureError)
                || !metalBlockIds.equals(other.metalBlockIds)
                || !metalBlockItemIds.equals(other.metalBlockItemIds)
                || !entries.keySet().equals(other.entries.keySet())) {
            return false;
        }
        return EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
            MetalBlockEntry entry = entries.get(id);
            MetalBlockEntry otherEntry = other.entries.get(id);
            return entry != null
                    && otherEntry != null
                    && entry.blockIdentity() == otherEntry.blockIdentity()
                    && entry.itemIdentity() == otherEntry.itemIdentity()
                    && entry.blockId().equals(otherEntry.blockId())
                    && entry.itemId().equals(otherEntry.itemId());
        });
    }

    boolean hasSameProperties(MetalBlockProbeState other) {
        return EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
            MetalBlockEntry entry = entries.get(id);
            MetalBlockEntry otherEntry = other.entries.get(id);
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
                    && entry.metalSoundGroup() == otherEntry.metalSoundGroup()
                    && entry.toolRequired() == otherEntry.toolRequired()
                    && entry.luminance() == otherEntry.luminance()
                    && entry.opaque() == otherEntry.opaque()
                    && entry.fullCube() == otherEntry.fullCube()
                    && entry.maxCount() == otherEntry.maxCount();
        });
    }

    boolean hasSameTags(MetalBlockProbeState other) {
        return EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
            MetalBlockEntry entry = entries.get(id);
            MetalBlockEntry otherEntry = other.entries.get(id);
            return entry != null
                    && otherEntry != null
                    && entry.pickaxeMineable() == otherEntry.pickaxeMineable()
                    && entry.needsIronTool() == otherEntry.needsIronTool()
                    && entry.beaconBase() == otherEntry.beaconBase();
        });
    }

    boolean hasSameStackNbt(MetalBlockProbeState other) {
        return EXPECTED_BLOCK_IDS.stream().allMatch(id -> {
            MetalBlockEntry entry = entries.get(id);
            MetalBlockEntry otherEntry = other.entries.get(id);
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

    String canonicalProperties() {
        return entries.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().propertySummary())
                .collect(Collectors.joining(","));
    }

    String canonicalSaveRepresentations() {
        return entries.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().saveRepresentation())
                .collect(Collectors.joining(","));
    }

    static String expectedCanonicalProperties() {
        return EXPECTED_METAL_BLOCKS.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + propertySummary(entry.getValue()))
                .collect(Collectors.joining(","));
    }

    static String expectedCanonicalSaveRepresentations() {
        return EXPECTED_BLOCK_IDS.stream()
                .map(id -> id + "=" + saveRepresentation(id, 64, EXPECTED_NBT_KEYS))
                .collect(Collectors.joining(","));
    }

    static MetalBlockPlacementState placeIn(ServerWorld world) {
        Map<String, String> positions = new LinkedHashMap<>();
        Map<String, String> placedBlockIds = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int index = 0;
        for (String id : EXPECTED_BLOCK_IDS) {
            BlockPos position = new BlockPos(8 + index, 200, 8);
            positions.put(id, position(position));
            Block block = entriesBlock(id);
            try {
                boolean changed = block != null && world.setBlockState(
                        position,
                        block.getDefaultState(),
                        Block.NOTIFY_ALL
                );
                Block placed = world.getBlockState(position).getBlock();
                Identifier placedId = Registries.BLOCK.getId(placed);
                placedBlockIds.put(id, placedId == null ? "" : placedId.toString());
                if (!changed && placed != block) errors.add(id + "=placement_rejected");
            } catch (RuntimeException exception) {
                placedBlockIds.put(id, "");
                errors.add(id + "=" + exception.getClass().getName());
            }
            index++;
        }
        return new MetalBlockPlacementState(
                String.join(",", errors),
                Collections.unmodifiableMap(positions),
                Collections.unmodifiableMap(placedBlockIds)
        );
    }

    static MetalBlockPlacementState capturePlacement(ServerWorld world) {
        Map<String, String> positions = expectedPlacementPositions();
        Map<String, String> placedBlockIds = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int index = 0;
        for (String id : EXPECTED_BLOCK_IDS) {
            BlockPos position = new BlockPos(8 + index, 200, 8);
            try {
                Identifier placedId = Registries.BLOCK.getId(
                        world.getBlockState(position).getBlock()
                );
                placedBlockIds.put(id, placedId == null ? "" : placedId.toString());
            } catch (RuntimeException exception) {
                placedBlockIds.put(id, "");
                errors.add(id + "=" + exception.getClass().getName());
            }
            index++;
        }
        return new MetalBlockPlacementState(
                String.join(",", errors),
                positions,
                Collections.unmodifiableMap(placedBlockIds)
        );
    }

    private boolean allEntriesMatch(Predicate<MetalBlockEntry> predicate) {
        return entries.size() == EXPECTED_METAL_BLOCKS.size()
                && entries.values().stream().allMatch(predicate);
    }

    private static MetalBlockEntry captureEntry(String id) {
        Identifier expectedId = Identifier.parse(id);
        Block block = Registries.BLOCK.getOrEmpty(expectedId).orElse(null);
        Item item = Registries.ITEM.getOrEmpty(expectedId).orElse(null);
        if (block == null || item == null) return MetalBlockEntry.failed(id);

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
        String actualId = itemId == null ? "" : itemId.toString();
        String representation = saveRepresentation(
                actualId,
                item.getMaxCount(),
                serializedKeys
        );
        return new MetalBlockEntry(
                block,
                item,
                blockId == null ? "" : blockId.toString(),
                actualId,
                block.getClass().getName(),
                item.getClass().getName(),
                blockItem != null,
                blockItem != null && blockItem.getBlock() == block,
                Block.getBlockFromItem(item) == block && block.asItem() == item,
                state.getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN),
                block.getBlastResistance(),
                state.getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).id,
                state.getSoundGroup() == BlockSoundGroup.METAL,
                state.isToolRequired(),
                state.getLuminance(),
                state.isOpaque(),
                state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN),
                state.isIn(BlockTags.PICKAXE_MINEABLE),
                state.isIn(BlockTags.NEEDS_IRON_TOOL),
                state.isIn(BlockTags.BEACON_BASE_BLOCKS),
                item.getMaxCount(),
                serializedId,
                serializedCount,
                serializedKeys,
                roundTripExact,
                representation
        );
    }

    private static Block entriesBlock(String id) {
        return Registries.BLOCK.getOrEmpty(Identifier.parse(id)).orElse(null);
    }

    private static String propertySummary(MetalBlockSpec spec) {
        return VANILLA_BLOCK_CLASS + "|item_class=" + BLOCK_ITEM_CLASS
                + "|hardness=" + Float.toString(spec.hardness())
                + "|blast=" + Float.toString(spec.blastResistance())
                + "|map_color=" + spec.mapColorId()
                + "|metal_sound=true|tool_required=true|luminance=0"
                + "|opaque=true|full_cube=true|max=64|pickaxe=true|needs_iron=true"
                + "|beacon=" + spec.beaconBase();
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

    private static Map<String, String> expectedPlacementPositions() {
        Map<String, String> positions = new LinkedHashMap<>();
        int index = 0;
        for (String id : EXPECTED_BLOCK_IDS) {
            positions.put(id, position(new BlockPos(8 + index, 200, 8)));
            index++;
        }
        return Collections.unmodifiableMap(positions);
    }

    private static String position(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static Map<String, MetalBlockSpec> expectedMetalBlocks() {
        Map<String, MetalBlockSpec> expected = new TreeMap<>();
        expected.put("etherology:azel_block", new MetalBlockSpec(5.0F, 6.0F, 32, false));
        expected.put("etherology:ebony_block", new MetalBlockSpec(5.0F, 6.0F, 15, true));
        expected.put("etherology:ethril_block", new MetalBlockSpec(3.0F, 6.0F, 30, true));
        return Collections.unmodifiableMap(expected);
    }

    record MetalBlockSpec(
            float hardness,
            float blastResistance,
            int mapColorId,
            boolean beaconBase
    ) {
    }

    record MetalBlockEntry(
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
            boolean metalSoundGroup,
            boolean toolRequired,
            int luminance,
            boolean opaque,
            boolean fullCube,
            boolean pickaxeMineable,
            boolean needsIronTool,
            boolean beaconBase,
            int maxCount,
            String serializedId,
            int serializedCount,
            List<String> serializedKeys,
            boolean roundTripExact,
            String saveRepresentation
    ) {

        static MetalBlockEntry failed(String id) {
            return new MetalBlockEntry(
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
                    false,
                    false,
                    -1,
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
                    + "|map_color=" + mapColorId
                    + "|metal_sound=" + metalSoundGroup
                    + "|tool_required=" + toolRequired + "|luminance=" + luminance
                    + "|opaque=" + opaque + "|full_cube=" + fullCube
                    + "|max=" + maxCount + "|pickaxe=" + pickaxeMineable
                    + "|needs_iron=" + needsIronTool + "|beacon=" + beaconBase;
        }
    }

    record MetalBlockPlacementState(
            String captureError,
            Map<String, String> positions,
            Map<String, String> placedBlockIds
    ) {

        static MetalBlockPlacementState missing() {
            return new MetalBlockPlacementState("not captured", Map.of(), Map.of());
        }

        boolean hasExactPlacement() {
            return captureError.isEmpty()
                    && expectedPlacementPositions().equals(positions)
                    && EXPECTED_BLOCK_IDS.stream().allMatch(id ->
                            id.equals(placedBlockIds.get(id))
                    );
        }

        boolean samePlacement(MetalBlockPlacementState other) {
            return captureError.equals(other.captureError)
                    && positions.equals(other.positions)
                    && placedBlockIds.equals(other.placedBlockIds);
        }

        String canonicalPositions() {
            return positions.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(","));
        }

        String canonicalPlacedBlockIds() {
            return placedBlockIds.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(","));
        }

        static String expectedCanonicalPositions() {
            return expectedPlacementPositions().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(","));
        }

        static String expectedCanonicalPlacedBlockIds() {
            return EXPECTED_BLOCK_IDS.stream()
                    .map(id -> id + "=" + id)
                    .collect(Collectors.joining(","));
        }
    }
}
