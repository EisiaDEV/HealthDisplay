package com.eisiadev.enceladus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.LivingEntity;

import java.text.DecimalFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HealthFormatter {

    private static final double[] VALUES = {
            1.0E21, 1.0E18, 1.0E15, 1.0E12, 1.0E9, 1.0E6, 1.0E3
    };

    private static final String[] UNITS = {
            "\uE046", "\uE045", "\uE044", "\uE043", "\uE042", "\uE041", "\uE040"
    };

    private static final DecimalFormat FORMATTER = new DecimalFormat("#.#");
    private static final DecimalFormat COMMA_FORMATTER = new DecimalFormat("#,###.#");

    private final ConcurrentHashMap<UUID, ComponentCache> nameCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ComponentCache> healthCache = new ConcurrentHashMap<>();

    private static class ComponentCache {
        final Component component;
        final double health;
        final Component customName;
        final long timestamp;

        ComponentCache(Component component, double health, Component customName) {
            this.component = component;
            this.health = health;
            this.customName = customName;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid(double currentHealth, Component currentCustomName, long now) {
            return (now - timestamp < 100) &&
                    Math.abs(health - currentHealth) < 0.01 &&
                    Objects.equals(customName, currentCustomName);
        }
    }

    public Component createNameComponent(LivingEntity mob) {
        UUID mobId = mob.getUniqueId();
        long now = System.currentTimeMillis();
        Component customName = mob.customName();

        ComponentCache cached = nameCache.get(mobId);
        if (cached != null && cached.isValid(0, customName, now)) {
            return cached.component;
        }

        Component nameComponent = customName != null ?
                customName : Component.text(mob.getType().name(), NamedTextColor.WHITE);

        Component component = Objects.requireNonNull(nameComponent)
                .decoration(TextDecoration.ITALIC, false);

        nameCache.put(mobId, new ComponentCache(component, 0, customName));
        return component;
    }

    public Component createHealthComponent(LivingEntity mob) {
        UUID mobId = mob.getUniqueId();
        double health = mob.getHealth();
        long now = System.currentTimeMillis();

        ComponentCache cached = healthCache.get(mobId);
        if (cached != null && cached.isValid(health, null, now)) {
            return cached.component;
        }

        Component component = Component.text(formatHealth(health), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);

        healthCache.put(mobId, new ComponentCache(component, health, null));
        return component;
    }

    private String formatHealth(double value) {
        if (value < 1.0E4) {
            return value >= 1000 ? COMMA_FORMATTER.format(value) : FORMATTER.format(value);
        }

        for (int i = 0; i < VALUES.length; i++) {
            if (value >= VALUES[i]) {
                double converted = value / VALUES[i];
                String formatted = converted >= 1000 ?
                        COMMA_FORMATTER.format(converted) : FORMATTER.format(converted);
                return formatted + UNITS[i];
            }
        }

        return FORMATTER.format(value / VALUES[0]) + UNITS[0];
    }

    public void cleanupCache() {
        long now = System.currentTimeMillis();
        nameCache.entrySet().removeIf(entry ->
                now - entry.getValue().timestamp > 1000
        );
        healthCache.entrySet().removeIf(entry ->
                now - entry.getValue().timestamp > 1000
        );
    }

    public void invalidateCache(UUID mobId) {
        nameCache.remove(mobId);
        healthCache.remove(mobId);
    }

    public void clearCache() {
        nameCache.clear();
        healthCache.clear();
    }
}