package com.femtendo.kingdombuilder.entities;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.femtendo.kingdombuilder.entities.ai.KingdomVillagerAi;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;

public class KingdomVillagerEntity extends Villager {

    private static final EntityDataAccessor<String> DATA_SKIN_ID =
            SynchedEntityData.defineId(KingdomVillagerEntity.class, EntityDataSerializers.STRING);

    private static final String[] BUILTIN_SKINS = {"steve"};
    private static final List<String> SKIN_POOL = new ArrayList<>();

    // POINTER: Our custom job manager that dictates the villager's current goals based on equipment
    private final com.femtendo.kingdombuilder.entities.jobs.JobManager jobManager = new com.femtendo.kingdombuilder.entities.jobs.JobManager(this);

    public static final List<MemoryModuleType<?>> MEMORY_MODULES = ImmutableList.of(
            MemoryModuleType.HOME,
            MemoryModuleType.MEETING_POINT,
            MemoryModuleType.JOB_SITE,
            MemoryModuleType.POTENTIAL_JOB_SITE,
            MemoryModuleType.LOOK_TARGET,
            MemoryModuleType.WALK_TARGET,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            MemoryModuleType.PATH,
            MemoryModuleType.INTERACTION_TARGET,
            MemoryModuleType.DOORS_TO_CLOSE,
            MemoryModuleType.LAST_SLEPT,
            MemoryModuleType.LAST_WOKEN,
            MemoryModuleType.NEAREST_BED,
            MemoryModuleType.HURT_BY,
            MemoryModuleType.HURT_BY_ENTITY
    );

    public static final List<SensorType<? extends Sensor<? super Villager>>> SENSOR_TYPES = ImmutableList.of(
            SensorType.NEAREST_BED,
            SensorType.SECONDARY_POIS,
            SensorType.NEAREST_LIVING_ENTITIES,
            SensorType.NEAREST_PLAYERS,
            SensorType.HURT_BY,
            SensorType.VILLAGER_HOSTILES
    );

    static {
        refreshSkinPool();
    }

    public static void refreshSkinPool() {
        SKIN_POOL.clear();
        SKIN_POOL.addAll(Arrays.asList(BUILTIN_SKINS));

        Path skinsDir = FMLPaths.CONFIGDIR.get().resolve("kingdomconfig/skins");
        if (Files.exists(skinsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(skinsDir, "*.png")) {
                for (Path path : stream) {
                    String fileName = path.getFileName().toString();
                    String skinId = fileName.substring(0, fileName.lastIndexOf('.'));
                    if (!SKIN_POOL.contains(skinId)) {
                        SKIN_POOL.add(skinId);
                    }
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    public KingdomVillagerEntity(EntityType<? extends Villager> p_35262_, Level p_35263_) {
        super(p_35262_, p_35263_);
        this.setVillagerData(this.getVillagerData().setProfession(VillagerProfession.NONE));
        this.getBrain().setSchedule(KingdomVillagerAi.KINGDOM_SCHEDULE);
        ((GroundPathNavigation) this.getNavigation()).setCanOpenDoors(true);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // POINTER: Evaluate tools and handle goal/behavior switching on tick
        this.jobManager.tick();

        // POINTER: Run armor auto-equip logic every 20 ticks (1 second).
        if (this.tickCount % 20 == 0) {
            this.evaluateAndEquipArmor();
        }
    }

    private void evaluateAndEquipArmor() {
        SimpleContainer inventory = this.getInventory();
        if (inventory == null) {
            System.out.println("DEBUG: Inventory is completely NULL!");
            return;
        }

        // DEBUG: Print the exact contents of the custom inventory to the server console
        System.out.println("--- Kingdom Villager Inventory Check ---");
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                System.out.println("Slot " + i + ": " + stack.getHoverName().getString() + " (Count: " + stack.getCount() + ")");
            }
        }
        System.out.println("----------------------------------------");

        // Iterate through all 4 armor slots: HEAD, CHEST, LEGS, FEET
        EquipmentSlot[] armorSlots = new EquipmentSlot[] {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };

        for (EquipmentSlot slot : armorSlots) {
            ItemStack currentEquipped = this.getItemBySlot(slot);
            double currentArmorValue = calculateArmorValue(currentEquipped, slot);

            int bestInvIndex = -1;
            double bestInvArmorValue = currentArmorValue;

            // Iterate over generic 8-slot inventory
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack invStack = inventory.getItem(i);
                if (invStack.isEmpty()) continue;

                // POINTER: 1.21.1 robust slot checking via Equipable (bypassing 1.21.2+ Data Component logic)
                net.minecraft.world.item.Equipable equipable = net.minecraft.world.item.Equipable.get(invStack);
                EquipmentSlot invSlot = equipable != null ? equipable.getEquipmentSlot() : null;

                // DEBUG: Let's see what the game actually thinks this item is!
                System.out.println("Checking " + invStack.getHoverName().getString() + " | Identified Slot: " + (invSlot != null ? invSlot.getName() : "NONE") + " | Target Slot: " + slot.getName());

                if (invSlot == slot) {
                    double invArmorValue = calculateArmorValue(invStack, slot);
                    
                    if (invArmorValue > bestInvArmorValue) {
                        bestInvArmorValue = invArmorValue;
                        bestInvIndex = i;
                    }
                }
            }

            // POINTER: Swap logic for full inventories.
            if (bestInvIndex != -1) {
                ItemStack betterArmor = inventory.getItem(bestInvIndex);
                
                // DEBUG: Print to server console to confirm the logic is actually firing!
                System.out.println("KingdomVillager equipping " + betterArmor.getHoverName().getString() + " in " + slot.getName());

                // 1:1 Swap
                inventory.setItem(bestInvIndex, currentEquipped.copy());
                this.setItemSlot(slot, betterArmor.copy());
                
                // POINTER: Ensure the villager actually drops the armor upon death
                this.setDropChance(slot, 2.0f); 
                
                if (betterArmor.getItem() instanceof net.minecraft.world.item.Equipable equipable) {
                    this.playSound(equipable.getEquipSound().value(), 1.0F, 1.0F);
                } else {
                    this.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0F, 1.0F);
                }
            }
        }
    }

