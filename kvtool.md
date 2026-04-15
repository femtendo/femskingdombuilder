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

Architect the Inventory and GUI Sync System - Completed by Exec Agent

Summary: Implemented the `KingdomVillagerMenu` with an 8-slot generic inventory mapping to the villager's internal `SimpleContainer` and a 1-slot Tool/Weapon slot dynamically wrapping `EquipmentSlot.MAINHAND`. Updated `KingdomVillagerEntity` to override `dropEquipment` and `dropCustomDeathLoot` to drop the generic inventory items while ensuring the main hand item isn't duplicated, and handled shift-click prioritizing the wrapper slot.

Technical Notes/Hurdles: Bypassed creating an entirely new inventory object on the entity by repurposing `Villager.getInventory()`, which natively syncs and saves. Handled `Slot#setByPlayer` alongside `Slot#set` in the custom mainhand slot wrapper to ensure 1.21.1 API compliance.

Next Agent Pointers: 
- `KingdomVillagerMenu` still needs a valid `MenuType` registered in a `ModMenus` registry class to be fully usable in-game. 
- You will need to build the `Screen` (GUI visual) class that links to this menu.
- The shift-click logic (`mayPlace` and priority targeting in `quickMoveStack`) currently allows any item into the Tool slot. The next agent implementing Jobs should update the `Slot#mayPlace` check to validate against the future job tool tags (e.g., `KingdomBuilderTags.Items.GUARD_WEAPON`).

Rename kingdombuilder.java - Completed by Exec Agent

Summary: Renamed the main mod class file `kingdombuilder.java` to `KingdomBuilder.java` to align with the class name and Java conventions.

Technical Notes/Hurdles: Also fixed a compilation error in `KingdomVillagerEntity.java` caused by an incorrect `dropEquipment` override signature left by a previous task.

Next Agent Pointers: Continue with the next set of features or bug fixes as defined in the project plan.

Kingdom Villager UI Open on Right-Click - Completed by Exec Agent

Summary: Registered the MenuType in ModMenus, created KingdomVillagerScreen GUI class, registered it on the client, and updated KingdomVillagerEntity#mobInteract to open the GUI using ServerPlayer#openMenu.

Technical Notes/Hurdles: Had to ensure the entity ID is sent via FriendlyByteBuf to allow the client to resolve the villager entity and properly bind the client-side KingdomVillagerMenu to the correct entity object.

Next Agent Pointers: The KingdomVillagerScreen uses a placeholder ResourceLocation for its texture. The GUI will currently render the missing texture graphic if the actual image file is not present in the mod resources. A custom graphic will need to be provided to match the slots.

Design the Dynamic AI and State Transitions - Completed by Exec Agent

Summary: Implemented the `JobManager` and the `Job` interface to allow dynamic behavior shifting based on the main-hand equipped item. Created `CivilianJob` (default state with `PanicGoal`) and `GuardJob` (active when holding a sword, featuring `MeleeAttackGoal` and tiered `NearestAttackableTargetGoal` logic).

Technical Notes/Hurdles: Bypassed the rigid nature of vanilla `Villager` brains by actively adding and removing goals directly from the `goalSelector` and `targetSelector` during job transitions. Used custom predicates in `GuardJob` to satisfy the complex target priority (active attackers -> attackers of villagers -> any hostile mob). Weapon breakage/swap check is efficiently done via `ItemStack.matches` in `JobManager.tick()` called by `KingdomVillagerEntity.customServerAiStep()`. Note that if an item takes damage but doesn't break, the class type stays the same, preventing goal resetting mid-combat.

Next Agent Pointers: 
- `JobManager` currently checks for the vanilla `#minecraft:swords` tag. You may want to refine this to use custom tags (e.g., `#kingdombuilder:tools/guard_weapon`) as specified in the original plan.
- Consider implementing pathfinding to resupply a weapon when the main-hand weapon breaks (a dev note exists in `JobManager` for this).
- As you add more jobs (e.g., Lumberjack, Farmer), create new classes implementing `Job` and register them in `JobManager.evaluateJob()`.

