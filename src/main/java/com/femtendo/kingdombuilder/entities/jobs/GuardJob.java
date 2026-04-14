package com.femtendo.kingdombuilder.entities.jobs;

import java.util.function.Predicate;

import com.femtendo.kingdombuilder.entities.KingdomVillagerEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;

// POINTER: Job assigned when a recognized sword/weapon is equipped.
public class GuardJob implements Job {
    private MeleeAttackGoal meleeAttackGoal;
    private HurtByTargetGoal hurtByTargetGoal;
    private NearestAttackableTargetGoal<LivingEntity> defendVillagerGoal;
    private NearestAttackableTargetGoal<LivingEntity> hostileTargetGoal;

    @Override
    public void onAssign(KingdomVillagerEntity entity) {
        // POINTER: Add MeleeAttackGoal with priority 2
        // We use 1.0D so the villager moves at regular speed instead of sprinting to targets
        this.meleeAttackGoal = new MeleeAttackGoal(entity, 1.0D, false);
        entity.goalSelector.addGoal(2, this.meleeAttackGoal);

        // POINTER: Target selector priority:
        // 1) Active attackers of this specific villager
        this.hurtByTargetGoal = new HurtByTargetGoal(entity);
        entity.targetSelector.addGoal(1, this.hurtByTargetGoal);

        // 2) Entities attacking other villagers in the area
        Predicate<LivingEntity> attackingVillagerPredicate = target -> {
            if (!(target instanceof Enemy)) return false;
            if (target instanceof net.minecraft.world.entity.Mob mob) {
                LivingEntity targetOfTarget = mob.getTarget();
                return targetOfTarget instanceof Villager;
            }
            return false;
        };
        this.defendVillagerGoal = new NearestAttackableTargetGoal<>(entity, LivingEntity.class, 10, true, false, attackingVillagerPredicate);
        entity.targetSelector.addGoal(2, this.defendVillagerGoal);

        // 3) Closest hostile mob
        Predicate<LivingEntity> hostilePredicate = target -> target instanceof Enemy;
        this.hostileTargetGoal = new NearestAttackableTargetGoal<>(entity, LivingEntity.class, 10, true, false, hostilePredicate);
        entity.targetSelector.addGoal(3, this.hostileTargetGoal);
    }

    @Override
    public void onRemove(KingdomVillagerEntity entity) {
        // POINTER: Instantly abort the current attack goal and target when removing GuardJob
        if (this.meleeAttackGoal != null) {
            entity.goalSelector.removeGoal(this.meleeAttackGoal);
        }
        if (this.hurtByTargetGoal != null) {
            entity.targetSelector.removeGoal(this.hurtByTargetGoal);
        }
        if (this.defendVillagerGoal != null) {
            entity.targetSelector.removeGoal(this.defendVillagerGoal);
        }
        if (this.hostileTargetGoal != null) {
            entity.targetSelector.removeGoal(this.hostileTargetGoal);
        }
        entity.setTarget(null);
    }
}
