# Kingdom Villager Architecture Plan

## AGENT DIRECTIVE
All developers/agents working on this project MUST document their work twofold:
(A) Write detailed, explanatory inline comments and pointers directly within the Java codebase.
(B) Upon completing a task, you MUST update the 'Dev Notes & Progress' section of this .md file with a summary of what was built, technical hurdles overcome, and pointers for the next agent so they can seamlessly pick up the context.

## 1. Inventory and GUI Sync System

### 1.1 Goal
Generic 8-slot inventory and 1-slot "Tool/Weapon" slot wrapping `EquipmentSlot.MAINHAND`.

### 1.2 Container/Menu Structure
- Create a custom `AbstractContainerMenu` subclass (`KingdomVillagerMenu`).
- **Slots:**
  - Slots 0-7: Generic inventory slots tied to a `SimpleContainer` or `ItemStackHandler` (8 size) on the entity.
  - Slot 8: Tool/Weapon slot. This slot is dynamically linked to the entity's `MAINHAND` equipment slot.
- **Sync Logic:** The GUI's Slot 8 overrides the `set` and `getItem` methods to directly access the villager's `setItemSlot(EquipmentSlot.MAINHAND, stack)`. This prevents desyncs as the source of truth is the entity's equipment data.

### 1.3 Death Drop Handling
- Override `dropEquipment()` and `dropCustomDeathLoot()` in the entity class.
- The default `EquipmentSlot` drop logic handles the `MAINHAND` and armor slots based on drop chances.
- Iterate over the 8-slot generic inventory and spawn `ItemEntity` instances for each non-empty slot.
- Ensure generic inventory does NOT contain the main hand item to avoid duplication.

### 1.4 Shift-Click Logic
- In `quickMoveStack` (shift-click logic):
  - Check if the item is a valid tool/weapon (via tags or registry).
  - If valid, try merging it into Slot 8 first.
  - If not valid, or Slot 8 is full, merge into Slots 0-7.
  - Conversely, shift-clicking from the villager's inventory to the player's inventory should work as standard.

## 2. Dynamic AI and State Transitions

### 2.1 Goal
Dynamic shifting between fleeing civilian and defending guard based on the main-hand tool.

### 2.2 Custom Goal Selector Logic
- Implement a custom `JobGoalSelector` or a Brain behavior that conditionally adds/removes goals based on the current job.
- On tick or when the `MAINHAND` item changes (detected via `onEquipItem` or a tick check), evaluate the item.
- **Weapon Equipped:**
  - Add `MeleeAttackGoal` or `RangedAttackGoal`.
  - Add `NearestAttackableTargetGoal` for monsters (Zombies, Skeletons, etc.).
  - Remove `PanicGoal`.
- **Unarmed/Civilian:**
  - Add `PanicGoal`.
  - Remove attack and target goals.

### 2.3 Edge Case: Weapon Breaks Mid-Combat
- When `ItemStack.hurtAndBreak` breaks the item, an event or callback removes the item from the `MAINHAND`.
- The entity's tick logic detects the `MAINHAND` is now empty.
- Immediately abort the current `AttackGoal` (e.g., call `stop()` on the goal).
- Clear the entity's target (`setTarget(null)`).
- Re-evaluate state, which seamlessly falls back to adding `PanicGoal`.

### 2.4 Targeting Priority System
- Use a custom `Predicate<LivingEntity>` in the `NearestAttackableTargetGoal`.
- Priority logic:
  1. Entities actively attacking the villager.
  2. Entities attacking other villagers or the village core.
  3. Distance to the villager (closer = higher priority).

## 3. Armor Auto-Equip Logic

### 3.1 Goal
Every 20 ticks, equip highest-tier armor from the 8-slot inventory, swapping current armor into inventory.

### 3.2 Robust Armor Value Calculation
- Do not solely rely on `getDefense()`.
- **Formula Concept:** `Base Armor * 1.0 + Armor Toughness * 2.0 + (Protection Level * 1.5)`
- Implementation: Read vanilla attributes `Attributes.ARMOR` and `Attributes.ARMOR_TOUGHNESS` from the item's default attribute modifiers. Parse enchantments to check for `Enchantments.PROTECTION`.

### 3.3 Swap Logic & Edge Cases (Full Inventory)
- Iterate over the 8 inventory slots. Find the best valid armor piece for a specific `EquipmentSlot` (Head, Chest, Legs, Feet).
- Compare the "best" found piece's value against the currently equipped piece's value.
- If the inventory piece is strictly better:
  - Is the currently equipped slot empty? Simply move the item from inventory to equipment slot.
  - Is the currently equipped slot filled?
    - Place the currently equipped item into the exact generic inventory slot that the new armor was taken from.
    - This guaranteed 1:1 swap prevents item deletion even if the generic inventory is 100% full.

### 3.4 Custom Modded Armor Compatibility
- Use `EquipmentSlot` validation and standard item tags.
- Instead of `instanceof ArmorItem`, rely on `Mob.getEquipmentSlotForItem(stack)` to determine if an item is wearable in a specific slot, which covers most Forge-compatible armors out of the box.

## 4. "Tool-to-Job" Future-Proofing Framework

### 4.1 Goal
Modular job system to add more tools (axes for lumberjacks, hoes for farmers) without core refactoring.

### 4.2 Job Manager Concept
- Create a `JobManager` class instantiated within the villager entity.
- The `JobManager` holds a reference to the current `Job` interface/abstract class.
- The villager entity delegates AI goal management to `JobManager.updateGoals()`.
- Each `Job` defines:
  - `onAssign(KingdomVillager entity)`
  - `onRemove(KingdomVillager entity)`
  - `getGoals()`
  - `getTargetSelectorGoals()`

### 4.3 Tag/Registry Based Detection
- Use Minecraft/Forge Tag system (`ItemTags`).
- Define tags: `kingdombuilder:tools/guard_weapon`, `kingdombuilder:tools/lumberjack_axe`, `kingdombuilder:tools/farmer_hoe`.
- Map tags to `Job` factories in a custom registry or a simple HashMap during mod initialization.
- **Evaluation Loop:** On item change, `JobManager` checks the `MAINHAND` item's tags.
  - If `stack.is(KingdomBuilderTags.Items.GUARD_WEAPON)`, switch to `GuardJob`.
  - If `stack.is(KingdomBuilderTags.Items.LUMBERJACK_AXE)`, switch to `LumberjackJob`.
  - If no recognized tag, switch to `CivilianJob`.

## Dev Notes & Progress
- *Currently in concepting phase. No implementation has begun.*