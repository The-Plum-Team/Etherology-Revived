package ru.feytox.etherology.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.function.ValueLists;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.feytox.etherology.util.misc.BoatTypes;

import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

@Mixin(BoatEntity.Type.class)
public abstract class BoatEntityTypeMixin {

    @Shadow
    @Mutable
    public static @Final StringIdentifiable.Codec<BoatEntity.Type> CODEC;

    @Shadow
    @Mutable
    private static @Final BoatEntity.Type[] field_7724;

    @Shadow
    @Mutable
    private static @Final IntFunction<BoatEntity.Type> BY_ID;

    @Invoker("<init>")
    private static BoatEntity.Type createType(String enumName, int ordinal, Block baseBlock, String name) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void injectCustomTypes(CallbackInfo ci) {
        var types = new ObjectArrayList<>(field_7724);
        var maxOrdinal = types.stream().map(Enum::ordinal).max(Comparator.naturalOrder()).get();

        BoatTypes.PEACH = createAndAdd(types, "ETHEROLOGY_PEACH", maxOrdinal + 1, Blocks.OAK_PLANKS, "etherology_peach");

        field_7724 = types.toArray(BoatEntity.Type[]::new);
        CODEC = StringIdentifiable.createCodec(BoatEntity.Type::values);
        BY_ID = ValueLists.createIdToValueFunction((ToIntFunction<BoatEntity.Type>) Enum::ordinal, BoatEntity.Type.values(), ValueLists.OutOfBoundsHandling.ZERO);
    }

    @Unique
    private static BoatEntity.Type createAndAdd(List<BoatEntity.Type> types, String enumName, int ordinal, Block baseBlock, String name) {
        var type = createType(enumName, ordinal, baseBlock, name);
        types.add(type);
        return type;
    }
}
