package ru.feytox.etherology.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;

import java.util.Map;
import java.util.UUID;

public class EbonyArmorItem extends ArmorItem {

    private static final Map<Type, UUID> SPEED_MODIFIER_IDS = Map.of(
            Type.BOOTS, UUID.fromString("845DB27C-C624-495F-8C9F-6020A9A58B6B"),
            Type.LEGGINGS, UUID.fromString("D8499B04-0E66-4726-AB29-64469D734E0D"),
            Type.CHESTPLATE, UUID.fromString("9F3D476D-C118-4544-8365-64846904B48E"),
            Type.HELMET, UUID.fromString("2AD3F246-FEE1-4E67-B886-69FD380BB150"));

    private final Multimap<EntityAttribute, EntityAttributeModifier> modifiers;

    public EbonyArmorItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);

        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(super.getAttributeModifiers(type.getEquipmentSlot()));
        builder.put(EntityAttributes.GENERIC_MOVEMENT_SPEED, new EntityAttributeModifier(SPEED_MODIFIER_IDS.get(type), "Ebony armor movement speed", 0.075d, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        modifiers = builder.build();
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        return slot == type.getEquipmentSlot() ? modifiers : super.getAttributeModifiers(slot);
    }
}
