package com.eisiadev.enceladus;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DisplayManager {

    private final Plugin plugin;
    private final DisplayFactory displayFactory;
    private final HealthDisplayConfig config;

    private final Map<UUID, DisplayFactory.DisplayPair> displays = new ConcurrentHashMap<>();
    private final Map<UUID, MobDataCache> cachedData = new ConcurrentHashMap<>();
    private final Set<UUID> processingMobs = ConcurrentHashMap.newKeySet();

    private final NamespacedKey healthDisplayTag;
    private BukkitTask updateTask;
    private BukkitTask slowScanTask;
    private BukkitTask cacheCleanupTask;

    public DisplayManager(Plugin plugin, DisplayFactory displayFactory, HealthDisplayConfig config) {
        this.plugin = plugin;
        this.displayFactory = displayFactory;
        this.config = config;
        this.healthDisplayTag = new NamespacedKey(plugin, "mob_health_display");
    }

    public NamespacedKey getHealthDisplayTag() {
        return healthDisplayTag;
    }

    public void startUpdateTasks() {
        startUpdateTask();
        startSlowScanTask();
        startCacheCleanupTask();
    }

    private void startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            processingMobs.removeIf(uuid -> {
                MobDataCache cache = cachedData.get(uuid);
                return cache != null && now - cache.getCreationTime() > config.getProcessingTimeout();
            });

            List<UUID> toRemove = new ArrayList<>();

            displays.forEach((mobId, displayPair) -> {
                if (!displayPair.isValid()) {
                    toRemove.add(mobId);
                    return;
                }

                Entity entity = Bukkit.getEntity(mobId);
                if (entity == null || !entity.isValid() || entity.isDead()) {
                    displayPair.remove();
                    toRemove.add(mobId);
                    return;
                }

                if (!(entity instanceof LivingEntity mob) || !shouldDisplayHealth(mob)) {
                    displayPair.remove();
                    toRemove.add(mobId);
                    return;
                }

                MobDataCache cache = cachedData.get(mobId);
                if (cache == null || cache.needsUpdate(mob, config.getMovementThreshold())) {
                    displayFactory.updateDisplay(mob, displayPair);
                    cachedData.put(mobId, new MobDataCache(mob));
                }
            });

            toRemove.forEach(this::cleanupDisplay);

        }, 0L, config.getUpdateInterval());
    }

    private void startSlowScanTask() {
        slowScanTask = Bukkit.getScheduler().runTaskTimer(plugin, () ->
                Bukkit.getWorlds().forEach(world ->
                        world.getEntitiesByClass(Monster.class).stream()
                                .filter(mob -> mob.isValid() && !mob.isDead()
                                        && shouldDisplayHealth(mob)
                                        && !displays.containsKey(mob.getUniqueId())
                                        && !processingMobs.contains(mob.getUniqueId()))
                                .forEach(this::scheduleDisplayCreation)
                ), config.getSlowScanInterval(), config.getSlowScanInterval());
    }

    private void startCacheCleanupTask() {
        cacheCleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            displayFactory.getHealthFormatter().cleanupCache();
            displayFactory.getHealthBarFormatter().cleanupCache();
        }, 20L, 20L);
    }

    public void createDisplaysForExistingMobs() {
        Bukkit.getWorlds().forEach(world ->
                world.getEntitiesByClass(Monster.class).stream()
                        .filter(mob -> mob.isValid() && !mob.isDead()
                                && shouldDisplayHealth(mob)
                                && !displays.containsKey(mob.getUniqueId()))
                        .forEach(this::createDisplayForMob)
        );
    }

    public boolean shouldDisplayHealth(LivingEntity mob) {
        return mob.getPassengers().isEmpty()
                && !config.getExcludedTypes().contains(mob.getType());
    }

    public void scheduleDisplayCreation(LivingEntity mob) {
        UUID mobId = mob.getUniqueId();
        processingMobs.add(mobId);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!mob.isValid() || mob.isDead() || !shouldDisplayHealth(mob)) {
                processingMobs.remove(mobId);
                return;
            }

            createDisplayForMob(mob);
            processingMobs.remove(mobId);
        }, 1L);
    }

    public void scheduleDisplayCreationWithDisguiseCheck(LivingEntity mob, boolean isDisguised) {
        UUID mobId = mob.getUniqueId();
        processingMobs.add(mobId);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!mob.isValid() || mob.isDead() || !shouldDisplayHealth(mob)) {
                processingMobs.remove(mobId);
                return;
            }

            if (isDisguised) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (mob.isValid() && !mob.isDead() && shouldDisplayHealth(mob)) {
                        createDisplayForMob(mob);
                    }
                    processingMobs.remove(mobId);
                }, config.getDisguiseDelay());
            } else {
                createDisplayForMob(mob);
                processingMobs.remove(mobId);
            }
        }, 1L);
    }

    private void createDisplayForMob(LivingEntity mob) {
        DisplayFactory.DisplayPair displayPair = displayFactory.createDisplay(mob);
        displays.put(mob.getUniqueId(), displayPair);
        cachedData.put(mob.getUniqueId(), new MobDataCache(mob));
    }

    public void updateDisplayForMob(LivingEntity mob) {
        DisplayFactory.DisplayPair displayPair = displays.get(mob.getUniqueId());
        if (displayPair != null && displayPair.isValid()) {
            displayFactory.updateDisplay(mob, displayPair);
            cachedData.put(mob.getUniqueId(), new MobDataCache(mob));
        }
    }

    public void removeDisplay(UUID mobId) {
        DisplayFactory.DisplayPair displayPair = displays.remove(mobId);
        if (displayPair != null) {
            displayPair.remove();
        }
        cleanupDisplay(mobId);
    }

    private void cleanupDisplay(UUID mobId) {
        displays.remove(mobId);
        cachedData.remove(mobId);
        processingMobs.remove(mobId);
        displayFactory.invalidateHeightCache(mobId);
        displayFactory.invalidateHealthCache(mobId);
    }

    public void removeAllExistingDisplays() {
        Bukkit.getWorlds().forEach(world ->
                world.getEntitiesByClass(TextDisplay.class).stream()
                        .filter(td -> td.getPersistentDataContainer()
                                .has(healthDisplayTag, PersistentDataType.BYTE))
                        .forEach(Entity::remove)
        );
    }

    public void shutdown() {
        if (updateTask != null) updateTask.cancel();
        if (slowScanTask != null) slowScanTask.cancel();
        if (cacheCleanupTask != null) cacheCleanupTask.cancel();

        displays.values().forEach(DisplayFactory.DisplayPair::remove);

        displays.clear();
        cachedData.clear();
        processingMobs.clear();
        displayFactory.clearCaches();
    }

    public boolean containsDisplay(UUID mobId) {
        return displays.containsKey(mobId);
    }

    public boolean isProcessing(UUID mobId) {
        return processingMobs.contains(mobId);
    }
}