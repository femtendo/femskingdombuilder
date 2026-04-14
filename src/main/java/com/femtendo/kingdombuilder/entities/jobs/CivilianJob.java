package com.femtendo.kingdombuilder.entities.jobs;

import com.femtendo.kingdombuilder.entities.KingdomVillagerEntity;
import net.minecraft.world.entity.ai.goal.PanicGoal;

// POINTER: Default job for unarmed villagers or villagers holding items with no specific job mapping.
// Prioritizes fleeing when hurt.
public class CivilianJob implements Job {
    private PanicGoal panicGoal;

    @Override
    public void onAssign(KingdomVillagerEntity entity) {
        // Create the goal once
        this.panicGoal = new PanicGoal(entity, 1.25D);
        
        // POINTER: Add PanicGoal with high priority (1)
        entity.goalSelector.addGoal(1, this.panicGoal);
    }

    @Override
    public void onRemove(KingdomVillagerEntity entity) {
        if (this.panicGoal != null) {
            entity.goalSelector.removeGoal(this.panicGoal);
        }
    }
}