Fix Villager Melee Attack Crash - Completed by Exec Agent

Summary: Fixed a crash that occurred when a KingdomVillager in the Guard role attempted to perform a melee attack. The `Attributes.ATTACK_DAMAGE` attribute has been added to the base villager attributes.

Technical Notes/Hurdles: Vanilla villagers do not have an attack damage attribute since they never fight. When `MeleeAttackGoal` tried to process an attack, it threw an `IllegalArgumentException`. Added `.add(Attributes.ATTACK_DAMAGE, 1.0D)` to `KingdomVillagerEntity.createAttributes()`.

Next Agent Pointers: 
- Base attack damage is set to 1.0D, but this will be naturally scaled by whatever weapon the villager is holding.
Fix Tool Visual Rendering - Completed by Exec Agent

Summary: Modified `KingdomVillagerRenderer` to extend `HumanoidMobRenderer` instead of `MobRenderer`, which natively adds the `ItemInHandLayer` needed to visually render equipped items. Also updated the `render` method to dynamically update the `HumanoidModel.ArmPose` based on whether the main-hand or off-hand are holding items, preventing the arms from resting at the sides while holding a weapon.

Technical Notes/Hurdles: The villager model was not rendering held items because the default `MobRenderer` does not attach `ItemInHandLayer`. We let `HumanoidMobRenderer` handle layer additions and then inject logic inside `render()` to sync the villager's `InteractionHand` item states to the model's arm poses. Added logic to correctly respect `entity.getMainArm().

Next Agent Pointers:
- If specific animations (like throwing a spear or pulling a bow) are needed later, you will need to expand the `ArmPose` mapping inside `KingdomVillagerRenderer.setModelProperties()` to check the item's `UseAnim` instead of just setting it to `ArmPose.ITEM`.

Fix Tool Durability - Completed by Exec Agent

Summary: Implemented weapon durability loss for Kingdom Villagers by overriding `doHurtTarget` to call `Item#postHurtEnemy` on the main-hand weapon.

Technical Notes/Hurdles: Vanilla `Mob` attacking logic natively handles item drops and enchantments but does not damage the mob's held item on attack. We injected `mainHandItem.getItem().postHurtEnemy` explicitly into the attack flow. Used `postHurtEnemy` instead of a hardcoded 1 damage decrement because tools like axes or tridents require specific logic (e.g. 2 damage per hit for axes). Ensured the slot clears natively via `ItemStack` decrement if the durability hits zero.

Next Agent Pointers:
- If we later allow villagers to dual-wield weapons or attack with the off-hand, `doHurtTarget` will need to check which hand performed the attack and damage that specific `ItemStack`.

Cap Attack Movement Speed - Completed by Exec Agent

Summary: Modified the `MeleeAttackGoal` in `GuardJob` to use a speed modifier of 1.0D instead of 1.2D so the kingdom villager moves at regular speed when engaging a target.

Technical Notes/Hurdles: The AI's `MeleeAttackGoal` naturally accepts a speed multiplier. By changing this parameter, the villager uses its base `MOVEMENT_SPEED` attribute without applying a sprint multiplier, matching the requested behavior.

Next Agent Pointers:
- If you introduce new combat goals (like `RangedAttackGoal`), ensure their movement speed modifier is also capped at 1.0D unless a specific "sprinting" behavior is intended.

Fix Kingdom Villager Death Drops - Completed by Exec Agent

Summary: Fixed an issue where kingdom villagers were not dropping items from their generic inventory or their main-hand tool upon death.

Technical Notes/Hurdles: Moved the generic inventory drop logic from `dropEquipment()` (which only executes reliably under certain loot conditions and was improperly checking for MAINHAND equality) to `dropCustomDeathLoot()`. Also forced a 100% drop chance for the `MAINHAND` equipment slot post-super call to bypass the default 8.5% mob drop chance without duplicating the item.

Next Agent Pointers:
- If kingdom villagers should wear and drop armor in the future, you may need to apply the same guaranteed drop logic to `EquipmentSlot.CHEST`, `LEGS`, etc. inside `dropCustomDeathLoot()`.

