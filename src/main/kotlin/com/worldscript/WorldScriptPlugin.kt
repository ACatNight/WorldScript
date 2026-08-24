package com.worldscript

import org.bukkit.plugin.java.JavaPlugin
import com.worldscript.command.WsCommand
import com.worldscript.command.RegionChatEditor
import com.worldscript.command.ActionPresetCatalog
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l1.region_events.RegionEventServiceImpl
import com.worldscript.modules.l1.region_core.SelectionService
import com.worldscript.modules.l1.region_events.RegionSelectionListener
import com.worldscript.modules.l2.script_actions.ScriptActionServiceImpl
import com.worldscript.modules.l2.admin_gui.RegionGuiService
import com.worldscript.modules.l2.rpg.ConditionEvaluator
import com.worldscript.modules.l2.rpg.RewardService
import com.worldscript.modules.l2.rpg.PlayerVariableService
import com.worldscript.modules.l2.atmosphere.RegionParticleService
import com.worldscript.integration.placeholder.WorldScriptPlaceholderExpansion
import com.worldscript.integration.taboolib.TabooLibBridge
import com.worldscript.foundation.MaterialResolver
import com.worldscript.foundation.api.PlayerRegionProgressService
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bstats.bukkit.Metrics

class WorldScriptPlugin : JavaPlugin(), Listener {
    lateinit var lang: com.worldscript.foundation.Lang
        private set
    lateinit var regionCore: RegionCoreServiceImpl
        private set
    private lateinit var playerVariables: PlayerVariableService
    lateinit var playerProgress: PlayerRegionProgressService
        private set
    lateinit var taboolib: TabooLibBridge
        private set
    private lateinit var particles: RegionParticleService
    private var placeholderExpansion: WorldScriptPlaceholderExpansion? = null

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        Metrics(this, 33524)
        saveDefaultConfig()
        if (!config.isString("language")) {
            config.set("language", "en_US")
            saveConfig()
        }
        saveResource("lang/en_US.yml", false)
        saveResource("lang/zh_CN.yml", false)
        saveResource("lang/zh_TW.yml", false)
        saveResource("presets/actions.yml", false)
        taboolib = TabooLibBridge(this)
        taboolib.report()
        validateMaterialConfig()
        lang = com.worldscript.foundation.Lang(this)
        regionCore = RegionCoreServiceImpl(this)
        regionCore.load()
        playerVariables = PlayerVariableService(this)
        playerProgress = playerVariables
        val rewards = RewardService(this, regionCore, playerVariables)
        particles = RegionParticleService(this, regionCore, playerVariables)
        val conditions = ConditionEvaluator(this, regionCore, playerVariables)
        val actions = ScriptActionServiceImpl(this, regionCore, playerVariables, conditions, rewards)
        val events = RegionEventServiceImpl(this, regionCore, playerVariables, conditions, actions::executeConditionFailure)
        val gui = RegionGuiService(this, regionCore)
        val selection = SelectionService(this)
        val command = WsCommand(this, regionCore, selection, playerVariables)
        command.guiOpener = { player -> gui.openList(player) }
        command.settingsOpener = { player -> gui.openSettings(player) }
        val presets = ActionPresetCatalog(this)
        val chatEditor = RegionChatEditor(this, regionCore, presets)
        command.chatEditor = chatEditor
        gui.editorOpener = { player, regionId -> chatEditor.open(player, regionId) }
        command.reloadHandler = {
            events.reset()
            actions.reset()
            chatEditor.reset()
            presets.reload()
            registerPlaceholderExpansion()
        }
        command.playerRefresh = events::refresh
        getCommand("ws")?.apply {
            setExecutor(command)
            tabCompleter = command
        }
        server.pluginManager.registerEvents(events, this)
        server.pluginManager.registerEvents(playerVariables, this)
        server.pluginManager.registerEvents(actions, this)
        server.pluginManager.registerEvents(gui, this)
        server.pluginManager.registerEvents(chatEditor, this)
        server.pluginManager.registerEvents(RegionSelectionListener(this, selection), this)
        registerPlaceholderExpansion()
        logger.info("WorldScript enabled with ${regionCore.all().size} regions.")
    }

    override fun onDisable() {
        placeholderExpansion?.unregister()
        placeholderExpansion = null
        if (::particles.isInitialized) particles.close()
        if (::playerVariables.isInitialized) playerVariables.saveAll()
        logger.info("WorldScript disabled.")
    }

    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name.equals("PlaceholderAPI", ignoreCase = true)) {
            registerPlaceholderExpansion()
        }
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin.name.equals("PlaceholderAPI", ignoreCase = true)) {
            placeholderExpansion?.unregister()
            placeholderExpansion = null
        }
    }

    private fun registerPlaceholderExpansion() {
        if (!server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            logger.info("PlaceholderAPI not found; WorldScript placeholders are disabled until PlaceholderAPI is enabled.")
            return
        }
        val expansion = placeholderExpansion ?: WorldScriptPlaceholderExpansion(this, regionCore, playerVariables).also {
            placeholderExpansion = it
        }
        if (expansion.isRegistered) return
        if (expansion.register()) {
            logger.info("Registered WorldScript PlaceholderAPI variables.")
        } else {
            logger.warning("Could not register WorldScript PlaceholderAPI variables. Check for another expansion using the 'worldscript' identifier.")
        }
    }

    private fun validateMaterialConfig() {
        val tool = config.getString("selection.tool", "GOLDEN_AXE") ?: "GOLDEN_AXE"
        if (MaterialResolver.find(tool, "GOLD_AXE") == null) {
            logger.warning("Invalid selection.tool '$tool'; using GOLD_AXE.")
            config.set("selection.tool", "GOLD_AXE")
        }
        val icon = config.getString("gui.event-icon", "PAPER") ?: "PAPER"
        if (MaterialResolver.find(icon, "PAPER") == null) {
            logger.warning("Invalid gui.event-icon '$icon'; using PAPER.")
            config.set("gui.event-icon", "PAPER")
        }
        saveConfig()
    }
}
