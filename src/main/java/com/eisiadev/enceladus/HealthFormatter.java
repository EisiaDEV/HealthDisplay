package com.eisiadev.enceladus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.LivingEntity;

import java.text.DecimalFormat;
import java.util.Objects;

public class HealthFormatter {

    private static final double[] VALUES = {
            1.0E28, 1.0E24, 1.0E20, 1.0E16, 1.0E12, 1.0E8, 1.0E4
    };

    private static final String[] UNITS = {
            "양", "자", "해", "경", "조", "억", "만"
    };

    private static final DecimalFormat FORMATTER = new DecimalFormat("#.#");
    private static final DecimalFormat COMMA_FORMATTER = new DecimalFormat("#,###.#");

    public Component createHealthComponent(LivingEntity mob) {
        double health = mob.getHealth();
        double maxHealth = mob.getMaxHealth();
        double ratio = health / maxHealth;

        TextColor healthColor = ratio > 0.6667 ? NamedTextColor.GREEN :
                ratio > 0.3333 ? NamedTextColor.YELLOW :
                        NamedTextColor.RED;

        Component nameComponent = mob.customName() != null ?
                mob.customName() : Component.text(mob.getType().name(), NamedTextColor.WHITE);

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
}