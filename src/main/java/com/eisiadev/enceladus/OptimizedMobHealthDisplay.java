package com.eisiadev.enceladus;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.FlagWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.SlimeWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.AgeableWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.ZombieWatcher;
import me.libraryaddict.disguise.events.DisguiseEvent;
import me.libraryaddict.disguise.events.UndisguiseEvent;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OptimizedMobHealthDisplay extends JavaPlugin implements Listener {

    private final Map<UUID, TextDisplay> displays = new ConcurrentHashMap<>();
    private final Map<UUID, CachedMobData> cachedData = new ConcurrentHashMap<>();
    private final Set<UUID> processingMobs = ConcurrentHashMap.newKeySet();

    private final Map<UUID, HeightCache> heightCache = new ConcurrentHashMap<>();

    private BukkitTask updateTask;
    private BukkitTask slowScanTask;

    private NamespacedKey HEALTH_DISPLAY_TAG;

    private static final int UPDATE_INTERVAL = 5;
    private static final int SLOW_SCAN_INTERVAL = 600;
    private static final int TELEPORT_DURATION = 5;
    private static final double HEIGHT_OFFSET = 0.3;
    private static final double MOVEMENT_THRESHOLD = 0.01;
    private static final double BABY_SCALE = 0.5;

    // 기준 값들
    private static final double BASE_HEIGHT = 1.8;
    private static final float BASE_SCALE = 1.2f;

    private static final double[] VALUES = { 1.0E28, 1.0E24, 1.0E20, 1.0E16, 1.0E12, 1.0E8, 1.0E4 };
    private static final String[] UNITS = { "양", "자", "해", "경", "조", "억", "만" };
    private static final DecimalFormat FORMATTER = new DecimalFormat("#.#");
    private static final DecimalFormat COMMA_FORMATTER = new DecimalFormat("#,###.#");

    private static final Set<EntityType> EXCLUDED_TYPES = Set.of(
            EntityType.PIG, EntityType.ALLAY, EntityType.SPIDER
    );

    private boolean modelEngineEnabled;
    private boolean libsDisguisesEnabled;

    @Override
    public void onEnable() {
        HEALTH_DISPLAY_TAG = new NamespacedKey(this, "mob_health_display");

        modelEngineEnabled = Bukkit.getPluginManager().isPluginEnabled("ModelEngine");
        libsDisguisesEnabled = Bukkit.getPluginManager().isPluginEnabled("LibsDisguises");

        removeAllExistingDisplays();
        Bukkit.getPluginManager().registerEvents(this, this);

        startUpdateTask();
        startSlowScanTask();

        Bukkit.getScheduler().runTaskLater(this, this::createDisplaysForExistingMobs, 20L);
        getLogger().info("OptimizedMobHealthDisplay enabled!");
    }

    @Override
    public void onDisable() {
        if (updateTask != null) updateTask.cancel();
        if (slowScanTask != null) slowScanTask.cancel();
        removeAllDisplays();
        getLogger().info("OptimizedMobHealthDisplay disabled!");
    }

    private void removeAllExistingDisplays() {
        Bukkit.getWorlds().forEach(world ->
                world.getEntitiesByClass(TextDisplay.class).stream()
                        .filter(td -> td.getPersistentDataContainer().has(HEALTH_DISPLAY_TAG, PersistentDataType.BYTE))
                        .forEach(Entity::remove)
        );
    }

    private void removeAllDisplays() {
        displays.values().forEach(d -> { if (d.isValid()) d.remove(); });
        displays.clear();
        cachedData.clear();
        processingMobs.clear();
        heightCache.clear();
    }

    private void createDisplaysForExistingMobs() {
        Bukkit.getWorlds().forEach(world ->
                world.getEntitiesByClass(Monster.class).stream()
                        .filter(mob -> mob.isValid() && !mob.isDead()
                                && shouldDisplayHealth(mob)
                                && !displays.containsKey(mob.getUniqueId()))
                        .forEach(this::createDisplayForMob)
        );
    }

    private boolean shouldDisplayHealth(LivingEntity mob) {
        return mob.getPassengers().isEmpty() && !EXCLUDED_TYPES.contains(mob.getType());
    }

    private void startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            processingMobs.removeIf(uuid -> {
                CachedMobData cache = cachedData.get(uuid);
                return cache != null && now - cache.creationTime > 5000;
            });

            List<UUID> toRemove = new ArrayList<>();

            displays.forEach((mobId, display) -> {
                if (!display.isValid()) {
                    toRemove.add(mobId);
                    return;
                }

                Entity entity = Bukkit.getEntity(mobId);
                if (entity == null || !entity.isValid() || entity.isDead()) {
                    display.remove();
                    toRemove.add(mobId);
                    return;
                }

                if (!(entity instanceof LivingEntity mob) || !shouldDisplayHealth(mob)) {
                    display.remove();
                    toRemove.add(mobId);
                    return;
                }

                CachedMobData cache = cachedData.get(mobId);
                if (cache == null || cache.needsUpdate(mob)) {
                    updateDisplay(mob, display);
                }
            });

            toRemove.forEach(uuid -> {
                displays.remove(uuid);
                cachedData.remove(uuid);
                processingMobs.remove(uuid);
                heightCache.remove(uuid);
            });

        }, 0L, UPDATE_INTERVAL);
    }

    private void startSlowScanTask() {
        slowScanTask = Bukkit.getScheduler().runTaskTimer(this, () -> Bukkit.getWorlds().forEach(world ->
                world.getEntitiesByClass(Monster.class).stream()
                        .filter(mob -> mob.isValid() && !mob.isDead()
                                && shouldDisplayHealth(mob)
                                && !displays.containsKey(mob.getUniqueId())
                                && !processingMobs.contains(mob.getUniqueId()))
                        .forEach(this::scheduleDisplayCreation)
        ), SLOW_SCAN_INTERVAL, SLOW_SCAN_INTERVAL);
    }

    private void updateDisplay(LivingEntity mob, TextDisplay display) {
        Location targetLoc = calculateDisplayLocation(mob);

        Location currentLoc = display.getLocation();
        double distSq = currentLoc.distanceSquared(targetLoc);

        if (distSq > MOVEMENT_THRESHOLD * MOVEMENT_THRESHOLD) {
            display.teleport(targetLoc);
        }

        display.text(createHealthComponent(mob));

        // 크기 업데이트
        float scale = calculateScale(mob);
        Transformation transformation = display.getTransformation();
        transformation.getScale().set(scale, scale, scale);
        display.setTransformation(transformation);

        cachedData.put(mob.getUniqueId(), new CachedMobData(mob));
    }

    private Location calculateDisplayLocation(LivingEntity mob) {
        Entity topEntity = getTopMostEntity(mob);
        Location loc = topEntity.getLocation();

        HeightCache cache = heightCache.get(mob.getUniqueId());
        double height;

        if (cache != null && !cache.isDirty()) {
            height = cache.height;
        } else {
            height = calculateStackedHeight(mob);
            heightCache.put(mob.getUniqueId(), new HeightCache(height));
        }

        loc.setY(loc.getY() + height + HEIGHT_OFFSET);
        return loc;
    }

    private Entity getTopMostEntity(Entity entity) {
        Entity current = entity;
        while (!current.getPassengers().isEmpty()) {
            current = current.getPassengers().get(0);
        }
        return current;
    }

    private double calculateStackedHeight(Entity entity) {
        double height = 0.0;
        Entity current = entity;

        while (current != null) {
            height += getEntityHeight(current);
            List<Entity> passengers = current.getPassengers();
            current = passengers.isEmpty() ? null : passengers.get(0);
        }

        return height;
    }

    private double getEntityHeight(Entity entity) {
        double height = entity.getHeight();

        if (libsDisguisesEnabled) {
            Double cachedDisguiseHeight = getDisguiseHeight(entity);
            if (cachedDisguiseHeight != null) {
                height = cachedDisguiseHeight;
            }
        }

        if (modelEngineEnabled) {
            double modelHeight = getModelHeight(entity);
            if (modelHeight > 0) {
                height = Math.max(height, modelHeight);
            }
        }

        return height;
    }

    // 몹의 크기에 비례한 스케일 계산
    private float calculateScale(LivingEntity mob) {
        double entityHeight = getEntityHeight(mob);
        double ratio = entityHeight / BASE_HEIGHT;

        // 기준 스케일에 비율을 곱함
        float scale = (float) (BASE_SCALE * ratio);

        // 너무 작거나 큰 값 제한 (선택사항)
        scale = Math.max(1.2f, Math.min(scale, 15.0f));

        return scale;
    }

    private Double getDisguiseHeight(Entity entity) {
        if (!DisguiseAPI.isDisguised(entity)) return null;

        try {
            Disguise disguise = DisguiseAPI.getDisguise(entity);
            if (disguise == null) return null;

            FlagWatcher watcher = disguise.getWatcher();

            if (watcher instanceof SlimeWatcher slimeWatcher) {
                return 0.51 * slimeWatcher.getSize();
            }

            EntityType disguisedType = disguise.getType().getEntityType();
            if (disguisedType == null) return null;

            if (isDisplayEntityType(disguisedType)) {
                return getDisplayEntityHeight(watcher);
            }

            double baseHeight = getDefaultEntityHeight(disguisedType);

            if (isBaby(watcher)) {
                baseHeight *= BABY_SCALE;
            }

            return baseHeight;

        } catch (Exception e) {
            getLogger().warning("Disguise height error for " + entity.getType() + ": " + e.getMessage());
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
            getLogger().fine("Could not get display entity scale: " + e.getMessage());
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
            getLogger().fine("Could not check baby status: " + e.getMessage());
        }

        return false;
    }

    private double getModelHeight(Entity entity) {
        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
        if (modeled == null || modeled.getModels() == null) return 0;

        double maxHeight = 0;
        Location entityLoc = entity.getLocation();

        for (ActiveModel model : modeled.getModels().values()) {
            if (model != null) {
                double modelHeight = model.getModeledEntity().getBase().getLocation().getY()
                        + model.getBlueprint().getMainHitbox().getHeight()
                        - entityLoc.getY();
                maxHeight = Math.max(maxHeight, modelHeight);
            }
        }

        return maxHeight;
    }

    private double getDefaultEntityHeight(EntityType type) {
        return switch (type) {
            case ZOMBIE, SKELETON, HUSK, STRAY, DROWNED,
                 VILLAGER, ZOMBIE_VILLAGER, PIGLIN, PIGLIN_BRUTE,
                 HOGLIN, ZOGLIN, WITCH -> 1.95;

            case BLOCK_DISPLAY, ITEM_DISPLAY, TEXT_DISPLAY -> 2.0;

            case ENDERMAN, WARDEN -> 2.9;
            case IRON_GOLEM -> 2.7;
            case WITHER_SKELETON -> 2.5;
            case WITHER -> 3.5;
            case ENDER_DRAGON -> 8.0;
            case RAVAGER -> 2.2;
            case STRIDER, CREEPER -> 1.7;

            case SPIDER, BAT -> 0.9;
            case CAVE_SPIDER, PHANTOM -> 0.5;
            case CHICKEN -> 0.7;
            case SILVERFISH, ENDERMITE -> 0.3;
            case VEX -> 0.8;
            case SHULKER, FOX, PANDA, PARROT, TURTLE, COD, SALMON, PUFFERFISH, TROPICAL_FISH, DOLPHIN, ALLAY, FROG,
                 TADPOLE, FALLING_BLOCK -> 1.0;
            case GOAT -> 1.3;

            case PIG, SHEEP, COW, OCELOT, CAT, DONKEY, MULE, LLAMA, TRADER_LLAMA, HORSE, SKELETON_HORSE, ZOMBIE_HORSE, CAMEL, SNIFFER, WOLF, POLAR_BEAR -> 1.6;

            case SLIME, MAGMA_CUBE -> 2.04;

            case GIANT -> 12.0;
            case GHAST -> 4.0;

            default -> 1.8;
        };
    }

    private Component createHealthComponent(LivingEntity mob) {
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Monster mob)) return;
        if (!shouldDisplayHealth(mob)) return;

        UUID mobId = mob.getUniqueId();
        if (processingMobs.contains(mobId) || displays.containsKey(mobId)) return;

        scheduleDisplayCreation(mob);
    }

    private void scheduleDisplayCreation(LivingEntity mob) {
        UUID mobId = mob.getUniqueId();
        processingMobs.add(mobId);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!mob.isValid() || mob.isDead() || !shouldDisplayHealth(mob)) {
                processingMobs.remove(mobId);
                return;
            }

            if (libsDisguisesEnabled && DisguiseAPI.isDisguised(mob)) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (mob.isValid() && !mob.isDead() && shouldDisplayHealth(mob)) {
                        createDisplayForMob(mob);
                    }
                    processingMobs.remove(mobId);
                }, 5L);
            } else {
                createDisplayForMob(mob);
                processingMobs.remove(mobId);
            }
        }, 1L);
    }

    private void createDisplayForMob(LivingEntity mob) {
        Location loc = calculateDisplayLocation(mob);
        float scale = calculateScale(mob);

        TextDisplay display = mob.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.getPersistentDataContainer().set(HEALTH_DISPLAY_TAG, PersistentDataType.BYTE, (byte) 1);
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setShadowed(true);
            d.setTeleportDuration(TELEPORT_DURATION);
            d.setSeeThrough(false);
            d.text(createHealthComponent(mob));
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));

            Transformation transformation = d.getTransformation();
            transformation.getScale().set(scale, scale, scale);
            d.setTransformation(transformation);
        });

        displays.put(mob.getUniqueId(), display);
        cachedData.put(mob.getUniqueId(), new CachedMobData(mob));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        UUID mobId = event.getEntity().getUniqueId();
        TextDisplay display = displays.remove(mobId);
        if (display != null) display.remove();

        cachedData.remove(mobId);
        processingMobs.remove(mobId);
        heightCache.remove(mobId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.getType() == EntityType.TEXT_DISPLAY) {
                TextDisplay td = (TextDisplay) entity;
                if (td.getPersistentDataContainer().has(HEALTH_DISPLAY_TAG, PersistentDataType.BYTE)) {
                    td.remove();

                    displays.values().removeIf(display -> display.getUniqueId().equals(td.getUniqueId()));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.getType() == EntityType.TEXT_DISPLAY) {
                TextDisplay td = (TextDisplay) entity;
                if (td.getPersistentDataContainer().has(HEALTH_DISPLAY_TAG, PersistentDataType.BYTE)) {
                    td.remove();
                }
            }
        }
    }

    private static class CachedMobData {
        final double health;
        final double maxHealth;
        final double x, y, z;
        final long creationTime;

        CachedMobData(LivingEntity mob) {
            this.health = mob.getHealth();
            this.maxHealth = mob.getMaxHealth();
            Location loc = mob.getLocation();
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.creationTime = System.currentTimeMillis();
        }

        boolean needsUpdate(LivingEntity mob) {
            Location loc = mob.getLocation();
            double dx = loc.getX() - x;
            double dy = loc.getY() - y;
            double dz = loc.getZ() - z;
            double distSq = dx*dx + dy*dy + dz*dz;

            return mob.getHealth() != health
                    || mob.getMaxHealth() != maxHealth
                    || distSq > MOVEMENT_THRESHOLD * MOVEMENT_THRESHOLD;
        }
    }

    private static class HeightCache {
        final double height;
        private boolean dirty = false;

        HeightCache(double height) {
            this.height = height;
        }

        boolean isDirty() {
            return dirty;
        }

        void markDirty() {
            this.dirty = true;
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
        if (!(entity instanceof LivingEntity mob)) return;
        UUID uuid = mob.getUniqueId();

        if (displays.containsKey(uuid)) {
            heightCache.remove(uuid);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                TextDisplay display = displays.get(uuid);
                if (mob.isValid() && !mob.isDead() && display != null && display.isValid()) {
                    updateDisplay(mob, display);
                }
            }, 1L);
        }
    }
}