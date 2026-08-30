package com.worldscript.foundation.module

interface WorldScriptModule {
    val id: String

    fun onLoad(context: ModuleContext) {
    }

    fun onEnable() {
    }

    fun onReload() {
    }

    fun onDisable() {
    }
}
