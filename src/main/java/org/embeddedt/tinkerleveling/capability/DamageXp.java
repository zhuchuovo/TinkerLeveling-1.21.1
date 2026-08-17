package org.embeddedt.tinkerleveling.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.tinkerleveling.ModToolLeveling;
import org.embeddedt.tinkerleveling.TinkerLeveling;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DamageXp {
    private static final String TAG_PLAYER_UUID = "player_uuid";
    private static final String TAG_DAMAGE_LIST = "damage_data";
    private static final String TAG_ITEM = "item";
    private static final String TAG_DAMAGE = "damage";

    private final Map<UUID, Map<UUID, Float>> playerToDamageMap = new HashMap<>();

    public void addDamageFromTool(float damage, UUID tool, Player player) {
        if (damage <= 0 || tool == null) {
            return;
        }
        Map<UUID, Float> damageMap = playerToDamageMap.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        damageMap.merge(tool, damage, Float::sum);
    }

    public void distributeXpToTools(LivingEntity deadEntity) {
        if (!(deadEntity.level() instanceof ServerLevel level)) {
            return;
        }
        playerToDamageMap.forEach((playerId, damageMap) -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                damageMap.forEach((toolId, damage) -> distributeXpToTool(player, toolId, damage));
            }
        });
        playerToDamageMap.clear();
    }

    private static void distributeXpToTool(ServerPlayer player, UUID toolId, float damage) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof IModifiable) {
                ToolStack tool = ToolStack.from(stack);
                if (tool.getPersistentData().contains(ModToolLeveling.UUID_KEY, Tag.TAG_INT_ARRAY)
                        && toolId.equals(NbtUtils.loadUUID(tool.getPersistentData().get(ModToolLeveling.UUID_KEY)))) {
                    TinkerLeveling.LEVELING_MODIFIER.get().addXp(tool, Math.round(damage), player);
                    return;
                }
            }
        }
    }

    public ListTag serializeNBT() {
        ListTag players = new ListTag();
        playerToDamageMap.forEach((playerId, damageMap) -> {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(TAG_PLAYER_UUID, playerId);
            ListTag damageList = new ListTag();
            damageMap.forEach((toolId, damage) -> {
                CompoundTag damageTag = new CompoundTag();
                damageTag.put(TAG_ITEM, NbtUtils.createUUID(toolId));
                damageTag.putFloat(TAG_DAMAGE, damage);
                damageList.add(damageTag);
            });
            playerTag.put(TAG_DAMAGE_LIST, damageList);
            players.add(playerTag);
        });
        return players;
    }

    public void deserializeNBT(ListTag players) {
        playerToDamageMap.clear();
        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            CompoundTag playerTag = players.getCompound(playerIndex);
            if (!playerTag.hasUUID(TAG_PLAYER_UUID)) {
                continue;
            }
            Map<UUID, Float> damageMap = new HashMap<>();
            ListTag damageList = playerTag.getList(TAG_DAMAGE_LIST, Tag.TAG_COMPOUND);
            for (int damageIndex = 0; damageIndex < damageList.size(); damageIndex++) {
                CompoundTag damageTag = damageList.getCompound(damageIndex);
                if (damageTag.contains(TAG_ITEM, Tag.TAG_INT_ARRAY)) {
                    damageMap.put(NbtUtils.loadUUID(damageTag.get(TAG_ITEM)), damageTag.getFloat(TAG_DAMAGE));
                }
            }
            playerToDamageMap.put(playerTag.getUUID(TAG_PLAYER_UUID), damageMap);
        }
    }
}
