package com.femtendo.kingdombuilder.entities.jobs;

import com.femtendo.kingdombuilder.entities.KingdomVillagerEntity;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

// POINTER: Manages the active job for a KingdomVillager based on their equipped tools.
public class JobManager {
    private final KingdomVillagerEntity entity;
    private Job currentJob;
    private ItemStack lastEvaluatedMainHand = ItemStack.EMPTY;

    // POINTER: Custom tag definitions using 1.21.1 ResourceLocation.fromNamespaceAndPath.
    public static final TagKey<Item> GUARD_WEAPON = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("kingdombuilder", "tools/guard_weapon"));

    // POINTER: The Job Factory Registry. 
    // This LinkedHashMap maps a specific Item Tag to a Supplier that creates the corresponding Job.
    // To register a new job (e.g. Lumberjack):
    // 1. Define a new TagKey<Item> (e.g. LUMBERJACK_AXE).
    // 2. Add it to this map: JOB_REGISTRY.put(LUMBERJACK_AXE, LumberjackJob::new);
    // 3. Create the corresponding JSON file in data/kingdombuilder/tags/item/tools/
    // Using LinkedHashMap ensures insertion order priority if an item has multiple tags.
    private static final Map<TagKey<Item>, Supplier<Job>> JOB_REGISTRY = new LinkedHashMap<>();

    static {
        JOB_REGISTRY.put(GUARD_WEAPON, GuardJob::new);
    }

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
        Job newJob = null;

        if (!mainHand.isEmpty()) {
            for (Map.Entry<TagKey<Item>, Supplier<Job>> entry : JOB_REGISTRY.entrySet()) {
                if (mainHand.is(entry.getKey())) {
                    newJob = entry.getValue().get();
                    break;
                }
            }
        }

        // Default job fallback if no recognized tags are matched or hands are empty
        if (newJob == null) {
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
