package ru.feytox.etherology.client.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.fabricmc.fabric.api.tag.convention.v1.ConventionalItemTags;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.data.EBlockTags;
import ru.feytox.etherology.data.EItemTags;
import ru.feytox.etherology.registry.item.SharedMaterialItems;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.registry.tag.ItemTags.*;
import static ru.feytox.etherology.registry.item.ArmorItems.*;
import static ru.feytox.etherology.registry.item.EItems.PEACH_BOAT;
import static ru.feytox.etherology.registry.item.EItems.PEACH_CHEST_BOAT;
import static ru.feytox.etherology.registry.item.ToolItems.*;

public class ItemTagGeneration extends FabricTagProvider.ItemTagProvider {

    public ItemTagGeneration(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> completableFuture, @Nullable BlockTagProvider blockTagProvider) {
        super(output, completableFuture, blockTagProvider);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup arg) {
        copy(BlockTags.PLANKS, PLANKS);
        copy(BlockTags.WOODEN_BUTTONS, WOODEN_BUTTONS);
        copy(BlockTags.BUTTONS, BUTTONS);
        copy(BlockTags.WOODEN_DOORS, WOODEN_DOORS);
        copy(BlockTags.WOODEN_STAIRS, WOODEN_STAIRS);
        copy(BlockTags.WOODEN_SLABS, WOODEN_SLABS);
        copy(BlockTags.WOODEN_FENCES, WOODEN_FENCES);
        copy(BlockTags.FENCE_GATES, FENCE_GATES);
        copy(BlockTags.WOODEN_PRESSURE_PLATES, WOODEN_PRESSURE_PLATES);
        copy(BlockTags.DOORS, DOORS);
        copy(BlockTags.SLABS, SLABS);
        copy(BlockTags.STAIRS, STAIRS);
        copy(BlockTags.WOODEN_TRAPDOORS, WOODEN_TRAPDOORS);
        copy(BlockTags.TRAPDOORS, TRAPDOORS);
        copy(BlockTags.FENCES, FENCES);
        copy(BlockTags.WALLS, WALLS);
        copy(BlockTags.LOGS, LOGS);
        copy(BlockTags.LOGS_THAT_BURN, LOGS_THAT_BURN);
        copy(BlockTags.SIGNS, SIGNS);
        copy(BlockTags.CEILING_HANGING_SIGNS, HANGING_SIGNS);
        copy(BlockTags.LEAVES, LEAVES);
        copy(BlockTags.SAPLINGS, SAPLINGS);

        copy(EBlockTags.PEACH_LOGS, EItemTags.PEACH_LOGS);
        copy(EBlockTags.SEDIMENTARY_STONES, EItemTags.SEDIMENTARY_STONES);
        addItems(
                BEACON_PAYMENT_ITEMS,
                SharedMaterialItems.EBONY_INGOT.get(),
                SharedMaterialItems.ETHRIL_INGOT.get()
        );

        addItems(EItemTags.TUNING_MACES, TUNING_MACE);
        addItems(EItemTags.IRON_SHIELDS, IRON_SHIELD);

        addItems(AXES, EBONY_AXE);
        addItems(HOES, EBONY_HOE);
        addItems(PICKAXES, EBONY_PICKAXE);
        addOptionalItems(PICKAXES, BATTLE_PICKAXES);
        addItems(SHOVELS, EBONY_SHOVEL);
        addItems(SWORDS, EBONY_SWORD);
        addOptionalItems(SWORDS, BROADSWORD, TUNING_MACE);
        addOptionalItems(SWORDS, BATTLE_PICKAXES);
        addItems(BOATS, PEACH_BOAT);
        addItems(CHEST_BOATS, PEACH_CHEST_BOAT);

        addItems(ConventionalItemTags.SHIELDS, IRON_SHIELD);
        // TODO: 18.02.2024 add to convention tags
    }

    private void addItems(TagKey<Item> tagKey, ItemConvertible... items) {
        Arrays.stream(items).map(ItemConvertible::asItem).forEach(getOrCreateTagBuilder(tagKey)::add);
    }

    private void addOptionalItems(TagKey<Item> tagKey, ItemConvertible... items) {
        var builder = getOrCreateTagBuilder(tagKey);
        Arrays.stream(items).map(ItemConvertible::asItem).forEach(item ->
                Registries.ITEM.getKey(item).ifPresent(builder::addOptional));
    }
}
