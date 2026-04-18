# Kingdom Builder — Full Architectural Blueprint & Implementation Plan

---

## AGENT DIRECTIVE (READ FIRST)

> **ALL developers and agents working on this project MUST document their work twofold:**
>
> **(A)** Write detailed, explanatory inline comments and pointers directly within the Java codebase. Every non-obvious decision must have a `// POINTER:` comment explaining the why, not just the what.
>
> **(B)** Upon completing a task, you MUST update the **Dev Notes & Progress** section of THIS file (`kingdom_builder_architecture_plan.md`) with:
> - A summary of what was built
> - Technical hurdles overcome
> - Pointers for the next agent so they can seamlessly pick up context
>
> **Failure to update this file before marking a task complete is a workflow violation.**

---

## Context & Motivation

This document captures the full architectural blueprint for the Kingdom Builder mod (modid: `kingdombuilder`, Forge 1.21.1). The existing codebase has a functional `KingdomVillagerEntity` with a job system, inventory GUI, custom AI schedule, and skin manager. This plan extends that foundation with seven new major systems: kingdom ownership/registry, new blocks and block entities, a capability layer, a blueprint/zone system, a food network, enhanced AI states, and supporting event subscribers.

**Existing files to surgically modify (never rewrite):**
- `KingdomBuilder.java` — add new `DeferredRegister` registrations and event bus wiring
- `ModItems.java` — add `ZONING_TOOL` and `WRENCH` registrations
- `KingdomVillagerEntity.java` — add `IS_HOMELESS` synced data field and `applyHomelessCrisis`/`resolveHomelessCrisis` static methods
- `KingdomVillagerAi.java` — add `registerHomelessActivity()` call and method body

---

## Package Architecture

```
com.femtendo.kingdombuilder
├── kingdom/                          ← NEW: Core kingdom ownership & registry
│   ├── KingdomData.java
│   ├── KingdomManager.java
│   └── KingdomRegistry.java
├── blocks/                           ← NEW: Settlement Hearth, Logistics Node, Silo, Tube, Scaffold
│   ├── ModBlocks.java
│   ├── SettlementHearthBlock.java
│   ├── LogisticsNodeBlock.java
│   ├── KingdomSiloBlock.java
│   ├── IronTubeBlock.java
│   └── KingdomScaffoldBlock.java
├── blockentities/                    ← NEW: Block entity counterparts + ItemHandler exposure
│   ├── ModBlockEntities.java
│   ├── SettlementHearthBlockEntity.java
│   ├── LogisticsNodeBlockEntity.java
│   ├── KingdomSiloBlockEntity.java
│   └── IronTubeBlockEntity.java
├── capability/                       ← NEW: Kingdom vault item handler
│   └── KingdomVaultItemHandler.java
├── blueprint/                        ← NEW: Zone bounding box, task queue, hologram
│   ├── ZoneData.java
│   ├── BlueprintRegistry.java
│   ├── BuildTask.java
│   └── HologramRenderer.java         ← Client-only
├── ai/                               ← EXTEND existing package
│   ├── BuilderActivity.java          ← NEW: Brain activity for builder NPCs
│   └── HomelessCrisisActivity.java   ← NEW: Homeless state behavior (registered Activity token)
├── food/                             ← NEW: Food network
│   ├── SiloItemHandler.java
│   ├── TubeNetwork.java
│   └── KingdomFoodRegistry.java
├── items/                            ← EXTEND existing ModItems
│   ├── ZoningToolItem.java
│   └── WrenchItem.java
├── client/                           ← EXTEND existing client package
│   ├── renderer/
│   │   ├── IronTubeRenderer.java     ← X-ray BER (client-only)
│   │   └── HologramRenderer.java
│   └── model/
│       └── IronTubeBakedModel.java   ← Facade dynamic model
└── events/                           ← NEW: Forge event subscribers
    ├── KingdomBlockEvents.java        ← BreakEvent, ExplosionEvent
    └── KingdomRenderEvents.java       ← RenderLevelStageEvent, RenderNameTagEvent
```

---

## System Breakdown

### System 1 — Block & BlockEntity Registries

