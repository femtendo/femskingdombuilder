package com.femtendo.kingdombuilder.entities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KingdomVillagerEntity extends AgeableMob {

    private static final EntityDataAccessor<String> DATA_SKIN_ID =
            SynchedEntityData.defineId(KingdomVillagerEntity.class, EntityDataSerializers.STRING);

    private static final String[] BUILTIN_SKINS = {"steve"};
    private static final List<String> SKIN_POOL = new ArrayList<>();

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
                // We use a simple logger or just ignore if it fails, but better to log
            }
        }
    }

    public KingdomVillagerEntity(EntityType<? extends AgeableMob> p_31343_, Level p_31344_) {
        super(p_31343_, p_31344_);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AgeableMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN_ID, "steve");
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
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel p_146743_, AgeableMob p_146744_) {
        return null; // No breeding
    }
}
