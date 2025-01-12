/*
 * MIT License
 *
 * Copyright (c) 2021 dkim19375
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.dkim19375.bedwars.plugin.manager

import me.dkim19375.bedwars.plugin.BedwarsPlugin
import me.dkim19375.bedwars.plugin.SERVER_ONLINE
import me.dkim19375.bedwars.plugin.data.GameData
import me.dkim19375.bedwars.plugin.data.PlayerData
import me.dkim19375.bedwars.plugin.enumclass.*
import me.dkim19375.bedwars.plugin.util.*
import me.dkim19375.dkim19375core.data.LocationWrapper
import org.apache.commons.io.FileUtils
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.nio.file.Paths
import java.util.*

@Suppress("JoinDeclarationAndAssignment", "MemberVisibilityCanBePrivate")
class BedwarsGame(private val plugin: BedwarsPlugin, data: GameData) {
    var state = GameState.LOBBY
    var countdown = 10
    var time: Long = 0
    val players = mutableMapOf<Team, MutableSet<UUID>>()
    val playersInLobby = mutableSetOf<UUID>()
    var task: BukkitTask? = null
    private val worldName: String = data.world.name
    val beds = mutableMapOf<Team, Boolean>()
    val npcManager = NPCManager(plugin, data)
    val upgradesManager = UpgradesManager(plugin, this)
    val spawnerManager = SpawnerManager(plugin, this)
    val placedBlocks = mutableSetOf<LocationWrapper>()
    val beforeData = mutableMapOf<UUID, PlayerData>()

    var data = data
        private set
        get() {
            return plugin.dataFileManager.getGameData(worldName)!!
        }

    init {
        npcManager.disableAI()
        Bukkit.getScheduler().runTaskTimer(plugin, {
            for (player in getPlayersInGame().getPlayers()) {
                player.foodLevel = 20
            }
        }, 20L, 20L)
    }

    fun start(force: Boolean): Result {
        val result = canStart(force)
        if (result != Result.SUCCESS) {
            return result
        }
        state = GameState.STARTING
        countdown = 10
        logMsg("Game ${data.world.name} is starting!")
        task?.cancel()
        plugin.scoreboardManager.update(this)
        val game = this
        task = object : BukkitRunnable() {
            override fun run() {
                if (countdown < 1) {
                    broadcast("${ChatColor.AQUA}The game started!")
                    cancel()
                    setupAfterStart()
                    return
                }
                broadcast("${ChatColor.GREEN}Starting in ${ChatColor.GOLD}$countdown")
                plugin.scoreboardManager.update(game)
                countdown--
            }
        }.runTaskTimer(plugin, 20L, 20L)
        return Result.SUCCESS
    }

    fun getElapsedTime(): Long {
        return Delay.fromTime(time).seconds
    }

    private fun setupAfterStart() {
        update()
        var i = 1
        val teams = data.teams.toList()
        for (uuid in playersInLobby.shuffled()) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            val teamData = teams[i % teams.size]
            val team = teamData.team
            player.playerListName = "${team.chatColor}${player.name}"
            val set = players.getOrDefault(team, mutableSetOf())
            set.add(player.uniqueId)
            players[team] = set
            i++
        }
        for (teamData in teams) {
            beds[teamData.team] = true
        }
        for (entry in players.entries) {
            val team = entry.key
            val players = entry.value.getPlayers()
            val spawn = (data.teams.getTeam(team) ?: continue).spawn
            for (player in players) {
                player.teleport(spawn)
            }
        }
        state = GameState.STARTED
        time = System.currentTimeMillis()
        plugin.scoreboardManager.update(this)
        spawnerManager.start()
        for (entry in players.entries) {
            val team = entry.key
            val players = entry.value.getPlayers()
            for (player in players) {
                giveItems(player, null, team)
            }
        }
    }

    fun stop(winner: Player?, team: Team) {
        Bukkit.broadcastMessage("${team.chatColor}${winner?.displayName ?: team.displayName} has won BedWars!")
        forceStop()
    }

    fun forceStop(whenDone: Runnable? = null) {
        getPlayersInGame().getPlayers().forEach { p -> leavePlayer(p, false) }
        players.clear()
        playersInLobby.clear()
        task?.cancel()
        task = null
        beds.clear()
        time = 0
        state = GameState.REGENERATING_WORLD
        spawnerManager.reset()
        upgradesManager.stop()
        placedBlocks.clear()
        revertBack()
        beforeData.clear()
        regenerateMap {
            state = GameState.LOBBY
            whenDone?.run()
        }
    }

    private fun revertBack() = getPlayersInGame().toSet().forEach { Bukkit.getPlayer(it)?.let(::revertPlayer) }

    fun isEditing() = plugin.dataFileManager.isEditing(data)

    fun canStart(force: Boolean): Result {
        update()
        if (isRunning()) {
            return Result.GAME_RUNNING
        }
        if (plugin.gameManager.isGameRunning(data.world)) {
            return Result.GAME_IN_WORLD
        }
        if (!force && playersInLobby.size < data.minPlayers) {
            return Result.NOT_ENOUGH_PLAYERS
        }
        if (state == GameState.REGENERATING_WORLD) {
            return Result.REGENERATING_WORLD
        }
        return Result.SUCCESS
    }

    fun isRunning() = state.running

    fun update() {
        if (state != GameState.STARTED) {
            if (state != GameState.STARTING) {
                return
            }
            if (playersInLobby.size >= data.minPlayers) {
                return
            }
            state = GameState.LOBBY
            task?.cancel()
            task = null
            return
        }
        for (team in players.keys.toList()) {
            for (player in players.getOrDefault(team, setOf()).toList()) {
                if (Bukkit.getPlayer(player) != null) continue
                players.getOrDefault(team, mutableSetOf()).remove(player)
            }
            if (beds[team] == true && players.getOrDefault(team, setOf()).isEmpty()) {
                bedBreak(team, null)
            }
        }
        if (getPlayersInGame().isEmpty()) {
            forceStop()
            return
        }
        if (getPlayersInGame().size == 1) {
            val player = Bukkit.getPlayer(getPlayersInGame().first())
            stop(player, getTeamOfPlayer(player)!!)
        }
    }

    fun addPlayer(player: Player): Result {
        if (state != GameState.LOBBY && state != GameState.STARTING) {
            if (isRunning()) {
                return Result.GAME_RUNNING
            }
            return Result.GAME_STOPPED
        }
        if (playersInLobby.size >= data.maxPlayers) {
            return Result.TOO_MANY_PLAYERS
        }
        if (!plugin.partiesListeners.onGameJoin(player, this)) {
            return Result.TOO_MANY_PLAYERS
        }
        playersInLobby.add(player.uniqueId)
        broadcast("${player.displayName}${ChatColor.GREEN} has joined the game! ${playersInLobby.size}/${data.maxPlayers}")
        val lobby = plugin.dataFileManager.getLobby()
        val gameLobby = data.lobby.clone()
        logMsg("game lobby location: ${gameLobby.format()}")
        if (!gameLobby.chunk.load()) {
            return Result.REGENERATING_WORLD
        }
        beforeData[player.uniqueId] = PlayerData.createDataAndReset(
            player,
            lobby,
            gameLobby
        )
        plugin.scoreboardManager.getScoreboard(player, true) // activate
        if (playersInLobby.size >= data.minPlayers) {
            start(false).message
        }
        return Result.SUCCESS
    }

    fun broadcast(text: String) {
        if (state == GameState.LOBBY || state == GameState.STARTING) {
            for (uuid in playersInLobby) {
                val p = Bukkit.getPlayer(uuid) ?: continue
                p.sendMessage(text)
            }
            return
        }
        if (!isRunning()) {
            return
        }
        for (uuid in players.values.getCombinedValues()) {
            val p = Bukkit.getPlayer(uuid) ?: continue
            p.sendMessage(text)
        }
    }

    fun playerKilled(player: Player, inventory: List<ItemStack>) {
        val team = getTeamOfPlayer(player) ?: return
        if (!beds.getOrDefault(team, false)) {
            leavePlayer(player)
            return
        }
        val teamData = data.teams.getTeam(team) ?: return
        player.inventory.clearAll()
        player.gameMode = GameMode.SPECTATOR
        player.teleport(data.spec)
        object : BukkitRunnable() {
            var countDown = 5
            override fun run() {
                if (countDown <= 0) {
                    player.teleport(teamData.spawn)
                    player.gameMode = GameMode.SURVIVAL
                    player.sendOtherTitle("${ChatColor.GREEN}Respawned!")
                    giveItems(player, inventory, team)
                    cancel()
                    return
                }
                player.sendOtherTitle(
                    "${ChatColor.RED}You died!",
                    "${ChatColor.YELLOW}Respawning in ${ChatColor.GREEN}$countDown",
                    fadeIn = 0,
                    stay = 25,
                    fadeOut = 0
                )
                countDown--
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    fun giveItems(player: Player, items: List<ItemStack?>?, team: Team) {
        var armorType: ArmorType = ArmorType.LEATHER
        var addPick = false
        var addAxe = false
        var addShears = false
        player.inventory.clearAll()
        items?.forEach { item ->
            item ?: return@forEach
            if (item.type.name.endsWith("PICKAXE")) {
                addPick = true
                return@forEach
            }
            if (item.type.name.endsWith("AXE")) {
                addAxe = true
                return@forEach
            }
            if (item.type == Material.SHEARS) {
                addShears = true
                return@forEach
            }
            val armor = ArmorType.fromMaterial(item.type) ?: return@forEach
            if (armor != ArmorType.LEATHER) {
                armorType = armor
            }
        }
        player.inventory.addItem(MainShopItems.WOOD_SWORD.item.toItemStack(team.color))
        player.inventory.helmet = team.getColored(ItemStack(Material.LEATHER_HELMET))
        player.inventory.chestplate = team.getColored(ItemStack(Material.LEATHER_CHESTPLATE))
        player.inventory.leggings = team.getColored(ItemStack(armorType.leggings))
        player.inventory.boots = team.getColored(ItemStack(armorType.boots))
        if (addPick) {
            player.inventory.addItem(MainShopItems.WOOD_PICK.item.toItemStack(team.color))
        }
        if (addAxe) {
            player.inventory.addItem(MainShopItems.WOOD_AXE.item.toItemStack(team.color))
        }
        if (addShears) {
            player.inventory.addItem(MainShopItems.SHEARS.item.toItemStack(team.color))
        }
        upgradesManager.applyUpgrades(player)
    }

    fun revertPlayer(player: Player) {
        plugin.scoreboardManager.getScoreboard(player, false).deactivate()
        val data = beforeData[player.uniqueId] ?: return
        data.apply(player)
    }

    fun leavePlayer(player: Player, update: Boolean = true) {
        revertPlayer(player)
        if (state == GameState.LOBBY || state == GameState.STARTING) {
            if (!playersInLobby.contains(player.uniqueId)) {
                return
            }
            playersInLobby.remove(player.uniqueId)
            plugin.scoreboardManager.update(this)
            broadcast("${player.displayName}${ChatColor.RED} has left the game! ${playersInLobby.size}/${data.maxPlayers}")
            if (playersInLobby.size < data.minPlayers) {
                task?.cancel()
                state = GameState.LOBBY
                broadcast("${ChatColor.RED}Cancelled - Not enough players to start!")
            }
            return
        }
        if (state != GameState.STARTED) {
            return
        }
        val team = getTeamOfPlayer(player) ?: return
        revertPlayer(player)
        player.playerListName = player.displayName
        players.getOrDefault(team, mutableSetOf()).remove(player.uniqueId)
        plugin.scoreboardManager.update(this)
        broadcast("${team.chatColor}${player.displayName}${ChatColor.RED} has left the game!")
        if (update) {
            update()
        }
        return
    }

    fun getPlayersInGame(): Set<UUID> = when (state) {
        GameState.LOBBY -> playersInLobby.toSet()
        GameState.STARTING -> playersInLobby.toSet()
        GameState.STARTED -> players.values.getCombinedValues().toSet()
        else -> setOf()
    }

    fun getTeamOfPlayer(player: Player): Team? = getTeamOfPlayer(player.uniqueId)

    fun getTeamOfPlayer(player: UUID): Team? {
        players.forEach { (team, players) ->
            if (players.contains(player)) {
                return team
            }
        }
        return null
    }

    fun saveMap() {
        for (uuid in getPlayersInGame()) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            leavePlayer(player)
        }
        for (player in data.world.players) {
            player.teleport(Bukkit.getWorld("world").spawnLocation)
        }
        val folder = data.world.worldFolder
        val originalCreator = WorldCreator(data.world.name).copy(data.world)
        if (!Bukkit.unloadWorld(data.world, true)) {
            throw RuntimeException("Could not unload world!")
        }
        Bukkit.getScheduler().runTaskLater(plugin, {
            Bukkit.getScheduler().runTaskAsynchronously(plugin) {
                folder.delete()
                val path = Paths.get(plugin.dataFolder.absolutePath, "worlds", data.world.name)
                path.toFile().mkdirs()
                val file = Paths.get(path.toFile().absolutePath, data.world.name).toFile()
                if (file.exists()) {
                    FileUtils.forceDelete(Paths.get(path.toFile().absolutePath, data.world.name).toFile())
                }
                FileUtils.copyDirectory(folder, path.toFile())
                Bukkit.getScheduler().runTask(plugin, originalCreator::createWorld)
            }
        }, 2L)
    }

    fun getPlayersInTeam(team: Team): Set<UUID> {
        return players.getOrDefault(team, setOf())
    }

    fun regenerateMap(whenDone: Runnable? = null) {
        val dir = Paths.get(plugin.dataFolder.absolutePath, "worlds", data.world.name).toFile()
        if (!dir.exists()) {
            // Something went wrong here
            throw IllegalStateException("The directory for the world: ${data.world.name} doesn't exist! (${dir.absolutePath})")
        }
        for (uuid in getPlayersInGame()) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            leavePlayer(player)
        }
        for (player in data.world.players) {
            player.teleport(Bukkit.getWorld("world").spawnLocation)
        }
        state = GameState.REGENERATING_WORLD
        val folder = data.world.worldFolder
        if (!SERVER_ONLINE) {
            return
        }
        Bukkit.getScheduler().runTaskLater(plugin, {
            val originalCreator = WorldCreator(data.world.name).copy(data.world)
            if (!Bukkit.unloadWorld(data.world, true)) {
                throw IllegalStateException("Could not unload world!")
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin) {
                FileUtils.forceDelete(folder)
                FileUtils.copyDirectory(dir, folder)
                Bukkit.getScheduler().runTask(plugin) {
                    data.copy(gameWorld = originalCreator.createWorld()).save(plugin)
                    println("${data.world.name} has finished regenerating!")
                    whenDone?.run()
                }
            }
        }, 2L)
    }

    // DURING GAME EVENTS

    fun bedBreak(team: Team, player: Player?) {
        if (beds[team] == false) {
            return
        }
        beds[team] = false
        plugin.scoreboardManager.update(this)
        getPlayersInTeam(team).getPlayers().forEach { p ->
            p.sendOtherTitle("${ChatColor.RED}BED DESTROYED!", "You will no longer respawn!")
        }
        if (player == null) {
            broadcast(
                "${ChatColor.BOLD}BED DESTRUCTION > ${team.chatColor}${team.displayName}${ChatColor.GRAY}'s " +
                        "bed was broken!"
            )
            return
        }
        val teamOfPlayer = getTeamOfPlayer(player) ?: return
        broadcast(
            "${ChatColor.BOLD}BED DESTRUCTION > ${team.chatColor}${team.displayName}${ChatColor.GRAY}'s " +
                    "bed was broken by " +
                    "${teamOfPlayer.chatColor}${player.displayName}${ChatColor.GRAY}!"
        )
        update()
    }

    enum class Result(val message: String) {
        SUCCESS("Successful!"),
        GAME_RUNNING("The game is currently running!"),
        GAME_STOPPED("The game is not running!"),
        GAME_IN_WORLD("The game is in the same world!"),
        NOT_ENOUGH_PLAYERS("Not enough players!"),
        REGENERATING_WORLD("The game world is regenerating!"),
        TOO_MANY_PLAYERS("Too many players!")
    }
}