**New file:** `blocks/ModBlocks.java`
- `DeferredRegister<Block>` with five entries: `SETTLEMENT_HEARTH`, `LOGISTICS_NODE`, `KINGDOM_SILO`, `IRON_TUBE`, `KINGDOM_SCAFFOLD`
- Key properties: `noOcclusion()` on IRON_TUBE and KINGDOM_SCAFFOLD; `noLootTable()` + `destroyTime(0)` on KINGDOM_SCAFFOLD

**New file:** `blockentities/ModBlockEntities.java`
- `DeferredRegister<BlockEntityType<?>>` with four entries: `SETTLEMENT_HEARTH_BE`, `LOGISTICS_NODE_BE`, `KINGDOM_SILO_BE`, `IRON_TUBE_BE`
- Pattern: `BlockEntityType.Builder.of(Constructor::new, ModBlocks.BLOCK.get()).build(null)`

**Modification:** `KingdomBuilder.java` — add `ModBlocks.register(modEventBus)` and `ModBlockEntities.register(modEventBus)` in constructor

---

### System 2 — Kingdom Data Layer (KingdomData + KingdomManager)

**New file:** `kingdom/KingdomData.java`
- POJO data carrier: `ownerUUID`, `corePos`, `dimensionKey`, `kingdomName`
- `save()` / `load(CompoundTag)` using `NbtUtils.writeBlockPos` / `NbtUtils.readBlockPos`
- NOT a SavedData itself — owned by KingdomManager

**New file:** `kingdom/KingdomManager.java`
- Extends `SavedData`, anchored to the Overworld ServerLevel
- `DATA_NAME = "kingdom_registry"` → saves to `data/kingdom_registry.dat`
- Key field: `Map<UUID, KingdomData> kingdomsByOwner`
- Public API: `claimKingdom()`, `abandonKingdom()`, `getKingdom(UUID)`, `getKingdomAtPos(BlockPos, String)`, `getAllKingdoms()`
- Static accessor: `KingdomManager.get(ServerLevel)` uses `storage.computeIfAbsent(Factory, DATA_NAME)`
- Every mutation calls `setDirty()`

---

### System 3 — Settlement Hearth Block & Block Entity

**New file:** `blocks/SettlementHearthBlock.java`
- Extends `BaseEntityBlock`
- `getRenderShape()` returns `RenderShape.MODEL`
- `use()`: server-side only, checks `getKingdomAtPos()` and `getKingdom(playerUUID)` before calling `claimKingdom()`
- Sends feedback via `player.sendSystemMessage(Component.literal(...))`

**New file:** `blockentities/SettlementHearthBlockEntity.java`
- Standard `BlockEntity` subclass, no special capability exposure
- Stores ownerUUID of the claiming player for display purposes

---

### System 4 — Logistics Node (Capability Proxy)

**New file:** `blockentities/LogisticsNodeBlockEntity.java`
- Extends `BlockEntity`
- Overrides `getCapability(Capability<T>, Direction)` to expose `ForgeCapabilities.ITEM_HANDLER`
- Lazily initializes `LazyOptional<IItemHandler>` backed by `KingdomVaultItemHandler`
- Resolves kingdom ownership by linear scan of `KingdomManager.getAllKingdoms()` (spatial index TODO in comments)
- `setRemoved()` calls `vaultCapability.invalidate()`

**New file:** `capability/KingdomVaultItemHandler.java`
- Implements `IItemHandler`; `VAULT_SIZE = 10000`
- `insertItem()`: merges with existing stacks, then appends; calls `KingdomManager.get(level).setDirty()`
- `extractItem()`: always returns `ItemStack.EMPTY` (one-way valve)
- `getSlotLimit()`: returns `Integer.MAX_VALUE`

---

### System 5 — Blueprint Zone System

**New file:** `blueprint/ZoneData.java`
- Fields: `zoneId`, `kingdomOwnerUUID`, `minPos`, `maxPos`, `blueprintId`, `completed`, `integrityState`
- `contains(BlockPos)` — AABB containment check for event detection
- `ZoneIntegrityState` enum: `COMPLETE`, `DAMAGED`, `DESTROYED`
- `save()` / `load(CompoundTag)` with `NbtUtils.writeBlockPos`/`readBlockPos`

