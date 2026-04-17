# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./gradlew build              # Build the mod JAR
./gradlew runClient          # Launch Minecraft client for manual testing
./gradlew runServer          # Run dedicated server
./gradlew runGameTestServer  # Run automated game tests
./gradlew runData            # Generate data files (recipes, loot tables, etc.)
```

- Java 21, Minecraft 1.21.1, Forge 52.1.0, official Mojang mappings
- Client/server run from `./run/`; data generation from `./run-data/`

## Architecture Overview

This is a Minecraft Forge mod implementing a kingdom-building system centered on custom NPCs (`KingdomVillager`). The NPC extends `Villager` but strips out trading, breeding, and iron golem summoning in favor of a player-manageable workforce.

### Registry Pattern

All game objects (entities, items, menus) are registered via `DeferredRegister` in `ModEntities`, `ModItems`, and `ModMenus`. These are subscribed during the FML mod-loading lifecycle in the main mod class.

### Entity & Rendering

`KingdomVillager` extends `Villager` with a `PlayerModel` renderer (`KingdomVillagerRenderer`). Skins are synced server→client via `SynchedEntityData` (skinId). Built-in skins live in `assets/kingdombuilder/textures/entity/kingdom_villager/skins/`; user skins load from `config/kingdomconfig/skins/` at startup and are cached as `DynamicTexture` by the client-only `ClientSkinManager`.

### AI / Brain System

Uses vanilla's `Brain` + `Activity` system with a custom `Schedule`:
- 06:00 → Wake, 07:00 → Work, 17:00 → Social, 18:00 → Sleep

Core behaviors (swimming, door interaction, bed pathfinding, panic) are always registered. Activities inject/remove additional behaviors dynamically. Kingdom Villagers explicitly skip all vanilla villager-specific goals.

### Job System (Strategy Pattern)

`JobManager` evaluates the NPC's equipped tool against `TagKey<Item>` registries each time the inventory changes. Matching a tag calls `onAssign()` on the new `Job` and `onRemove()` on the old one. Jobs modify the `GoalSelector` directly.

- `CivilianJob` — default; panic on attack
- `GuardJob` — melee attack AI; target priority: hurt-by → other villagers → hostiles

New jobs implement the `Job` interface and register a corresponding item tag.

### Inventory & GUI

Each NPC holds two containers:
1. **8-slot generic inventory** — `SimpleContainer` serialized to NBT
2. **1-slot tool slot** — wraps `EquipmentSlot.MAINHAND`; drives job assignment

`KingdomVillagerMenu` (extends `AbstractContainerMenu`) exposes both to the player. `KingdomVillagerScreen` renders the GUI client-side. Shift-click logic routes items to the correct slot based on type (tools → mainhand slot).

### Client/Server Separation

- Client-only code (renderer, skin manager, screen) is guarded with `@Dist.CLIENT` or registered in the `FMLClientSetupEvent`
- Server is authoritative: entity state persisted via NBT (`addAdditionalSaveData` / `readAdditionalSaveData`)
- Data synced to clients via `SynchedEntityData` and vanilla packets

## Key Documentation Files

- `kingdombuilder.md` — overall project goals and dev notes
- `kvtool.md` — inventory, AI, armor, and job framework design
- `villagerplan.md` — implementation roadmap with completion status
- `tool-to-job.md` — tool→job mapping strategy