Remove Testing Files - Completed by Exec Agent

Summary: Removed residual, non-implemented testing and temporary files (test_layer.java, test_override.java, test_code.java, and fix.py) from the workspace.

Technical Notes/Hurdles: These files were left over from previous debugging and investigation phases and were removed to clean up the repository.

Next Agent Pointers:
- None. The workspace is now clean of these temporary testing artifacts.

Refine the Armor Auto-Equip Logic - Completed by Exec Agent

Summary: Implemented `evaluateAndEquipArmor` in `KingdomVillagerEntity` which runs every 20 ticks. The system calculates a robust armor value using `Attributes.ARMOR`, `Attributes.ARMOR_TOUGHNESS`, and `Enchantments.PROTECTION`, and securely 1:1 swaps newly found better armor pieces from the generic 8-slot inventory with currently equipped pieces. Modified `dropCustomDeathLoot` to drop armor to ensure players recover all villager gear upon death.

Technical Notes/Hurdles: Reading attributes directly required parsing `ItemAttributeModifiers` dynamically to properly account for 1.21.1 component-based attribute modifiers. Ensured modded armor compatibility by checking `Mob.getEquipmentSlotForItem` instead of strict class instances.

Next Agent Pointers:
- The `evaluateAndEquipArmor` does not yet have sound effects (like armor equip sounds) when an item swaps. Consider adding `level.playSound` if the UX feels too silent.
- Armor is forced to drop 100% on death inside `dropCustomDeathLoot()`. If you implement curses like Curse of Vanishing or Curse of Binding, you will need to add checks before swapping/dropping.

Fix Kingdom Villager Armor Auto-Equip - Completed by Exec Agent

Summary: Fixed an issue where kingdom villagers were not visually or mechanically equipping armor from their inventory.

Technical Notes/Hurdles: Modified `KingdomVillagerRenderer` to include `HumanoidArmorLayer` so armor renders visually on the villager model. Updated `calculateArmorValue` in `KingdomVillagerEntity` to safely use `.value().equals()` instead of `.unwrapKey()` for comparing attribute holders. Added auditory feedback using `playSound` when an item is equipped.

Next Agent Pointers:
- The villager will now correctly swap and visually wear armor. If custom model layers are added for modded armor later, ensure they integrate with `HumanoidArmorLayer`.

Fix Kingdom Villager Armor Auto-Equip - Completed by Exec Agent

Summary: Refined the `evaluateAndEquipArmor` logic and attribute checking to ensure kingdom villagers successfully evaluate and equip better armor from their inventory.

Technical Notes/Hurdles: The `Holder<Attribute>` comparison was failing when checking for `Attributes.ARMOR` due to mismatching holder references. Updated the attribute loop to use `.value().equals()` or `.is()` for reliable evaluation. Also added `this.setDropChance(slot, 2.0f)` to ensure the equipped modded armor is properly dropped on death instead of relying on forced custom drops for the armor slots.

Next Agent Pointers:
- The system now reliably equips and drops armor using vanilla mechanics. If you add additional equipment slots (like dual-wielding or curios), remember to call `this.setDropChance(slot, 2.0f)` for those items as well.

Add Armor Evaluation Diagnostics - Completed by Exec Agent

Summary: Added a temporary diagnostic log block to `evaluateAndEquipArmor` in `KingdomVillagerEntity.java` to print the exact contents of the 8-slot generic inventory to the server console.

Technical Notes/Hurdles: This diagnostic prints the slot index, hover name, and count of non-empty items to pinpoint if the armor is actually in the inventory from the server's perspective before the swap logic runs.

Next Agent Pointers:
- Review the server console output to determine if the server actually sees the armor items inside the villager's `SimpleContainer`. If it prints empty or missing items, the GUI sync is likely failing to send the client-side items to the server. Remove this debug block once the core issue is resolved.


Fix Armor Calculation Data Component Extraction - Completed by Exec Agent

Summary: Replaced the `calculateArmorValue` method with a refined version that reliably extracts data components from 1.21.1 un-modified vanilla items and circumvents Holder attribute mismatches.

