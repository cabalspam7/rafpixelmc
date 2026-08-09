package com.tuan.coins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class CoinsPlugin extends JavaPlugin implements CommandExecutor, CoinsService {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, Integer> coins = new HashMap<>();
    private File dataFile;
    private static final int DAILY_BONUS = 100;

    @Override
    public void onEnable() {
        dataFile = new File(getDataFolder(), "coins.json");
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        load();
        Bukkit.getServicesManager().register(CoinsService.class, this, this, org.bukkit.plugin.ServicePriority.Normal);
        var cmd = getCommand("coins");
        if (cmd != null) cmd.setExecutor(this);
        getLogger().info("CoinsPlugin v1.0.0 enabled (" + coins.size() + " players loaded)");
    }

    @Override
    public void onDisable() {
        save();
        Bukkit.getServicesManager().unregister(CoinsService.class, this);
    }

    // ---- API ----
    @Override
    public boolean isAvailable() { return true; }

    @Override
    public int getCoins(UUID uuid) {
        return coins.getOrDefault(uuid, 0);
    }

    public void addCoins(UUID uuid, int amount) {
        coins.put(uuid, Math.max(0, getCoins(uuid) + amount));
        saveAsync();
    }

    public void setCoins(UUID uuid, int amount) {
        coins.put(uuid, Math.max(0, amount));
        saveAsync();
    }

    // ---- persistence ----
    private void load() {
        if (!dataFile.exists()) return;
        try (FileReader r = new FileReader(dataFile)) {
            JsonObject root = gson.fromJson(r, JsonObject.class);
            if (root == null) return;
            for (var e : root.entrySet()) {
                try {
                    UUID u = UUID.fromString(e.getKey());
                    coins.put(u, e.getValue().getAsInt());
                } catch (Exception ignore) {}
            }
        } catch (Exception ex) {
            getLogger().warning("Failed to load coins: " + ex.getMessage());
        }
    }

    private void save() {
        try {
            JsonObject root = new JsonObject();
            for (var e : coins.entrySet()) root.addProperty(e.getKey().toString(), e.getValue());
            try (FileWriter w = new FileWriter(dataFile)) {
                gson.toJson(root, w);
            }
        } catch (Exception ex) {
            getLogger().warning("Failed to save coins: " + ex.getMessage());
        }
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, this::save);
    }

    // ---- command ----
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Player only."); return true; }
            sender.sendMessage(ChatColor.GOLD + "Your coins: " + ChatColor.YELLOW + getCoins(p.getUniqueId()));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "top" -> {
                var top = coins.entrySet().stream()
                        .sorted((a, b) -> b.getValue() - a.getValue())
                        .limit(10)
                        .collect(Collectors.toList());
                sender.sendMessage(ChatColor.GOLD + "=== Coin Leaderboard ===");
                int i = 1;
                for (var e : top) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
                    String name = op.getName() != null ? op.getName() : e.getKey().toString().substring(0, 8);
                    sender.sendMessage(ChatColor.GRAY + String.valueOf(i++) + ". " + ChatColor.YELLOW + name + " - " + e.getValue());
                }
            }
            case "give" -> {
                if (!sender.hasPermission("hypixel.admin")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
                if (args.length < 3) { sender.sendMessage(ChatColor.RED + "/coins give <player> <amt>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(ChatColor.RED + "Player not online."); return true; }
                try {
                    int amt = Integer.parseInt(args[2]);
                    addCoins(target.getUniqueId(), amt);
                    sender.sendMessage(ChatColor.GREEN + "Gave " + amt + " coins to " + target.getName());
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Amount must be a number.");
                }
            }
            case "daily" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Player only."); return true; }
                addCoins(p.getUniqueId(), DAILY_BONUS);
                p.sendMessage(ChatColor.GREEN + "Claimed daily bonus: +" + DAILY_BONUS + " coins");
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown. /coins [top|give|daily]");
        }
        return true;
    }
}
