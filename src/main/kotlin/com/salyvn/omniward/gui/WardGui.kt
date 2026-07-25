package com.salyvn.omniward.gui

import com.salyvn.omniward.OmniWardPlugin
import com.salyvn.omniward.model.Ward
import com.salyvn.omniward.util.TextUtil
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Read/interact GUI listing a player's active wards. Each ward shows its location, radius, remaining
 * lifetime and current control/capture state. Clicking a ward removes it (restoring any placed block).
 */
class WardGui(private val plugin: OmniWardPlugin) {

    fun open(player: Player) {
        val wards = plugin.wardManager.wardsOf(player.uniqueId)
        val rows = ((wards.size + 8) / 9).coerceIn(1, 6)

        val gui = Gui.gui()
            .title(TextUtil.component("&8Your Wards"))
            .rows(rows)
            .disableAllInteractions()
            .create()

        if (wards.isEmpty()) {
            val empty = ItemStack(Material.BARRIER)
            val meta = empty.itemMeta
            meta.displayName(TextUtil.component("&cNo active wards"))
            meta.lore(listOf(TextUtil.component("&7Use &e/ward plant &7to place one.")))
            empty.itemMeta = meta
            gui.addItem(GuiItem(empty))
            gui.open(player)
            return
        }

        val now = System.currentTimeMillis()
        val config = plugin.wardConfig
        for (ward in wards) {
            gui.addItem(buildWardItem(player, ward, now, config.requiredCaptureTicks()))
        }
        gui.open(player)
    }

    private fun buildWardItem(player: Player, ward: Ward, now: Long, requiredTicks: Int): GuiItem {
        val contested = ward.captureProgress > 0
        val material = if (contested) Material.RED_WOOL else Material.LIME_WOOL
        val item = ItemStack(material)
        val meta = item.itemMeta

        val loc = ward.location
        meta.displayName(TextUtil.component("&aWard &7@ &f${loc.blockX}, ${loc.blockY}, ${loc.blockZ}"))

        val lore = mutableListOf<Component>()
        lore.add(TextUtil.component("&7World: &f${loc.world?.name ?: "?"}"))
        lore.add(TextUtil.component("&7Radius: &f${ward.radius.toInt()} &7blocks"))
        lore.add(TextUtil.component("&7Aura effects: &f${ward.auraEffects.size}"))

        if (ward.lifetimeMs > 0) {
            val remaining = ((ward.createdAtMs + ward.lifetimeMs - now) / 1000).coerceAtLeast(0)
            lore.add(TextUtil.component("&7Expires in: &f${remaining}s"))
        } else {
            lore.add(TextUtil.component("&7Lifetime: &fpermanent"))
        }

        if (contested) {
            val pct = (ward.captureFraction(requiredTicks) * 100).toInt()
            lore.add(TextUtil.component("&c⚠ Under capture: &e$pct%"))
        } else {
            lore.add(TextUtil.component("&aSecure"))
        }
        lore.add(Component.empty())
        lore.add(TextUtil.component("&eClick to remove this ward."))
        meta.lore(lore)
        item.itemMeta = meta

        return GuiItem(item) {
            val removed = plugin.wardManager.removeWard(ward)
            if (removed) {
                player.sendMessage(TextUtil.colorize("&aRemoved your ward."))
                player.closeInventory()
            }
        }
    }
}
