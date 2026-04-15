# Tool-to-Job Framework Guide

This document explains how to add new tools and associate them with specific jobs and behaviors in the Kingdom Builder mod. The system is designed to be data-driven and modular, relying on item tags and a registry pattern rather than hardcoded logic.

## Overview

The `JobManager` class evaluates a `KingdomVillagerEntity`'s equipped main-hand item to determine their job. It uses a `LinkedHashMap` called `JOB_REGISTRY` to map `TagKey<Item>` definitions to `Supplier<Job>` instances (factories). 

When a villager holds an item, the system checks if the item possesses any of the tags in the registry. If a match is found, the associated `Job` is instantiated and assigned. If an item matches multiple tags, the first registered tag in the `LinkedHashMap` takes priority. If the hands are empty or no recognized tags are matched, the villager defaults to a `CivilianJob`.

## Step-by-Step Guide: Adding a New Job

For this example, let's add a "Lumberjack" job associated with axes.

### 1. Create the Job Class

First, create a new class implementing the `Job` interface (or extending an abstract base job class if one exists).

```java
package com.femtendo.kingdombuilder.entities.jobs;

import com.femtendo.kingdombuilder.entities.KingdomVillagerEntity;

public class LumberjackJob implements Job {
    @Override
    public void onAssign(KingdomVillagerEntity entity) {
        // Add specific AI goals, modify attributes, etc.
    }

    @Override
    public void onRemove(KingdomVillagerEntity entity) {
        // Remove specific AI goals, clean up, etc.
    }
}
```

### 2. Define the Item Tag

In the `JobManager.java` class, define a new `TagKey<Item>` for your tool category. **Crucially, for Forge 1.21.1, you must use `ResourceLocation.fromNamespaceAndPath()`**.

```java
// Inside com.femtendo.kingdombuilder.entities.jobs.JobManager:

public static final TagKey<Item> LUMBERJACK_AXE = TagKey.create(
    Registries.ITEM, 
    ResourceLocation.fromNamespaceAndPath("kingdombuilder", "tools/lumberjack_axe")
);
```

### 3. Register the Job in the Factory

Add your new tag and the job's constructor reference to the `JOB_REGISTRY` static block in `JobManager.java`.

```java
    static {
        JOB_REGISTRY.put(GUARD_WEAPON, GuardJob::new);
        // Add the new job here:
        JOB_REGISTRY.put(LUMBERJACK_AXE, LumberjackJob::new);
    }
```

### 4. Create the Tag Data JSON

To make the tag functional, you must define it via a datapack JSON file. Create the corresponding file in your resources directory:

**File Path:** `src/main/resources/data/kingdombuilder/tags/item/tools/lumberjack_axe.json`

**File Content:**
```json
{
  "replace": false,
  "values": [
    "minecraft:wooden_axe",
    "minecraft:stone_axe",
    "minecraft:iron_axe",
    "minecraft:golden_axe",
    "minecraft:diamond_axe",
    "minecraft:netherite_axe",
    "#minecraft:axes"
  ]
}
```
*Note: You can include both individual items (`minecraft:iron_axe`) and other tags (`#minecraft:axes`).*

## Modifying Existing Jobs

To add new tools to an existing job (like adding a custom modded sword to the Guard job):
1. Locate the corresponding JSON file (e.g., `data/kingdombuilder/tags/item/tools/guard_weapon.json`).
2. Add the item ID or another tag reference to the `"values"` array.

## Best Practices

- **Priority:** Order matters in the `JOB_REGISTRY` static block. If an item happens to have both the `guard_weapon` and `lumberjack_axe` tags, it will be assigned the job of whichever was registered *first* in the `LinkedHashMap`.
- **Fallback:** Never assume a villager will always have a tool. The system safely falls back to `CivilianJob()` when a tool breaks or is removed.
- **Data-Driven:** Keep tool definitions in JSON files whenever possible. This allows modpack developers and players to easily add compatibility with other mods without writing Java code.
