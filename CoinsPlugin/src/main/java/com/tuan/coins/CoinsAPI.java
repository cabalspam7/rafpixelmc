package com.tuan.coins;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Static API for other plugins to award/query coins.
 * Usage (no compile dependency needed — just shade this + CoinsService):
 *   CoinsAPI.addCoins(player, 50);
 *   int bal = CoinsAPI.getCoins(player);
 */
public final class CoinsAPI {

    private CoinsAPI() {}

    private static CoinsService svc() {
        return Bukkit.getServicesManager().load(CoinsService.class);
    }

    public static boolean isAvailable() {
        return svc() != null;
    }

    public static int getCoins(OfflinePlayer player) {
        CoinsService s = svc();
        return s == null ? 0 : s.getCoins(player.getUniqueId());
    }

    public static int getCoins(UUID uuid) {
        CoinsService s = svc();
        return s == null ? 0 : s.getCoins(uuid);
    }

    public static void addCoins(OfflinePlayer player, int amount) {
        CoinsService s = svc();
        if (s != null) s.addCoins(player.getUniqueId(), amount);
    }

    public static void addCoins(UUID uuid, int amount) {
        CoinsService s = svc();
        if (s != null) s.addCoins(uuid, amount);
    }

    public static void setCoins(OfflinePlayer player, int amount) {
        CoinsService s = svc();
        if (s != null) s.setCoins(player.getUniqueId(), amount);
    }
}
