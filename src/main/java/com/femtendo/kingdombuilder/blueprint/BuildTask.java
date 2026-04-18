package com.femtendo.kingdombuilder.blueprint;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Single queued block-placement order consumed by a builder NPC (System 10 /
 * BuilderActivity). Instances live in a {@link java.util.PriorityQueue} keyed
 * by this class's natural order so that a builder always pops the "next most
 * sensible" block to place.
 *
 * <h2>Architectural intent (System 5 of kingdom_builder_architecture_plan.md)</h2>
 *
 * <p><strong>Sort rules (from the issue spec):</strong>
 * <ol>
 *   <li>Lower Y placed first. You can't place a door mid-air — foundations go
 *       before walls, walls before roofs.</li>
 *   <li>Tie-break on same Y: lower {@code dependencyPriority} first, where
 *       priority is 1=SOLID (load-bearing blocks), 2=NON_SOLID (glass,
 *       fences), 3=ATTACHED (torches, signs, redstone wires — anything that
 *       needs a neighbour to stand).</li>
 * </ol>
 *
 * POINTER: This ordering is why implementing {@link Comparable} matters —
 * {@link java.util.PriorityQueue#poll} uses {@link Comparable#compareTo}
 * directly. Do NOT wrap instances in a {@code Comparator} at queue-construction
 * time; callers assume the natural order.
 *
 * POINTER: BuildTask is deliberately NOT persisted. The task queue is rebuilt
 * on world load from the {@link ZoneData} footprint + blueprint template —
 * re-running the rasterizer is cheap and sidesteps the problem of a persisted
 * queue becoming stale if the blueprint definition changes between versions.
 * If a future feature needs task persistence (e.g. resuming long constructions
 * across many sessions without rework), add NBT (de)serialization mirroring
 * {@link ZoneData#save} — but be prepared to version the schema.
 *
 * <h2>Locking</h2>
 *
 * <p>Multiple builders can share one queue. To prevent two builders grabbing
 * the same task, a builder calls {@link #lock()} atomically on the pollef
 * task before pathing to it; when work completes (or the builder dies/times
 * out), {@link #unlock()} returns the task to the pool. The queue itself is
 * single-threaded (server-tick), so {@code locked} is a plain boolean flag —
 * no {@code AtomicBoolean} needed.
 *
 * POINTER: Unlock on builder death, not on builder pickup. If a builder
 * path-fails mid-task, the task is stuck locked until someone calls unlock.
 * BuilderActivity (System 10) is responsible for matching lock/unlock pairs.
 */
public class BuildTask implements Comparable<BuildTask> {

    /**
     * Dependency priority constants (lower = built first on the same Y plane).
     *
     * POINTER: Kept as {@code public static final int} rather than an enum so
     * callers can pass custom values (e.g. a blueprint that needs a sub-tier
     * between SOLID and NON_SOLID can use 1.5 rounded to an int). Enum would
     * force a fixed ladder.
     */
    public static final int PRIORITY_SOLID = 1;
    public static final int PRIORITY_NON_SOLID = 2;
    public static final int PRIORITY_ATTACHED = 3;

    private final BlockPos pos;
    private final BlockState state;
    private final int dependencyPriority;
    private final UUID kingdomOwnerUUID;

    // Locked flag — see class javadoc. Package-private volatile isn't needed
    // because the queue is only touched on the main server thread.
    private boolean locked;

    public BuildTask(BlockPos pos, BlockState state, int dependencyPriority, UUID kingdomOwnerUUID) {
        // Defensive immutable() copy — same pattern as ZoneData / KingdomData.
        this.pos = pos.immutable();
        this.state = state;
        this.dependencyPriority = dependencyPriority;
        this.kingdomOwnerUUID = kingdomOwnerUUID;
        this.locked = false;
    }

    // --- Getters -----------------------------------------------------------

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    public int getDependencyPriority() {
        return dependencyPriority;
    }

    public UUID getKingdomOwnerUUID() {
        return kingdomOwnerUUID;
    }

    public boolean isLocked() {
        return locked;
    }

    // --- Lock / unlock -----------------------------------------------------

    /**
     * Claim this task for a single builder.
     *
     * POINTER: Returns {@code false} if the task was already locked. Callers
     * must check the return value — ignoring it is the canonical multi-builder
     * race-condition bug. Correct pattern:
     * <pre>{@code
     * BuildTask t = queue.poll();
     * if (t != null && t.lock()) { ... start building ... }
     * }</pre>
     *
     * POINTER: Even though the server tick is single-threaded, {@code poll()}
     * followed by "re-insert if unsuitable" can lead to the same task being
     * handed to two builders in the same tick. The lock flag is the guard.
     */
    public boolean lock() {
        if (locked) {
            return false;
        }
        locked = true;
        return true;
    }

    /**
     * Release the lock so another builder can try this task.
     *
     * POINTER: Must be called when a builder gives up on a task (death, path
     * failure, logoff). BuilderActivity (System 10) owns this lifecycle. If a
     * task is polled, locked, and then dropped without {@code unlock()}, it is
     * permanently orphaned until a server restart rebuilds the queue.
     */
    public void unlock() {
        locked = false;
    }

    // --- Comparable --------------------------------------------------------

    /**
     * Sort order: lower Y first, then lower {@code dependencyPriority} first.
     *
     * POINTER: Breaking ties on {@code dependencyPriority} (SOLID before
     * ATTACHED on the same Y) is what makes the queue produce a sane build
     * sequence — without it a torch task could be polled before its wall. The
     * issue spec acceptance criterion ("PriorityQueue polls bottom-layer
     * solid blocks before upper-layer attached blocks") is satisfied by the
     * two-key comparison below.
     *
     * POINTER: We deliberately do NOT tie-break on X/Z or on
     * {@code kingdomOwnerUUID}. Identical (Y, priority) tasks are treated as
     * interchangeable from the queue's perspective — the PriorityQueue spec
     * only requires a strict ordering for the head, not a total order. If you
     * need deterministic replay for tests, add a monotonic insertion sequence.
     */
    @Override
    public int compareTo(BuildTask other) {
        int yCompare = Integer.compare(this.pos.getY(), other.pos.getY());
        if (yCompare != 0) {
            return yCompare;
        }
        return Integer.compare(this.dependencyPriority, other.dependencyPriority);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BuildTask that)) return false;
        // POINTER: Identity by (pos, owner). Two tasks at the same world
        // location for the same kingdom are the same task even if the target
        // BlockState differs (which happens during blueprint re-issues). This
        // lets us de-duplicate when rebuilding the queue after a save reload.
        return this.pos.equals(that.pos)
                && this.kingdomOwnerUUID.equals(that.kingdomOwnerUUID);
    }

    @Override
    public int hashCode() {
        return 31 * pos.hashCode() + kingdomOwnerUUID.hashCode();
    }
}
