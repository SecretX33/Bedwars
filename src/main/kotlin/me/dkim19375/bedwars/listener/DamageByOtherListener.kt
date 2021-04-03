package me.dkim19375.bedwars.listener

import me.dkim19375.bedwars.BedwarsPlugin
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

class DamageByOtherListener(private val plugin: BedwarsPlugin) : Listener {
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private fun EntityDamageByEntityEvent.onEvent() {
        if (entity !is Player || damager !is Player) return
        val player = entity as Player
        if (plugin.gameManager.invisPlayers.contains(player.uniqueId)) {
            player.sendMessage(ChatColor.RED.toString() + "You took damage from another player, your armor is now visible!")
            plugin.restoreArmor(player)
        }
    }
}