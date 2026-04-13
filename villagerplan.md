# Kingdom Villager Entity Implementation Plan

## Project Overview
Implement the `KingdomVillagerEntity`, a custom Minecraft NPC for version 1.21.1 that looks like a player and follows specific vanilla village behaviors while excluding others.

## Main Goals
- Create a player-like NPC (`KingdomVillagerEntity`) from scratch.
- Implement a dynamic skin system using local assets.
- Port specific vanilla Brain/Activity behaviors: Bed-claiming, Sleeping, and Bell-gathering.
- Implement a custom daily schedule.
- Ensure strict exclusion of unwanted vanilla villager behaviors (Trading, Golems, Breeding, etc.).

## Technical Requirements
- **Minecraft Version:** 1.21.1 (Forge)
- **Base Class:** `PathfinderMob` or `AgeableMob` (NOT `Villager`)
- **Naming Convention:** `kingdom_villager`
- **Model:** Standard Bipedal (Player-like)

## Roadmap & Features

### 1. Core Entity & Registry
- [ ] Create `KingdomVillagerEntity` class.
- [ ] Register entity type and Spawn Egg.
- [ ] Set up player hitboxes and physics.

### 2. Player-Like Model & Skin System
- [ ] Implement bipedal model and animations.
- [ ] Create local `skins` folder in assets.
- [ ] Implement random skin selection logic from local assets.

### 3. Brain & Activity System (AI Porting)
- [ ] Initialize Brain with required Sensors (`NEAREST_BED`, `SECONDARY_POIS`).
- [ ] Initialize Memory Modules (`HOME`, `MEETING_POINT`, `GOSSIPS`).
- [ ] Implement `Activity.REST` (Bed/Home POI logic).
- [ ] Implement `Activity.MEET` (Bell/Meeting Point logic).
- [ ] Maintain `GOSSIPS` memory for future use.

### 4. Custom Schedule Integration
- [ ] **0600 (Tick 0):** Wake up / Commute.
- [ ] **0700 (Tick 1000):** Work (Idle/Wander).
- [ ] **1700 (Tick 11000):** Social Hour (Bell).
- [ ] **1800 (Tick 12000):** Sleep (Home).

### 5. Strict Exclusions & Quality Control
- [ ] Remove/Exclude Iron Golem logic.
- [ ] Remove/Exclude Trading mechanics.
- [ ] Remove/Exclude Breeding/Love mechanics.
- [ ] Remove/Exclude Farming/Food-sharing.

---

## Development Notes & Progress
*To be updated as tasks are completed.*
