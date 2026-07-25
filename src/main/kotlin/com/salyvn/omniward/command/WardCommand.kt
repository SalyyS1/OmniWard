package com.salyvn.omniward.command

import com.salyvn.omniward.OmniWardPlugin
import com.salyvn.omniward.gui.WardGui
import com.salyvn.omniward.util.TextUtil
import com.salyvn.omniward.ward.WardManager
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.entity.Player

/**
 * Registers the /ward command tree via the Paper Lifecycle + Brigadier API.
 *   /ward            -> open the ward GUI (or plant if run with no GUI need)
 *   /ward plant      -> plant a ward at your location
 *   /ward list       -> open the GUI listing your active wards
 *   /ward remove     -> remove your nearest own ward
 *   /ward reload     -> admin reload (perm: omniward.admin)
 */
@Suppress("UnstableApiUsage")
class WardCommand(private val plugin: OmniWardPlugin) {

    fun register() {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val root = Commands.literal("ward")
                .requires { it.sender.hasPermission("omniward.use") }
                .executes { ctx ->
                    val player = ctx.source.sender as? Player ?: return@executes playersOnly(ctx)
                    WardGui(plugin).open(player)
                    1
                }
                .then(
                    Commands.literal("plant").executes { ctx ->
                        val player = ctx.source.sender as? Player ?: return@executes playersOnly(ctx)
                        plant(player)
                        1
                    }
                )
                .then(
                    Commands.literal("list").executes { ctx ->
                        val player = ctx.source.sender as? Player ?: return@executes playersOnly(ctx)
                        WardGui(plugin).open(player)
                        1
                    }
                )
                .then(
                    Commands.literal("remove").executes { ctx ->
                        val player = ctx.source.sender as? Player ?: return@executes playersOnly(ctx)
                        removeNearest(player)
                        1
                    }
                )
                .then(
                    Commands.literal("reload")
                        .requires { it.sender.hasPermission("omniward.admin") }
                        .executes { ctx ->
                            plugin.reload()
                            ctx.source.sender.sendMessage(TextUtil.colorize("&aOmniWard reloaded."))
                            1
                        }
                )
                .build()

            event.registrar().register(root, "Ward map-control commands", listOf("wards"))
        }
    }

    private fun plant(player: Player) {
        when (val result = plugin.wardManager.plant(player)) {
            is WardManager.PlantResult.Success -> {
                val cfg = plugin.wardConfig
                val msg = cfg.message("planted", "&aWard planted! It projects an aura within &e%s&a blocks.")
                player.sendMessage(TextUtil.colorize(String.format(msg, result.ward.radius.toInt())))
            }
            is WardManager.PlantResult.Denied -> {
                // Resolve the config message (falling back to the built-in), THEN format any %s
                // placeholder so numbers (e.g. remaining cooldown seconds) render correctly.
                val template = plugin.wardConfig.message(result.reasonKey, result.fallback)
                val text = if (result.extra > 0 && template.contains("%s")) {
                    String.format(template, result.extra)
                } else {
                    template
                }
                player.sendMessage(TextUtil.colorize(text))
            }
        }
    }

    private fun removeNearest(player: Player) {
        val ward = plugin.wardManager.nearestOwnWard(player.uniqueId, player.location)
        if (ward == null) {
            player.sendMessage(TextUtil.colorize(plugin.wardConfig.message("no-wards", "&cYou have no active wards.")))
            return
        }
        plugin.wardManager.removeWard(ward)
        player.sendMessage(TextUtil.colorize(plugin.wardConfig.message("removed", "&aRemoved your nearest ward.")))
    }

    private fun playersOnly(ctx: com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack>): Int {
        ctx.source.sender.sendMessage(TextUtil.colorize("&cPlayers only."))
        return 1
    }
}