**New file:** `blueprint/BuildTask.java`
- Implements `Comparable<BuildTask>` for `PriorityQueue` ordering
- Sort rules: lower Y first; same Y → lower `dependencyPriority` first (1=SOLID, 2=NON_SOLID, 3=ATTACHED)
- Fields: `pos`, `state`, `dependencyPriority`, `kingdomOwnerUUID`, `locked`

**New file:** `blueprint/BlueprintRegistry.java`
- Extends `SavedData`, `DATA_NAME = "kingdom_blueprint_registry"`
- `Map<UUID, ZoneData> zones` keyed by `zoneId`
- Public API: `addZone()`, `removeZone()`, `getZone()`, `getZonesForKingdom(UUID)`
- Static accessor: `BlueprintRegistry.get(ServerLevel)`

---

### System 6 — Kingdom Block Events (Integrity)

**New file:** `events/KingdomBlockEvents.java`
- `@Mod.EventBusSubscriber(modid = ...)` — FORGE bus (default)
- `onBlockBreak(BlockEvent.BreakEvent)`: guards `isClientSide()`, finds zones containing `brokenPos`, sets `DAMAGED`, calls `setDirty()`, notifies ruler if online
- `onExplosionDetonate(ExplosionEvent.Detonate)`: iterates `event.getAffectedBlocks()`, same damage logic
- `notifyRuler()`: `level.getServer().getPlayerList().getPlayer(UUID)`, null-checked before `sendSystemMessage()`

**Modification:** `KingdomBuilder.java` — add `MinecraftForge.EVENT_BUS.register(KingdomBlockEvents.class)`

---

### System 7 — Hologram Renderer (Client Blueprint Preview)

**New file:** `client/renderer/HologramRenderer.java`
- `@OnlyIn(Dist.CLIENT)` + `@Mod.EventBusSubscriber(..., value = Dist.CLIENT)`
- Static state: `Map<BlockPos, BlockState> pendingHologram`, `UUID pendingKingdomOwner`
- `onRenderLevelStage(RenderLevelStageEvent)`: fires at `AFTER_TRANSLUCENT_BLOCKS`, applies camera offset translation, calls `renderGhostBlock()` per entry
- `renderGhostBlock()`: `RenderSystem.enableBlend()`, `mc.getBlockRenderer().renderSingleBlock(state, poseStack, bufferSource, 0xF000F0, NO_OVERLAY)`

---

### System 8 — Iron Tube Block Entity & X-Ray Renderer

**New file:** `blockentities/IronTubeBlockEntity.java`
- `EnumSet<Direction> connectedFaces`, `forcedDisconnects`
- `EnumMap<Direction, BlockState> facades`
- Bitmask serialization for connection/disconnect sets; `NbtUtils.writeBlockState`/`readBlockState` for facades
- `setFacade()` calls `level.sendBlockUpdated()` to trigger model re-bake on client

**New file:** `client/renderer/IronTubeRenderer.java`
- `@OnlyIn(Dist.CLIENT)`, implements `BlockEntityRenderer<IronTubeBlockEntity>`
- Shows X-ray pipe overlay only when player holds `ModItems.WRENCH` and owns the tube's kingdom
- `RenderSystem.disableDepthTest()` before drawing, `enableDepthTest()` after `endBatch(RenderType.lines())`
- Active connections = white; force-disconnected = red

---

### System 9 — Food Network

**New file:** `food/SiloItemHandler.java`
- Implements `IItemHandler`, `SILO_SLOTS = 1000`
- `insertItem()`: validates via `stack.getItem().getFoodProperties(stack, null) != null || KingdomFoodRegistry.isRegisteredFood(stack)`; returns original stack on rejection (backpressure)
- `extractItem()`: always `ItemStack.EMPTY`

**New file:** `food/KingdomFoodRegistry.java`
- `Set<ResourceLocation> REGISTERED_FOOD_IDS` — populated by datapack reload listener
- `reload(Collection<ResourceLocation>)` — called by `ReloadableDataProvider` on world load / `/reload`
- `isRegisteredFood(ItemStack)`: uses `ForgeRegistries.ITEMS.getKey(stack.getItem())`

**New file (stub):** `food/TubeNetwork.java` — teleportation-based food distribution from Silo to Mess Halls; full implementation as separate task

---

### System 10 — Homeless Crisis AI State

