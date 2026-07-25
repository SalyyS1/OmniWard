package com.salyvn.omniward.team

import org.bukkit.entity.Player
import java.util.UUID

/**
 * Pluggable team-resolution strategy (KISS). Determines whether a player is an ally of a ward's
 * controlling side. Two implementations ship:
 *
 *  - [ScoreboardTeamResolver]: uses Bukkit scoreboard teams. Two players are allies iff they share
 *    the same scoreboard team. Used automatically when the ward owner has a scoreboard team.
 *  - [OwnerOnlyTeamResolver]: the simple fallback — only the controlling player is an ally, everyone
 *    else is an enemy. Used when no scoreboard team exists.
 *
 * The manager picks per-ward at apply time via [TeamResolvers.forOwner].
 */
interface TeamResolver {
    /** True if [player] is on the same side as the entity identified by [controllerUuid]. */
    fun isAlly(player: Player, controllerUuid: UUID): Boolean
}

/** Allies = same Bukkit scoreboard team as the controller. */
class ScoreboardTeamResolver : TeamResolver {
    override fun isAlly(player: Player, controllerUuid: UUID): Boolean {
        if (player.uniqueId == controllerUuid) return true
        val scoreboard = player.scoreboard
        val playerTeam = scoreboard.getEntryTeam(player.name) ?: return false
        val controller = player.server.getOfflinePlayer(controllerUuid)
        val controllerName = controller.name ?: return false
        val controllerTeam = scoreboard.getEntryTeam(controllerName) ?: return false
        return playerTeam.name == controllerTeam.name
    }
}

/** Allies = only the controller themselves. Everyone else is an enemy. */
class OwnerOnlyTeamResolver : TeamResolver {
    override fun isAlly(player: Player, controllerUuid: UUID): Boolean =
        player.uniqueId == controllerUuid
}

/**
 * Chooses the right resolver for a given controller. If the controller is online and belongs to a
 * scoreboard team, team play is enabled; otherwise it degrades to owner-only.
 */
object TeamResolvers {

    private val scoreboardResolver = ScoreboardTeamResolver()
    private val ownerOnlyResolver = OwnerOnlyTeamResolver()

    fun forOwner(server: org.bukkit.Server, controllerUuid: UUID): TeamResolver {
        val online = server.getPlayer(controllerUuid) ?: return ownerOnlyResolver
        val team = online.scoreboard.getEntryTeam(online.name)
        return if (team != null) scoreboardResolver else ownerOnlyResolver
    }
}