Technical Notes/Hurdles: Vanilla items may not contain explicit attribute modifier components in `ItemStack`, so logic was added to fall back to the base `Item`'s default components. Switched attribute checking to use string matching on the unwrapped key (`attrName.equals("armor")`) instead of direct `Holder<Attribute>` equivalence which failed on server loading. Also streamlined protection enchantment level extraction to use the modern `DataComponents.ENCHANTMENTS`.

Next Agent Pointers:
- When evaluating attributes for equipment/tools in the future, follow this fallback pattern (`stack.get(...)` then `stack.getItem().components().getOrDefault(...)`) to capture data on standard un-modified items.
- If custom attributes are added, make sure to use their string path name to circumvent registry holder failures.


Fix Equipment Rendering and Data Component Slot Logic - Completed by Exec Agent

Summary: Restored the HumanoidArmorLayer registration in KingdomVillagerRenderer and updated the armor evaluation logic to use 1.21.1 Equipable interface for robust slot detection.

Technical Notes/Hurdles: The requested `getEquipmentRenderer` and `DataComponents.EQUIPPABLE` components do not exist in Forge 1.21.1 (they are 1.21.2+ features). The code was refactored to use the actual 1.21.1 API equivalent `net.minecraft.world.item.Equipable.get(invStack)` to definitively check intended equipment slots, and `context.getModelManager()` was kept for the armor layer constructor as required by the 1.21.1 signature.

Next Agent Pointers:
- Remember that this project is strictly on 1.21.1 mappings, not 1.21.2+. Do not attempt to use `EquipmentAssetManager` or the `EQUIPPABLE` data component.

Remove Testing Artifacts - Completed by Exec Agent

Summary: Removed the remaining `test17.java`, `test18.java`, and `test19.java` files from the root directory that were generated during API evaluations.

Technical Notes/Hurdles: Cleaned up the root directory to ensure no lingering temporary script files from the data component investigations remained in the project structure.

Next Agent Pointers:
- None. The workspace is fully clean of temporary test files and ready for the next implementation phase.

Build the "Tool-to-Job" Future-Proofing Framework - Completed by Exec Agent

Summary: Implemented the Job Factory Registry within `JobManager` using a `LinkedHashMap` to map custom `TagKey<Item>` definitions to `Job` instantiation. Created a new datapack-driven 1.21.1 tag (`kingdombuilder:tools/guard_weapon`) and the corresponding JSON file (`guard_weapon.json`) to register swords into the system dynamically, removing the hardcoded `if/else` checks.

Technical Notes/Hurdles: Used `ResourceLocation.fromNamespaceAndPath()` as required by the 1.21.1 tag registry API. `LinkedHashMap` was used instead of a standard `HashMap` to ensure that evaluation order priority is preserved in case items carry multiple job tags in the future.

Next Agent Pointers: 
- To add a new job (e.g., Lumberjack): 1. Create `LumberjackJob.java` implementing `Job`. 2. Define `TagKey<Item> LUMBERJACK_AXE` in `JobManager` using `ResourceLocation.fromNamespaceAndPath`. 3. Add `JOB_REGISTRY.put(LUMBERJACK_AXE, LumberjackJob::new);` to the static block. 4. Create the corresponding JSON file in `src/main/resources/data/kingdombuilder/tags/item/tools/`.
- If an entity drops their tool and their main hand becomes empty, the fallback is safely defaulting to `CivilianJob()`.

Document the Tool-to-Job Framework - Completed by Exec Agent

Summary: Created `tool-to-job.md` in the project root to document the process of adding new tools, linking them to jobs via tags, and explaining the registry-based JobManager evaluation loop.

Technical Notes/Hurdles: Standardized the documentation to reflect the recent 1.21.1 updates like using `ResourceLocation.fromNamespaceAndPath()` and creating tag JSON files, ensuring clear steps for modders adding jobs like Lumberjack.

Next Agent Pointers: 
- The `tool-to-job.md` serves as a high-level API reference for the mod's job system. Refer to it when implementing the next jobs (e.g., Farmer, Lumberjack).
