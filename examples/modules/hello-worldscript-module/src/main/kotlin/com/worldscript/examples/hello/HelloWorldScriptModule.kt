package com.worldscript.examples.hello

import com.worldscript.foundation.module.ModuleContext
import com.worldscript.foundation.module.WorldScriptModule
import org.bukkit.ChatColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class HelloWorldScriptModule : WorldScriptModule, Listener {
    override val id: String = "hello"
    private lateinit var context: ModuleContext
    private var joinMessage: String = "&aHello from a WorldScript module."
    private var joinMessageEnabled: Boolean = true

    override fun onLoad(context: ModuleContext) {
        this.context = context
        loadSettings()
        context.registerListener(this)
    }

    override fun onEnable() {
        context.logger.info("WorldScript hello module enabled.")
    }

    override fun onReload() {
        loadSettings()
    }

    override fun onDisable() {
        context.logger.info("WorldScript hello module disabled.")
    }

    private fun loadSettings() {
        val config = context.config()
        config.addDefault("join-message.enabled", true)
        config.addDefault("join-message.text", joinMessage)
        config.options().copyDefaults(true)
        context.saveConfig(config)
        joinMessageEnabled = config.getBoolean("join-message.enabled", true)
        joinMessage = config.getString("join-message.text", joinMessage) ?: joinMessage
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!joinMessageEnabled) return
        event.player.sendMessage(ChatColor.translateAlternateColorCodes('&', joinMessage))
    }
}
