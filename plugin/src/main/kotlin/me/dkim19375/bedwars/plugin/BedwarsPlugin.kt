package me.dkim19375.bedwars.plugin

import com.comphenix.protocol.ProtocolLibrary
import de.tr7zw.nbtinjector.NBTInjector
import io.github.thatkawaiisam.assemble.Assemble
import me.dkim19375.bedwars.plugin.command.MainCommand
import me.dkim19375.bedwars.plugin.command.TabCompletionHandler
import me.dkim19375.bedwars.plugin.data.BedData
import me.dkim19375.bedwars.plugin.data.GameData
import me.dkim19375.bedwars.plugin.data.SpawnerData
import me.dkim19375.bedwars.plugin.listener.*
import me.dkim19375.bedwars.plugin.manager.DataFileManager
import me.dkim19375.bedwars.plugin.manager.GameManager
import me.dkim19375.bedwars.plugin.manager.PacketManager
import me.dkim19375.bedwars.plugin.manager.ScoreboardManager
import me.dkim19375.dkim19375core.ConfigFile
import me.dkim19375.dkim19375core.CoreJavaPlugin
import org.bukkit.configuration.serialization.ConfigurationSerialization


@Suppress("MemberVisibilityCanBePrivate")
class BedwarsPlugin : CoreJavaPlugin() {
    lateinit var gameManager: GameManager
        private set
    lateinit var dataFile: ConfigFile
        private set
    lateinit var dataFileManager: DataFileManager
        private set
    lateinit var scoreboardManager: ScoreboardManager
        private set
    lateinit var packetManager: PacketManager
        private set
    lateinit var assemble: Assemble
        private set


    override fun onLoad() {
        val before = System.currentTimeMillis()
        ConfigurationSerialization.registerClass(BedData::class.java)
        ConfigurationSerialization.registerClass(SpawnerData::class.java)
        ConfigurationSerialization.registerClass(GameData::class.java)
        NBTInjector.inject()
        assemble.boards
        log("Successfully loaded (not enabled) ${description.name} v${description.version} in ${System.currentTimeMillis() - before}ms!")
    }

    override fun onEnable() {
        val before = System.currentTimeMillis()
        initVariables()
        registerCommands()
        registerListeners()
        reloadConfig()
        packetManager.addListeners()
        log("Successfully enabled ${description.name} v${description.version} in ${System.currentTimeMillis() - before}ms!")
    }

    override fun onDisable() {
        ProtocolLibrary.getProtocolManager().removePacketListeners(this)
        dataFile.save()
        ConfigurationSerialization.unregisterClass(BedData::class.java)
        ConfigurationSerialization.unregisterClass(SpawnerData::class.java)
        ConfigurationSerialization.unregisterClass(GameData::class.java)
        unregisterConfig(dataFile)
    }

    private fun initVariables() {
        dataFile = ConfigFile(this, "data.yml")
        registerConfig(dataFile)
        dataFileManager = DataFileManager(this)
        gameManager = GameManager(this)
        scoreboardManager = ScoreboardManager(this)
        packetManager = PacketManager(this)
        assemble = Assemble(this, scoreboardManager)
        assemble.ticks = 5
    }

    private fun registerCommands() {
        registerCommand("bedwars", MainCommand(this), TabCompletionHandler(this))
    }

    private fun registerListeners() {
        registerListener(PlayerQuitListener(this))
        registerListener(DamageByOtherListener(this))
        registerListener(PotionConsumeListener(this))
        registerListener(scoreboardManager)
        registerListener(PlayerMoveListener())
        registerListener(PlayerCoordsChangeListener(this))
        registerListener(PlayerInteractListener(this))
    }
}