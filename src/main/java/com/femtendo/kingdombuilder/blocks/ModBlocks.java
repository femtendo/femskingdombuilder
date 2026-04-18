package com.femtendo.kingdombuilder.blocks;

import com.femtendo.kingdombuilder.KingdomBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block registry for Kingdom Builder (System 1 of the architecture plan).
 *
 * POINTER: All five blocks listed in kingdom_builder_architecture_plan.md (System 1)
 * are registered here. Concrete behavior (kingdom-claim on right-click, tube facades,
 * scaffold auto-removal, etc.) lives in each block's dedicated subclass introduced by
 * Systems 3/4/7/8. This file's ONLY job is DeferredRegister plumbing — do NOT add
 * gameplay logic here; layer it into Block subclasses when they are introduced.
 *
 * POINTER: These blocks are referenced by ModBlockEntities.java via `ModBlocks.X.get()`
 * when building BlockEntityType.Builder. Keep the RegistryObject field names stable —
 * renaming them will break the BE registry and every future Block subclass swap-in.
 */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, KingdomBuilder.MODID);

    // POINTER: SETTLEMENT_HEARTH — kingdom-claim "core" block (System 3). Explosion
    // resistance 6.0F matches stone so a creeper can't trivially erase a kingdom core;
    // destroy time 3.5F is deliberately non-trivial.
    //
    // POINTER (System 3 — completed): the supplier now instantiates
    // {@link SettlementHearthBlock} (extends BaseEntityBlock). It overrides
    // newBlockEntity() so placing one in-world spawns the matching
    // SettlementHearthBlockEntity. RenderShape.MODEL is forced in the subclass
    // so the JSON block model renders normally (BaseEntityBlock's default is
    // INVISIBLE which would leave a hole).
    public static final RegistryObject<Block> SETTLEMENT_HEARTH = BLOCKS.register("settlement_hearth",
            () -> new SettlementHearthBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.5F, 6.0F)));

    // POINTER: LOGISTICS_NODE — kingdom vault access point (System 4). Exposes an
    // IItemHandler capability via its BlockEntity. Stone-tier hardness (2.0F) keeps it
    // destructible so rivals can sabotage a poorly-defended logistics network.
    //
    // POINTER (System 4 — completed): The supplier now instantiates
    // {@link LogisticsNodeBlock} (extends BaseEntityBlock). It overrides
    // newBlockEntity() so placing one in-world spawns the matching
    // LogisticsNodeBlockEntity, which is where the capability proxy lives.
    // RenderShape.MODEL is forced in the subclass so the JSON model renders
    // (BaseEntityBlock defaults to INVISIBLE).
    public static final RegistryObject<Block> LOGISTICS_NODE = BLOCKS.register("logistics_node",
            () -> new LogisticsNodeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F)));

    // POINTER: KINGDOM_SILO — food network input (System 9). Slightly tougher than the
    // logistics node to reflect its role as a bulk-storage depot.
    public static final RegistryObject<Block> KINGDOM_SILO = BLOCKS.register("kingdom_silo",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(2.5F)));

    // POINTER: IRON_TUBE — food transport pipe with X-ray renderer (System 8).
    // noOcclusion() is REQUIRED: without it, neighboring-face culling blanks adjacent
    // transparent blocks, the X-ray BER would z-fight the opaque cube fallback, and
    // facades applied via the Wrench wouldn't show through. Strength 1.5F matches
    // iron bars — easy to reconfigure, hard to grief en masse.
    //
    // POINTER (System 11 — completed): Supplier now instantiates {@link IronTubeBlock}
    // (extends BaseEntityBlock) so placing one spawns IronTubeBlockEntity. System 11
    // promoted this swap early because the Wrench's click-to-toggle acceptance criterion
    // requires the BE to exist at the clicked position. System 8 should extend
    // IronTubeBlockEntity with connectedFaces + facades and register the X-ray BER,
    // but must NOT redo this block-class swap.
    public static final RegistryObject<Block> IRON_TUBE = BLOCKS.register("iron_tube",
            () -> new IronTubeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F)
                    .noOcclusion()));

    // POINTER: KINGDOM_SCAFFOLD — ephemeral placeholder placed by builder NPCs while
    // constructing a blueprint (Systems 5/10). Three properties are load-bearing:
    //   (1) destroyTime(0.0F) → instant break; builders must not be delayed by mining.
    //   (2) noLootTable() → scaffold NEVER drops items. Explosions bypass destroyTime
    //       but still respect loot tables, so THIS is what prevents scaffold litter
    //       on the System 6 ExplosionEvent damage path.
    //   (3) noOcclusion() → the hologram renderer (System 7) overlays a translucent
    //       preview; ambient occlusion would darken it.
    public static final RegistryObject<Block> KINGDOM_SCAFFOLD = BLOCKS.register("kingdom_scaffold",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .destroyTime(0.0F)
                    .noLootTable()
                    .noOcclusion()));

    /**
     * Invoked once from {@link com.femtendo.kingdombuilder.KingdomBuilder}'s constructor.
     *
     * POINTER: Call BEFORE ModBlockEntities.register() — BlockEntityType.Builder.of(...)
     * in that class calls `ModBlocks.X.get()` during the BE registration pass.
     */
    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
