package org.embeddedt.tinkerleveling;

import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

public final class ToolHelper {
    private ToolHelper() {}

    public static boolean isEqualTinkersItem(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.getItem() != second.getItem()) {
            return false;
        }
        ToolStack firstTool = ToolStack.from(first);
        ToolStack secondTool = ToolStack.from(second);
        return firstTool.getModifiers().equals(secondTool.getModifiers())
                && firstTool.getMaterials().equals(secondTool.getMaterials());
    }

    public static boolean syncToPlayerInventory(ToolStack source, Player player) {
        if (!source.getPersistentData().contains(ModToolLeveling.UUID_KEY, Tag.TAG_INT_ARRAY)) {
            return false;
        }
        UUID sourceId = NbtUtils.loadUUID(source.getPersistentData().get(ModToolLeveling.UUID_KEY));
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof IModifiable) {
                ToolStack candidate = ToolStack.from(stack);
                if (candidate.getPersistentData().contains(ModToolLeveling.UUID_KEY, Tag.TAG_INT_ARRAY)
                        && sourceId.equals(NbtUtils.loadUUID(candidate.getPersistentData().get(ModToolLeveling.UUID_KEY)))) {
                    // Keep the source and inventory stack backed by the same NBT instance. Experience
                    // is awarded from modifier hooks that may run before Tinkers' Construct applies
                    // the tool's durability damage. The default updateStack call copies the NBT and
                    // leaves the active ToolStack pointing at the old compound, so the subsequent
                    // damage is written to a detached object and the item appears indestructible.
                    source.updateStack(stack, false);
                    inventory.setChanged();
                    return true;
                }
            }
        }
        return false;
    }
}
