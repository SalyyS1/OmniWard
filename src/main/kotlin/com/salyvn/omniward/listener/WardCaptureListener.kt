package com.salyvn.omniward.listener

import com.salyvn.omniward.util.TextUtil
import com.salyvn.omniward.ward.WardManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent

/**
 * Interrupts capture channels when the channeling player takes damage.
 *
 * The design brief requires a channel to break if the capturer "takes damage or leaves range".
 * Leaving range is handled inside [com.salyvn.omniward.ward.WardCaptureTask] (progress resets when no
 * enemy is in range); this listener covers the damage case for any live channel led by the victim.
 */
class WardCaptureListener(
    private val manager: WardManager
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val interrupted = manager.interruptCapturesBy(player.uniqueId)
        if (interrupted > 0) {
            val cfg = manager.currentConfig()
            player.sendActionBar(
                TextUtil.colorize(cfg.message("capture-interrupted", "&cCapture interrupted — you took damage!"))
            )
        }
    }
}
