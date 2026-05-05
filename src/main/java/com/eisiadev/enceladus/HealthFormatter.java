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

    // -------------------------------------------------------------------------
    // 숫자 단위 (영문)
    // -------------------------------------------------------------------------
    private static final double[] VALUES = {
            1.0E30, 1.0E27, 1.0E24, 1.0E21, 1.0E18, 1.0E15, 1.0E12, 1.0E9, 1.0E6, 1.0E3
    };

    /** K M B T q Q s S o N  (short-scale 영문 단위) */
    private static final char[] UNIT_CHARS = {
            'N', 'o', 'S', 's', 'Q', 'q', 'T', 'B', 'M', 'K'
    };

    private static final DecimalFormat FORMATTER       = new DecimalFormat("#.#");
    private static final DecimalFormat COMMA_FORMATTER = new DecimalFormat("#,###.#");

    // -------------------------------------------------------------------------
    // 체력바 설정
    // -------------------------------------------------------------------------
    /** 체력바 전체 세그먼트 수 */
    private static final int BAR_SEGMENTS = 25;

    /**
     * 텍스트가 표시되는 최대 세그먼트 수 (홀수 권장 — 가운데 정렬).
     * 실제 표시 너비는 텍스트 길이에 따라 이 값 이하로 줄어든다.
     */
    private static final int TEXT_MAX_WIDTH = 5;

    // -------------------------------------------------------------------------
    // 유니코드 매핑 — active
    // -------------------------------------------------------------------------
    // Row 1: 숫자 0‥9  →  E400‥E409
    private static final int ACTIVE_DIGIT_BASE  = 0xE400;
    // Row 2
    private static final char ACTIVE_K   = '\uE410';
    private static final char ACTIVE_M   = '\uE411';
    private static final char ACTIVE_B   = '\uE412';
    private static final char ACTIVE_T   = '\uE413';
    private static final char ACTIVE_q   = '\uE414';
    private static final char ACTIVE_Q   = '\uE415';
    private static final char ACTIVE_s   = '\uE416';
    private static final char ACTIVE_S   = '\uE417';
    private static final char ACTIVE_o   = '\uE418';
    private static final char ACTIVE_N   = '\uE419';
    private static final char ACTIVE_DOT       = '\uE421';
    private static final char ACTIVE_BAR_START = '\uE422';
    private static final char ACTIVE_BAR_MID   = '\uE423';
    private static final char ACTIVE_BAR_END   = '\uE424';

    // -------------------------------------------------------------------------
    // 유니코드 매핑 — inactive
    // -------------------------------------------------------------------------
    private static final int INACTIVE_DIGIT_BASE  = 0xE500;
    private static final char INACTIVE_K   = '\uE510';
    private static final char INACTIVE_M   = '\uE511';
    private static final char INACTIVE_B   = '\uE512';
    private static final char INACTIVE_T   = '\uE513';
    private static final char INACTIVE_q   = '\uE514';
    private static final char INACTIVE_Q   = '\uE515';
    private static final char INACTIVE_s   = '\uE516';
    private static final char INACTIVE_S   = '\uE517';
    private static final char INACTIVE_o   = '\uE518';
    private static final char INACTIVE_N   = '\uE519';
    private static final char INACTIVE_DOT       = '\uE521';
    private static final char INACTIVE_BAR_START = '\uE522';
    private static final char INACTIVE_BAR_MID   = '\uE523';
    private static final char INACTIVE_BAR_END   = '\uE524';

    // -------------------------------------------------------------------------
    // 캐시
    // -------------------------------------------------------------------------
    private final ConcurrentHashMap<UUID, ComponentCache> nameCache   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ComponentCache> healthCache = new ConcurrentHashMap<>();

    private static class ComponentCache {
        final Component component;
        final double health;
        final double maxHealth;
        final Component customName;
        final long timestamp;

        ComponentCache(Component component, double health, double maxHealth, Component customName) {
            this.component  = component;
            this.health     = health;
            this.maxHealth  = maxHealth;
            this.customName = customName;
            this.timestamp  = System.currentTimeMillis();
        }

        boolean isValid(double currentHealth, double currentMaxHealth, Component currentCustomName, long now) {
            return (now - timestamp < 100)
                    && Math.abs(health    - currentHealth)    < 0.01
                    && Math.abs(maxHealth - currentMaxHealth) < 0.01
                    && Objects.equals(customName, currentCustomName);
        }
    }

    // =========================================================================
    // 공개 API
    // =========================================================================

    public Component createNameComponent(LivingEntity mob) {
        UUID mobId = mob.getUniqueId();
        long now = System.currentTimeMillis();
        Component customName = mob.customName();

        ComponentCache cached = nameCache.get(mobId);
        if (cached != null && cached.isValid(0, 0, customName, now)) {
            return cached.component;
        }

        Component nameComponent = customName != null
                ? customName
                : Component.text(mob.getType().name(), NamedTextColor.WHITE);

        Component component = Objects.requireNonNull(nameComponent)
                .decoration(TextDecoration.ITALIC, false);

        nameCache.put(mobId, new ComponentCache(component, 0, 0, customName));
        return component;
    }

    /**
     * 유니코드 체력바 컴포넌트를 반환합니다.
     *
     * <p>구조: [바_시작] [세그먼트 × 25] [바_끝]
     * <br>세그먼트 중 가운데 {@value TEXT_MAX_WIDTH}칸 안에 영문 단위 체력 수치가 삽입됩니다.
     */
    public Component createHealthComponent(LivingEntity mob) {
        UUID mobId    = mob.getUniqueId();
        double health    = mob.getHealth();
        double maxHealth = mob.getMaxHealth();
        long now = System.currentTimeMillis();

        ComponentCache cached = healthCache.get(mobId);
        if (cached != null && cached.isValid(health, maxHealth, null, now)) {
            return cached.component;
        }

        Component component = buildBarComponent(health, maxHealth)
                .decoration(TextDecoration.ITALIC, false);

        healthCache.put(mobId, new ComponentCache(component, health, maxHealth, null));
        return component;
    }

    // =========================================================================
    // 체력바 합성
    // =========================================================================

    /** 각 문자 사이에 삽입할 spacing 문자 (font space advance -1.05px) */
    private static final char SPACING = '\uE036';

    private Component buildBarComponent(double health, double maxHealth) {
        double ratio = maxHealth <= 0 ? 0 : Math.max(0, Math.min(1, health / maxHealth));

        int activeCount = (int) Math.round(ratio * BAR_SEGMENTS);

        char[] textChars = buildTextChars(health);
        int textLen = textChars.length;

        int textWidth   = Math.min(textLen, TEXT_MAX_WIDTH);
        int centerStart = (BAR_SEGMENTS - textWidth) / 2;
        int centerEnd   = centerStart + textWidth - 1;
        int textOffset  = (textWidth - textLen) / 2;

        // 각 문자 뒤에 SPACING 이 붙으므로 용량 여유 확보
        StringBuilder sb = new StringBuilder((BAR_SEGMENTS + 2) * 2);

        boolean startActive = (activeCount >= 1);
        sb.append(startActive ? ACTIVE_BAR_START : INACTIVE_BAR_START);
        sb.append(SPACING);

        for (int i = 0; i < BAR_SEGMENTS; i++) {
            boolean isActive = (i < activeCount);

            if (i >= centerStart && i <= centerEnd) {
                int charIdx = (i - centerStart) - textOffset;
                if (charIdx >= 0 && charIdx < textLen) {
                    char raw = textChars[charIdx];
                    sb.append(remapChar(raw, isActive));
                } else {
                    sb.append(isActive ? ACTIVE_BAR_MID : INACTIVE_BAR_MID);
                }
            } else {
                sb.append(isActive ? ACTIVE_BAR_MID : INACTIVE_BAR_MID);
            }
            sb.append(SPACING);
        }

        boolean endActive = (activeCount >= BAR_SEGMENTS);
        sb.append(endActive ? ACTIVE_BAR_END : INACTIVE_BAR_END);
        sb.append(SPACING);

        return Component.text(sb.toString(), NamedTextColor.WHITE);
    }

    // =========================================================================
    // 텍스트 → 유니코드 char 배열
    // =========================================================================

    private char[] buildTextChars(double value) {
        String formatted = formatHealth(value);
        char[] result = new char[formatted.length()];
        for (int i = 0; i < formatted.length(); i++) {
            result[i] = toActiveChar(formatted.charAt(i));
        }
        return result;
    }

    private char toActiveChar(char c) {
        if (c >= '0' && c <= '9') return (char) (ACTIVE_DIGIT_BASE + (c - '0'));
        return switch (c) {
            case 'K'  -> ACTIVE_K;
            case 'M'  -> ACTIVE_M;
            case 'B'  -> ACTIVE_B;
            case 'T'  -> ACTIVE_T;
            case 'q'  -> ACTIVE_q;
            case 'Q'  -> ACTIVE_Q;
            case 's'  -> ACTIVE_s;
            case 'S'  -> ACTIVE_S;
            case 'o'  -> ACTIVE_o;
            case 'N'  -> ACTIVE_N;
            case '.'  -> ACTIVE_DOT;
            default   -> '\0';
        };
    }

    private char toInactiveChar(char activeChar) {
        if (activeChar >= (char) ACTIVE_DIGIT_BASE && activeChar <= (char) (ACTIVE_DIGIT_BASE + 9)) {
            return (char) (INACTIVE_DIGIT_BASE + (activeChar - (char) ACTIVE_DIGIT_BASE));
        }
        if (activeChar == ACTIVE_K)   return INACTIVE_K;
        if (activeChar == ACTIVE_M)   return INACTIVE_M;
        if (activeChar == ACTIVE_B)   return INACTIVE_B;
        if (activeChar == ACTIVE_T)   return INACTIVE_T;
        if (activeChar == ACTIVE_q)   return INACTIVE_q;
        if (activeChar == ACTIVE_Q)   return INACTIVE_Q;
        if (activeChar == ACTIVE_s)   return INACTIVE_s;
        if (activeChar == ACTIVE_S)   return INACTIVE_S;
        if (activeChar == ACTIVE_o)   return INACTIVE_o;
        if (activeChar == ACTIVE_N)   return INACTIVE_N;
        if (activeChar == ACTIVE_DOT) return INACTIVE_DOT;
        return activeChar;
    }

    private char remapChar(char activeChar, boolean isActive) {
        if (activeChar == '\0') {
            return isActive ? ACTIVE_BAR_MID : INACTIVE_BAR_MID;
        }
        return isActive ? activeChar : toInactiveChar(activeChar);
    }

    // =========================================================================
    // 숫자 포매팅 (영문 단위)
    // =========================================================================

    private String formatHealth(double value) {
        for (int i = 0; i < VALUES.length; i++) {
            if (value >= VALUES[i]) {
                double converted = value / VALUES[i];
                String formatted = converted >= 1000
                        ? COMMA_FORMATTER.format(converted)
                        : FORMATTER.format(converted);
                return formatted + UNIT_CHARS[i];
            }
        }
        // 1000 미만
        return value >= 1000
                ? COMMA_FORMATTER.format(value)
                : FORMATTER.format(value);
    }

    // =========================================================================
    // 캐시 관리
    // =========================================================================

    public void cleanupCache() {
        long now = System.currentTimeMillis();
        nameCache.entrySet().removeIf(e -> now - e.getValue().timestamp > 1000);
        healthCache.entrySet().removeIf(e -> now - e.getValue().timestamp > 1000);
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