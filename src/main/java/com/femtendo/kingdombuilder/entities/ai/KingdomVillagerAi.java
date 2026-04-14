package com.femtendo.kingdombuilder.entities.ai;

import com.femtendo.kingdombuilder.entities.KingdomVillagerEntity;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import java.util.Optional;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.entity.Mob;

// POINTER: This class contains the core AI logic for our villager. We are mimicking the vanilla
// villager AI structure by creating "packages" of behaviors for each activity.
public class KingdomVillagerAi {

    // POINTER: Custom schedule based on the Kingdom specification.
    public static final Schedule KINGDOM_SCHEDULE = new ScheduleBuilder(new Schedule())
        .changeActivityAt(0, Activity.IDLE) // Wake up / Commute
        .changeActivityAt(1000, Activity.WORK) // Work (Idle/Wander)
        .changeActivityAt(11000, Activity.MEET) // Social Hour at Bell
        .changeActivityAt(13000, Activity.REST) // Sleep at Home
        .build();

    public static Brain<?> BrainProvider(KingdomVillagerEntity villager, Brain<Villager> brain) {
        registerCoreActivities(brain);
        registerIdleActivity(brain);
        registerWorkActivity(brain);
        registerMeetActivity(brain);
        registerRestActivity(brain);

        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    // POINTER: Currently, work consists of idle/wander activities. Future tasks will add job-specific actions.
    private static void registerWorkActivity(Brain<Villager> brain) {
        brain.addActivity(Activity.WORK, 10, ImmutableList.of(
            new RunOne<>(
                ImmutableList.of(
                    Pair.of(InteractWith.of(EntityType.PLAYER, 8, MemoryModuleType.INTERACTION_TARGET, 0.5f, 2), 2),
                    Pair.of(RandomStroll.stroll(0.6f), 2),
                    Pair.of(new DoNothing(30, 60), 1)
                )
            )
        ));
    }

    // POINTER: These are the core, always-on behaviors. They run regardless of the current activity.
    // Includes critical survival logic like swimming and panicking when hurt.
    private static void registerCoreActivities(Brain<Villager> brain) {
        brain.addActivity(Activity.CORE, 0, ImmutableList.of(
            new Swim(0.8f),
            new VillagerPanicTrigger(), // POINTER: This makes the villager run when hit.
            InteractWithDoor.create(), // POINTER: Allows the villager to open and close doors while pathfinding.
            WakeUp.create(), // POINTER: Wakes up the villager when the schedule changes.
            new LookAtTargetSink(45, 90),
            new MoveToTargetSink(),
            AcquirePoi.create((holder) -> holder.is(PoiTypes.HOME), MemoryModuleType.HOME, false, Optional.of((byte) 14)) // POINTER: Claim beds
        ));
    }

    // POINTER: This is the default state. When the villager has nothing else to do, it will wander,
    // look at players, or just stand around. This is crucial for making the entity feel alive.
    private static void registerIdleActivity(Brain<Villager> brain) {
        brain.addActivity(Activity.IDLE, 10, ImmutableList.of(
            new RunOne<>(
                ImmutableList.of(
                    Pair.of(InteractWith.of(EntityType.PLAYER, 8, MemoryModuleType.INTERACTION_TARGET, 0.5f, 2), 2),
                    Pair.of(RandomStroll.stroll(0.6f), 2),
                    Pair.of(new DoNothing(30, 60), 1)
                )
            )
        ));
    }

    // POINTER: This activity handles the villager's sleeping behavior. It now includes pathfinding
    // to the bed (HOME memory) before attempting to sleep.
    private static void registerRestActivity(Brain<Villager> brain) {
        // POINTER: We use the vanilla RestPackage to ensure all bed validation and sleeping logic is perfectly replicated.
        brain.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(VillagerProfession.NONE, 0.5F));
    }

    // POINTER: This activity is for gathering at the village bell. It now includes pathfinding
    // to the MEETING_POINT before the villager starts to linger.
    private static void registerMeetActivity(Brain<Villager> brain) {
         brain.addActivity(Activity.MEET, 10, ImmutableList.of(
            SetWalkTargetFromBlockMemory.create(MemoryModuleType.MEETING_POINT, 0.5F, 2, 10, 2),
            new RunOne<>(
                 ImmutableList.of(
                    Pair.of(RandomStroll.stroll(0.6f), 2),
                    Pair.of(new DoNothing(30, 60), 1)
                )
            )
        ));
    }
}
