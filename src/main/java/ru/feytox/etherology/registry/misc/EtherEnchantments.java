package ru.feytox.etherology.registry.misc;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.experimental.UtilityClass;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ru.feytox.etherology.data.EItemTags;
import ru.feytox.etherology.item.BattlePickaxe;
import ru.feytox.etherology.item.BroadSwordItem;
import ru.feytox.etherology.item.IronShield;
import ru.feytox.etherology.item.TuningMaceItem;
import ru.feytox.etherology.mixin.EntityHitResultAccessor;
import ru.feytox.etherology.util.misc.EIdentifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.minecraft.enchantment.Enchantments.*;

@UtilityClass
public class EtherEnchantments {

    private static final Map<Class<? extends Item>, List<Enchantment>> BANNED_ENCHANTMENTS = new Object2ObjectOpenHashMap<>();

    public static final Enchantment PEAL = register("peal", new PealEnchantment());
    public static final Enchantment REFLECTION = register("reflection", new ReflectionEnchantment());

    public static void registerAll() {
        banEnchantments(BattlePickaxe.class, FORTUNE, SILK_TOUCH);
        banEnchantments(BroadSwordItem.class, LOOTING);
        banEnchantments(TuningMaceItem.class, SHARPNESS, FIRE_ASPECT, LOOTING, SWEEPING);
    }

    public static int getLevel(World world, Enchantment enchantment, ItemStack stack) {
        return EnchantmentHelper.getLevel(enchantment, stack);
    }

    public static int getLevel(World world, Enchantment enchantment, LivingEntity entity) {
        return EnchantmentHelper.getEquipmentLevel(enchantment, entity);
    }

    public static boolean isAcceptableItem(Enchantment enchantment, Item item, boolean fallback) {
        if (enchantment == PEAL) return item.getRegistryEntry().isIn(EItemTags.TUNING_MACES);
        if (enchantment == REFLECTION) return item.getRegistryEntry().isIn(EItemTags.IRON_SHIELDS);

        boolean acceptable = fallback || item instanceof BattlePickaxe && enchantment.target == EnchantmentTarget.WEAPON;
        List<Enchantment> banned = BANNED_ENCHANTMENTS.get(item.getClass());
        return acceptable && (banned == null || !banned.contains(enchantment));
    }

    private static Enchantment register(String id, Enchantment enchantment) {
        return Registry.register(Registries.ENCHANTMENT, EIdentifier.of(id), enchantment);
    }

    private static void banEnchantments(Class<? extends Item> itemClass, Enchantment... enchantments) {
        BANNED_ENCHANTMENTS.put(itemClass, new ObjectArrayList<>(enchantments));
    }

    public static boolean applyReflection(EntityHitResult entityHitResult, ProjectileEntity projectile) {
        World world = projectile.getWorld();
        if (world == null || world.isClient) return true;
        Entity entity = entityHitResult.getEntity();
        if (!(entity instanceof LivingEntity target)) return true;
        Optional<ItemStack> optionalShield = IronShield.getUsingShield(target);
        if (optionalShield.isEmpty()) return true;
        ItemStack shield = optionalShield.get();
        if (getLevel(world, REFLECTION, shield) < 1) return true;

        Vec3d targetRotation = target.getRotationVec(1.0F);
        Vec3d targetPos = target.getPos();
        if (!IronShield.shieldBlockCheck(targetRotation, targetPos, projectile.getPos())) return true;

        Vec3d newVelocity = target.getRotationVec(1.0f).multiply(projectile.getVelocity().length());
        if (!(projectile.getType().create(world) instanceof ProjectileEntity newProjectile)) return true;

        newProjectile.copyFrom(projectile);
        newProjectile.refreshPositionAndAngles(projectile.getX(), projectile.getY(), projectile.getZ(), projectile.getYaw(), projectile.getPitch());
        newProjectile.setVelocity(newVelocity);
        projectile.setPosition(projectile.getPos().add(0, -10000, 0));
        ((EntityHitResultAccessor) entityHitResult).setEntity(projectile);
        projectile.discard();
        world.spawnEntity(newProjectile);

        var newOwner = projectile instanceof TridentEntity ? projectile.getOwner() : target;
        newProjectile.setOwner(newOwner);

        world.playSound(null, target.getBlockPos(), EtherSounds.DEFLECT, target.getSoundCategory(), 0.5f, 1.0f);
        var activeHand = target.getActiveHand();
        shield.damage(2, target, livingEntity -> livingEntity.sendToolBreakStatus(activeHand));
        return false;
    }
}
