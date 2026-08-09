package com.tuan.murder;

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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class MurderMysteryPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private static final int ARENA_RADIUS = 30;
    private static final int DURATION_SECONDS = 120;

    private World world;
    private final List<UUID> inGame = new ArrayList<>();
    private final Map<UUID, Location> returnLoc = new HashMap<>();
    private final Map<UUID, String> role = new HashMap<>();   // murderer/detective/innocent
    private final Map<UUID, UUID> lastHitBy = new HashMap<>();
    private boolean running = false;
    private int task = -1;
    private Location center;

    @Override
    public void onEnable() {
        world = Bukkit.getWorlds().get(0);
        center = new Location(world, 0, 80, 100);
        Bukkit.getPluginManager().registerEvents(this, this);
        var c = getCommand("mm");
        if (c != null) c.setExecutor(this);
        getLogger().info("MurderMysteryPlugin v1.0.0 enabled");
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
            p.sendMessage(ChatColor.AQUA + "=== Murder Mystery ===");
            p.sendMessage(ChatColor.GRAY + "/mm join | leave | start");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "join" -> {
                if (running) { p.sendMessage(ChatColor.RED + "Game in progress."); return true; }
                if (inGame.contains(me)) { p.sendMessage(ChatColor.YELLOW + "Already joined."); return true; }
                returnLoc.put(me, p.getLocation().clone());
                inGame.add(me);
                p.sendMessage(ChatColor.AQUA + "Joined Murder Mystery! (" + inGame.size() + ")");
                if (inGame.size() == 1) {
                    p.sendMessage(ChatColor.GRAY + "Waiting for players (auto-start 20s or /mm start)...");
                    task = Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (inGame.size() >= 2) startGame(); else { p.sendMessage(ChatColor.RED + "Not enough players."); reset(); }
                    }, 20 * 20).getTaskId();
                }
            }
            case "leave" -> {
                inGame.remove(me);
                if (returnLoc.containsKey(me)) p.teleport(returnLoc.get(me));
                returnLoc.remove(me); role.remove(me);
                p.sendMessage(ChatColor.YELLOW + "Left Murder Mystery.");
            }
            case "start" -> {
                if (!p.hasPermission("hypixel.admin")) { p.sendMessage(ChatColor.RED + "No permission."); return true; }
                if (inGame.size() < 2) { p.sendMessage(ChatColor.RED + "Need 2+."); return true; }
                if (task != -1) { Bukkit.getScheduler().cancelTask(task); task = -1; }
                startGame();
            }
            default -> p.sendMessage(ChatColor.RED + "/mm <join|leave|start>");
        }
        return true;
    }

    private void reset() {
        if (task != -1) { Bukkit.getScheduler().cancelTask(task); task = -1; }
        inGame.clear(); returnLoc.clear(); role.clear(); lastHitBy.clear(); running = false;
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
        // assign roles
        List<UUID> players = new ArrayList<>(inGame);
        Collections.shuffle(players);
        int murderer = 0, detective = (players.size() >= 3) ? 1 : -1;
        for (int i = 0; i < players.size(); i++) {
            UUID u = players.get(i);
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            if (i == murderer) { role.put(u, "murderer"); }
            else if (i == detective) { role.put(u, "detective"); }
            else { role.put(u, "innocent"); }
            // place around circle
            double angle = (2 * Math.PI * i) / players.size();
            int x = center.getBlockX() + (int) (Math.cos(angle) * ARENA_RADIUS);
            int z = center.getBlockZ() + (int) (Math.sin(angle) * ARENA_RADIUS);
            p.teleport(new Location(world, x, 81, z));
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20); p.setFoodLevel(20);
            p.getInventory().clear();
            String r = role.get(u);
            if (r.equals("murderer")) {
                p.getInventory().addItem(new ItemStack(Material.IRON_SWORD)); // knife
                p.sendMessage(ChatColor.RED + "You are the MURDERER! Kill everyone with your knife.");
            } else if (r.equals("detective")) {
                p.getInventory().addItem(new ItemStack(Material.BOW));
                p.getInventory().addItem(new ItemStack(Material.ARROW, 16));
                p.sendMessage(ChatColor.BLUE + "You are the DETECTIVE! Shoot the murderer with your bow.");
            } else {
                p.sendMessage(ChatColor.GRAY + "You are INNOCENT! Survive or find the murderer.");
            }
        }
        broadcast(ChatColor.GOLD + "Murder Mystery started! Roles assigned. Survive " + DURATION_SECONDS + "s.");
        task = Bukkit.getScheduler().runTaskLater(this, this::endByTime, DURATION_SECONDS * 20).getTaskId();
    }

    private void buildArena() {
        // flat stone platform
        int cx = center.getBlockX(), cz = center.getBlockZ();
        for (int dx = -ARENA_RADIUS - 2; dx <= ARENA_RADIUS + 2; dx++)
            for (int dz = -ARENA_RADIUS - 2; dz <= ARENA_RADIUS + 2; dz++) {
                double d = Math.hypot(dx, dz);
                var b = world.getBlockAt(cx + dx, 80, cz + dz);
                if (d <= ARENA_RADIUS) b.setType(Material.STONE);
                else if (d <= ARENA_RADIUS + 2) b.setType(Material.AIR);
            }
        // spawn center podium
        world.getBlockAt(cx, 81, cz).setType(Material.OBSIDIAN);
    }

    private void broadcast(String msg) {
        for (UUID u : inGame) { Player p = Bukkit.getPlayer(u); if (p != null) p.sendMessage(msg); }
    }

    private int aliveCount() {
        int n = 0;
        for (UUID u : new ArrayList<>(inGame)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline() && p.getGameMode() != GameMode.SPECTATOR && p.getHealth() > 0) n++;
        }
        return n;
    }

    private boolean murdererAlive() {
        for (UUID u : inGame) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline() && role.get(u) == "murderer" && p.getHealth() > 0) return true;
        }
        return false;
    }

    private UUID murdererUuid() {
        for (UUID u : inGame) if (role.get(u) == "murderer") return u;
        return null;
    }

    private void checkWin() {
        boolean murdererDead = !murdererAlive();
        int innocentsAlive = 0;
        for (UUID u : inGame) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline() && p.getHealth() > 0 && !role.get(u).equals("murderer")) innocentsAlive++;
        }
        if (murdererDead) {
            awardWin("Innocents win! The murderer was eliminated.");
            return;
        }
        if (innocentsAlive == 0) {
            awardWin("Murderer wins! Everyone is dead.");
        }
    }

    private void awardWin(String msg) {
        if (!running) return;
        running = false;
        UUID m = murdererUuid();
        broadcast(ChatColor.GOLD + msg);
        if (m != null) {
            Player mp = Bukkit.getPlayer(m);
            if (mp != null && mp.getHealth() > 0) {
                mp.sendMessage(ChatColor.GREEN + "Victory! +50 Coins");
                com.tuan.coins.CoinsAPI.addCoins(m, 50);
            }
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

    private void endByTime() {
        if (!running) return;
        // time up: innocents win if murderer still alive (murderer failed)
        if (murdererAlive()) awardWin("Time up! Innocents survive — murderer loses.");
        else awardWin("Time up!");
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!running) return;
        if (!(e.getDamager() instanceof Player d) || !(e.getEntity() instanceof Player v)) return;
        if (!inGame.contains(d.getUniqueId()) || !inGame.contains(v.getUniqueId())) return;
        // murderer knife = melee sword, detective bow = projectile (handled separately maybe)
        lastHitBy.put(v.getUniqueId(), d.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        if (!inGame.contains(dead.getUniqueId())) return;
        e.setDeathMessage(ChatColor.RED + dead.getName() + " was killed!");
        dead.setGameMode(GameMode.SPECTATOR);
        UUID killer = lastHitBy.get(dead.getUniqueId());
        if (killer != null && role.get(killer) == "murderer") {
            Player kp = Bukkit.getPlayer(killer);
            if (kp != null) kp.sendMessage(ChatColor.GREEN + "+25 Coins for the kill");
            com.tuan.coins.CoinsAPI.addCoins(killer, 25);
        }
        Bukkit.getScheduler().runTask(this, this::checkWin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (inGame.contains(p.getUniqueId())) {
            inGame.remove(p.getUniqueId());
            returnLoc.remove(p.getUniqueId()); role.remove(p.getUniqueId());
            if (running) checkWin();
        }
    }
}
