package com.tuan.coins;

import java.util.UUID;

/**
 * Shared service interface. CoinsPlugin registers an implementation in the
 * Bukkit ServicesManager. Other plugins shade this interface (and CoinsAPI)
 * so they can award coins at runtime without a hard dependency.
 */
public interface CoinsService {
    int getCoins(UUID uuid);
    void addCoins(UUID uuid, int amount);
    void setCoins(UUID uuid, int amount);
    boolean isAvailable();
}
