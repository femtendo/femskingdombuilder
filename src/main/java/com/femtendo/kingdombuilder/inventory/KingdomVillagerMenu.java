package com.femtendo.kingdombuilder.inventory;

import com.femtendo.kingdombuilder.entities.KingdomVillagerEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
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

        // 1. Add the 8 generic inventory slots (Slots 0 - 7)
        for (int i = 0; i < 8; ++i) {
            this.addSlot(new Slot(this.genericInventory, i, 8 + i * 18, 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return true;
                }
            });
        }

        // 2. Add the 1 "Tool/Weapon" slot wrapping MAINHAND (Slot 8)
        // POINTER: This slot wraps the entity's MAINHAND directly to prevent desyncs.
        this.addSlot(new Slot(new SimpleContainer(1), 0, 8 + 8 * 18 + 10, 18) {
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
                // Entity handles its own sync when setItemSlot is called.
            }

            @Override
            public int getMaxStackSize() {
                return 1; // Tools/weapons are typically stack size 1, but this enforces it for the slot.
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
                // POINTER: Future jobs could limit this to valid tools/weapons via tags
                return true; 
            }
        });

        // 3. Add Player Inventory and Hotbar (Slots 9 - 44)
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 50 + i * 18));
            }
        }
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 108));
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

            // 0-7: Generic, 8: Tool, 9-35: Player Inv, 36-44: Hotbar
            if (pIndex < 9) {
                // Shift-clicking from villager to player
                if (!this.moveItemStackTo(slotStack, 9, 45, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Shift-clicking from player to villager
                // POINTER: Priority to Slot 8 (Tool/Weapon) if valid (currently any item).
                if (!this.moveItemStackTo(slotStack, 8, 9, false)) {
                    // Fallback to Slots 0-7 (Generic)
                    if (!this.moveItemStackTo(slotStack, 0, 8, false)) {
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
