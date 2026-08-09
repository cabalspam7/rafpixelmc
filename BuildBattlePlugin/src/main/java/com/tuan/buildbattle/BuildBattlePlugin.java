package com.tuan.buildbattle;

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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BuildBattlePlugin extends JavaPlugin implements Listener, CommandExecutor {

    private static final int PLOT = 7;            // plot size (x,z)
    private static final int SPACING = 12;        // distance between plots
    private static final int BUILD_SECONDS = 60;
    private static final int VOTE_SECONDS = 30;

    private final List<String> THEMES = Arrays.asList(
            "A House", "A Tree", "A Car", "A Castle", "A Robot", "A Cat", "A Boat", "Space", "A Flower", "A Sword"
    );

    private World world;
    private final List<UUID> inGame = new ArrayList<>();
    private final Map<UUID, Location> returnLoc = new HashMap<>();
    private final Map<UUID, Location> plotCorner = new HashMap<>();
    private final Map<UUID, Integer> votes = new HashMap<>();
    private final Map<UUID, UUID> votedFor = new HashMap<>();
    private String currentTheme = "";
    private Phase phase = Phase.IDLE;
    private int task = -1;

    enum Phase { IDLE, BUILD, VOTE }

    @Override
    public void onEnable() {
        world = Bukkit.getWorlds().get(0);
        Bukkit.getPluginManager().registerEvents(this, this);
        var c = getCommand("bb");
        if (c != null) c.setExecutor(this);
        getLogger().info("BuildBattlePlugin v1.0.0 enabled");
    }

    @Override
    public void onDisable() {
        if (task != -1) Bukkit.getScheduler().cancelTask(task);
        for (UUID u : new ArrayList<>(inGame)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && returnLoc.containsKey(u)) p.teleport(returnLoc.get(u));
        }
        inGame.clear(); returnLoc.clear(); plotCorner.clear(); votes.clear(); votedFor.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Player only."); return true; }
        UUID me = p.getUniqueId();
        if (args.length == 0) {
            p.sendMessage(ChatColor.AQUA + "=== Build Battle ===");
            p.sendMessage(ChatColor.GRAY + "/bb join | leave | start | vote <player> | theme");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "join" -> {
                if (phase != Phase.IDLE) { p.sendMessage(ChatColor.RED + "Game in progress."); return true; }
                if (inGame.contains(me)) { p.sendMessage(ChatColor.YELLOW + "Already joined."); return true; }
                returnLoc.put(me, p.getLocation().clone());
                inGame.add(me);
                p.sendMessage(ChatColor.AQUA + "Joined Build Battle! (" + inGame.size() + ")");
                if (inGame.size() == 1) {
                    p.sendMessage(ChatColor.GRAY + "Waiting for more players... (need 2+, auto-start 30s)");
                    task = Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (inGame.size() >= 2) startBuild(); else { p.sendMessage(ChatColor.RED + "Not enough players."); reset(); }
                    }, 30 * 20).getTaskId();
                }
            }
            case "leave" -> {
                inGame.remove(me);
                if (returnLoc.containsKey(me)) p.teleport(returnLoc.get(me));
                returnLoc.remove(me); plotCorner.remove(me);
                p.sendMessage(ChatColor.YELLOW + "Left Build Battle.");
            }
            case "start" -> {
                if (!p.hasPermission("hypixel.admin")) { p.sendMessage(ChatColor.RED + "No permission."); return true; }
                if (inGame.size() < 2) { p.sendMessage(ChatColor.RED + "Need 2+."); return true; }
                if (task != -1) { Bukkit.getScheduler().cancelTask(task); task = -1; }
                startBuild();
            }
            case "vote" -> {
                if (phase != Phase.VOTE) { p.sendMessage(ChatColor.RED + "Not in vote phase."); return true; }
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "/bb vote <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !inGame.contains(target.getUniqueId())) { p.sendMessage(ChatColor.RED + "Player not in game."); return true; }
                if (target.getUniqueId().equals(me)) { p.sendMessage(ChatColor.RED + "Can't vote yourself."); return true; }
                if (votedFor.containsKey(me)) { p.sendMessage(ChatColor.RED + "Already voted."); return true; }
                votes.put(target.getUniqueId(), votes.getOrDefault(target.getUniqueId(), 0) + 1);
                votedFor.put(me, target.getUniqueId());
                p.sendMessage(ChatColor.GREEN + "Voted for " + target.getName() + "!");
            }
            case "theme" -> {
                if (phase == Phase.IDLE) p.sendMessage(ChatColor.GRAY + "No active theme.");
                else p.sendMessage(ChatColor.GOLD + "Theme: " + currentTheme);
            }
            default -> p.sendMessage(ChatColor.RED + "/bb <join|leave|start|vote|theme>");
        }
        return true;
    }

    private void reset() {
        if (task != -1) { Bukkit.getScheduler().cancelTask(task); task = -1; }
        inGame.clear(); returnLoc.clear(); plotCorner.clear(); votes.clear(); votedFor.clear();
        phase = Phase.IDLE;
    }

    private void startBuild() {
        if (inGame.size() < 2) return;
        phase = Phase.BUILD;
        currentTheme = THEMES.get(new Random().nextInt(THEMES.size()));
        // assign plots radially
        int n = inGame.size();
        for (int i = 0; i < n; i++) {
            UUID u = inGame.get(i);
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            double angle = (2 * Math.PI * i) / Math.max(n, 1);
            int cx = (int) (Math.cos(angle) * SPACING * (n > 1 ? 1 : 0));
            int cz = (int) (Math.sin(angle) * SPACING * (n > 1 ? 1 : 0));
            clearPlot(cx, cz);
            Location corner = new Location(world, cx - PLOT / 2, 70, cz - PLOT / 2);
            plotCorner.put(u, corner);
            p.teleport(corner.clone().add(PLOT / 2.0, 1, PLOT / 2.0));
            p.setGameMode(GameMode.SURVIVAL);
            p.setAllowFlight(true);
            p.setFlying(true);
            p.getInventory().clear();
            giveBlocks(p);
            p.sendMessage(ChatColor.GOLD + "THEME: " + currentTheme);
            p.sendMessage(ChatColor.AQUA + "Build for " + BUILD_SECONDS + "s! Fly enabled.");
        }
        broadcast(ChatColor.GOLD + "Build Battle started! Theme: " + currentTheme);
        task = Bukkit.getScheduler().runTaskLater(this, this::startVote, BUILD_SECONDS * 20).getTaskId();
    }

    private void giveBlocks(Player p) {
        Material[] palette = {Material.WHITE_WOOL, Material.RED_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL,
                Material.YELLOW_WOOL, Material.BLACK_WOOL, Material.OAK_PLANKS, Material.STONE,
                Material.GLASS, Material.BRICKS, Material.SANDSTONE, Material.QUARTZ_BLOCK};
        for (Material m : palette) p.getInventory().addItem(new ItemStack(m, 64));
        p.getInventory().addItem(new ItemStack(Material.DIAMOND_PICKAXE));
    }

    private void clearPlot(int cx, int cz) {
        int x0 = cx - PLOT / 2, z0 = cz - PLOT / 2;
        for (int dx = 0; dx < PLOT; dx++)
            for (int dz = 0; dz < PLOT; dz++)
                for (int dy = 0; dy <= 6; dy++) {
                    var blk = world.getBlockAt(x0 + dx, 70 + dy, z0 + dz);
                    if (dy == 0) blk.setType(Material.GRASS_BLOCK);
                    else blk.setType(Material.AIR);
                }
    }

    private void startVote() {
        phase = Phase.VOTE;
        votes.clear(); votedFor.clear();
        for (UUID u : inGame) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            p.setFlying(false); p.setAllowFlight(false);
            p.setGameMode(GameMode.ADVENTURE);
        }
        broadcast(ChatColor.GOLD + "VOTING! Use /bb vote <player> for " + VOTE_SECONDS + "s");
        task = Bukkit.getScheduler().runTaskLater(this, this::endGame, VOTE_SECONDS * 20).getTaskId();
    }

    private void endGame() {
        UUID winner = null; int best = -1;
        for (var e : votes.entrySet()) {
            if (e.getValue() > best) { best = e.getValue(); winner = e.getKey(); }
        }
        if (winner != null) {
            Player wp = Bukkit.getPlayer(winner);
            broadcast(ChatColor.GOLD + (wp != null ? wp.getName() : winner.toString()) + " wins Build Battle with " + best + " votes!");
            if (wp != null) {
                wp.sendMessage(ChatColor.GREEN + "Victory! +50 Coins");
                com.tuan.coins.CoinsAPI.addCoins(winner, 50);
            }
        } else {
            broadcast(ChatColor.GRAY + "Build Battle ended - no votes.");
        }
        // return players
        for (UUID u : new ArrayList<>(inGame)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                if (returnLoc.containsKey(u)) p.teleport(returnLoc.get(u));
                p.setGameMode(GameMode.ADVENTURE);
                p.setFlying(false); p.setAllowFlight(false);
                p.getInventory().clear();
            }
        }
        reset();
    }

    private void broadcast(String msg) {
        for (UUID u : inGame) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(msg);
        }
    }

    // restrict block edits to own plot during build
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (phase != Phase.BUILD) return;
        Player p = e.getPlayer();
        if (!inGame.contains(p.getUniqueId())) return;
        Location c = plotCorner.get(p.getUniqueId());
        if (c == null) return;
        Location b = e.getBlock().getLocation();
        if (Math.abs(b.getBlockX() - c.getBlockX()) > PLOT || Math.abs(b.getBlockZ() - c.getBlockZ()) > PLOT
                || b.getBlockY() < 70 || b.getBlockY() > 80) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (phase != Phase.BUILD) return;
        Player p = e.getPlayer();
        if (!inGame.contains(p.getUniqueId())) return;
        Location c = plotCorner.get(p.getUniqueId());
        if (c == null) return;
        Location b = e.getBlock().getLocation();
        if (Math.abs(b.getBlockX() - c.getBlockX()) > PLOT || Math.abs(b.getBlockZ() - c.getBlockZ()) > PLOT
                || b.getBlockY() < 70 || b.getBlockY() > 80) {
            e.setCancelled(true);
        }
    }
}
