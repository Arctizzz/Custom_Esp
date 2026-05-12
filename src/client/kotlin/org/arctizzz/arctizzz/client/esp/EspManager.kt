package org.arctizzz.arctizzz.client.esp

import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.entity.boss.WitherEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Box
import org.arctizzz.arctizzz.client.config.HighlightConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EspManager {

    /**
     * Resolved ESP render targets.
     * Key   = armor stand UUID (stable identity used for tracking and cleanup).
     * Value = the mob entity to highlight, or the armor stand itself as a fallback
     *         when no mob is found nearby.
     *
     * Written on the tick/main thread, read on the render thread — ConcurrentHashMap
     * ensures safe concurrent access.
     */
    val targets: ConcurrentHashMap<UUID, Entity> = ConcurrentHashMap()

    /**
     * Flat set of target entity UUIDs rebuilt whenever [targets] changes.
     * Used by [EntityRenderManagerMixin] for O(1) per-entity lookup instead of
     * iterating all entries on every rendered entity.
     *
     * @Volatile ensures the render thread always sees the latest published set.
     */
    @Volatile
    private var targetEntityUuids: Set<UUID> = emptySet()

    /** Armor stand references kept so we can re-resolve the mob if it despawns.
     *  Tick/main thread only — not accessed from the render thread. */
    private val trackedStands: HashMap<UUID, ArmorStandEntity> = HashMap()

    /** Armor stands confirmed not to match any filter. Skipped on future scans until
     *  filters change. Tick/main thread only. */
    private val invalidIds: MutableSet<UUID> = HashSet()

    private var tickCounter = 0

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns true if [uuid] belongs to a current ESP render target. O(1) lookup. */
    fun isTarget(uuid: UUID): Boolean = targetEntityUuids.contains(uuid)

    fun tick(config: HighlightConfig) {
        if (config.filters.isEmpty()) {
            if (targets.isNotEmpty() || trackedStands.isNotEmpty() || invalidIds.isNotEmpty()) clear()
            return
        }
        if (++tickCounter < config.scanDelay) return
        tickCounter = 0
        scan(config.filters, config.depthCheck)
    }

    /**
     * Called immediately when an entity is added to the client world.
     * Matching armor stands are registered right away without waiting for the
     * next scan cycle, eliminating the delay where a nametag is visible but
     * ESP hasn't appeared yet.
     */
    fun onEntityLoad(entity: Entity, config: HighlightConfig) {
        if (config.filters.isEmpty()) return
        if (entity !is ArmorStandEntity) return
        if (trackedStands.containsKey(entity.uuid) || invalidIds.contains(entity.uuid)) return

        val name = entity.customName?.string
            ?: entity.displayName?.string
            ?: entity.name.string
        val matches = config.filters.any { name.contains(it, ignoreCase = true) }

        if (matches) {
            if (!entity.isCustomNameVisible && !config.depthCheck) return
            val world = MinecraftClient.getInstance().world ?: return
            trackedStands[entity.uuid] = entity
            val mob = findClosestMob(world, entity)
            targets[entity.uuid] = mob ?: entity
            rebuildTargetUuids()
        }
        // Non-matching entities are intentionally NOT added to invalidIds here.
        // ENTITY_LOAD fires before the entity's NBT (custom name) is guaranteed to be
        // applied, so a match failure at this point is unreliable. scan() handles
        // rejection into invalidIds once the entity is fully initialised.
    }

    /**
     * Called immediately when an entity is removed from the client world.
     *
     * If it was a tracked armor stand: the whole entry is dropped.
     * If it was the mob target of a tracked stand: the entry falls back to the
     * armor stand so ESP keeps rendering while the next scan re-resolves.
     */
    fun onEntityUnload(entity: Entity) {
        val uuid = entity.uuid

        // Armor stand leaving — drop the whole entry and clear any cached rejection.
        if (trackedStands.remove(uuid) != null) {
            targets.remove(uuid)
            invalidIds.remove(uuid)
            rebuildTargetUuids()
            return
        }

        // Rejected armor stand leaving — clear the cached rejection so it gets
        // re-evaluated fresh if it re-enters render distance.
        if (invalidIds.remove(uuid)) return

        // Mob leaving — fall back to the armor stand so the outline stays visible.
        if (targetEntityUuids.contains(uuid)) {
            for ((standUuid, target) in targets) {
                if (target.uuid == uuid) {
                    val stand = trackedStands[standUuid] ?: continue
                    targets[standUuid] = stand
                }
            }
            rebuildTargetUuids()
        }
    }

    /** Full reset — called on filter removal or world change. */
    fun clear() {
        targets.clear()
        trackedStands.clear()
        invalidIds.clear()
        targetEntityUuids = emptySet()
        tickCounter = 0
    }

    // -------------------------------------------------------------------------
    // Internal scan logic (tick/main thread only)
    // -------------------------------------------------------------------------

    private fun scan(filters: List<String>, depthCheck: Boolean) {
        val world = MinecraftClient.getInstance().world ?: return

        val armorStands = world.entities.filterIsInstance<ArmorStandEntity>()
        val liveIds = armorStands.mapTo(HashSet()) { it.uuid }

        // 1. Prune stale entries (safety net — onEntityUnload handles the common case).
        var changed = targets.keys.retainAll(liveIds)
        changed = trackedStands.keys.retainAll(liveIds) || changed
        invalidIds.retainAll(liveIds)

        // 2. Refresh targets for tracked stands whose mob despawned or was never found.
        for ((uuid, stand) in trackedStands) {
            val current = targets[uuid]
            // Re-resolve if: missing, mob was removed, or we're still using the stand as fallback.
            if (current == null || current.isRemoved || current === stand) {
                val mob = findClosestMob(world, stand)
                targets[uuid] = mob ?: stand
                changed = true
            }
        }

        if (changed) rebuildTargetUuids()

        // 3. Categorise armor stands we haven't seen yet.
        for (stand in armorStands) {
            if (trackedStands.containsKey(stand.uuid) || invalidIds.contains(stand.uuid)) continue

            val name = stand.customName?.string
                ?: stand.displayName?.string
                ?: stand.name.string
            val matches = filters.any { name.contains(it, ignoreCase = true) }

            if (matches) {
                if (!stand.isCustomNameVisible && !depthCheck) continue
                trackedStands[stand.uuid] = stand
                val mob = findClosestMob(world, stand)
                targets[stand.uuid] = mob ?: stand
                rebuildTargetUuids()
            } else {
                invalidIds.add(stand.uuid)
            }
        }
    }

    /**
     * Replaces [targetEntityUuids] with a fresh snapshot of the current target UUIDs.
     * Called on the tick thread; the volatile write publishes the new set to the render thread.
     */
    private fun rebuildTargetUuids() {
        targetEntityUuids = targets.values.mapTo(HashSet()) { it.uuid }
    }

    /**
     * Searches for the actual mob beneath the armor-stand nametag.
     *
     * Uses the stand's own bounding box offset 1 block down — the mob sits
     * directly beneath the stand so their boxes naturally overlap. Players
     * and invisible Withers are excluded to avoid false positives.
     */
    private fun findClosestMob(world: ClientWorld, stand: ArmorStandEntity): Entity? {
        val searchBox = stand.boundingBox.offset(0.0, -1.0, 0.0)
        val mc = MinecraftClient.getInstance()
        return world.getOtherEntities(stand, searchBox) { entity ->
            if (entity is ArmorStandEntity) return@getOtherEntities false
            if (entity == mc.player) return@getOtherEntities false
            if (entity is PlayerEntity) return@getOtherEntities false
            if (entity is WitherEntity && entity.isInvisible) return@getOtherEntities false
            true
        }.minByOrNull { it.squaredDistanceTo(stand) }
    }
}
