package com.eisiadev.enceladus;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.FlagWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.SlimeWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.PhantomWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.AgeableWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.ZombieWatcher;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

public class DisguiseHeightCalculator {

    private static final double BABY_SCALE = 0.5;
    private final Plugin plugin;

    public DisguiseHeightCalculator(Plugin plugin) {
        this.plugin = plugin;
    }

    public Double getDisguiseHeight(Entity entity) {
        if (!DisguiseAPI.isDisguised(entity)) {
            return null;
        }

        try {
            Disguise disguise = DisguiseAPI.getDisguise(entity);
            if (disguise == null) return null;

            FlagWatcher watcher = disguise.getWatcher();

            // Slime 특수 처리
            if (watcher instanceof SlimeWatcher slimeWatcher) {
                return 0.51 * slimeWatcher.getSize();
            }

            // Phantom 특수 처리
            if (watcher instanceof PhantomWatcher phantomWatcher) {
                return 0.2 * phantomWatcher.getSize();
            }

            EntityType disguisedType = disguise.getType().getEntityType();
            if (disguisedType == null) return null;

            // Display Entity 특수 처리
            if (isDisplayEntityType(disguisedType)) {
                return getDisplayEntityHeight(watcher);
            }

            double baseHeight = DefaultEntityHeights.getHeight(disguisedType);

            // Baby 처리
            if (isBaby(watcher)) {
                baseHeight *= BABY_SCALE;
            }

            return baseHeight;

        } catch (Exception e) {
            plugin.getLogger().warning("Disguise height error for " +
                    entity.getType() + ": " + e.getMessage());
            return null;
        }
    }

    private boolean isDisplayEntityType(EntityType type) {
        return type == EntityType.BLOCK_DISPLAY ||
                type == EntityType.ITEM_DISPLAY ||
                type == EntityType.TEXT_DISPLAY;
    }

    private Double getDisplayEntityHeight(FlagWatcher watcher) {
        try {
            Transformation transformation = (Transformation) watcher.getClass()
                    .getMethod("getTransformation")
                    .invoke(watcher);

            if (transformation != null) {
                Vector3f scale = transformation.getScale();
                return 0.5 * scale.y();
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Could not get display entity scale: " + e.getMessage());
        }

        return 0.5;
    }

    private boolean isBaby(FlagWatcher watcher) {
        try {
            if (watcher instanceof AgeableWatcher ageableWatcher) {
                return ageableWatcher.isBaby();
            }

            if (watcher instanceof ZombieWatcher zombieWatcher) {
                return zombieWatcher.isBaby();
            }

            try {
                Object isBaby = watcher.getClass().getMethod("isBaby").invoke(watcher);
                if (isBaby instanceof Boolean) {
                    return (Boolean) isBaby;
                }
            } catch (NoSuchMethodException ignored) {
            }

        } catch (Exception e) {
            plugin.getLogger().fine("Could not check baby status: " + e.getMessage());
        }

        return false;
    }
}