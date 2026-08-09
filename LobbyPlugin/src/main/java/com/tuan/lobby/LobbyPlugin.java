package com.tuan.lobby;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.util.*;

public class LobbyPlugin extends JavaPlugin implements Listener, CommandExecutor {

    // game name -> join command
    static final String[][] GAMES = {
        {"SkyWars","skywars join"},{"Duels","duels join"},{"BedWars","bedwars join"},
        {"SkyBlock","cave create"},{"Build Battle","bb join"},{"Murder Mystery","mm join"},
        {"The Walls","walls join"},{"Mega Walls","mw join"},{"Arcade","arcade join"},
        {"Blitz","blitz join"},{"Smash Heroes","smash join"},{"TNT Games","tnt join"},
        {"Turbo Kart","kart join"},{"UHC","uhc join"},{"VampireZ","vampirez join"},
        {"Warlords","warlords join"},{"Arena Brawl","arenabrawl join"},{"Paintball","paintball join"},
        {"Quakecraft","quake join"}
    };

    static final int NPC_RADIUS = 8;
    static Location LOBBY;
    final Map<UUID, Location> npcLocs = new HashMap<>();
    final Set<UUID> npcEntities = new HashSet<>();
    final Map<UUID, String> inGame = new HashMap<>();  // player -> game label

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        var c = getCommand("lobby"); if (c != null) c.setExecutor(this);
        LOBBY = new Location(Bukkit.getWorlds().get(0), 0, 80, 0);
        Bukkit.getScheduler().runTaskLater(this, this::buildLobby, 20);
        // scoreboard refresh loop
        Bukkit.getScheduler().runTaskTimer(this, this::updateScoreboards, 20, 40);
        getLogger().info("LobbyPlugin v1.0.0 enabled");
    }

    @Override public void onDisable() {
        for (UUID e : new HashSet<>(npcEntities)) { Entity en = Bukkit.getEntity(e); if (en != null) en.remove(); }
        npcEntities.clear(); npcLocs.clear();
    }

    private void buildLobby() {
        World w = LOBBY.getWorld();
        // platform
        for (int dx = -12; dx <= 12; dx++)
            for (int dz = -12; dz <= 12; dz++) {
                double d = Math.hypot(dx, dz);
                var b = w.getBlockAt(LOBBY.getBlockX()+dx, 79, LOBBY.getBlockZ()+dz);
                b.setType(d <= 11 ? Material.STONE : Material.AIR);
                if (d <= 10) w.getBlockAt(LOBBY.getBlockX()+dx, 80, LOBBY.getBlockZ()+dz).setType(Material.AIR);
            }
        w.getBlockAt(0, 79, 0).setType(Material.OBSIDIAN);
        // hub portal: gold pressure plate at center edge -> return to lobby
        w.getBlockAt(0, 80, 11).setType(Material.GOLD_BLOCK);
        w.getBlockAt(0, 81, 11).setType(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        // spawn NPCs in ring
        int n = GAMES.length;
        for (int i = 0; i < n; i++) {
            double ang = (2*Math.PI*i)/n;
            int x = LOBBY.getBlockX() + (int)(Math.cos(ang)*NPC_RADIUS);
            int z = LOBBY.getBlockZ() + (int)(Math.sin(ang)*NPC_RADIUS);
            spawnNpc(GAMES[i][0], GAMES[i][1], new Location(w, x+0.5, 80, z+0.5));
        }
        getLogger().info("Lobby built with " + n + " game NPCs");
    }

    private void spawnNpc(String label, String cmd, Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        // pillar + item frame holding player head
        w.getBlockAt(bx, by, bz).setType(Material.BARRIER);
        ItemFrame frame = (ItemFrame) w.spawnEntity(new Location(w, bx+0.5, by+1.2, bz+0.5), EntityType.ITEM_FRAME);
        frame.setFacingDirection(org.bukkit.block.BlockFace.NORTH, true);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        try { var prof = Bukkit.createPlayerProfile(UUID.randomUUID(), label.replace(" ","")); sm.setOwnerProfile(prof); } catch (Exception e) {}
        head.setItemMeta(sm);
        frame.setItem(head);
        frame.setCustomName(ChatColor.GOLD + label);
        frame.setCustomNameVisible(true);
        npcEntities.add(frame.getUniqueId());
        npcLocs.put(frame.getUniqueId(), loc);
        frame.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(this, "cmd"), org.bukkit.persistence.PersistentDataType.STRING, cmd);
        frame.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(this, "label"), org.bukkit.persistence.PersistentDataType.STRING, label);
    }

    @EventHandler public void onNpcClick(PlayerInteractAtEntityEvent e) {
        if (e.getRightClicked() instanceof ItemFrame f && npcEntities.contains(f.getUniqueId())) {
            e.setCancelled(true);
            triggerNpc(e.getPlayer(), f);
        }
    }

    @EventHandler public void onNpcClick2(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof ItemFrame f && npcEntities.contains(f.getUniqueId())) {
            e.setCancelled(true);
            triggerNpc(e.getPlayer(), f);
        }
    }

    private void triggerNpc(Player p, ItemFrame f) {
        String cmd = f.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(this, "cmd"), org.bukkit.persistence.PersistentDataType.STRING);
        String label = f.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(this, "label"), org.bukkit.persistence.PersistentDataType.STRING);
        if (cmd == null) return;
        p.sendMessage(ChatColor.AQUA + "Joining " + label + "...");
        Bukkit.dispatchCommand(p, cmd);
        inGame.put(p.getUniqueId(), label);
    }

    @EventHandler public void onPortalStep(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        Block b = e.getClickedBlock();
        if (b != null && b.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            Player p = e.getPlayer();
            p.teleport(LOBBY.clone().add(0, 1, 0));
            p.sendMessage(ChatColor.GOLD + "Returned to Lobby!");
            inGame.remove(p.getUniqueId());
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        p.teleport(LOBBY.clone().add(0, 1, 0));
        p.setGameMode(GameMode.ADVENTURE);
        inGame.remove(p.getUniqueId());
        p.sendMessage(ChatColor.GOLD + "Welcome to Hypixel Clone Lobby!");
        p.sendMessage(ChatColor.GRAY + "Click a game NPC to play. Step on gold plate or /lobby to return.");
    }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        // if died in game, mark returned after respawn handled by game plugins
    }

    @Override public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Player only."); return true; }
        if (label.equalsIgnoreCase("lobby")) {
            p.teleport(LOBBY.clone().add(0, 1, 0));
            p.setGameMode(GameMode.ADVENTURE);
            inGame.remove(p.getUniqueId());
            p.sendMessage(ChatColor.GOLD + "Teleported to Lobby!");
            return true;
        }
        if (label.equalsIgnoreCase("play")) {
            if (a.length == 0) {
                p.sendMessage(ChatColor.GRAY + "Games: " + String.join(", ", Arrays.stream(GAMES).map(g->g[0]).toArray(String[]::new)));
                return true;
            }
            String want = String.join(" ", a);
            for (String[] g : GAMES) {
                if (g[0].equalsIgnoreCase(want)) {
                    p.sendMessage(ChatColor.AQUA + "Joining " + g[0] + "...");
                    Bukkit.dispatchCommand(p, g[1]);
                    inGame.put(p.getUniqueId(), g[0]);
                    return true;
                }
            }
            p.sendMessage(ChatColor.RED + "Unknown game: " + want);
            return true;
        }
        return false;
    }

    private void updateScoreboards() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = p.getScoreboard();
            if (sb == null || sb == Bukkit.getScoreboardManager().getMainScoreboard()) {
                sb = Bukkit.getScoreboardManager().getNewScoreboard();
                p.setScoreboard(sb);
            }
            NamespacedKey key = new NamespacedKey(this, "hypixel");
            Objective obj = sb.getObjective("hypixel");
            if (obj != null) obj.unregister();
            obj = sb.registerNewObjective("hypixel", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(ChatColor.GOLD + "Hypixel Clone");
            String game = inGame.getOrDefault(p.getUniqueId(), "Lobby");
            int coins = com.tuan.coins.CoinsAPI.getCoins(p.getUniqueId());
            int online = Bukkit.getOnlinePlayers().size();
            setLine(obj, 1, ChatColor.GRAY + "Coins: " + ChatColor.GOLD + coins);
            setLine(obj, 2, ChatColor.GRAY + "Players: " + ChatColor.AQUA + online);
            setLine(obj, 3, ChatColor.GRAY + "Game: " + ChatColor.GREEN + game);
            setLine(obj, 4, ChatColor.YELLOW + "/lobby to return");
        }
    }

    private void setLine(Objective obj, int slot, String text) {
        Score s = obj.getScore(text);
        s.setScore(slot);
    }
}
