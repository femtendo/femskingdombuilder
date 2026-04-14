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

public class KingdomVillagerEntity extends Villager {

    private static final EntityDataAccessor<String> DATA_SKIN_ID =
            SynchedEntityData.defineId(KingdomVillagerEntity.class, EntityDataSerializers.STRING);

    private static final String[] BUILTIN_SKINS = {"steve"};
    private static final List<String> SKIN_POOL = new ArrayList<>();

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
                .add(Attributes.FOLLOW_RANGE, 48.0D); 
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN_ID, "steve");
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