**New file:** `ai/HomelessCrisisActivity.java`
- `DeferredRegister<Activity>` for `ForgeRegistries.ACTIVITIES`
- `HOMELESS_CRISIS = ACTIVITIES.register("homeless_crisis", Activity::new)`
- `register(IEventBus)` called from `KingdomBuilder.java` constructor

**Modification:** `KingdomVillagerEntity.java`
- Add `EntityDataAccessor<Boolean> IS_HOMELESS` using `EntityDataSerializers.BOOLEAN`
- Add to `defineSynchedData`: `builder.define(IS_HOMELESS, false)`
- Add static methods `applyHomelessCrisis(entity)` and `resolveHomelessCrisis(entity)` (see Phase 3 spec)

**Modification:** `KingdomVillagerAi.java`
- Add `registerHomelessActivity(Brain<Villager>)` private static method
- Brain package: `RunOne` of `RandomStroll.stroll(0.5f)` + `DoNothing(20, 40)`

**New file:** `events/KingdomRenderEvents.java`
- `@Mod.EventBusSubscriber(..., value = Dist.CLIENT)`
- `onRenderNameTag(RenderNameTagEvent)`: if entity is `KingdomVillagerEntity` and `IS_HOMELESS == true`, appends `§c[HOMELESS]` to display name via `event.setContent()`

---

### System 11 — Items (Zoning Tool & Wrench)

**New files:** `items/ZoningToolItem.java`, `items/WrenchItem.java`
- Both `stacksTo(1)` — single-item tools
- `ZoningToolItem`: use behavior triggers blueprint placement flow (sends packet to server, server populates `BlueprintRegistry`)
- `WrenchItem`: use behavior on `IronTubeBlockEntity` toggles `forceDisconnect`/`clearForceDisconnect`; holding it enables `IronTubeRenderer` X-ray overlay

**Modification:** `ModItems.java`
- Add `ZONING_TOOL` and `WRENCH` `RegistryObject<Item>` following existing spawn egg pattern

---

### System 12 — Dynamic Territory & Housing Progression

#### Concept

Kingdom borders expand organically based on an **Influence Score** derived from housing quality, not live population. This prevents border collapse during attacks. Border conflicts resolve by **first-claimed priority** — no two kingdoms may share a chunk.

#### Housing Tiers & Influence Points

| Tier | Examples | Influence per Bed |
|------|----------|-------------------|
| 1 | Tents, Shacks | 1 pt |
| 2 | Wood Cabins | 3 pts |
| 3 | Stone Manors | (defined in datapack) |

- Tiers are defined per-blueprint in a datapack (`data/kingdombuilder/housing_tiers/<entry>.json`)
- Influence values and radius-threshold breakpoints are also datapack-driven so server admins can tune expansion speed

#### Upgrade Process

- Player drags a higher-tier blueprint over a completed lower-tier zone using the Zoning Tool
- Server validates the overlap and creates a new `ZoneData` with `upgradeFrom = oldZoneId`
- Builder NPCs automatically deconstruct old zone blocks (removing scaffold-style), then construct the new blueprint
- Old `ZoneData` is removed from `BlueprintRegistry` on completion; new zone inherits the footprint

#### Influence Score & Territory Radius

**New file:** `kingdom/InfluenceManager.java`
- Stateless utility class — no SavedData of its own; reads `BlueprintRegistry` and housing tier config
- `calculateScore(UUID kingdomOwnerUUID, ServerLevel)` — sums Influence points for all `COMPLETE` zones belonging to the kingdom
- `calculateRadius(int score)` — reads threshold table from datapack config, returns chunk radius integer
- `generateClaimedChunks(BlockPos corePos, int radius)` — returns `Set<ChunkPos>` radiating outward from the core chunk using Chebyshev distance (square expansion)

**Data stored in `KingdomData`:**
- Add `int cachedInfluenceScore` — last computed score (avoids full recalculation on every query)
- Add `Set<ChunkPos> claimedChunks` — the authoritative set of chunks this kingdom owns; persisted via `ListTag` of `[x, z]` int arrays

