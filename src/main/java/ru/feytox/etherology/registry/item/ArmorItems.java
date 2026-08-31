package ru.feytox.etherology.registry.item;

import lombok.experimental.UtilityClass;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import ru.feytox.etherology.item.EbonyArmorItem;
import ru.feytox.etherology.item.PranaVisionItem;

import static net.minecraft.item.ArmorItem.Type.*;
import static ru.feytox.etherology.registry.item.ToolItems.register;

@UtilityClass
public class ArmorItems {

    // materials
    public static final ArmorMaterial EBONY_MATERIAL = EbonyArmorMaterial.INSTANCE;

    // ebony armor
    public static final Item EBONY_HELMET = register("ebony_helmet", createArmor(EbonyArmorItem::new, EBONY_MATERIAL, HELMET));
    public static final Item EBONY_CHESTPLATE = register("ebony_chestplate", createArmor(EbonyArmorItem::new, EBONY_MATERIAL, CHESTPLATE));
    public static final Item EBONY_LEGGINGS = register("ebony_leggings", createArmor(EbonyArmorItem::new, EBONY_MATERIAL, LEGGINGS));
    public static final Item EBONY_BOOTS = register("ebony_boots", createArmor(EbonyArmorItem::new, EBONY_MATERIAL, BOOTS));

    public static final Item[] ARMOR_ITEMS = {EBONY_HELMET, EBONY_CHESTPLATE, EBONY_LEGGINGS, EBONY_BOOTS};

    // trinkets
    public static final Item PRANA_VISION = register("prana_vision", new PranaVisionItem());

    public static void registerAll() {}

    private static ArmorItem createArmor(ArmorFactory factory, ArmorMaterial material, ArmorItem.Type armorType) {
        return factory.create(material, armorType, new Item.Settings());
    }

    @FunctionalInterface
    private interface ArmorFactory {

        ArmorItem create(ArmorMaterial material, ArmorItem.Type type, Item.Settings settings);
    }
}
