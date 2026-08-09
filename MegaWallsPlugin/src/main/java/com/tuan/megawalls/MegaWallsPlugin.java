package com.tuan.megawalls;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class MegaWallsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private static final int QUADRANT = 40;
    private static final int WALL_HEIGHT = 16;
    private static final int PREP_SECONDS = 30;
    private static final int FIGHT_SECONDS = 120;

    private static final String[] CLASSES = {"Golem", "Shark", "Pigman", "Enderman"};
    private static final String[] TEAM_NAMES = {"Red", "Blue", "Green", "Yellow"};
    private static final ChatColor[] TEAM_COLORS = {ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW};

    private World world;
    private final List<UUID> inGame = new ArrayList<>();
    private final Map<UUID, Location> returnLoc = new HashMap<>();
    private final Map<UUID, Integer> team = new HashMap<>();
    private final Map<UUID, String> pclass = new HashMap<>();
    private boolean running = false;
    private int task = -1;
    private Location center;
    private final List<UUID> withers = new ArrayList<>();

    @Override
    public void onEnable() {
        world = Bukkit.getWorlds().get(0);
        center = new Location(world, 600, 80, 0);
        Bukkit.getPluginManager().registerEvents(this, this);
        var c = getCommand("mw");
        if (c != null) c.setExecutor(this);
        getLogger().info("MegaWallsPlugin v1.0.0 enabled");
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
            p.sendMessage(ChatColor.AQUA + "=== Mega Walls ===");
            p.sendMessage(ChatColor.GRAY + "/mw join | leave | start | class <Golem|Shark|Pigman|Enderman>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "class" -> {
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "/mw class <Golem|Shark|Pigman|Enderman>"); return true; }
                String cl = args[1].substring(0, 1).toUpperCase() + args[1].substring(1).toLowerCase();
                if (!Arrays.asList(CLASSES).contains(cl)) { p.sendMessage(ChatColor.RED + "Unknown class."); return true; }
                pclass.put(me, cl);
                p.sendMessage(ChatColor.GREEN + "Class set to " + cl);
            }
            case "join" -> {
                if (running) { p.sendMessage(ChatColor.RED + "Game in progress."); return true; }
                if (inGame.contains(me)) { p.sendMessage(ChatColor.YELLOW + "Already joined."); return true; }
                returnLoc.put(me, p.getLocation().clone());
                if (!pclass.containsKey(me)) pclass.put(me, CLASSES[new Random().nextInt(CLASSES.length)]);
                inGame.add(me);
                p.sendMessage(ChatColor.AQUA + "Joined Mega Walls! (" + inGame.size() + ") Class: " + pclass.get(me));
                if (inGame.size() == 1) {
                    p.sendMessage(ChatColor.GRAY + "Waiting for players (auto-start 20s or /mw start)...");
                    task = Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (inGame.size() >= 2) startGame(); else { p.sendMessage(ChatColor.RED + "Not enough players."); reset(); }
                    }, 20 * 20).getTaskId();
                }
            }
            case "leave" -> {
                inGame.remove(me);
                if (returnLoc.containsKey(me)) p.teleport(returnLoc.get(me));
                returnLoc.remove(me); team.remove(me); pclass.remove(me);
                p.sendMessage(ChatColor.YELLOW + "Left Mega Walls.");
            }
            case "start" -> {
                if (!p.hasPermission("hypixel.admin")) { p.sendMessage(ChatColor.RED + "No permission."); return true; }
                if (inGame.size() < 2) { p.sendMessage(ChatColor.RED + "Need 2+."); return true; }
                if (task != -1) { Bukkit.getScheduler().cancelTask(task); task = -1; }
                startGame();
            }
            default -> p.sendMessage(ChatColor.RED + "/mw <join|leave|start|class>");
        }
        return true;
    }

    private void reset() {
        if (task != -1) { Bukkit.getScheduler().cancelTask(task); task = -1; }
        inGame.clear(); returnLoc.clear(); team.clear(); pclass.clear(); running = false;
        for (UUID w : withers) { var e = Bukkit.getEntity(w); if (e != null) e.remove(); }
        withers.clear();
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
            giveKit(p, pclass.get(u));
            p.sendMessage(TEAM_COLORS[t] + "You are on Team " + TEAM_NAMES[t] + "! Class: " + pclass.get(u));
            p.sendMessage(ChatColor.GRAY + "Walls drop in " + PREP_SECONDS + "s. Withers will spawn!");
        }
        broadcast(ChatColor.GOLD + "Mega Walls started! Prepare (" + PREP_SECONDS + "s).");
        task = Bukkit.getScheduler().runTaskLater(this, () -> dropWalls(), PREP_SECONDS * 20).getTaskId();
    }

    private void giveKit(Player p, String cl) {
        p.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        p.getInventory().addItem(new ItemStack(Material.BOW));
        p.getInventory().addItem(new ItemStack(Material.ARROW, 16));
        p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 10));
        if ("Golem".equals(cl)) { p.getInventory().addItem(new ItemStack(Material.IRON_CHESTPLATE)); p.setMaxHealth(30); p.setHealth(30); }
        else if ("Shark".equals(cl)) { p.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD)); }
        else if ("Pigman".equals(cl)) { p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 5)); }
        else if ("Enderman".equals(cl)) { p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 4)); }
    }

    private void buildArena() {
        int cx = center.getBlockX(), cz = center.getBlockZ();
        for (int dx = -QUADRANT; dx <= QUADRANT; dx++)
            for (int dz = -QUADRANT; dz <= QUADRANT; dz++)
                world.getBlockAt(cx + dx, 80, cz + dz).setType(Material.STONE);
        for (int y = 81; y <= 80 + WALL_HEIGHT; y++) {
            for (int d = -QUADRANT; d <= QUADRANT; d++) {
                world.getBlockAt(cx + d, y, cz).setType(Material.GLASS);
                world.getBlockAt(cx, y, cz + d).setType(Material.GLASS);
            }
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
        // spawn wither per team near center
        for (int t = 0; t < 4; t++) {
            int wx = cx + (t == 0 || t == 1 ? -1 : 1) * (QUADRANT / 2);
            int wz = cz + (t == 0 || t == 3 ? -1 : 1) * (QUADRANT / 2);
            var w = world.spawnEntity(new Location(world, wx, 85, wz), EntityType.WITHER);
            withers.add(w.getUniqueId());
        }
        broadcast(ChatColor.RED + "WALLS DOWN! Withers spawned. Fight for your team!");
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
            broadcast(ChatColor.GOLD + "Team " + TEAM_NAMES[winningTeam] + " wins Mega Walls!");
            for (UUID u : new ArrayList<>(inGame)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && team.get(u) == winningTeam && p.getHealth() > 0) {
                    p.sendMessage(ChatColor.GREEN + "+75 Coins");
                    com.tuan.coins.CoinsAPI.addCoins(u, 75);
                }
            }
        } else {
            broadcast(ChatColor.GRAY + "Mega Walls ended - no clear winner.");
        }
        for (UUID u : new ArrayList<>(inGame)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                if (returnLoc.containsKey(u)) p.teleport(returnLoc.get(u));
                p.setGameMode(GameMode.ADVENTURE);
                p.setMaxHealth(20); p.setHealth(20);
                p.getInventory().clear();
            }
        }
        reset();
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player dead = e.getEntity();
        if (!inGame.contains(dead.getUniqueId())) return;
        e.setDeathMessage(ChatColor.RED + dead.getName() + " (Team " + TEAM_NAMES[team.get(dead.getUniqueId())] + ") fell in Mega Walls!");
        dead.setGameMode(GameMode.SPECTATOR);
        Bukkit.getScheduler().runTask(this, this::checkWin);
    }

    @EventHandler
    public void onWitherDeath(EntityDeathEvent e) {
        if (withers.contains(e.getEntity().getUniqueId())) {
            withers.remove(e.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (inGame.contains(p.getUniqueId())) {
            inGame.remove(p.getUniqueId());
            returnLoc.remove(p.getUniqueId()); team.remove(p.getUniqueId()); pclass.remove(p.getUniqueId());
            if (running) checkWin();
        }
    }
}
