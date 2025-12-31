package com.eisiadev.enceladus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.LivingEntity;

import java.text.DecimalFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HealthFormatter {

    private static final double[] VALUES = {
            1.0E28, 1.0E24, 1.0E20, 1.0E16, 1.0E12, 1.0E8, 1.0E4
    };

    private static final String[] UNITS = {
            "양", "자", "해", "경", "조", "억", "만"
    };

    private static final DecimalFormat FORMATTER = new DecimalFormat("#.#");
    private static final DecimalFormat COMMA_FORMATTER = new DecimalFormat("#,###.#");

    private final ConcurrentHashMap<UUID, ComponentCache> componentCache = new ConcurrentHashMap<>();

    private static class ComponentCache {
        final Component component;
        final double health;
        final double maxHealth;
        final Component customName;
        final long timestamp;

        ComponentCache(Component component, double health, double maxHealth, Component customName) {
            this.component = component;
            this.health = health;
            this.maxHealth = maxHealth;
            this.customName = customName;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid(double currentHealth, double currentMaxHealth, Component currentCustomName, long now) {
            return (now - timestamp < 100) &&
                    Math.abs(health - currentHealth) < 0.01 &&
                    Math.abs(maxHealth - currentMaxHealth) < 0.01 &&
                    Objects.equals(customName, currentCustomName);
        }
    }

    public Component createHealthComponent(LivingEntity mob) {
        UUID mobId = mob.getUniqueId();
        double health = mob.getHealth();
        double maxHealth = mob.getMaxHealth();
        long now = System.currentTimeMillis();

        Component customName = mob.customName();

        ComponentCache cached = componentCache.get(mobId);
        if (cached != null && cached.isValid(health, maxHealth, customName, now)) {
            return cached.component;
        }

        Component component = buildHealthComponent(health, maxHealth, customName, mob);
        componentCache.put(mobId, new ComponentCache(component, health, maxHealth, customName));

        return component;
    }

    private Component buildHealthComponent(double health, double maxHealth, Component customName, LivingEntity mob) {
        double ratio = health / maxHealth;

        TextColor healthColor = ratio > 0.6667 ? NamedTextColor.GREEN :
                ratio > 0.3333 ? NamedTextColor.YELLOW : NamedTextColor.RED;

        Component nameComponent = customName != null ?
                customName : Component.text(mob.getType().name(), NamedTextColor.WHITE);

        return Objects.requireNonNull(nameComponent)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.newline())
                .append(Component.text(formatHealth(health), healthColor))
                .append(Component.text(" | ", NamedTextColor.WHITE))
                .append(Component.text(formatHealth(maxHealth), NamedTextColor.WHITE));
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
        componentCache.entrySet().removeIf(entry ->
                now - entry.getValue().timestamp > 1000
        );
    }

    public void invalidateCache(UUID mobId) {
        componentCache.remove(mobId);
    }

    public void clearCache() {
        componentCache.clear();
    }
}