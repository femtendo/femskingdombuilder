package com.femtendo.kingdombuilder.entities.jobs;

import com.femtendo.kingdombuilder.entities.KingdomVillagerEntity;

// POINTER: Base Job interface. Represents a dynamic role a KingdomVillager can take on.
public interface Job {
    // Called when the job is assigned to a villager. Inject goals here.
    void onAssign(KingdomVillagerEntity entity);
    
    // Called when the job is removed from a villager. Remove injected goals here.
    void onRemove(KingdomVillagerEntity entity);
}
