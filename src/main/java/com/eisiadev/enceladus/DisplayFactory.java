package com.eisiadev.enceladus;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DisplayFactory {

    private final HealthDisplayConfig config;
    private final EntityHeightCalculator heightCalculator;
    private final HealthFormatter healthFormatter;
    private final Map<UUID, HeightCache> heightCache = new ConcurrentHashMap<>();

    public DisplayFactory(HealthDisplayConfig config, EntityHeightCalculator heightCalculator) {
        this.config = config;
        this.heightCalculator = heightCalculator;
        this.healthFormatter = new HealthFormatter();
    }

    public TextDisplay createDisplay(LivingEntity mob) {
        Location loc = calculateDisplayLocation(mob);
        float scale = calculateScale(mob);

        return mob.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.getPersistentDataContainer().set(
                    heightCalculator.getHealthDisplayTag(),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setShadowed(true);
            d.setTeleportDuration(config.getTeleportDuration());
            d.setSeeThrough(false);
            d.text(healthFormatter.createHealthComponent(mob));
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));

            Transformation transformation = d.getTransformation();
            transformation.getScale().set(scale, scale, scale);
            d.setTransformation(transformation);
        });
    }

    public void updateDisplay(LivingEntity mob, TextDisplay display) {
        Location targetLoc = calculateDisplayLocation(mob);
        Location currentLoc = display.getLocation();
        double distSq = currentLoc.distanceSquared(targetLoc);

        if (distSq > config.getMovementThreshold() * config.getMovementThreshold()) {
            display.teleport(targetLoc);
        }

        display.text(healthFormatter.createHealthComponent(mob));

        float scale = calculateScale(mob);
        Transformation transformation = display.getTransformation();
        transformation.getScale().set(scale, scale, scale);
        display.setTransformation(transformation);
    }

    private Location calculateDisplayLocation(LivingEntity mob) {
        Location loc = mob.getLocation();

        HeightCache cache = heightCache.get(mob.getUniqueId());
        double height;

        if (cache != null && !cache.isDirty()) {
            height = cache.height();
        } else {
            height = heightCalculator.calculateTotalHeight(mob);
            heightCache.put(mob.getUniqueId(), new HeightCache(height));
        }

        loc.setY(loc.getY() + height + config.getHeightOffset());
        return loc;
    }

    private float calculateScale(LivingEntity mob) {
        double entityHeight = heightCalculator.getEntityHeight(mob);
        double ratio = entityHeight / config.getBaseHeight();
        float scale = (float) (config.getBaseScale() * ratio);

        return Math.max(config.getMinScale(), Math.min(scale, config.getMaxScale()));
    }

    public void invalidateHeightCache(UUID mobId) {
        heightCache.remove(mobId);
    }

    public void invalidateHealthCache(UUID mobId) {
        healthFormatter.invalidateCache(mobId);
    }

    public void clearCaches() {
        heightCache.clear();
        healthFormatter.clearCache();
    }

    public HealthFormatter getHealthFormatter() {
        return healthFormatter;
    }

    private record HeightCache(double height) {
        boolean isDirty() {
            return false;
        }
    }
}