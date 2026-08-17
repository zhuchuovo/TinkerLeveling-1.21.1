package org.embeddedt.tinkerleveling;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;
import slimeknights.tconstruct.common.SoundUtils;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.armor.ElytraFlightModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.armor.OnAttackedModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.build.ModifierRemovalHook;
import slimeknights.tconstruct.library.modifiers.hook.build.RawDataModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.build.VolatileDataModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.mining.BlockBreakModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.mining.HarvestEnchantmentsModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileLaunchModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.special.ShearsModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.context.ToolHarvestContext;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableLauncherItem;
import slimeknights.tconstruct.library.tools.nbt.IModDataView;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.RestrictedCompoundTag;
import slimeknights.tconstruct.tools.data.ModifierIds;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class ModToolLeveling extends Modifier implements BlockBreakModifierHook,
        ElytraFlightModifierHook, OnAttackedModifierHook, MeleeHitModifierHook,
        RawDataModifierHook, VolatileDataModifierHook, ModifierRemovalHook,
        HarvestEnchantmentsModifierHook, ShearsModifierHook,
        ProjectileHitModifierHook, ProjectileLaunchModifierHook {

    public static final ResourceLocation XP_KEY = TinkerLeveling.id("xp");
    public static final ResourceLocation BONUS_MODIFIERS_KEY = TinkerLeveling.id("bonus_modifiers");
    public static final ResourceLocation LEVEL_KEY = TinkerLeveling.id("level");
    public static final ResourceLocation UUID_KEY = TinkerLeveling.id("uuid");

    private static final WeakHashMap<Projectile, Pair<ItemStack, Integer>> LAUNCH_INFO_MAP = new WeakHashMap<>();

    @Override
    public boolean shouldDisplay(boolean advanced) {
        return false;
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        hookBuilder.addHook(this, ModifierHooks.RAW_DATA, ModifierHooks.VOLATILE_DATA, ModifierHooks.REMOVE);
        hookBuilder.addHook(this, ModifierHooks.BLOCK_BREAK, ModifierHooks.MELEE_HIT, ModifierHooks.ON_ATTACKED);
        hookBuilder.addHook(this, ModifierHooks.HARVEST_ENCHANTMENTS, ModifierHooks.SHEAR_ENTITY, ModifierHooks.ELYTRA_FLIGHT);
        hookBuilder.addHook(this, ModifierHooks.PROJECTILE_LAUNCH, ModifierHooks.PROJECTILE_SHOT, ModifierHooks.PROJECTILE_THROWN);
        hookBuilder.addHook(this, ModifierHooks.PROJECTILE_HIT);
    }

    @Override
    public void addVolatileData(IToolContext context, ModifierEntry entry, ToolDataNBT volatileData) {
        int bonusModifiers = context.getPersistentData().getInt(BONUS_MODIFIERS_KEY);
        int abilitySlots = bonusModifiers / 2;
        volatileData.addSlots(SlotType.ABILITY, abilitySlots);
        volatileData.addSlots(SlotType.UPGRADE, bonusModifiers - abilitySlots);
    }

    @Override
    public Component onRemoved(IToolStackView tool, Modifier modifier) {
        tool.getPersistentData().remove(XP_KEY);
        tool.getPersistentData().remove(BONUS_MODIFIERS_KEY);
        tool.getPersistentData().remove(LEVEL_KEY);
        tool.getPersistentData().remove(UUID_KEY);
        return null;
    }

    @Override
    public void addRawData(IToolStackView tool, ModifierEntry entry, RestrictedCompoundTag tag) {
        if (!tool.getPersistentData().contains(UUID_KEY, Tag.TAG_INT_ARRAY)) {
            tool.getPersistentData().put(UUID_KEY, NbtUtils.createUUID(UUID.randomUUID()));
        }
        if (tool.getPersistentData().getInt(LEVEL_KEY) <= 0) {
            tool.getPersistentData().putInt(LEVEL_KEY, 1);
        }
    }

    @Override
    public void removeRawData(IToolStackView tool, Modifier modifier, RestrictedCompoundTag tag) {
        tool.getPersistentData().remove(UUID_KEY);
        tool.getPersistentData().remove(LEVEL_KEY);
    }

    public static int getXpForLevelup(int level, Item item) {
        int normalizedLevel = Math.max(1, level);
        double required = TinkerConfig.getBaseXpForTool(item)
                * Math.pow(TinkerConfig.levelMultiplier.get(), normalizedLevel - 1.0);
        return required >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int) required);
    }

    public void addXp(IToolStackView tool, int amount, Player player) {
        if (amount <= 0 || player.level().isClientSide) {
            return;
        }
        ModDataNBT levelData = tool.getPersistentData();
        int level = Math.max(1, levelData.getInt(LEVEL_KEY));
        if (!TinkerConfig.canLevelUp(level)) {
            return;
        }

        long updatedXp = (long) levelData.getInt(XP_KEY) + amount;
        levelData.putInt(XP_KEY, (int) Math.min(Integer.MAX_VALUE, updatedXp));
        int requiredXp = getXpForLevelup(level, tool.getItem());
        if (levelData.getInt(XP_KEY) < requiredXp) {
            if (tool instanceof ToolStack toolStack) {
                ToolHelper.syncToPlayerInventory(toolStack, player);
            }
            return;
        }

        levelData.putInt(XP_KEY, levelData.getInt(XP_KEY) - requiredXp);
        levelData.putInt(LEVEL_KEY, level + 1);
        levelData.putInt(BONUS_MODIFIERS_KEY, levelData.getInt(BONUS_MODIFIERS_KEY) + 1);

        SoundUtils.playSoundForAll(player, TinkerLeveling.SOUND_LEVELUP.get(), 1.0f, 1.0f);
        TinkerPacketHandler.sendLevelUp(level + 1, player);
        if (tool instanceof ToolStack toolStack) {
            toolStack.rebuildStats();
            ToolHelper.syncToPlayerInventory(toolStack, player);
        } else {
            throw new IllegalStateException("Unable to rebuild a non-ToolStack leveling tool");
        }
    }

    @Override
    public void afterBlockBreak(IToolStackView tool, ModifierEntry entry, ToolHarvestContext context) {
        if (context.isEffective() && context.getPlayer() != null) {
            addXp(tool, 1, context.getPlayer());
        }
    }

    @Override
    public boolean elytraFlightTick(IToolStackView tool, ModifierEntry entry, LivingEntity entity, int flightTicks) {
        if (flightTicks > 0 && flightTicks % 100 == 0 && entity instanceof Player player) {
            addXp(tool, 1, player);
        }
        return false;
    }

    @Override
    public void onAttacked(IToolStackView tool, ModifierEntry entry, EquipmentContext context,
                           EquipmentSlot slot, DamageSource source, float amount, boolean isDirectDamage) {
        if (!(context.getEntity() instanceof Player player)) {
            return;
        }
        boolean mobDamage = source.getEntity() != player && source.getEntity() instanceof LivingEntity;
        ModifierEntry blockingModifier = tool.getModifiers().getEntry(ModifierIds.blocking);
        boolean levelable = slot.isArmor() && (mobDamage || TinkerConfig.allowArmorExploits.get());
        if (!levelable && player.isBlocking() && blockingModifier != ModifierEntry.EMPTY) {
            levelable = GeneralInteractionModifierHook.getActiveModifier(tool).equals(blockingModifier);
        }
        if (isDirectDamage && levelable && !player.level().isClientSide) {
            addXp(tool, 1, player);
        }
    }

    @Override
    public void afterMeleeHit(IToolStackView tool, ModifierEntry entry, ToolAttackContext context, float damageDealt) {
        LivingEntity target = context.getLivingTarget();
        Player player = context.getPlayerAttacker();
        if (target == null || player == null || target.level().isClientSide) {
            return;
        }
        if (!target.isAlive()) {
            addXp(tool, Math.round(damageDealt), player);
            return;
        }
        IModDataView data = tool.getPersistentData();
        if (data.contains(UUID_KEY, Tag.TAG_INT_ARRAY)) {
            target.getData(TinkerLeveling.DAMAGE_XP.get())
                    .addDamageFromTool(damageDealt, NbtUtils.loadUUID(data.get(UUID_KEY)), player);
        }
    }

    @Override
    public void updateHarvestEnchantments(IToolStackView tool, ModifierEntry entry, ToolHarvestContext context,
                                          EquipmentContext equipmentContext, EquipmentSlot slot,
                                          Map<Holder<Enchantment>, Integer> enchantments) {
        if (context.getPlayer() != null) {
            addXp(tool, 1, context.getPlayer());
        }
    }

    @Override
    public void afterShearEntity(IToolStackView tool, ModifierEntry entry, Player player, Entity entity, boolean isTarget) {
        addXp(tool, 1, player);
    }

    @Override
    public void onProjectileLaunch(IToolStackView tool, ModifierEntry entry, LivingEntity entity,
                                   Projectile projectile, @Nullable AbstractArrow arrow,
                                   ModDataNBT persistentData, boolean primary) {
        if (!(entity instanceof Player player)) {
            return;
        }
        ItemStack stack = player.getUseItem();
        if (stack.isEmpty()) {
            ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
            stack = mainHand.getItem() == tool.getItem() ? mainHand : offHand;
        }
        if (stack.getItem() == tool.getItem() && stack.getItem() instanceof ModifiableLauncherItem) {
            float drawSpeed = ConditionalStatModifierHook.getModifiedStat(tool, player, ToolStats.DRAW_SPEED) / 20.0f;
            if (drawSpeed <= 0) {
                return;
            }
            int fullDrawTime = Mth.ceil(1.0f / drawSpeed);
            if ((arrow != null && arrow.shotFromCrossbow()) || player.getTicksUsingItem() >= fullDrawTime) {
                synchronized (LAUNCH_INFO_MAP) {
                    LAUNCH_INFO_MAP.put(projectile, Pair.of(stack, fullDrawTime));
                }
            }
        }
    }

    @Override
    public boolean onProjectileHitEntity(ModifierNBT modifiers, ModDataNBT persistentData, ModifierEntry entry,
                                         Projectile projectile, EntityHitResult hit,
                                         @Nullable LivingEntity attacker, @Nullable LivingEntity target) {
        if (projectile.getDeltaMovement().length() <= 0.4f || !(attacker instanceof Player player)) {
            return false;
        }
        Pair<ItemStack, Integer> launchInfo;
        synchronized (LAUNCH_INFO_MAP) {
            launchInfo = LAUNCH_INFO_MAP.remove(projectile);
        }
        if (launchInfo != null && launchInfo.getSecond() > 0) {
            int xp = Mth.ceil(5.0d * launchInfo.getSecond() / 20.0d);
            addXp(ToolStack.from(launchInfo.getFirst()), xp, player);
        }
        return false;
    }
}
