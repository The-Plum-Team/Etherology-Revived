package ru.feytox.etherology.client.mixin;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Tessellator.class)
public interface TessellatorAccessor {
    @Accessor("buffer")
    BufferBuilder getAllocator();
}
