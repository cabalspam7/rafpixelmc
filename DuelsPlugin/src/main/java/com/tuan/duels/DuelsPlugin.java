package com.tuan.duels;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DuelsPlugin extends JavaPlugin implements Listener {

    // queue of players waiting
    private UUID queued = null;
    // active matches: player -> opponent
    private final Map<UUID, UUID> matches = new HashMap<>();
    // player -> return location (pre-match)
    private final Map<UUID, Location> returnLoc = new HashMap<>();
    // player -> match arena id
    private final Map<UUID, Integer> arenaOf = new HashMap<>();

    // Two 1v1 arenas (simple platforms) — initialized in onEnable when worlds exist
    private Location[] arenas;
    private int arenaCycle = 0;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("duels");
        if (cmd != null) cmd.setExecutor(new DuelsCommand());
        this.arenas = new Location[]{
            new Location(Bukkit.getWorlds().get(0), 100, 80, 0),
            new Location(Bukkit.getWorlds().get(0), -100, 80, 0)
        };
        buildArenas();
        getLogger().info("DuelsPlugin v1.0.0 enabled (custom 1v1)");
    }

    @Override
    public void onDisable() {
        // return any in-match players
        for (UUID u : matches.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && returnLoc.containsKey(u)) p.teleport(returnLoc.get(u));
        }
        matches.clear();
    }

    private void buildArenas() {
        for (Location base : arenas) {
            World w = base.getWorld();
            int bx = base.getBlockX(), bz = base.getBlockZ(), by = base.getBlockY() - 1;
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    w.getBlockAt(bx + dx, by, bz + dz).setType(Material.QUARTZ_BLOCK);
                }
            }
            // border glass
            for (int dx = -5; dx <= 5; dx++) {
                w.getBlockAt(bx + dx, by + 1, bz - 5).setType(Material.GLASS);
                w.getBlockAt(bx + dx, by + 1, bz + 5).setType(Material.GLASS);
                w.getBlockAt(bx - 5, by + 1, bz + dx).setType(Material.GLASS);
                w.getBlockAt(bx + 5, by + 1, bz + dx).setType(Material.GLASS);
            }
        }
    }

    private Location arenaSpawn(Location base, boolean side) {
        int off = side ? 4 : -4;
        return new Location(base.getWorld(), base.getBlockX() + off, base.getBlockY(), base.getBlockZ());
    }

    private void giveKit(Player p) {
        p.getInventory().clear();
        p.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD));
        p.getInventory().addItem(new ItemStack(Material.BOW));
        p.getInventory().addItem(new ItemStack(Material.ARROW, 32));
        p.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        p.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        p.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        for (int i = 0; i < 32; i++) p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE));
    }

    public class DuelsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Player only.");
                return true;
            }
            if (args.length == 0) {
                p.sendMessage(ChatColor.GOLD + "=== Duels 1v1 ===");
                p.sendMessage(ChatColor.GRAY + "/duels join - queue for a 1v1 match");
                p.sendMessage(ChatColor.GRAY + "/duels leave - leave queue");
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "join" -> {
                    if (matches.containsKey(p.getUniqueId())) {
                        p.sendMessage(ChatColor.RED + "You're already in a duel!");
                        return true;
                    }
                    if (queued != null && queued.equals(p.getUniqueId())) {
                        p.sendMessage(ChatColor.YELLOW + "Already in queue.");
                        return true;
                    }
                    if (queued == null) {
                        queued = p.getUniqueId();
                        p.sendMessage(ChatColor.YELLOW + "Searching for opponent...");
                    } else {
                        // start match
                        Player opp = Bukkit.getPlayer(queued);
                        queued = null;
                        if (opp == null || !opp.isOnline()) {
                            queued = p.getUniqueId();
                            p.sendMessage(ChatColor.YELLOW + "Searching for opponent...");
                            return true;
                        }
                        startMatch(p, opp);
                    }
                }
                case "leave" -> {
                    if (queued != null && queued.equals(p.getUniqueId())) {
                        queued = null;
                        p.sendMessage(ChatColor.YELLOW + "Left queue.");
                    } else {
                        p.sendMessage(ChatColor.RED + "Not in queue.");
                    }
                }
                default -> p.sendMessage(ChatColor.RED + "Unknown. Use /duels join");
            }
            return true;
        }
    }

    private void startMatch(Player a, Player b) {
        int arenaId = arenaCycle;
        arenaCycle = (arenaCycle + 1) % arenas.length;
        Location base = arenas[arenaId];

        returnLoc.put(a.getUniqueId(), a.getLocation().clone());
        returnLoc.put(b.getUniqueId(), b.getLocation().clone());

        matches.put(a.getUniqueId(), b.getUniqueId());
        matches.put(b.getUniqueId(), a.getUniqueId());
        arenaOf.put(a.getUniqueId(), arenaId);
        arenaOf.put(b.getUniqueId(), arenaId);

        a.teleport(arenaSpawn(base, false));
        b.teleport(arenaSpawn(base, true));
        for (Player p : new Player[]{a, b}) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20);
            p.setFoodLevel(20);
            giveKit(p);
            p.sendMessage(ChatColor.GOLD + "Duel start! Last one standing wins!");
        }
    }

    private void endMatch(Player winner, Player loser) {
        if (winner != null) {
            winner.sendMessage(ChatColor.GREEN + "You won the duel! +25 Coins");
            com.tuan.coins.CoinsAPI.addCoins(winner.getUniqueId(), 25);
            if (returnLoc.containsKey(winner.getUniqueId())) {
                winner.teleport(returnLoc.get(winner.getUniqueId()));
            }
            winner.getInventory().clear();
        }
        if (loser != null) {
            loser.sendMessage(ChatColor.RED + "You lost the duel!");
            if (returnLoc.containsKey(loser.getUniqueId())) {
                loser.teleport(returnLoc.get(loser.getUniqueId()));
            }
            loser.getInventory().clear();
        }
        if (winner != null) {
            matches.remove(winner.getUniqueId());
            matches.remove(loser != null ? loser.getUniqueId() : winner.getUniqueId());
        }
        cleanup(winner);
        cleanup(loser);
    }

    private void cleanup(Player p) {
        if (p == null) return;
        returnLoc.remove(p.getUniqueId());
        arenaOf.remove(p.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        UUID oppUuid = matches.get(dead.getUniqueId());
        if (oppUuid == null) return; // not in duel
        Player killer = dead.getKiller();
        Player winner = (killer != null && matches.containsKey(killer.getUniqueId())) ? killer : Bukkit.getPlayer(oppUuid);
        e.setDeathMessage(ChatColor.RED + dead.getName() + " was defeated in a duel!");
        // defer end to next tick to avoid teleport-during-death issues
        final Player fWinner = winner;
        Bukkit.getScheduler().runTask(this, () -> endMatch(fWinner, dead));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID oppUuid = matches.get(p.getUniqueId());
        if (oppUuid != null) {
            Player opp = Bukkit.getPlayer(oppUuid);
            if (opp != null) {
                opp.sendMessage(ChatColor.YELLOW + p.getName() + " left — you win by default!");
                endMatch(opp, p);
            }
        }
    }
}
