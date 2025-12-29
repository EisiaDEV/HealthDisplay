package com.eisiadev.enceladus;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class EntityHeightCalculator {

    private final PluginIntegrations integrations;
    private final DisguiseHeightCalculator disguiseCalculator;
    private final ModelEngineHeightCalculator modelCalculator;
    private final NamespacedKey healthDisplayTag;

    public EntityHeightCalculator(PluginIntegrations integrations, Plugin plugin) {
        this.integrations = integrations;
        this.healthDisplayTag = new NamespacedKey(plugin, "mob_health_display");

        this.disguiseCalculator = integrations.getLibsDisguisesEnabled()
                ? new DisguiseHeightCalculator(plugin)
                : null;

        this.modelCalculator = integrations.getModelEngineEnabled()
                ? new ModelEngineHeightCalculator()
                : null;
    }

    public NamespacedKey getHealthDisplayTag() {
        return healthDisplayTag;
    }

    public double calculateTotalHeight(Entity entity) {
        double height = 0.0;
        Entity current = entity;

        while (current != null) {
            height += getEntityHeight(current);
            List<Entity> passengers = current.getPassengers();
            current = passengers.isEmpty() ? null : passengers.get(0);
        }

        return height;
    }

    public double getEntityHeight(Entity entity) {
        double height = entity.getHeight();

        if (integrations.getLibsDisguisesEnabled() && disguiseCalculator != null) {
            Double disguiseHeight = disguiseCalculator.getDisguiseHeight(entity);
            if (disguiseHeight != null) {
                height = disguiseHeight;
            }
        }

        if (integrations.getModelEngineEnabled() && modelCalculator != null) {
            double modelHeight = modelCalculator.getModelHeight(entity);
            if (modelHeight > 0) {
                height = Math.max(height, modelHeight);
            }
        }

        return height;
    }
}