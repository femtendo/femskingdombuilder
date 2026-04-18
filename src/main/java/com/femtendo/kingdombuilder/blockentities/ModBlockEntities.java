package com.femtendo.kingdombuilder.blockentities;

import com.femtendo.kingdombuilder.KingdomBuilder;
import com.femtendo.kingdombuilder.blocks.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block-entity type registry for Kingdom Builder (System 1).
 *
 * POINTER: Four BE types are registered here, one per block that needs server-side
 * state (Settlement Hearth, Logistics Node, Kingdom Silo, Iron Tube). KINGDOM_SCAFFOLD
 * intentionally has NO block entity — it is a dumb placeholder; its lifecycle is
 * managed by the builder NPC / BlueprintRegistry (System 5), not per-block state.
 *
 * POINTER: The `BlockEntityType.Builder.of(Constructor::new, ModBlocks.X.get())`
 * pattern binds each BE class to the single block that may host it. If a later
 * system needs a BE hostable on multiple blocks (e.g. variant tubes), pass
 * additional block args: `.of(C::new, B1.get(), B2.get())`.
 * `.build(null)` passes a null DataFixer type — acceptable because we ship our own
 * NBT migration via `load()` if/when the schema changes.
 */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, KingdomBuilder.MODID);

    // POINTER: SETTLEMENT_HEARTH_BE — holds claiming player's UUID + kingdom name.
    // Stub in System 1; System 3 adds save/load logic and EntityBlock wiring on the
    // block class so the BE actually spawns on placement.
    public static final RegistryObject<BlockEntityType<SettlementHearthBlockEntity>> SETTLEMENT_HEARTH_BE =
            BLOCK_ENTITIES.register("settlement_hearth_be",
                    () -> BlockEntityType.Builder.of(SettlementHearthBlockEntity::new,
                            ModBlocks.SETTLEMENT_HEARTH.get()).build(null));

    // POINTER: LOGISTICS_NODE_BE — capability proxy for the kingdom vault (System 4).
    // Will override getCapability() to expose a KingdomVaultItemHandler LazyOptional.
    public static final RegistryObject<BlockEntityType<LogisticsNodeBlockEntity>> LOGISTICS_NODE_BE =
            BLOCK_ENTITIES.register("logistics_node_be",
                    () -> BlockEntityType.Builder.of(LogisticsNodeBlockEntity::new,
                            ModBlocks.LOGISTICS_NODE.get()).build(null));

    // POINTER: KINGDOM_SILO_BE — owns a SiloItemHandler with food-only validation
    // (System 9).
    public static final RegistryObject<BlockEntityType<KingdomSiloBlockEntity>> KINGDOM_SILO_BE =
            BLOCK_ENTITIES.register("kingdom_silo_be",
                    () -> BlockEntityType.Builder.of(KingdomSiloBlockEntity::new,
                            ModBlocks.KINGDOM_SILO.get()).build(null));

    // POINTER: IRON_TUBE_BE — stores connectedFaces, forcedDisconnects, facades map
    // (System 8). setChanged() alone won't re-bake the client model; System 8 requires
    // level.sendBlockUpdated(...) when facade state mutates.
    public static final RegistryObject<BlockEntityType<IronTubeBlockEntity>> IRON_TUBE_BE =
            BLOCK_ENTITIES.register("iron_tube_be",
                    () -> BlockEntityType.Builder.of(IronTubeBlockEntity::new,
                            ModBlocks.IRON_TUBE.get()).build(null));

    /**
     * Invoked once from {@link com.femtendo.kingdombuilder.KingdomBuilder}'s constructor
     * AFTER {@link ModBlocks#register(IEventBus)}.
     */
    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