    private double calculateArmorValue(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return 0.0;

        double baseArmor = 0.0;
        double armorToughness = 0.0;

        // POINTER: In 1.21.1, check the stack's explicit modifiers first. 
        // If empty (standard for un-modified vanilla items), fall back to the base Item's default components.
        net.minecraft.world.item.component.ItemAttributeModifiers modifiers = stack.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
        
        if (modifiers == null || modifiers.modifiers().isEmpty()) {
            modifiers = stack.getItem().components().getOrDefault(
                net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, 
                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY
            );
        }

        for (net.minecraft.world.item.component.ItemAttributeModifiers.Entry mod : modifiers.modifiers()) {
            // POINTER: In 1.21.1, slot.test() uses EquipmentSlotGroup. Ensure the target slot is valid for this modifier.
            if (mod.slot().test(slot)) {
                String attrName = mod.attribute().unwrapKey().map(key -> key.location().getPath()).orElse("");
                
                if (attrName.equals("armor")) {
                    baseArmor += mod.modifier().amount();
                } else if (attrName.equals("armor_toughness")) {
                    armorToughness += mod.modifier().amount();
                }
            }
        }

        // POINTER: 1.21.1 streamlines enchantments into a Data Component as well.
        int protection = 0;
        if (this.level() != null && this.level().registryAccess() != null) {
            try {
                net.minecraft.world.item.enchantment.ItemEnchantments enchantments = stack.getOrDefault(
                    net.minecraft.core.component.DataComponents.ENCHANTMENTS, 
                    net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY
                );
                
                var enchRegistry = this.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                var protectionHolder = enchRegistry.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION);
                
                // Get the level of protection directly from the component
                protection = enchantments.getLevel(protectionHolder);
            } catch (Exception e) {
                // Ignore gracefully if registries are temporarily unavailable
            }
        }

