package com.eisiadev.enceladus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.LivingEntity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HealthBarFormatter {

    private static final char FILLED_START = '\uE030';
    private static final char FILLED_MIDDLE = '\uE031';
    private static final char FILLED_END = '\uE032';
    private static final char EMPTY_START = '\uE033';
    private static final char EMPTY_MIDDLE = '\uE034';
    private static final char EMPTY_END = '\uE035';
    private static final char SPACING = '\uE036';
    private static final int TOTAL_SEGMENTS = 20; // 시작(1) + 중간(18) + 끝(1)

    private final ConcurrentHashMap<UUID, BarCache> barCache = new ConcurrentHashMap<>();

    private static class BarCache {
        final Component component;
        final double health;
        final double maxHealth;
        final long timestamp;

        BarCache(Component component, double health, double maxHealth) {
            this.component = component;
            this.health = health;
            this.maxHealth = maxHealth;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid(double currentHealth, double currentMaxHealth, long now) {
            return (now - timestamp < 100) &&
                    Math.abs(health - currentHealth) < 0.01 &&
                    Math.abs(maxHealth - currentMaxHealth) < 0.01;
        }
    }

    public Component createHealthBarComponent(LivingEntity mob) {
        UUID mobId = mob.getUniqueId();
        double health = mob.getHealth();
        double maxHealth = mob.getMaxHealth();
        long now = System.currentTimeMillis();

        BarCache cached = barCache.get(mobId);
        if (cached != null && cached.isValid(health, maxHealth, now)) {
            return cached.component;
        }

        Component component = buildHealthBarComponent(health, maxHealth);
        barCache.put(mobId, new BarCache(component, health, maxHealth));

        return component;
    }

    private Component buildHealthBarComponent(double health, double maxHealth) {
        double ratio = health / maxHealth;
        String healthBar = buildHealthBar(ratio);

        return Component.text(healthBar, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * 체력 비율에 따라 체력바 문자열을 생성합니다.
     * 총 20개 세그먼트: 시작(1) + 중간(18) + 끝(1)
     * @param ratio 체력 비율 (0.0 ~ 1.0)
     * @return 체력바 문자열
     */
    private String buildHealthBar(double ratio) {
        StringBuilder bar = new StringBuilder();

        // 채워진 세그먼트 수 계산 (0~20)
        int filledSegments = (int) Math.round(ratio * TOTAL_SEGMENTS);

        // 각 세그먼트를 순회하면서 채워진/빈 상태 결정
        for (int i = 0; i < TOTAL_SEGMENTS; i++) {
            boolean isFilled = i < filledSegments;

            if (i == 0) {
                // 시작 부분
                bar.append(isFilled ? FILLED_START : EMPTY_START).append(SPACING);
            } else if (i == TOTAL_SEGMENTS - 1) {
                // 끝 부분
                bar.append(isFilled ? FILLED_END : EMPTY_END).append(SPACING);
            } else {
                // 중간 부분
                bar.append(isFilled ? FILLED_MIDDLE : EMPTY_MIDDLE).append(SPACING);
            }
        }

        return bar.toString();
    }

    public void cleanupCache() {
        long now = System.currentTimeMillis();
        barCache.entrySet().removeIf(entry ->
                now - entry.getValue().timestamp > 1000
        );
    }

    public void invalidateCache(UUID mobId) {
        barCache.remove(mobId);
    }

    public void clearCache() {
        barCache.clear();
    }
}