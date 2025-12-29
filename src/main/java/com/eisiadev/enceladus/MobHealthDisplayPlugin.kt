package com.eisiadev.enceladus

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class MobHealthDisplayPlugin : JavaPlugin() {

    private lateinit var displayManager: DisplayManager
    private lateinit var eventHandler: MobHealthEventHandler

    override fun onEnable() {
        val integrations = PluginIntegrations(
            modelEngineEnabled = Bukkit.getPluginManager().isPluginEnabled("ModelEngine"),
            libsDisguisesEnabled = Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")
        )
        val config = HealthDisplayConfig.load()
        val heightCalculator = EntityHeightCalculator(integrations, this)
        val displayFactory = DisplayFactory(config, heightCalculator)

        displayManager = DisplayManager(this, displayFactory, config)
        eventHandler = MobHealthEventHandler(displayManager, integrations, this)

        server.pluginManager.registerEvents(eventHandler, this)

        displayManager.removeAllExistingDisplays()
        displayManager.startUpdateTasks()

        Bukkit.getScheduler().runTaskLater(this, Runnable {
            displayManager.createDisplaysForExistingMobs()
        }, 20L)

        logger.info("OptimizedMobHealthDisplay enabled!")
    }

    override fun onDisable() {
        displayManager.shutdown()
        logger.info("OptimizedMobHealthDisplay disabled!")
    }
}

data class PluginIntegrations(
    val modelEngineEnabled: Boolean,
    val libsDisguisesEnabled: Boolean
)