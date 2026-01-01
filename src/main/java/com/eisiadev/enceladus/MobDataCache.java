package com.eisiadev.enceladus;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public class MobDataCache {

    private final double health;
    private final double x, y, z;
    private final long creationTime;

    public MobDataCache(LivingEntity mob) {
        this.health = mob.getHealth();
        Location loc = mob.getLocation();
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.creationTime = System.currentTimeMillis();
    }

    public boolean needsUpdate(LivingEntity mob, double movementThreshold) {
        if (mob.getHealth() != health) {
            return true;
        }

        Location loc = mob.getLocation();
        double dx = loc.getX() - x;
        double dy = loc.getY() - y;
        double dz = loc.getZ() - z;
        double distSq = dx*dx + dy*dy + dz*dz;

        return distSq > movementThreshold * movementThreshold;
    }

    public long getCreationTime() {
        return creationTime;
    }
}