**Recalculation trigger (event-driven, NOT ticking):**
- Recalculate only when a `ZoneData.integrityState` changes to `COMPLETE`, `DAMAGED`, or when an upgrade finishes
- Entry point: `InfluenceManager.recalculateAndApply(UUID ownerUUID, ServerLevel)` — called from `BlueprintRegistry` mutation points and `KingdomBlockEvents`
- After recalculation, new `Set<ChunkPos>` is checked against all other kingdoms' `claimedChunks`; any overlap is denied (first-claimed wins, the expanding kingdom simply cannot claim that chunk)

#### Border Conflict (First-Claimed Priority)

- `KingdomManager` exposes `getOwnerOfChunk(ChunkPos, String dimensionKey)` — scans all kingdoms' `claimedChunks` sets; returns owning UUID or null
- During `InfluenceManager.generateClaimedChunks()`, each candidate `ChunkPos` is validated against this method before being added to the expanding set
- Contested chunks are silently skipped — the border stops naturally at the neighbor's edge

#### Border Overlay (Client Rendering)

**New network packet:** `S2CKingdomBorderPacket`
- Sent to the client when they begin holding the Zoning Tool (or a future "Sovereign Staff")
- Payload: `List<ChunkPos>` of the player's own kingdom's claimed chunks
- Triggered server-side by `ServerGamePacketListenerImpl` hook or item `inventoryTick`

**New file:** `client/renderer/KingdomBorderRenderer.java`
- `@OnlyIn(Dist.CLIENT)`, subscribes to `RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS`
- Stores received `Set<ChunkPos>` in a static field; cleared when player stops holding the trigger item
- Algorithm: for each claimed `ChunkPos`, check its four cardinal neighbor chunks; if a neighbor is NOT in the claimed set, that shared block edge is a border edge
- Renders a vertical column of rising gold particles (`ParticleTypes.ENCHANT` tinted gold via `ClientLevel.addParticle`) along the outer block boundaries of border-edge chunks, from Y=minBuildHeight to Y=minBuildHeight+24

---

### System 13 — Dev & Troubleshooting Commands

A Brigadier-based command tree registered under `/kingdom` giving developers, server admins, and players visibility into live kingdom state without needing to dig into save files or logs.

#### Command Registration

**New file:** `commands/KingdomCommands.java`
- Registered in a `RegisterCommandsEvent` subscriber on the FORGE bus (not the MOD bus)
- Root literal: `/kingdom`
- All player-facing subcommands require no special permission level (0)
- All admin/debug subcommands require op level 2 (`Commands.literal(...).requires(src -> src.hasPermission(2))`)

#### Player-Facing Commands (Permission Level 0)

| Command | Description |
|---------|-------------|
| `/kingdom info` | Prints the executing player's kingdom name, core position, dimension, influence score, chunk radius, and zone count |
| `/kingdom status` | Lists all of the player's zones with their blueprint ID and integrity state (COMPLETE / DAMAGED / DESTROYED) |
| `/kingdom border` | Triggers the same `S2CKingdomBorderPacket` dispatch as holding the Zoning Tool — shows the gold particle border for 30 seconds without needing the item |

#### Admin / Debug Commands (Permission Level 2)

| Command | Description |
|---------|-------------|
| `/kingdom info <player>` | Same output as player `/kingdom info` but targets any online or offline player by name/UUID |
| `/kingdom list` | Lists every registered kingdom: owner name, kingdom name, core pos, influence score, chunk count |
| `/kingdom zones <player>` | Lists all zones for a kingdom with zone UUID, blueprint ID, bounding box, and integrity state |
| `/kingdom chunks <player>` | Prints the full `Set<ChunkPos>` of claimed chunks for a kingdom (useful for verifying expansion math) |
| `/kingdom whoowns` | Checks which kingdom owns the chunk the admin is currently standing in; prints owner UUID + kingdom name or "unclaimed" |
| `/kingdom recalculate <player>` | Force-runs `InfluenceManager.recalculateAndApply()` for the target kingdom — useful after manual data edits |
| `/kingdom abandon <player>` | Admin force-removes a kingdom from the registry (calls `KingdomManager.abandonKingdom()`); use for stuck or corrupted kingdom states |
| `/kingdom reload` | Re-triggers the datapack food registry and housing tier config reload without a full `/reload` |
| `/kingdom setname <player> <name>` | Sets a kingdom's display name server-side |

#### Implementation Notes

