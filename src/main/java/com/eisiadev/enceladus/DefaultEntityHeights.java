package com.eisiadev.enceladus;

import org.bukkit.entity.EntityType;

public class DefaultEntityHeights {

    public static double getHeight(EntityType type) {
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
            case SHULKER, FOX, PANDA, PARROT, TURTLE, COD, SALMON,
                 PUFFERFISH, TROPICAL_FISH, DOLPHIN, ALLAY, FROG,
                 TADPOLE, FALLING_BLOCK -> 1.0;
            case GOAT -> 1.3;

            case PIG, SHEEP, COW, OCELOT, CAT, DONKEY, MULE, LLAMA,
                 TRADER_LLAMA, HORSE, SKELETON_HORSE, ZOMBIE_HORSE,
                 CAMEL, SNIFFER, WOLF, POLAR_BEAR -> 1.6;

            case SLIME, MAGMA_CUBE -> 2.04;

            case GIANT -> 12.0;
            case GHAST -> 4.0;

            default -> 1.8;
        };
    }
}