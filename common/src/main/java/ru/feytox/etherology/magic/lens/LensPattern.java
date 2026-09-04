package ru.feytox.etherology.magic.lens;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIntArray;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.util.misc.CodecUtil;

import java.util.Objects;
import java.util.stream.Collectors;

public class LensPattern {

    public static final Codec<LensPattern> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(CodecUtil.INT_SET.fieldOf("cracks").forGetter(pattern -> pattern.cracks),
                    CodecUtil.INT_SET.fieldOf("soft_cells").forGetter(pattern -> pattern.softCells)
            ).apply(instance, LensPattern::new));

    protected final IntArraySet cracks;
    protected final IntArraySet softCells;

    public LensPattern(IntArraySet cracks, IntArraySet softCells) {
        if (cracks == null) {
            throw new NullPointerException("cracks is marked non-null but is null");
        }
        if (softCells == null) {
            throw new NullPointerException("softCells is marked non-null but is null");
        }
        this.cracks = cracks;
        this.softCells = softCells;
    }

    public static LensPattern empty() {
        return new LensPattern(new IntArraySet(), new IntArraySet());
    }

    public boolean isCracked() {
        return !cracks.isEmpty() || !softCells.isEmpty();
    }

    public int getTextureOffset(int index) {
        if (isHard(index)) return 1;
        if (isSoft(index)) return 2;
        return 0;
    }

    public boolean isSoft(int index) {
        return softCells.contains(index);
    }

    public boolean isHard(int index) {
        return cracks.contains(index);
    }

    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        writeCells(nbt, "cracks", cracks);
        writeCells(nbt, "soft_cells", softCells);
        return nbt;
    }

    @Nullable
    public static LensPattern readNbt(NbtCompound nbt) {
        IntArraySet cracks = readCells(nbt, "cracks");
        IntArraySet softCells = readCells(nbt, "soft_cells");
        // TODO: 22.01.2024 maybe replace with try-catch or smth else
        if (cracks == null || softCells == null) return null;
        return new LensPattern(cracks, softCells);
    }

    private static void writeCells(NbtCompound nbt, String key, IntArraySet intSet) {
        int[] arr = intSet.toArray(new int[]{});
        NbtIntArray cellsArr = new NbtIntArray(arr);
        nbt.put(key, cellsArr);
    }

    @Nullable
    private static IntArraySet readCells(NbtCompound nbt, String key) {
        NbtElement element = nbt.get(key);
        if (!(element instanceof NbtIntArray nbtArr)) return null;

        return nbtArr.stream()
                .map(NbtInt::intValue)
                .collect(Collectors.toCollection(IntArraySet::new));
    }

    public Mutable asMutable() {
        return new Mutable(cracks.clone(), softCells.clone());
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof LensPattern otherPattern)) return false;
        if (!otherPattern.canEqual(this)) return false;
        return Objects.equals(cracks, otherPattern.cracks)
                && Objects.equals(softCells, otherPattern.softCells);
    }

    protected boolean canEqual(Object other) {
        return other instanceof LensPattern;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = result * 59 + (cracks == null ? 43 : cracks.hashCode());
        result = result * 59 + (softCells == null ? 43 : softCells.hashCode());
        return result;
    }

    // TODO: 08.07.2024 add toImmutable again
    public static class Mutable extends LensPattern {

        public Mutable(IntArraySet cracks, IntArraySet softCells) {
            super(cracks, softCells);
        }

        public boolean markSoft(int index) {
            return !isHard(index) && softCells.add(index);
        }

        public void unSoft(int index) {
            softCells.remove(index);
        }

        public boolean markHard(int index) {
            return !isSoft(index) && cracks.add(index);
        }
    }
}