- **Brigadier pattern:** Each subcommand is a `Commands.literal(...)` node with `.executes(ctx -> ...)` lambda; admin variants add `.then(Commands.argument("player", EntityArgument.player()))` for targeting
- **Offline player lookup:** For commands that accept a player name but the player may be offline, use `server.getProfileCache().get(name)` to resolve UUID, then `KingdomManager.get(overworld).getKingdom(uuid)` — no online player required
- **Output formatting:** Use `Component.literal(...)` with `§a` (green) for success values, `§c` (red) for errors/damaged states, `§e` (yellow) for warnings. Wrap multi-line output in a single `sendSuccess()` call per logical block
- **`/kingdom whoowns`:** reads `KingdomManager.get(level).getOwnerOfChunk(new ChunkPos(player.blockPosition()), dimKey)` — directly exercises the conflict-resolution lookup introduced in System 12
- **`/kingdom recalculate`:** intentionally exposed so admins can repair territory after manual NBT edits without restarting the server

---

## Integration Checklist

| # | Task | File | Action |
|---|------|------|--------|
| 1 | Register ModBlocks DeferredRegister | `KingdomBuilder.java` | `ModBlocks.register(modEventBus)` |
| 2 | Register ModBlockEntities DeferredRegister | `KingdomBuilder.java` | `ModBlockEntities.register(modEventBus)` |
| 3 | Register HomelessCrisisActivity | `KingdomBuilder.java` | `HomelessCrisisActivity.register(modEventBus)` |
| 4 | Register KingdomBlockEvents on FORGE bus | `KingdomBuilder.java` | `MinecraftForge.EVENT_BUS.register(KingdomBlockEvents.class)` |
| 5 | Add IS_HOMELESS synced field | `KingdomVillagerEntity.java` | `EntityDataAccessor<Boolean>` + `defineSynchedData` entry |
| 6 | Add homeless/resolve static methods | `KingdomVillagerEntity.java` | `applyHomelessCrisis()` + `resolveHomelessCrisis()` |
| 7 | Add homeless brain package | `KingdomVillagerAi.java` | `registerHomelessActivity()` call + method |
| 8 | Add Zoning Tool + Wrench | `ModItems.java` | Two `RegistryObject<Item>` fields |
| 9 | Register IronTubeRenderer BER | `ClientModEvents` | `EntityRenderersEvent` handler |
| 10 | Add `claimedChunks` + `cachedInfluenceScore` to `KingdomData` | `KingdomData.java` | New fields + NBT serialization |
| 11 | Create `InfluenceManager` | New file `kingdom/InfluenceManager.java` | Score calc, radius calc, chunk set generation, conflict check |
| 12 | Add `getOwnerOfChunk()` to `KingdomManager` | `KingdomManager.java` | Chunk ownership lookup for conflict resolution |
| 13 | Wire recalculation trigger | `BlueprintRegistry.java` + `KingdomBlockEvents.java` | Call `InfluenceManager.recalculateAndApply()` on zone state changes |
| 14 | Create `S2CKingdomBorderPacket` | New network packet class | Send `List<ChunkPos>` to client on tool equip |
| 15 | Create `KingdomBorderRenderer` | New file `client/renderer/KingdomBorderRenderer.java` | Particle border overlay at chunk edges |
| 16 | Create `/kingdom` command tree | New file `commands/KingdomCommands.java` + `RegisterCommandsEvent` subscriber | All player + admin debug subcommands |

---

## Dev Notes & Progress

> **Agents: update this section after completing each issue.**

| Date | Agent | Issue | Notes |
|------|-------|-------|-------|
| 2026-04-17 | Exec Agent | System 1: Block & BlockEntity Registries | See entry below. |

---

### System 1: Block & BlockEntity Registries — Completed by Exec Agent

**Summary:** Created the `blocks/` and `blockentities/` packages with `ModBlocks` (5 blocks: `SETTLEMENT_HEARTH`, `LOGISTICS_NODE`, `KINGDOM_SILO`, `IRON_TUBE`, `KINGDOM_SCAFFOLD`) and `ModBlockEntities` (4 BE types). Added 4 stub `BlockEntity` subclasses (`SettlementHearthBlockEntity`, `LogisticsNodeBlockEntity`, `KingdomSiloBlockEntity`, `IronTubeBlockEntity`) with minimal `(BlockPos, BlockState)` constructors. Wired both `DeferredRegister`s into `KingdomBuilder`'s constructor between the existing `ModItems` and `ModMenus` registrations.

