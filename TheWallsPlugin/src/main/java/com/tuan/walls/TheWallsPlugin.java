package com.tuan.walls;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class TheWallsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private static final int QUADRANT = 25;     // half-size of each quadrant
    private static final int WALL_HEIGHT = 12;
    private static final int PREP_SECONDS = 30;
    private static final int FIGHT_SECONDS = 90;

    private World world;
    private final List<UUID> inGame = new ArrayList<>();
    private final Map<UUID, Location> returnLoc = new HashMap<>();
    private final Map<UUID, Integer> team = new HashMap<>();  // 0..3
    private boolean running = false;
    private int task = -1;
    private Location center;

    @Override
    public void onEnable() {
        world = Bukkit.getWorlds().get(0);
        center = new Location(world, 300, 80, 0);
        Bukkit.getPluginManager().registerEvents(this, this);
        var c = getCommand("walls");
        if (c != null) c.setExecutor(this);
        getLogger().info("TheWallsPlugin v1.0.0 enabled");
    }

    @Override
    public void onDisable() {
        if (task != -1) Bukkit.getScheduler().cancelTask(task);
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Player only."); return true; }
        UUID me = p.getUniqueId();
        if (args.length == 0) {
            p.sendMessage(ChatColor.AQUA + "=== The Walls ===");
            p.sendMessage(ChatColor.GRAY + "/walls join | leave | start");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "join" -> {
                if (running) { p.sendMessage(ChatColor.RED + "Game in progress."); return true; }
                if (inGame.contains(me)) { p.sendMessage(ChatColor.YELLOW + "Already joined."); return true; }
                returnLoc.put(me, p.getLocation().clone());
                inGame.add(me);
                p.sendMessage(ChatColor.AQUA + "Joined The Walls! (" + inGame.size() + ")");
                if (inGame.size() == 1) {
                    p.sendMessage(ChatColor.GRAY + "Waiting for players (auto-start 20s or /walls start)...");
                    task = Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (inGame.size() >= 2) startGame(); else { p.sendMessage(ChatColor.RED + "Not enough players."); reset(); }
                    }, 20 * 20).getTaskId();
                }
            }
            case "leave" -> {
                inGame.remove(me);
                if (returnLoc.containsKey(me)) p.teleport(returnLoc.get(me));
                returnLoc.remove(me); team.remove(me);
                p.sendMessage(ChatColor.YELLOW + "Left The Walls.");
            }
            case "start" -> {
                if (!p.hasPermission("hypixel.admin")) { p.sendMessage(ChatColor.RED + "No permission."); return true; }
                if (inGame.size() < 2) { p.sendMessage(ChatColor.RED + "Need 2+."); return true; }
                if (task != -1) { Bukkit.getScheduler().cancelTask(task); task = -1; }
                startGame();
            }
            default -> p.sendMessage(ChatColor.RED + "/walls <join|leave|start>");
        }
        return true;
    }

    private void reset() {
        if (task != -1) { Bukkit.getScheduler().cancelTask(task); task = -1; }
        inGame.clear(); returnLoc.clear(); team.clear(); running = false;
    }

    private void cleanup() {
        for (UUID u : new ArrayList<>(inGame)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                if (returnLoc.containsKey(u)) p.teleport(returnLoc.get(u));
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
            }
        }
        reset();
    }

    private void startGame() {
        if (inGame.size() < 2) return;
        running = true;
        buildArena();
        // assign teams round-robin
        for (int i = 0; i < inGame.size(); i++) {
            UUID u = inGame.get(i);
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            int t = i % 4;
            team.put(u, t);
            int cx = center.getBlockX() + (t == 0 || t == 1 ? -1 : 1) * QUADRANT / 2;
            int cz = center.getBlockZ() + (t == 0 || t == 3 ? -1 : 1) * QUADRANT / 2;
            p.teleport(new Location(world, cx, 81, cz));
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20); p.setFoodLevel(20);
            p.getInventory().clear();
            giveKit(p);
            String color = new String[]{ChatColor.RED+"", ChatColor.BLUE+"", ChatColor.GREEN+"", ChatColor.YELLOW+""}[t];
            p.sendMessage(color + "You are on Team " + (t+1) + "! Walls drop in " + PREP_SECONDS + "s. Prepare!");
        }
        broadcast(ChatColor.GOLD + "The Walls started! Prepare phase (" + PREP_SECONDS + "s).");
        task = Bukkit.getScheduler().runTaskLater(this, () -> dropWalls(), PREP_SECONDS * 20).getTaskId();
    }

    private void giveKit(Player p) {
        p.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        p.getInventory().addItem(new ItemStack(Material.IRON_PICKAXE));
        p.getInventory().addItem(new ItemStack(Material.IRON_AXE));
        p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 8));
        p.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 32));
    }

    private void buildArena() {
        int cx = center.getBlockX(), cz = center.getBlockZ();
        // floor
        for (int dx = -QUADRANT; dx <= QUADRANT; dx++)
            for (int dz = -QUADRANT; dz <= QUADRANT; dz++)
                world.getBlockAt(cx + dx, 80, cz + dz).setType(Material.GRASS_BLOCK);
        // walls (glass, between quadrants) up to height
        for (int y = 81; y <= 80 + WALL_HEIGHT; y++) {
            for (int d = -QUADRANT; d <= QUADRANT; d++) {
                world.getBlockAt(cx + d, y, cz).setType(Material.GLASS);       // z-axis divider
                world.getBlockAt(cx, y, cz + d).setType(Material.GLASS);       // x-axis divider
            }
        }
        // border
        for (int d = -QUADRANT; d <= QUADRANT; d++) {
            world.getBlockAt(cx + d, 81, cz - QUADRANT).setType(Material.BEDROCK);
            world.getBlockAt(cx + d, 81, cz + QUADRANT).setType(Material.BEDROCK);
            world.getBlockAt(cx - QUADRANT, 81, cz + d).setType(Material.BEDROCK);
            world.getBlockAt(cx + QUADRANT, 81, cz + d).setType(Material.BEDROCK);
        }
    }

    private void dropWalls() {
        int cx = center.getBlockX(), cz = center.getBlockZ();
        for (int y = 81; y <= 80 + WALL_HEIGHT; y++) {
            for (int d = -QUADRANT; d <= QUADRANT; d++) {
                world.getBlockAt(cx + d, y, cz).setType(Material.AIR);
                world.getBlockAt(cx, y, cz + d).setType(Material.AIR);
            }
        }
        broadcast(ChatColor.RED + "THE WALLS HAVE DROPPED! Fight!");
        task = Bukkit.getScheduler().runTaskLater(this, () -> endGame(), FIGHT_SECONDS * 20).getTaskId();
    }

    private void broadcast(String msg) {
        for (UUID u : inGame) { Player p = Bukkit.getPlayer(u); if (p != null) p.sendMessage(msg); }
    }

    private int aliveTeams() {
        Set<Integer> alive = new HashSet<>();
        for (UUID u : inGame) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline() && p.getGameMode() != GameMode.SPECTATOR && p.getHealth() > 0)
                alive.add(team.get(u));
        }
        return alive.size();
    }

    private void checkWin() {
        if (!running) return;
        if (aliveTeams() <= 1) {
            // find winning team
            Integer wt = null;
            for (UUID u : inGame) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && p.isOnline() && p.getHealth() > 0) { wt = team.get(u); break; }
            }
            endGame(wt);
        }
    }

    private void endGame() { if (running) endGame(null); }

    private void endGame(Integer winningTeam) {
        if (!running) return;
        running = false;
        if (winningTeam != null) {
            String color = new String[]{ChatColor.RED+"", ChatColor.BLUE+"", ChatColor.GREEN+"", ChatColor.YELLOW+""}[winningTeam];
            broadcast(ChatColor.GOLD + "Team " + (winningTeam+1) + " wins The Walls!");
            // award coins to survivors
            for (UUID u : new ArrayList<>(inGame)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && team.get(u) == winningTeam && p.getHealth() > 0) {
                    p.sendMessage(ChatColor.GREEN + "+50 Coins");
                    com.tuan.coins.CoinsAPI.addCoins(u, 50);
                }
            }
        } else {
            broadcast(ChatColor.GRAY + "The Walls ended - no clear winner.");
        }
        for (UUID u : new ArrayList<>(inGame)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                if (returnLoc.containsKey(u)) p.teleport(returnLoc.get(u));
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
            }
        }
        reset();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        if (!inGame.contains(dead.getUniqueId())) return;
        e.setDeathMessage(ChatColor.RED + dead.getName() + " (Team " + (team.get(dead.getUniqueId())+1) + ") was eliminated!");
        dead.setGameMode(GameMode.SPECTATOR);
        Bukkit.getScheduler().runTask(this, this::checkWin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (inGame.contains(p.getUniqueId())) {
            inGame.remove(p.getUniqueId());
            returnLoc.remove(p.getUniqueId()); team.remove(p.getUniqueId());
            if (running) checkWin();
        }
    }
}
