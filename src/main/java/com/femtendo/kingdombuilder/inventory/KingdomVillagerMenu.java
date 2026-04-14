package com.femtendo.kingdombuilder.inventory;

import com.femtendo.kingdombuilder.entities.KingdomVillagerEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Container;

public class KingdomVillagerMenu extends AbstractContainerMenu {

    private final KingdomVillagerEntity villager;
    private final Container genericInventory;

    public KingdomVillagerMenu(int pContainerId, Inventory playerInventory, KingdomVillagerEntity villager) {
        // POINTER: MenuType is registered in ModMenus
        super(ModMenus.KINGDOM_VILLAGER_MENU.get(), pContainerId);
        this.villager = villager;
        // POINTER: Accessing the custom 8-slot generic inventory.
        this.genericInventory = villager.getInventory();

        // 1. Add the 1 "Tool/Weapon" slot wrapping MAINHAND (Slot 0)
        // POINTER: Placed prominently at top-center.
        this.addSlot(new Slot(new SimpleContainer(1), 0, 80, 37) {
            @Override
            public ItemStack getItem() {
                return villager.getItemBySlot(EquipmentSlot.MAINHAND);
            }

            @Override
            public void setByPlayer(ItemStack stack, ItemStack previousStack) {
                villager.setItemSlot(EquipmentSlot.MAINHAND, stack);
                this.setChanged();
            }

            @Override
            public void set(ItemStack pStack) {
                villager.setItemSlot(EquipmentSlot.MAINHAND, pStack);
                this.setChanged();
            }

            @Override
            public void setChanged() {
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public ItemStack remove(int pAmount) {
                ItemStack stack = this.getItem();
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                ItemStack split = stack.split(pAmount);
                if (stack.isEmpty()) {
                    this.set(ItemStack.EMPTY);
                } else {
                    this.set(stack);
                }
                return split;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                // Limit to tools/weapons to prevent random blocks from entering the tool slot
                return stack.getItem() instanceof net.minecraft.world.item.TieredItem ||
                       stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem ||
                       stack.getItem() instanceof net.minecraft.world.item.TridentItem ||
                       stack.getItem() instanceof net.minecraft.world.item.ShieldItem;
            }
        });

        // 2. Add the 8 generic inventory slots (Slots 1 - 8)
        // POINTER: Row directly below Tool slot
        for (int i = 0; i < 8; ++i) {
            this.addSlot(new Slot(this.genericInventory, i, 8 + i * 18, 65) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return true;
                }
            });
        }

        // 3. Add Player Inventory and Hotbar (Slots 9 - 44)
        // POINTER: Standard bottom-half configuration
        // Change the addSlot line inside the double loop:
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 7 + j * 18, 89 + i * 18));
            }
        }
        
        // 4. Add the Hotbar
        for (int i = 0; i < 9; ++i) {
            // The last number is the Y-coordinate. 
            // Decrease this number to move the hotbar UP.
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 150));
        }
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return this.villager.isAlive() && pPlayer.distanceToSqr(this.villager) < 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(pIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            // Slots map: 0 is Tool, 1-8 is Generic, 9-35 is Player Inv, 36-44 is Hotbar
            if (pIndex < 9) {
                // Shift-clicking from villager to player
                if (!this.moveItemStackTo(slotStack, 9, 45, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Shift-clicking from player to villager
                // Priority to Slot 0 (Tool/Weapon) if valid
                boolean movedToVillager = false;
                if (this.slots.get(0).mayPlace(slotStack)) {
                    movedToVillager = this.moveItemStackTo(slotStack, 0, 1, false);
                }
                
                if (!movedToVillager) {
                    // Fallback to Slots 1-8 (Generic)
                    movedToVillager = this.moveItemStackTo(slotStack, 1, 9, false);
                }
                
                // If still not moved, move between player inv and hotbar
                if (!movedToVillager) {
                    if (pIndex >= 9 && pIndex < 36) {
                        if (!this.moveItemStackTo(slotStack, 36, 45, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (pIndex >= 36 && pIndex < 45 && !this.moveItemStackTo(slotStack, 9, 36, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY, slotStack);
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (slotStack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(pPlayer, slotStack);
        }

        return itemstack;
    }
}