**Technical Notes/Hurdles:**
- Touching the main mod class exposed a pre-existing case-mismatch bug: the git-tracked file was `kingdombuilder.java` (lowercase) while the class inside is `public class KingdomBuilder`. macOS APFS is case-insensitive so this compiled silently before, but `./gradlew compileJava` now failed with "class KingdomBuilder is public, should be declared in a file named KingdomBuilder.java". Fixed by a two-step `git mv` (through a `.tmp` intermediate) to rename the file to `KingdomBuilder.java`. If future agents find references or imports to a lowercase path, update them — the canonical name is now `KingdomBuilder.java`.
- Used `BlockBehaviour.Properties.of()` (no-arg, Forge 1.21.1 signature — no Material argument). `noLootTable()` + `destroyTime(0.0F)` + `noOcclusion()` are chained directly on the Properties builder; all three are load-bearing for scaffold (see inline comments in `ModBlocks.java` for why each matters separately).
- `BlockEntityType.Builder.of(Constructor::new, block).build(null)` passes a null DataFixer type. This is standard Forge mod practice — we handle NBT migration ourselves in each BE's `load()` override if the schema ever changes.
- The 5 blocks are currently plain `Block` instances, NOT `BaseEntityBlock`. This means placing one in-world will NOT spawn its corresponding BE; System 3 (Settlement Hearth), System 4 (Logistics Node), System 8 (Iron Tube), and System 9 (Silo) will each swap their supplier to a dedicated `BaseEntityBlock` subclass that overrides `newBlockEntity(pos, state)`. Acceptance criteria was "mod loads without crash; all five blocks and four BE types are accessible via `.get()`" — verified by successful `./gradlew build`.
- Kept code comments but did NOT add loot tables, block-states, models, item-block registrations, or creative-tab entries. Those are out of scope for System 1 per the issue description; separate tickets will cover assets.

**Next Agent Pointers:**
- **System 3 (Settlement Hearth):** Replace the `() -> new Block(...)` supplier for `SETTLEMENT_HEARTH` in `ModBlocks.java` with `() -> new SettlementHearthBlock(...)` that extends `BaseEntityBlock`, implements `EntityBlock`, and overrides `newBlockEntity(pos, state)` to return `new SettlementHearthBlockEntity(pos, state)`. Then flesh out `SettlementHearthBlockEntity` (currently a stub) with ownerUUID + addAdditionalSaveData / readAdditionalSaveData.
- **System 4 (Logistics Node):** Same swap for `LOGISTICS_NODE`. Override `getCapability(...)` in `LogisticsNodeBlockEntity` and wire a `LazyOptional<KingdomVaultItemHandler>`. Don't forget `vaultCapability.invalidate()` in `setRemoved()` — I left a POINTER comment in the stub class.
- **System 8 (Iron Tube):** `IronTubeBlockEntity` stub is ready. Remember `level.sendBlockUpdated(...)` on facade mutations (not `setChanged()`), and the `noOcclusion()` flag on `IRON_TUBE` in `ModBlocks.java` is already set up for the X-ray BER + facade rendering to work.
- **System 9 (Silo):** Attach `SiloItemHandler` as an ITEM_HANDLER capability in `KingdomSiloBlockEntity`.
- **Item-block registrations:** When creative-tab / BlockItem wiring is introduced, register `new BlockItem(ModBlocks.X.get(), new Item.Properties())` under `ModItems.ITEMS` using the same `"settlement_hearth"` etc. names so resource locations align.
- **Lookup pattern:** `ModBlocks.SETTLEMENT_HEARTH.get()` and `ModBlockEntities.SETTLEMENT_HEARTH_BE.get()` are the supported access points. The DeferredRegister flush order is `BLOCKS` then `BLOCK_ENTITY_TYPES` (Forge resolves registries in dependency order), so `.get()` on a block from within a BE supplier is safe.
- **Filename case:** `KingdomBuilder.java` is now the canonical main-class file name — do not reintroduce the lowercase variant.
