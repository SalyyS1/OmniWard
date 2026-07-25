package com.salyvn.omniward.ward

import com.salyvn.omniward.config.WardConfig
import com.salyvn.omniward.model.Ward
import com.tcoded.folialib.FoliaLib
import com.tcoded.folialib.wrapper.task.WrappedTask
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns all live wards and their lifecycle: placement, removal, cooldowns, and the repeating
 * capture/aura task. State lives in a [ConcurrentHashMap] keyed by ward id so the region-thread
 * task and command threads never race on a coarse lock.
 *
 * Block placement/restoration is delegated to [WardVisuals] and always dispatched onto the owning
 * region thread via FoliaLib, so this is Folia-safe.
 */
class WardManager(
    private val plugin: JavaPlugin,
    private val foliaLib: FoliaLib,
    @Volatile private var config: WardConfig
) {
    private val wards = ConcurrentHashMap<UUID, Ward>()
    private val cooldowns = ConcurrentHashMap<UUID, Long>()
    private val visuals = WardVisuals(foliaLib)
    private var task: WrappedTask? = null

    fun start() {
        schedule()
    }

    fun reload(newConfig: WardConfig) {
        config = newConfig
        cancelTask()
        schedule()
    }

    private fun schedule() {
        val interval = config.tickIntervalTicks
        val captureTask = WardCaptureTask(plugin, foliaLib, this, visuals)
        task = foliaLib.scheduler.runTimer(Runnable { captureTask.tick(config) }, interval, interval)
    }

    private fun cancelTask() {
        val t = task ?: return
        runCatching { foliaLib.scheduler.cancelTask(t) }
        task = null
    }

    fun shutdown() {
        cancelTask()
        // Restore every placed block on disable so the world is left clean.
        for (ward in wards.values) visuals.removeMarker(ward)
        wards.clear()
        cooldowns.clear()
    }

    // --- queries ---

    fun currentConfig(): WardConfig = config

    fun allWards(): Collection<Ward> = wards.values

    fun wardsOf(uuid: UUID): List<Ward> = wards.values.filter { it.ownerUuid == uuid }

    fun countOf(uuid: UUID): Int = wards.values.count { it.ownerUuid == uuid }

    fun remainingCooldownMs(uuid: UUID, nowMs: Long): Long {
        val readyAt = cooldowns[uuid] ?: return 0L
        return (readyAt - nowMs).coerceAtLeast(0L)
    }

    /** The caller's ward nearest to [location] within the same world, or null. */
    fun nearestOwnWard(uuid: UUID, location: Location): Ward? {
        var best: Ward? = null
        var bestDist = Double.MAX_VALUE
        for (ward in wards.values) {
            if (ward.ownerUuid != uuid) continue
            if (ward.location.world?.uid != location.world?.uid) continue
            val d = ward.location.distanceSquared(location)
            if (d < bestDist) {
                bestDist = d
                best = ward
            }
        }
        return best
    }

    // --- mutations ---

    sealed class PlantResult {
        data class Success(val ward: Ward) : PlantResult()
        data class Denied(val reasonKey: String, val fallback: String, val extra: Long = 0L) : PlantResult()
    }

    /** Attempt to plant a ward at [player]'s location, enforcing limits and cooldown. */
    fun plant(player: Player): PlantResult {
        val now = System.currentTimeMillis()
        val uuid = player.uniqueId

        val cdRemaining = remainingCooldownMs(uuid, now)
        if (cdRemaining > 0) {
            return PlantResult.Denied("cooldown", "&cOn cooldown for &e%s&cs.", cdRemaining / 1000 + 1)
        }
        if (countOf(uuid) >= config.maxWardsPerPlayer) {
            return PlantResult.Denied("max-per-player", "&cYou already have the max &e${config.maxWardsPerPlayer}&c wards.")
        }
        if (wards.size >= config.maxTotalWards) {
            return PlantResult.Denied("max-total", "&cThe server ward limit has been reached.")
        }

        val loc = player.location.block.location.add(0.5, 0.0, 0.5)
        val original = if (config.placeBlock && config.markerBlock != null) {
            loc.block.type
        } else {
            null
        }

        val ward = Ward(
            id = UUID.randomUUID(),
            ownerUuid = uuid,
            location = loc,
            radius = config.radius,
            auraEffects = config.auraEffects,
            createdAtMs = now,
            lifetimeMs = config.lifetimeSeconds * 1000L,
            originalMaterial = original
        )
        wards[ward.id] = ward

        if (original != null) {
            val marker = config.markerBlock
            if (marker != null) visuals.placeMarker(ward, marker)
        }

        if (config.cooldownSeconds > 0) {
            cooldowns[uuid] = now + config.cooldownSeconds * 1000L
        }
        return PlantResult.Success(ward)
    }

    /** Remove a ward by id, restoring any placed block. Returns true if it existed. */
    fun remove(id: UUID): Boolean {
        val ward = wards.remove(id) ?: return false
        visuals.removeMarker(ward)
        return true
    }

    fun removeWard(ward: Ward): Boolean = remove(ward.id)

    /**
     * Interrupt any active capture channel led by [uuid] (e.g. the channeler took damage).
     * Resets progress on every ward they were channeling. Returns the number interrupted.
     */
    fun interruptCapturesBy(uuid: UUID): Int {
        var count = 0
        for (ward in wards.values) {
            if (ward.capturingPlayer == uuid && ward.captureProgress > 0) {
                ward.resetCapture()
                count++
            }
        }
        return count
    }
}
