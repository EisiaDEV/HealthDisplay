package com.eisiadev.enceladus;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.events.DisguiseEvent;
import me.libraryaddict.disguise.events.UndisguiseEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class MobHealthEventHandler implements Listener {

    private final DisplayManager displayManager;
    private final PluginIntegrations integrations;
    private final Plugin plugin;

    public MobHealthEventHandler(
            DisplayManager displayManager,
            PluginIntegrations integrations,
            Plugin plugin
    ) {
        this.displayManager = displayManager;
        this.integrations = integrations;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Monster mob)) {
            return;
        }

        if (!displayManager.shouldDisplayHealth(mob)) {
            return;
        }

        UUID mobId = mob.getUniqueId();
        if (displayManager.isProcessing(mobId) || displayManager.containsDisplay(mobId)) {
            return;
        }

        boolean isDisguised = integrations.getLibsDisguisesEnabled()
                && DisguiseAPI.isDisguised(mob);

        displayManager.scheduleDisplayCreationWithDisguiseCheck(mob, isDisguised);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        displayManager.removeDisplay(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.getType() == EntityType.TEXT_DISPLAY) {
                TextDisplay td = (TextDisplay) entity;
                if (td.getPersistentDataContainer().has(
                        displayManager.getHealthDisplayTag(),
                        PersistentDataType.BYTE
                )) {
                    td.remove();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.getType() == EntityType.TEXT_DISPLAY) {
                TextDisplay td = (TextDisplay) entity;
                if (td.getPersistentDataContainer().has(
                        displayManager.getHealthDisplayTag(),
                        PersistentDataType.BYTE
                )) {
                    td.remove();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDisguise(DisguiseEvent event) {
        handleDisguiseChange(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUndisguise(UndisguiseEvent event) {
        handleDisguiseChange(event.getEntity());
    }

    private void handleDisguiseChange(Entity entity) {
        if (!(entity instanceof LivingEntity mob)) {
            return;
        }

        UUID uuid = mob.getUniqueId();

        if (displayManager.containsDisplay(uuid)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (mob.isValid() && !mob.isDead()) {
                    displayManager.updateDisplayForMob(mob);
                }
            }, 1L);
        }
    }
}