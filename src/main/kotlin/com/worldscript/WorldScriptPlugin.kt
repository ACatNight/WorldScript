package com.worldscript

import org.bukkit.plugin.java.JavaPlugin
import com.worldscript.command.WsCommand
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l1.region_events.RegionEventServiceImpl
import com.worldscript.modules.l1.region_core.SelectionService
import com.worldscript.modules.l1.region_events.RegionSelectionListener
import com.worldscript.modules.l2.script_actions.ScriptActionServiceImpl
import com.worldscript.modules.l2.admin_gui.RegionGuiService
import com.worldscript.modules.l2.rpg.ConditionEvaluator
import com.worldscript.modules.l2.rpg.RewardService
import com.worldscript.modules.l2.rpg.PlayerVariableService
import com.worldscript.integration.placeholder.WorldScriptPlaceholderExpansion
import com.worldscript.integration.taboolib.TabooLibBridge
import com.worldscript.foundation.api.PlayerRegionProgressService
import org.bukkit.Material

class WorldScriptPlugin : JavaPlugin() {
    lateinit var lang: com.worldscript.foundation.Lang
        private set
    lateinit var regionCore: RegionCoreServiceImpl
        private set
    private lateinit var playerVariables: PlayerVariableService
    lateinit var playerProgress: PlayerRegionProgressService
        private set
    lateinit var taboolib: TabooLibBridge
        private set

    override fun onEnable() {
        saveDefaultConfig()
        if (!config.isString("language")) {
            config.set("language", "en_US")
            saveConfig()
        }
        saveResource("lang/en_US.yml", false)
        saveResource("lang/zh_CN.yml", false)
        taboolib = TabooLibBridge(this, server.pluginManager)
        taboolib.report()
        validateMaterialConfig()
        lang = com.worldscript.foundation.Lang(this)
        regionCore = RegionCoreServiceImpl(this)
        regionCore.load()
        playerVariables = PlayerVariableService(this)
        playerProgress = playerVariables
        val rewards = RewardService(this, regionCore, playerVariables)
        val conditions = ConditionEvaluator(regionCore, playerVariables)
        val events = RegionEventServiceImpl(this, regionCore, playerVariables)
        val actions = ScriptActionServiceImpl(this, regionCore, playerVariables, conditions, rewards)
        val gui = RegionGuiService(this, regionCore)
        val selection = SelectionService(this)
        val command = WsCommand(this, regionCore, selection, playerVariables)
        command.guiOpener = gui::openList
        getCommand("ws")?.apply {
            setExecutor(command)
            tabCompleter = command
        }
        server.pluginManager.registerEvents(events, this)
        server.pluginManager.registerEvents(playerVariables, this)
        server.pluginManager.registerEvents(actions, this)
        server.pluginManager.registerEvents(gui, this)
        server.pluginManager.registerEvents(RegionSelectionListener(this, selection), this)
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            WorldScriptPlaceholderExpansion(this, regionCore, playerVariables).register()
            logger.info("Registered WorldScript PlaceholderAPI variables.")
        }
        logger.info("WorldScript enabled with ${regionCore.all().size} regions.")
    }

    override fun onDisable() {
        if (::playerVariables.isInitialized) playerVariables.saveAll()
        logger.info("WorldScript disabled.")
    }

    private fun validateMaterialConfig() {
        val tool = config.getString("selection.tool", "GOLDEN_AXE") ?: "GOLDEN_AXE"
        if (Material.matchMaterial(tool) == null) {
            logger.warning("Invalid selection.tool '$tool'; using GOLDEN_AXE.")
            config.set("selection.tool", "GOLDEN_AXE")
        }
        val icon = config.getString("gui.event-icon", "PAPER") ?: "PAPER"
        if (Material.matchMaterial(icon) == null) {
            logger.warning("Invalid gui.event-icon '$icon'; using PAPER.")
            config.set("gui.event-icon", "PAPER")
        }
        saveConfig()
    }
}
