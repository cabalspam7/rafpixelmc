package com.tuan.skywars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SkyWarsPlugin extends JavaPlugin implements Listener {

    private final int ISLAND_COUNT = 8;
    private final double ISLAND_RADIUS = 40.0;
    private final int MAX_PLAYERS = 8;
    private final long COUNTDOWN_MS = 30000;

    private World world;
    private final List<Location> islands = new ArrayList<>();
    private final Map<UUID, Location> returnLoc = new HashMap<>();
    private final List<UUID> inGame = new ArrayList<>();
    private boolean running = false;
    private long startWaitTime = 0;
    private int countdownTask = -1;

    @Override
    public void onEnable() {
        this.world = Bukkit.getWorlds().get(0);
        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("skywars");
        if (cmd != null) cmd.setExecutor(new SkyWarsCommand());
        getLogger().info("SkyWarsPlugin v1.0.0 enabled (solo)");
    }

    @Override
    public void onDisable() {
        if (countdownTask != -1) Bukkit.getScheduler().cancelTask(countdownTask);
        for (UUID u : new ArrayList<>(inGame)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && returnLoc.containsKey(u)) p.teleport(returnLoc.get(u));
        }
        inGame.clear();
        returnLoc.clear();
    }

    private void buildArena() {
        islands.clear();
        double cx = 0, cz = 0;
        for (int i = 0; i < ISLAND_COUNT; i++) {
            double angle = (2 * Math.PI * i) / ISLAND_COUNT;
            int x = (int) (cx + ISLAND_RADIUS * Math.cos(angle));
            int z = (int) (cz + ISLAND_RADIUS * Math.sin(angle));
            int y = 90;
            Location center = new Location(world, x, y, z);
            buildIsland(center);
            islands.add(center);
        }
        // center podium
        Location mid = new Location(world, 0, 88, 0);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                world.getBlockAt(dx, 88, dz).setType(Material.OBSIDIAN);
    }

    private void buildIsland(Location center) {
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        // grass platform 5x5
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                world.getBlockAt(cx + dx, cy, cz + dz).setType(Material.GRASS_BLOCK);
        // dirt below
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 1; dy <= 3; dy++)
                    world.getBlockAt(cx + dx, cy - dy, cz + dz).setType(Material.DIRT);
        // chest with loot
        Block chestBlock = world.getBlockAt(cx, cy + 1, cz);
        chestBlock.setType(Material.CHEST);
        if (chestBlock.getState() instanceof Chest chest) {
            fillLoot(chest);
            chest.update();
        }
    }

    private void fillLoot(Chest chest) {
        chest.getBlockInventory().clear();
        chest.getBlockInventory().addItem(new ItemStack(Material.IRON_SWORD));
        chest.getBlockInventory().addItem(new ItemStack(Material.BOW));
        chest.getBlockInventory().addItem(new ItemStack(Material.ARROW, 16));
        chest.getBlockInventory().addItem(new ItemStack(Material.IRON_CHESTPLATE));
        chest.getBlockInventory().addItem(new ItemStack(Material.IRON_LEGGINGS));
        chest.getBlockInventory().addItem(new ItemStack(Material.COOKED_BEEF, 4));
        chest.getBlockInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 2));
        chest.getBlockInventory().addItem(new ItemStack(Material.OAK_PLANKS, 16));
        chest.getBlockInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
    }

    private void giveKit(Player p) {
        p.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
    }

    public class SkyWarsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            Player p = (sender instanceof Player) ? (Player) sender : null;
            if (args.length == 0) {
                sender.sendMessage(ChatColor.AQUA + "=== SkyWars ===");
                sender.sendMessage(ChatColor.GRAY + "/skywars join - join solo queue");
                sender.sendMessage(ChatColor.GRAY + "/skywars leave - leave queue");
                sender.sendMessage(ChatColor.GRAY + "/skywars start - force start (op)");
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "join", "leave", "start" -> {
                    if (p == null) { sender.sendMessage(ChatColor.RED + "Players only."); return true; }
                    switch (args[0].toLowerCase()) {
                        case "join" -> {
                            if (running) { p.sendMessage(ChatColor.RED + "Game in progress. Wait for next round."); return true; }
                            if (inGame.contains(p.getUniqueId())) { p.sendMessage(ChatColor.YELLOW + "Already queued."); return true; }
                            returnLoc.put(p.getUniqueId(), p.getLocation().clone());
                            inGame.add(p.getUniqueId());
                            p.sendMessage(ChatColor.AQUA + "Joined SkyWars! (" + inGame.size() + "/" + MAX_PLAYERS + ")");
                            if (inGame.size() == 1) startWaitTime = System.currentTimeMillis();
                            if (inGame.size() >= MAX_PLAYERS) startGame();
                            else startCountdown();
                        }
                        case "leave" -> {
                            inGame.remove(p.getUniqueId());
                            if (returnLoc.containsKey(p.getUniqueId())) p.teleport(returnLoc.get(p.getUniqueId()));
                            returnLoc.remove(p.getUniqueId());
                            p.sendMessage(ChatColor.YELLOW + "Left SkyWars queue.");
                        }
                        case "start" -> {
                            if (!p.hasPermission("hypixel.admin")) { p.sendMessage(ChatColor.RED + "No permission."); return true; }
                            if (inGame.size() < 2) { p.sendMessage(ChatColor.RED + "Need 2+ players."); return true; }
                            startGame();
                        }
                    }
                }
                case "build" -> {
                    buildArena();
                    sender.sendMessage(ChatColor.GREEN + "SkyWars arena built (" + islands.size() + " islands).");
                }
                default -> sender.sendMessage(ChatColor.RED + "Unknown. /skywars join");
            }
            return true;
        }
    }

    private void startCountdown() {
        if (countdownTask != -1) return;
        countdownTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!running && inGame.size() >= 2) startGame();
            countdownTask = -1;
        }, COUNTDOWN_MS / 50).getTaskId();
    }

    private void startGame() {
        if (running) return;
        running = true;
        if (countdownTask != -1) { Bukkit.getScheduler().cancelTask(countdownTask); countdownTask = -1; }
        buildArena();
        for (int i = 0; i < inGame.size() && i < islands.size(); i++) {
            Player p = Bukkit.getPlayer(inGame.get(i));
            if (p == null || !p.isOnline()) continue;
            Location spawn = islands.get(i).clone().add(0, 2, 0);
            p.teleport(spawn);
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.getInventory().clear();
            giveKit(p);
            p.sendMessage(ChatColor.AQUA + "SkyWars started! " + inGame.size() + " players. Last one alive wins!");
        }
        broadcast(ChatColor.AQUA + "SkyWars round started with " + inGame.size() + " players!");
    }

    private void broadcast(String msg) {
        for (UUID u : inGame) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(msg);
        }
    }

    private void checkWin() {
        List<Player> alive = new ArrayList<>();
        for (UUID u : new ArrayList<>(inGame)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline() && p.getGameMode() != GameMode.SPECTATOR && p.getHealth() > 0) {
                alive.add(p);
            }
        }
        if (alive.size() <= 1) {
            Player winner = alive.isEmpty() ? null : alive.get(0);
            endGame(winner);
        }
    }

    private void endGame(Player winner) {
        running = false;
        if (winner != null) {
            broadcast(ChatColor.GOLD + winner.getName() + " wins SkyWars!");
            winner.sendMessage(ChatColor.GREEN + "Victory! +50 Coins");
            com.tuan.coins.CoinsAPI.addCoins(winner.getUniqueId(), 50);
            if (returnLoc.containsKey(winner.getUniqueId())) winner.teleport(returnLoc.get(winner.getUniqueId()));
            winner.getInventory().clear();
        } else {
            broadcast(ChatColor.GRAY + "SkyWars ended - no winner.");
        }
        // return everyone
        for (UUID u : new ArrayList<>(inGame)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && returnLoc.containsKey(u)) p.teleport(returnLoc.get(u));
            p.getInventory().clear();
        }
        inGame.clear();
        returnLoc.clear();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        if (!inGame.contains(dead.getUniqueId())) return;
        e.setDeathMessage(ChatColor.RED + dead.getName() + " was eliminated from SkyWars!");
        dead.setGameMode(GameMode.SPECTATOR);
        final Player killer = dead.getKiller();
        Bukkit.getScheduler().runTask(this, () -> {
            if (killer != null && inGame.contains(killer.getUniqueId())) {
                killer.sendMessage(ChatColor.GREEN + "+10 Coins for elimination");
                com.tuan.coins.CoinsAPI.addCoins(killer.getUniqueId(), 10);
            }
            checkWin();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (inGame.contains(p.getUniqueId())) {
            inGame.remove(p.getUniqueId());
            returnLoc.remove(p.getUniqueId());
            if (running) checkWin();
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        // prevent breaking center podium / islands floor during game? allow normal play
    }
}
