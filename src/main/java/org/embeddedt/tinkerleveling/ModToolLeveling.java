package org.embeddedt.tinkerleveling;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.EntityHitResult;
import org.embeddedt.tinkerleveling.data.LevelingRules;
import org.embeddedt.tinkerleveling.data.LevelingRules.LevelingRule;
import org.embeddedt.tinkerleveling.data.LevelingRules.SlotGrant;
import org.embeddedt.tinkerleveling.data.LevelingRules.SlotGrantResult;
import org.embeddedt.tinkerleveling.data.SlotTypeLoadable;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class ModToolLeveling extends Modifier implements BlockBreakModifierHook,
        ElytraFlightModifierHook, OnAttackedModifierHook, MeleeHitModifierHook,
        RawDataModifierHook, VolatileDataModifierHook, ModifierRemovalHook,
        HarvestEnchantmentsModifierHook, ShearsModifierHook,
        ProjectileHitModifierHook, ProjectileLaunchModifierHook {

    public static final ResourceLocation XP_KEY = TinkerLeveling.id("xp");
    public static final ResourceLocation LEVEL_KEY = TinkerLeveling.id("level");
    public static final ResourceLocation UUID_KEY = TinkerLeveling.id("uuid");
    /** Slots earned from leveling, stored as a compound of slot type name to count */
    public static final ResourceLocation BONUS_SLOTS_KEY = TinkerLeveling.id("bonus_slots");
    /** Slots the player still has to assign, stored the same way as {@link #BONUS_SLOTS_KEY} */
    public static final ResourceLocation PENDING_SLOTS_KEY = TinkerLeveling.id("pending_slots");
    /** Legacy key from before data packs, migrated into {@link #BONUS_SLOTS_KEY} on load */
    private static final ResourceLocation LEGACY_BONUS_MODIFIERS_KEY = TinkerLeveling.id("bonus_modifiers");

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
        CompoundTag granted = context.getPersistentData().getCompound(BONUS_SLOTS_KEY);
        for (String slotName : granted.getAllKeys()) {
            int count = granted.getInt(slotName);
            if (count > 0) {
                volatileData.addSlots(SlotType.getOrCreate(slotName), count);
            }
        }
    }

    @Override
    public Component onRemoved(IToolStackView tool, Modifier modifier) {
        tool.getPersistentData().remove(XP_KEY);
        tool.getPersistentData().remove(LEVEL_KEY);
        tool.getPersistentData().remove(UUID_KEY);
        tool.getPersistentData().remove(BONUS_SLOTS_KEY);
        tool.getPersistentData().remove(PENDING_SLOTS_KEY);
        tool.getPersistentData().remove(LEGACY_BONUS_MODIFIERS_KEY);
        return null;
    }

    @Override
    public void addRawData(IToolStackView tool, ModifierEntry entry, RestrictedCompoundTag tag) {
        ModDataNBT persistent = tool.getPersistentData();
        if (!persistent.contains(UUID_KEY, Tag.TAG_INT_ARRAY)) {
            persistent.put(UUID_KEY, NbtUtils.createUUID(UUID.randomUUID()));
        }
        if (persistent.getInt(LEVEL_KEY) <= 0) {
            persistent.putInt(LEVEL_KEY, 1);
        }
        migrateLegacySlots(persistent);
    }

    @Override
    public void removeRawData(IToolStackView tool, Modifier modifier, RestrictedCompoundTag tag) {
        tool.getPersistentData().remove(UUID_KEY);
        tool.getPersistentData().remove(LEVEL_KEY);
    }

    /**
     * Moves the pre data pack bonus modifier counter into per slot type storage. That counter used to split into
     * {@code abilities = n / 2} and {@code upgrades = n - n / 2}, so the migration keeps the exact same slot counts.
     */
    private static void migrateLegacySlots(ModDataNBT persistent) {
        int legacy = persistent.getInt(LEGACY_BONUS_MODIFIERS_KEY);
        if (legacy <= 0) {
            return;
        }
        persistent.remove(LEGACY_BONUS_MODIFIERS_KEY);
        CompoundTag granted = persistent.getCompound(BONUS_SLOTS_KEY).copy();
        int abilities = legacy / 2;
        addToCompound(granted, SlotType.ABILITY, abilities);
        addToCompound(granted, SlotType.UPGRADE, legacy - abilities);
        persistent.put(BONUS_SLOTS_KEY, granted);
    }

    /** Adds {@code amount} to a counter inside a compound, dropping the entry when the total is zero */
    private static void addToCompound(CompoundTag compound, SlotType type, int amount) {
        String name = SlotTypeLoadable.nameOf(type);
        int updated = compound.getInt(name) + amount;
        if (updated <= 0) {
            compound.remove(name);
        } else {
            compound.putInt(name, updated);
        }
    }

    /** Reads a counter compound into a map, skipping non positive entries */
    private static Map<SlotType,Integer> readCounters(IModDataView data, ResourceLocation key) {
        Map<SlotType,Integer> counts = new LinkedHashMap<>();
        CompoundTag compound = data.getCompound(key);
        for (String name : compound.getAllKeys()) {
            int count = compound.getInt(name);
            if (count > 0) {
                counts.put(SlotType.getOrCreate(name), count);
            }
        }
        return counts;
    }

    /** Writes a counter map back into persistent data, or removes the key when everything is spent */
    private static void writeCounters(ModDataNBT data, ResourceLocation key, Map<SlotType,Integer> counts) {
        CompoundTag compound = new CompoundTag();
        counts.forEach((type, count) -> {
            if (count > 0) {
                compound.putInt(SlotTypeLoadable.nameOf(type), count);
            }
        });
        if (compound.isEmpty()) {
            data.remove(key);
        } else {
            data.put(key, compound);
        }
    }

    /** Slots the player has not assigned yet */
    public static Map<SlotType,Integer> getPendingSlots(IToolStackView tool) {
        return readCounters(tool.getPersistentData(), PENDING_SLOTS_KEY);
    }

    /** Rule currently driving this tool, resolved from the loaded data packs */
    public static LevelingRule getRule(IToolStackView tool) {
        return LevelingRules.INSTANCE.getRule(tool);
    }

    /** Experience needed to advance from {@code level} to {@code level + 1} for this specific tool */
    public static int getXpForLevelup(int level, IToolStackView tool) {
        return getRule(tool).xpForLevel(level);
    }

    public void addXp(IToolStackView tool, int amount, Player player) {
        if (amount <= 0 || player.level().isClientSide) {
            return;
        }
        if (!(tool instanceof ToolStack toolStack)) {
            throw new IllegalStateException("Unable to rebuild a non-ToolStack leveling tool");
        }
        ModDataNBT levelData = tool.getPersistentData();
        int level = Math.max(1, levelData.getInt(LEVEL_KEY));
        LevelingRule rule = getRule(tool);
        if (!rule.canLevelUp(level)) {
            return;
        }
        // the player decides where the next slots go, so stop earning levels until that is settled
        if (!getPendingSlots(tool).isEmpty()) {
            return;
        }

        long updatedXp = (long) levelData.getInt(XP_KEY) + amount;
        levelData.putInt(XP_KEY, (int) Math.min(Integer.MAX_VALUE, updatedXp));
        int requiredXp = rule.xpForLevel(level);
        if (levelData.getInt(XP_KEY) < requiredXp) {
            ToolHelper.syncToPlayerInventory(toolStack, player);
            return;
        }

        int newLevel = level + 1;
        levelData.putInt(XP_KEY, levelData.getInt(XP_KEY) - requiredXp);
        levelData.putInt(LEVEL_KEY, newLevel);

        SoundUtils.playSoundForAll(player, TinkerLeveling.SOUND_LEVELUP.get(), 1.0f, 1.0f);
        TinkerPacketHandler.sendLevelUp(newLevel, toolStack.getItem().getDescriptionId(), player);
        grantSlots(toolStack, rule, newLevel, player);

        if (TinkerConfig.debugLogging.get()) {
            TinkerLeveling.LOG.info("{} reached level {} using rule {}{}", toolStack.getItem(), newLevel,
                    rule.isConfigDefault() ? "config defaults" : rule.id(),
                    getPendingSlots(toolStack).isEmpty() ? "" : " (slot choice pending)");
        }

        toolStack.rebuildStats();
        ToolHelper.syncToPlayerInventory(toolStack, player);
    }

    /**
     * Hands out the slots configured for the reached level. Grants in {@code CHOOSE} mode are queued instead, so the
     * player can pick later.
     */
    private void grantSlots(ToolStack tool, LevelingRule rule, int level, Player player) {
        SlotGrantResult result = LevelingRules.grantsFor(rule, level);
        if (result.isEmpty()) {
            return;
        }
        ModDataNBT data = tool.getPersistentData();
        Map<SlotType,Integer> granted = readCounters(data, BONUS_SLOTS_KEY);
        Map<SlotType,Integer> pending = readCounters(data, PENDING_SLOTS_KEY);

        for (SlotGrant grant : result.grants()) {
            if (grant.needsChoice()) {
                // every option stays queued until the player picks one
                for (SlotType type : grant.slotTypes()) {
                    pending.merge(type, 1, Integer::sum);
                }
            } else {
                for (SlotType type : grant.slotTypes()) {
                    granted.merge(type, 1, Integer::sum);
                }
            }
        }
        writeCounters(data, BONUS_SLOTS_KEY, granted);
        writeCounters(data, PENDING_SLOTS_KEY, pending);
        resolvePending(tool, level, player);
    }

    /**
     * Either hands the queued slots over directly, or asks the player to pick.
     * <p>
     * Only one option is ever left to pick between once the others have been resolved, and a lone option is not a
     * choice, so in that case it is granted outright rather than confirmed. Loops because handing over the last option
     * cannot free up another one, but the guard keeps that guarantee explicit.
     */
    private void resolvePending(ToolStack tool, int level, Player player) {
        for (int guard = 0; guard < MAX_PENDING_TYPES; guard++) {
            Map<SlotType,Integer> pending = getPendingSlots(tool);
            if (pending.size() != 1) {
                break;
            }
            applySlotChoice(tool, pending.keySet().iterator().next(), player, false);
        }
        sendPendingChoice(tool, level, player);
    }

    /** Maximum distinct slot types one level up can queue, used as a loop guard */
    private static final int MAX_PENDING_TYPES = 16;

    /** Tells the client which slot types are still waiting to be assigned */
    private void sendPendingChoice(ToolStack tool, int level, Player player) {
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        Map<SlotType,Integer> pending = getPendingSlots(tool);
        UUID toolId = NbtUtils.loadUUID(tool.getPersistentData().get(UUID_KEY));
        List<TinkerPacketHandler.SlotChoice> choices = new ArrayList<>(pending.size());
        for (Map.Entry<SlotType,Integer> entry : pending.entrySet()) {
            choices.add(new TinkerPacketHandler.SlotChoice(SlotTypeLoadable.nameOf(entry.getKey()), entry.getValue()));
        }
        TinkerPacketHandler.sendSlotChoice(toolId, level, tool.getItem().getDescriptionId(), choices, player);
    }

    /** Entry point for the network handler once the server validated a client choice */
    public void applySlotChoice(ToolStack tool, SlotType type, Player player) {
        applySlotChoice(tool, type, player, true);
    }

    /**
     * Applies one queued slot choice.
     * <p>
     * The reward for a level is a single choice, so the option the player picked is granted and the alternatives it was
     * offered against are dropped.
     * @param resolve  Whether to continue resolving the queue, false when called from {@link #resolvePending}
     */
    private void applySlotChoice(ToolStack tool, SlotType type, Player player, boolean resolve) {
        ModDataNBT data = tool.getPersistentData();
        Map<SlotType,Integer> pending = readCounters(data, PENDING_SLOTS_KEY);
        int available = pending.getOrDefault(type, 0);
        if (available <= 0) {
            return;
        }
        // the pick consumes the other options it was offered against
        pending.clear();
        if (available > 1) {
            pending.put(type, available - 1);
        }
        Map<SlotType,Integer> granted = readCounters(data, BONUS_SLOTS_KEY);
        granted.merge(type, 1, Integer::sum);
        writeCounters(data, BONUS_SLOTS_KEY, granted);
        writeCounters(data, PENDING_SLOTS_KEY, pending);

        tool.rebuildStats();
        ToolHelper.syncToPlayerInventory(tool, player);
        if (resolve) {
            resolvePending(tool, Math.max(1, data.getInt(LEVEL_KEY)), player);
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
