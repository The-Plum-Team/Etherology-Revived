package ru.feytox.etherology.registry.misc;

import com.mojang.serialization.Codec;
import lombok.experimental.UtilityClass;
import ru.feytox.etherology.item.glints.GlintEtherData;
import ru.feytox.etherology.magic.corruption.Corruption;
import ru.feytox.etherology.magic.lens.LensComponent;
import ru.feytox.etherology.magic.staff.StaffComponent;
import ru.feytox.etherology.util.misc.ItemComponent;
import ru.feytox.etherology.util.misc.ItemDataKey;

@UtilityClass
public class ComponentTypes {

    public static final ItemDataKey<Float> STORED_ETHER = GlintEtherData.STORED_ETHER;
    public static final ItemDataKey<LensComponent> LENS = new ItemDataKey<>("lens", LensComponent.CODEC);
    public static final ItemDataKey<ItemComponent> STAFF_LENS = new ItemDataKey<>("staff_lens", ItemComponent.CODEC);
    public static final ItemDataKey<StaffComponent> STAFF = new ItemDataKey<>("staff", StaffComponent.CODEC);
    public static final ItemDataKey<Corruption> CORRUPTION = new ItemDataKey<>("corruption", Corruption.CODEC);
    public static final ItemDataKey<Integer> PSEUDO_DAMAGE = new ItemDataKey<>("pseudo_damage", Codec.INT);

    public static void registerAll() {
    }
}
