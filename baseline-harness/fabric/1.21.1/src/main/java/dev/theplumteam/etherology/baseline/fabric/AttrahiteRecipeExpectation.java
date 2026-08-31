package dev.theplumteam.etherology.baseline.fabric;

import net.minecraft.util.Identifier;

record AttrahiteRecipeExpectation(
        Identifier id,
        Identifier typeId,
        Identifier resultId,
        int resultCount
) {
}
