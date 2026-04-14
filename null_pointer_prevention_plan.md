# Null Pointer Exception Prevention Plan

"AGENT DIRECTIVE: All developers/agents working on this project MUST document their work twofold: (A) Write detailed, explanatory inline comments and pointers directly within the Java codebase. (B) Upon completing a task, you MUST update the 'Dev Notes & Progress' section of this .md file with a summary of what was built, technical hurdles overcome, and pointers for the next agent so they can seamlessly pick up the context."

## 1. Project Overview
The goal of this task is to refactor specific components of the Kingdom Builder mod to improve null-safety and prevent runtime `NullPointerExceptions`. This involves updating the configuration loading logic, the client-side skin management system to use `Optional` types, and the villager renderer to handle missing data gracefully.

## 2. Technical Requirements

### 2.1 Configuration Safety (`Config.java`)
- **Objective:** Prevent invalid registry names in the config from introducing null values into the item set.
- **Requirement:** In `src/main/java/com/femtendo/kingdombuilder/Config.java`, update the `onLoad` method's stream pipeline.
- **Specific Change:** Add `.filter(java.util.Objects::nonNull)` immediately after the `.map(...)` step and before `.collect(...)`.

### 2.2 Client Skin Manager Modernization (`ClientSkinManager.java`)
- **Objective:** Use Java `Optional` to explicitly handle the possibility of missing external skins.
- **Requirement:** Update `src/main/java/com/femtendo/kingdombuilder/client/ClientSkinManager.java`.
- **Specific Changes:**
    - Change `loadExternalTexture` return type to `Optional<ResourceLocation>`. Return `Optional.empty()` on failure and `Optional.of(location)` on success.
    - Change `getExternalSkinLocation` return type to `Optional<ResourceLocation>`, returning `Optional.ofNullable(...)`.
    - Update `loadExternalSkins` to handle these `Optional` returns (e.g., using `.ifPresent(...)`).

### 2.3 Villager Renderer Robustness (`KingdomVillagerRenderer.java`)
- **Objective:** Ensure a valid texture is always returned, even if skin data or external files are missing.
- **Requirement:** Update `src/main/java/com/femtendo/kingdombuilder/client/renderer/KingdomVillagerRenderer.java`.
- **Specific Changes:**
    - Handle the new `Optional<ResourceLocation>` from `ClientSkinManager.getExternalSkinLocation`.
    - Add a null check for `entity.getSkinId()`, defaulting to `"steve"` if null.
    - Ensure the final fallback `ResourceLocation` uses the validated `skinId`.

## 3. Success Criteria
- [ ] `Config.java` successfully filters null items during load.
- [ ] `ClientSkinManager.java` uses `Optional` for all external skin lookups.
- [ ] `KingdomVillagerRenderer.java` never returns null from `getTextureLocation` and has a working "steve" fallback.
- [ ] The project builds successfully without regression.

## 4. Dev Notes & Progress
- **[2026-04-13]:** Master plan created by Concepting & Planning Architect.