        return baseArmor * 1.0 + armorToughness * 2.0 + (protection * 1.5);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean didHurt = super.doHurtTarget(target);
        if (didHurt && target instanceof net.minecraft.world.entity.LivingEntity livingTarget) {
            ItemStack mainHandItem = this.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!mainHandItem.isEmpty()) {
                // POINTER: Apply weapon durability damage when the villager successfully attacks.
                mainHandItem.getItem().postHurtEnemy(mainHandItem, livingTarget, this);
                if (mainHandItem.isEmpty()) {
                    this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
            }
        }
        return didHurt;
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> p_35363_) {
        Brain<Villager> brain = this.brainProvider().makeBrain(p_35363_);
        this.registerBrainGoals(brain);
        return brain;
    }

    public void refreshBrain(ServerLevel p_35377_) {
        Brain<Villager> brain = this.getBrain();
        brain.stopAll(p_35377_, this);
        this.registerBrainGoals(this.getBrain());
    }

    private void registerBrainGoals(Brain<Villager> p_35346_) {
        KingdomVillagerAi.BrainProvider(this, p_35346_);
    }

    @Override
    public Brain.Provider<Villager> brainProvider() {
        return Brain.provider(MEMORY_MODULES, SENSOR_TYPES);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D); 
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN_ID, "steve");
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel pLevel, DamageSource pSource, boolean pRecentlyHit) {
        super.dropCustomDeathLoot(pLevel, pSource, pRecentlyHit);
        
        // POINTER: The generic inventory drops are handled here during death loot generation.
        // We drop them here to ensure they drop consistently with equipment.
        SimpleContainer inventory = this.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                // POINTER: Spawn the item entity in the world for the player to collect.
                this.spawnAtLocation(stack.copy());
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        
        // POINTER: Ensure MAINHAND is dropped natively or via manual drop if drop chance wasn't set.
        ItemStack mainHandStack = this.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!mainHandStack.isEmpty()) {
            this.spawnAtLocation(mainHandStack.copy());
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }
    
    @Override
    public void setOffers(MerchantOffers p_35414_) {
        // No trading
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkinId", this.getSkinId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SkinId", 8)) {
            this.setSkinId(tag.getString("SkinId"));
        }
    }

    public String getSkinId() {
        return this.entityData.get(DATA_SKIN_ID);
    }

    public void setSkinId(String skinId) {
        this.entityData.set(DATA_SKIN_ID, skinId);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        if (!SKIN_POOL.isEmpty()) {
            String randomSkin = SKIN_POOL.get(this.random.nextInt(SKIN_POOL.size()));
            this.setSkinId(randomSkin);
        }
        this.setVillagerData(this.getVillagerData().setProfession(VillagerProfession.NONE));
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    @Nullable
    @Override
    public Villager getBreedOffspring(ServerLevel p_146743_, AgeableMob p_146744_) {
        return null; 
    }

    public boolean canBeConvertedToZombie() {
        return false;
    }
    
    public boolean isWanderer() {
        return false;
    }

    @Override
    public net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player pPlayer, net.minecraft.world.InteractionHand pHand) {
        if (!this.level().isClientSide() && pPlayer instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // POINTER: Open the Kingdom Villager UI when right-clicked.
            // We pass the entity ID to the client via the extra data buffer so it can resolve the entity.
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider((windowId, playerInv, player) -> {
                return new com.femtendo.kingdombuilder.inventory.KingdomVillagerMenu(windowId, playerInv, this);
            }, this.getDisplayName()), buf -> buf.writeInt(this.getId()));
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(this.level().isClientSide());
    }

    @Override
    public boolean canBreed() {
        return false;
    }

    @Override
    public boolean wantsMoreFood() {
        return false;
    }

    @Override
    public boolean hasExcessFood() {
        return false;
    }

    @Override
    public boolean hasFarmSeeds() {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public void spawnGolemIfNeeded(ServerLevel level, long gameTime, int minVillagers) {
        // No-op
    }

    @Override
    public boolean wantsToSpawnGolem(long gameTime) {
        return false;
    }

    protected void releaseAllPois() {
        this.releasePoi(MemoryModuleType.HOME);
        this.releasePoi(MemoryModuleType.MEETING_POINT);
        this.releasePoi(MemoryModuleType.JOB_SITE);
        this.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
    }

    @Override
    public void releasePoi(MemoryModuleType<net.minecraft.core.GlobalPos> p_35429_) {
        if (p_35429_ == MemoryModuleType.HOME || p_35429_ == MemoryModuleType.MEETING_POINT || p_35429_ == MemoryModuleType.JOB_SITE || p_35429_ == MemoryModuleType.POTENTIAL_JOB_SITE) {
            super.releasePoi(p_35429_);
        }
    }
}