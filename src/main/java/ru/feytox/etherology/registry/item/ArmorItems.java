package ru.feytox.etherology.registry.item;

import lombok.experimental.UtilityClass;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import ru.feytox.etherology.item.PranaVisionItem;

import static ru.feytox.etherology.registry.item.ToolItems.register;

@UtilityClass
public class ArmorItems {

    // materials
    public static final ArmorMaterial EBONY_MATERIAL = EbonyArmorMaterial.INSTANCE;

    // ebony armor
    public static final Item EBONY_HELMET = SharedArmorItems.EBONY_HELMET.get();
    public static final Item EBONY_CHESTPLATE = SharedArmorItems.EBONY_CHESTPLATE.get();
    public static final Item EBONY_LEGGINGS = SharedArmorItems.EBONY_LEGGINGS.get();
    public static final Item EBONY_BOOTS = SharedArmorItems.EBONY_BOOTS.get();

    public static final Item[] ARMOR_ITEMS = {EBONY_HELMET, EBONY_CHESTPLATE, EBONY_LEGGINGS, EBONY_BOOTS};

    // trinkets
    public static final Item PRANA_VISION = register("prana_vision", new PranaVisionItem());

    public static void registerAll() {}
}
