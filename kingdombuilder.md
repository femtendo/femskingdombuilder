# Kingdom Builder Mod - Project Documentation & Implementation Plan

"AGENT DIRECTIVE: All developers/agents working on this project MUST document their work twofold: (A) Write detailed, explanatory inline comments and pointers directly within the Java codebase. (B) Upon completing a task, you MUST update the 'Dev Notes & Progress' section of this .md file with a summary of what was built, technical hurdles overcome, and pointers for the next agent so they can seamlessly pick up the context."

## Project Overview
Kingdom Builder is a Minecraft mod (Forge 1.21.1) that introduces player-like NPCs (`KingdomVillagerEntity`) with custom AI, daily schedules, and a dynamic skin system. The project focuses on creating a high-fidelity kingdom-building experience while strictly excluding certain vanilla villager mechanics.

---

## Implemented Features

### 1. Kingdom Villager Entity
- **Base Class:** `AgeableMob` (specifically avoiding `Villager` to bypass hardcoded behaviors).
- **Appearance:** Uses a `PlayerModel` via `KingdomVillagerRenderer` to look like a standard Minecraft player.
- **Physics:** Standard player-like hitboxes and movement.
- **Registration:** Handled via `DeferredRegister` in `ModEntities` and `ModItems`.

### 2. Dynamic Skin System
- **Internal Skins:** Located in `src/main/resources/assets/kingdombuilder/textures/entity/kingdom_villager/skins/`.
- **External Skins:** Supports user-added skins in `config/kingdomconfig/skins/`.
- **Management:** `ClientSkinManager` (client-side only) caches external textures as `DynamicTexture` to prevent performance drops.
- **Selection:** `KingdomVillagerEntity` selects a random skin from the combined pool of internal and external assets upon spawning. The `skinId` is synchronized via `SynchedEntityData`.

### 3. AI & Brain System
- **Architecture:** Uses the vanilla `Brain` system with custom `Activity` packages defined in `KingdomVillagerAi`.
- **Daily Schedule:**
    - **06:00 (Tick 0):** Wake Up / Commute.
    - **07:00 (Tick 1000):** Work (Currently Idle/Wander).
    - **17:00 (Tick 11000):** Social Hour (Bell/Meeting Point).
    - **18:00 (Tick 13000):** Sleep (Bed/Home).
- **Key Behaviors:**
    - **Bed Claiming:** Successfully claims beds (`PoiTypes.HOME`) and sleeps through the night.
    - **Door Navigation:** Can open and close doors while pathfinding.
    - **Panic:** Reacts to being hurt but does NOT trigger Iron Golem spawning.
- **Technical Stability:** 
    - AI is maintained through `UpdateActivityFromSchedule` in the `CORE` activity to ensure synchronization with the world clock.
    - Missing memory modules (e.g., `POTENTIAL_JOB_SITE`, `JOB_SITE`) are registered to prevent crashes in inherited vanilla logic.

### 4. Strict Exclusions
- **Trading:** Right-clicking the entity does nothing (`mobInteract` returns `PASS`).
- **Breeding:** `canBreed` always returns `false`.
- **Farming/Food:** `wantsMoreFood`, `hasExcessFood`, and `hasFarmSeeds` return `false`.
- **Looting:** `canPickUpLoot` returns `false`.
- **Iron Golems:** Golem spawning logic is entirely bypassed and disabled.

---

## Technical Architecture Notes

### Core Components
- **`KingdomVillagerEntity`**: The main entity class. Manages state, registry, and brain initialization.
- **`KingdomVillagerAi`**: Static helper class containing brain provider setup and activity registration logic.
- **`KingdomVillagerRenderer`**: Handles client-side rendering, using `PlayerModel` and the dynamic skin system.
- **`ClientSkinManager`**: Orchestrates the loading of external `.png` files into Minecraft's texture manager.

### Key Workflows
- **Spawning:** `finalizeSpawn` chooses a random `skinId`. This ID is used by the renderer to fetch the correct `ResourceLocation`.
- **AI Ticking:** The brain updates activities based on the world time. `KingdomVillagerEntity` no longer overrides `customServerAiStep` for manual schedule updates to prevent instability.
- **Death/POI Release:** Overridden `releaseAllPois` ensures only relevant POIs are released, avoiding crashes on unregistered memory modules.

---

## Dev Notes & Progress

### Summary of Current State
The foundation for the Kingdom Villager is complete. The entity looks like a player, follows a custom schedule, sleeps in beds, navigates doors, and has a flexible skin system. Null-safety improvements have been integrated into the config and skin loading pipelines.

### Technical Hurdles Overcome
- **AI Stability:** Resolved "double-ticking" and immediate wake-up issues by switching to vanilla schedule enforcement.
- **Compilation/Type Safety:** Navigated complex generic type requirements for `Behavior` and `OneShot` in the AI system.
- **Asset Integrity:** Fixed corrupted PNG assets and ensured external directory generation.

### Pointers for Next Agents
1. **Work Activities:** Currently, `Activity.WORK` just triggers wandering. Future tasks should implement actual professions or tasks (e.g., mining, building).
2. **External Skins:** The `SKIN_POOL` is currently populated on entity initialization. If the user adds skins while the game is running, a restart is required.
3. **Guard Logic:** If defense is needed, it must be a custom behavior since vanilla Iron Golem logic is disabled.
4. **Gossip System:** Gossip logic was removed for stability; it can be re-added if social mechanics are required.

---

## Dual-Documentation Mandate
AGENT DIRECTIVE: All developers/agents working on this project MUST document their work twofold:
(A) Write detailed, explanatory inline comments and pointers directly within the Java codebase.
(B) Upon completing a task, you MUST update the 'Dev Notes & Progress' section of this .md file with a summary of what was built, technical hurdles overcome, and pointers for the next agent so they can seamlessly pick up the context.
