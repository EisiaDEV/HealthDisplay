package com.eisiadev.enceladus

import org.bukkit.entity.EntityType

data class HealthDisplayConfig(
    val updateInterval: Int = 5,
    val slowScanInterval: Int = 600,
    val teleportDuration: Int = 5,
    val heightOffset: Double = 0.3,
    val movementThreshold: Double = 0.01,
    val babyScale: Double = 0.5,
    val baseHeight: Double = 1.8,
    val baseScale: Float = 1.0f,
    val minScale: Float = 1.0f,
    val maxScale: Float = 15.0f,
    val processingTimeout: Long = 5000,
    val disguiseDelay: Long = 5L,
    val excludedTypes: Set<EntityType> = setOf(
        EntityType.PIG,
        EntityType.ALLAY,
        EntityType.SPIDER
    )
) {
    companion object {
        fun load(): HealthDisplayConfig {
            return HealthDisplayConfig()
        }
    }
}