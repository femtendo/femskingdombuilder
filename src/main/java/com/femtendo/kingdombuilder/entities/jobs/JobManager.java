package com.femtendo.kingdombuilder.entities.jobs;

import com.femtendo.kingdombuilder.entities.KingdomVillagerEntity;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;

// POINTER: Manages the active job for a KingdomVillager based on their equipped tools.
public class JobManager {
    private final KingdomVillagerEntity entity;
    private Job currentJob;
    private ItemStack lastEvaluatedMainHand = ItemStack.EMPTY;

    // POINTER: Future-proofing - Tag definitions. Using standard forge/minecraft tags for weapons if possible,
    // but defining custom tags as per spec. For now we use the common minecraft:swords tag.
    public static final TagKey<Item> GUARD_WEAPON = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("minecraft", "swords"));

    public JobManager(KingdomVillagerEntity entity) {
        this.entity = entity;
    }

    // POINTER: Called every tick or when an item change is suspected.
    public void tick() {
        ItemStack currentMainHand = this.entity.getItemBySlot(EquipmentSlot.MAINHAND);

        // Check for weapon breakage or item switch
        if (!ItemStack.matches(this.lastEvaluatedMainHand, currentMainHand)) {
            // DEV NOTE: Expand weapon breakage to allow resupply later.
            // If the weapon breaks, they fall back to CivilianJob, but we might want them to pathfind to a weapon rack.
            this.lastEvaluatedMainHand = currentMainHand.copy();
            evaluateJob(currentMainHand);
        }
    }

    private void evaluateJob(ItemStack mainHand) {
        Job newJob;

        if (!mainHand.isEmpty() && mainHand.is(GUARD_WEAPON)) {
            newJob = new GuardJob();
        } else {
            // Default job
            newJob = new CivilianJob();
        }

        // Only swap if class type differs (don't constantly recreate the same job)
        if (this.currentJob == null || this.currentJob.getClass() != newJob.getClass()) {
            setJob(newJob);
        }
    }

    private void setJob(Job newJob) {
        if (this.currentJob != null) {
            this.currentJob.onRemove(this.entity);
        }
        
        this.currentJob = newJob;
        this.currentJob.onAssign(this.entity);
    }
}
