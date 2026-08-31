package ru.feytox.etherology.client.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;

import static ru.feytox.etherology.registry.misc.EtherEnchantments.PEAL;
import static ru.feytox.etherology.registry.misc.EtherEnchantments.REFLECTION;

public class EnchantmentTagGeneration extends FabricTagProvider.EnchantmentTagProvider {

    private static final TagKey<Enchantment> NON_TREASURE = TagKey.of(RegistryKeys.ENCHANTMENT, new Identifier("non_treasure"));

    public EnchantmentTagGeneration(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> completableFuture) {
        super(output, completableFuture);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
        getOrCreateTagBuilder(NON_TREASURE).add(PEAL, REFLECTION);
    }
}
