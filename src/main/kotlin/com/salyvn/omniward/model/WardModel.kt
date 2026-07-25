package com.salyvn.omniward.model

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * A single aura effect projected by a ward. [amplifier] is 0-based (0 = level I).
 */
data class AuraEffect(val type: PotionEffectType, val amplifier: Int) {
    /** Build a fresh [PotionEffect] with a duration long enough to survive between task ticks. */
    fun toPotionEffect(durationTicks: Int): PotionEffect =
        PotionEffect(type, durationTicks, amplifier, true, false, true)
}

/**
 * A live, placed ward. Runtime object stored in the manager's [java.util.concurrent.ConcurrentHashMap].
 *
 * Thread-safety: [ownerTeam] / capture bookkeeping use atomics so the region-thread task and the
 * command/listener threads can read/mutate without coarse locking. [location] is immutable once placed.
 */
class Ward(
    val id: UUID,
    val ownerUuid: UUID,
    val location: Location,
    val radius: Double,
    val auraEffects: List<AuraEffect>,
    val createdAtMs: Long,
    val lifetimeMs: Long,
    /** Material originally at the ward block, restored when the ward is removed. null = no block placed. */
    val originalMaterial: Material?
) {
    /** Team currently controlling this ward. Flips when an enemy completes a capture. */
    private val teamRef = AtomicReference(ownerUuid)

    /** Capture progress in "ticks of channel" toward the configured requirement. 0 = uncaptured. */
    private val captureTicks = AtomicInteger(0)

    /** UUID of the player currently channeling a capture, or null when nobody is channeling. */
    private val capturingRef = AtomicReference<UUID?>(null)

    /** Last time (ms) capture progress advanced; used to decay stale channels. */
    private val lastProgressMs = AtomicLong(0L)

    var ownerTeam: UUID
        get() = teamRef.get()
        set(value) = teamRef.set(value)

    var capturingPlayer: UUID?
        get() = capturingRef.get()
        set(value) = capturingRef.set(value)

    val captureProgress: Int get() = captureTicks.get()

    fun addCaptureProgress(delta: Int, nowMs: Long): Int {
        lastProgressMs.set(nowMs)
        return captureTicks.updateAndGet { (it + delta).coerceAtLeast(0) }
    }

    fun resetCapture() {
        captureTicks.set(0)
        capturingRef.set(null)
    }

    fun lastProgressAt(): Long = lastProgressMs.get()

    fun isExpired(nowMs: Long): Boolean = lifetimeMs > 0 && (nowMs - createdAtMs) >= lifetimeMs

    /** Fraction 0.0..1.0 of the way to a completed capture, given the requirement in ticks. */
    fun captureFraction(requiredTicks: Int): Double {
        if (requiredTicks <= 0) return 0.0
        return (captureTicks.get().toDouble() / requiredTicks).coerceIn(0.0, 1.0)
    }
}
