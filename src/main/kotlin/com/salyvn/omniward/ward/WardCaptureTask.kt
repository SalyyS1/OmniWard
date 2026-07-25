package com.salyvn.omniward.ward

import com.salyvn.omniward.config.WardConfig
import com.salyvn.omniward.model.Ward
import com.salyvn.omniward.team.TeamResolvers
import com.salyvn.omniward.util.TextUtil
import com.tcoded.folialib.FoliaLib
import com.tcoded.folialib.wrapper.task.WrappedTask
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.function.Consumer

/**
 * The heart of OmniWard: runs every [WardConfig.tickIntervalTicks] ticks and, for each live ward:
 *   1. expires it if past its lifetime,
 *   2. applies aura [org.bukkit.potion.PotionEffect]s to nearby allied players,
 *   3. advances a capture channel for the nearest enemy standing in range (interrupted if nobody
 *      enemy is channeling — progress resets), and
 *   4. flips or destroys the ward when capture completes.
 *
 * Folia-safety: all player/entity effect application and block edits are dispatched onto the owning
 * region thread. The repeating timer body itself only reads thread-safe atomic ward state and the
 * online-player snapshot, then hands region-bound work to the correct scheduler.
 */
class WardCaptureTask(
    private val plugin: JavaPlugin,
    private val foliaLib: FoliaLib,
    private val manager: WardManager,
    private val visuals: WardVisuals
) {

    fun tick(config: WardConfig) {
        val now = System.currentTimeMillis()
        val required = config.requiredCaptureTicks()
        val players = plugin.server.onlinePlayers.toList()

        for (ward in manager.allWards()) {
            if (ward.isExpired(now)) {
                expire(ward, config)
                continue
            }
            processWard(ward, config, players, now, required)
        }
    }

    private fun processWard(
        ward: Ward,
        config: WardConfig,
        players: List<Player>,
        now: Long,
        required: Int
    ) {
        val wardWorld = ward.location.world ?: return
        val radiusSq = ward.radius * ward.radius
        val controller = ward.ownerTeam
        val resolver = TeamResolvers.forOwner(plugin.server, controller)

        val allies = ArrayList<Player>()
        val enemies = ArrayList<Player>()
        for (player in players) {
            if (player.world.uid != wardWorld.uid) continue
            if (player.location.distanceSquared(ward.location) > radiusSq) continue
            if (resolver.isAlly(player, controller)) allies.add(player) else enemies.add(player)
        }

        // 1. Apply aura to allies on their own region threads.
        if (ward.auraEffects.isNotEmpty()) {
            for (ally in allies) {
                foliaLib.scheduler.runAtEntity(ally, Consumer<WrappedTask> {
                    for (effect in ward.auraEffects) {
                        ally.addPotionEffect(effect.toPotionEffect(config.auraDurationTicks))
                    }
                })
            }
        }

        // 2. Advance / decay capture.
        advanceCapture(ward, config, enemies, now, required)

        // 3. Visuals: contested when any enemy is channeling.
        val contested = ward.captureProgress > 0
        visuals.drawRing(ward, config.particlePoints, config.ownerParticle, config.captureParticle, contested)

        // 4. Broadcast progress via actionbar to everyone in range.
        broadcastProgress(ward, config, allies, enemies, required)
    }

    private fun advanceCapture(
        ward: Ward,
        config: WardConfig,
        enemies: List<Player>,
        now: Long,
        required: Int
    ) {
        if (enemies.isEmpty()) {
            // No enemy channeling -> reset progress (channel interrupted by leaving range).
            if (ward.captureProgress > 0) ward.resetCapture()
            return
        }

        // Nearest enemy becomes the active channeler.
        var channeler = enemies[0]
        var bestDist = channeler.location.distanceSquared(ward.location)
        for (i in 1 until enemies.size) {
            val candidate = enemies[i]
            val d = candidate.location.distanceSquared(ward.location)
            if (d < bestDist) {
                bestDist = d
                channeler = candidate
            }
        }

        ward.capturingPlayer = channeler.uniqueId
        val progress = ward.addCaptureProgress(1, now)

        if (progress >= required) {
            complete(ward, config, channeler)
        }
    }

    private fun complete(ward: Ward, config: WardConfig, capturer: Player) {
        if (config.captureMode == "DESTROY") {
            manager.removeWard(ward)
            capturer.sendMessage(TextUtil.colorize(config.message("captured-destroy", "&aYou destroyed the enemy ward!")))
            notifyOwner(ward.ownerUuid, config.message("owner-lost", "&cOne of your wards was destroyed!"))
        } else {
            // FLIP: transfer control to the capturer's side and reset the channel.
            ward.ownerTeam = capturer.uniqueId
            ward.resetCapture()
            capturer.sendMessage(TextUtil.colorize(config.message("captured-flip", "&aYou captured the ward! It now serves your side.")))
            notifyOwner(ward.ownerUuid, config.message("owner-flipped", "&cOne of your wards was captured!"))
        }
    }

    private fun expire(ward: Ward, config: WardConfig) {
        manager.removeWard(ward)
        notifyOwner(ward.ownerUuid, config.message("expired", "&7One of your wards has expired."))
    }

    private fun notifyOwner(ownerUuid: UUID, message: String) {
        val owner = plugin.server.getPlayer(ownerUuid) ?: return
        owner.sendMessage(TextUtil.colorize(message))
    }

    private fun broadcastProgress(
        ward: Ward,
        config: WardConfig,
        allies: List<Player>,
        enemies: List<Player>,
        required: Int
    ) {
        if (ward.captureProgress <= 0) return
        val pct = (ward.captureFraction(required) * 100).toInt()
        val bar = progressBar(ward.captureFraction(required))
        val allyMsg = TextUtil.colorize(config.message("progress-ally", "&e⚠ Ward under attack! &f$bar &7$pct%").replace("%bar%", bar).replace("%pct%", pct.toString()))
        val enemyMsg = TextUtil.colorize(config.message("progress-enemy", "&aCapturing... &f$bar &7$pct%").replace("%bar%", bar).replace("%pct%", pct.toString()))
        for (ally in allies) ally.sendActionBar(allyMsg)
        for (enemy in enemies) enemy.sendActionBar(enemyMsg)
    }

    private fun progressBar(fraction: Double): String {
        val total = 20
        val filled = (fraction * total).toInt().coerceIn(0, total)
        val sb = StringBuilder()
        var i = 0
        while (i < total) {
            sb.append(if (i < filled) '|' else '.')
            i++
        }
        return sb.toString()
    }
}
