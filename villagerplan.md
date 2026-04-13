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
- [x] Create `KingdomVillagerEntity` class.
- [x] Register entity type and Spawn Egg.
- [x] Set up player hitboxes and physics.

### 2. Player-Like Model & Skin System
- [x] Implement bipedal model and animations.
- [x] Create local `skins` folder in assets.
- [x] Implement random skin selection logic from local assets.

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

### Task 1: Core Entity & Registry - Completed by Exec Agent

**Summary:** Created the `KingdomVillagerEntity` class, extending `AgeableMob`. Registered the entity and its corresponding spawn egg using `DeferredRegister`. The entity has standard player physics and hitboxes.

**Technical Notes/Hurdles:** No major hurdles. The Forge registration system is straightforward. I've set the base attributes for health and movement speed. The entity currently has basic AI goals (wander, look at player).

**Next Agent Pointers:** The next step is to implement the player-like model and skin system. You'll need to create a new renderer for the `KingdomVillagerEntity` and override the default `AgeableMob` renderer. The renderer should use the standard player model. You will also need to implement the logic to randomly select a skin from the `assets/femskingdombuilder/textures/entity/kingdom_villager` directory.

### Task 2: Player-Like Model & Skin System - Completed by Exec Agent

**Summary:** Implemented `KingdomVillagerRenderer` using `PlayerModel` to give the entity a player-like appearance. Established a dynamic skin system where the entity randomly selects a skin from `assets/kingdombuilder/textures/entity/kingdom_villager/skins/` upon spawning.

**Technical Notes/Hurdles:** Used `SynchedEntityData` to synchronize the selected `SkinId` between server and client. The renderer dynamically constructs the `ResourceLocation` based on this ID. Added a default `steve.png` to the skins folder. Renamed the main mod class file to `KingdomBuilder.java` to match the class name and fix a compilation error. (Fixed: Initial `steve.png` was corrupted with HTML; replaced it with a valid local PNG asset).

**Next Agent Pointers:** The AI and Brain system (Task 3) should now be implemented. When adding new skins, simply drop them into the `skins` folder and add their filename (without extension) to the `DEFAULT_SKINS` array in `KingdomVillagerEntity.java`. Future improvements could include a dynamic directory scanner for skins.

### Task 2.1: Skin Asset Fix - Completed by Exec Agent

Summary: Fixed the `steve.png` asset in the skins folder which was corrupted with HTML content. Replaced it with the valid PNG from the root directory.

Technical Notes/Hurdles: Verified file integrity using `file` command. The initial `curl` download had failed and saved an error page instead of the image.

Next Agent Pointers: Ensure any future external asset downloads are verified before committing. The path `assets/kingdombuilder/textures/entity/kingdom_villager/skins/steve.png` is now a valid 64x64 PNG.

### Task 2.2: External Custom Skin System - Completed by Exec Agent

Summary: Expanded the skin system to support user-provided skins via the `config/kingdomconfig/skins` directory. Implemented a client-side `ClientSkinManager` to load and cache these skins as `DynamicTexture`s to prevent performance issues.

Technical Notes/Hurdles: 
- Directory generation is handled in `KingdomBuilder` constructor using `FMLPaths.CONFIGDIR`.
- `KingdomVillagerEntity` scans the external folder to populate a combined `SKIN_POOL` used for randomization in `finalizeSpawn`.
- `KingdomVillagerRenderer` priorities external textures from `ClientSkinManager` over built-in ones.
- Strict `Dist.CLIENT` separation was maintained for texture loading to prevent server-side crashes.
- Had to resolve a compilation issue where `KingdomBuilder.java` was named lowercase and missing imports in the renderer.

Next Agent Pointers: Users can now drop any `.png` skin into the `config/kingdomconfig/skins` folder. These skins will be automatically picked up and randomly applied to spawned villagers. The `skinId` is derived from the filename.
