package com.tuan.more;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public class HypixelMorePlugin extends JavaPlugin implements Listener, CommandExecutor {

    static class Mode {
        final String id, label, cmd;
        final ChatColor color;
        final int teams;            // 0 = free-for-all
        final int prep;             // seconds
        final int fight;            // seconds
        final int coins;
        final String[] kit;         // material names
        final String flavor;
        Mode(String id, String label, String cmd, ChatColor color, int teams, int prep, int fight, int coins, String[] kit, String flavor) {
            this.id=id; this.label=label; this.cmd=cmd; this.color=color; this.teams=teams;
            this.prep=prep; this.fight=fight; this.coins=coins; this.kit=kit; this.flavor=flavor;
        }
    }

    static final Mode[] MODES = {
        new Mode("arcade","Arcade","arcade",ChatColor.LIGHT_PURPLE,0,20,90,40,new String[]{"IRON_SWORD","SNOWBALL"},"Mixed mini-games FFA"),
        new Mode("blitz","Blitz Survival","blitz",ChatColor.GOLD,0,20,120,50,new String[]{"DIAMOND_SWORD","BOW","ARROW","GOLDEN_APPLE"},"Kit PvP FFA"),
        new Mode("smash","Smash Heroes","smash",ChatColor.AQUA,2,15,90,50,new String[]{"IRON_SWORD","FEATHER"},"Knockback 2-team"),
        new Mode("tnt","TNT Games","tnt",ChatColor.RED,0,15,100,40,new String[]{"TNT","FLINT_AND_STEEL"},"TNT run FFA"),
        new Mode("kart","Turbo Kart","kart",ChatColor.GREEN,0,15,120,40,new String[]{"CARROT_ON_A_STICK"},"Race to finish"),
        new Mode("uhc","UHC","uhc",ChatColor.DARK_RED,4,30,180,75,new String[]{"IRON_SWORD","BOW","ARROW"},"No regen 4-team"),
        new Mode("vampirez","VampireZ","vampirez",ChatColor.DARK_PURPLE,2,20,150,50,new String[]{"WOODEN_SWORD","PORKCHOP"},"Human vs Vampire"),
        new Mode("warlords","Warlords","warlords",ChatColor.BLUE,2,20,120,75,new String[]{"DIAMOND_SWORD","BOW","ARROW"},"Class PvP 2-team"),
        new Mode("arenabrawl","Arena Brawl","arenabrawl",ChatColor.YELLOW,2,15,90,40,new String[]{"IRON_SWORD","BREAD"},"Team arena 2-team"),
        new Mode("paintball","Paintball","paintball",ChatColor.DARK_AQUA,2,15,90,40,new String[]{"SNOWBALL","LEATHER_CHESTPLATE"},"Snowball 2-team"),
        new Mode("quake","Quakecraft","quake",ChatColor.DARK_GREEN,0,15,90,40,new String[]{"IRON_HOE"},"Railgun FFA"),
    };

    static class GM {
        final Mode m;
        final List<UUID> in = new ArrayList<>();
        final Map<UUID,Location> ret = new HashMap<>();
        final Map<UUID,Integer> team = new HashMap<>();
        boolean running = false;
        int task = -1;
        GM(Mode m){this.m=m;}
    }

    final Map<String,GM> games = new HashMap<>();
    World world;
    final Map<String,Integer> centers = new HashMap<>();  // mode -> x offset

    @Override public void onEnable() {
        world = Bukkit.getWorlds().get(0);
        int off = 900;
        for (Mode m : MODES) { games.put(m.cmd, new GM(m)); centers.put(m.cmd, off); off += 100; }
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Mode m : MODES) { org.bukkit.command.PluginCommand c = getCommand(m.cmd); if (c != null) c.setExecutor(this); }
        getLogger().info("HypixelMore v1.0.0 enabled - " + MODES.length + " modes");
    }

    @Override public void onDisable() {
        for (GM g : games.values()) { if (g.task != -1) Bukkit.getScheduler().cancelTask(g.task); cleanup(g); }
    }

    @Override public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Player only."); return true; }
        String c = cmd.getName().toLowerCase();
        GM g = games.get(c);
        if (g == null) return true;
        Mode m = g.m;
        UUID me = p.getUniqueId();
        if (a.length == 0) {
            p.sendMessage(m.color + "=== " + m.label + " === " + m.flavor);
            p.sendMessage(ChatColor.GRAY + "/" + c + " join | leave | start");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "join" -> {
                if (g.running) { p.sendMessage(ChatColor.RED + "In progress."); return true; }
                if (g.in.contains(me)) { p.sendMessage(ChatColor.YELLOW + "Already in."); return true; }
                g.ret.put(me, p.getLocation().clone());
                g.in.add(me);
                p.sendMessage(m.color + "Joined " + m.label + "! (" + g.in.size() + ")");
                if (g.in.size() == 1) {
                    p.sendMessage(ChatColor.GRAY + "Auto-start 20s or /" + c + " start");
                    g.task = Bukkit.getScheduler().runTaskLater(this, () -> { if (g.in.size() >= 2) start(g); else { p.sendMessage(ChatColor.RED+"Not enough."); reset(g);} }, 20*20).getTaskId();
                }
            }
            case "leave" -> {
                g.in.remove(me);
                if (g.ret.containsKey(me)) p.teleport(g.ret.get(me));
                g.ret.remove(me); g.team.remove(me);
                p.sendMessage(ChatColor.YELLOW + "Left " + m.label + ".");
            }
            case "start" -> {
                if (!p.hasPermission("hypixel.admin")) { p.sendMessage(ChatColor.RED+"No perm."); return true; }
                if (g.in.size() < 2) { p.sendMessage(ChatColor.RED+"Need 2+."); return true; }
                if (g.task != -1) { Bukkit.getScheduler().cancelTask(g.task); g.task = -1; }
                start(g);
            }
            default -> p.sendMessage(ChatColor.RED + "/" + c + " <join|leave|start>");
        }
        return true;
    }

    private void reset(GM g) {
        if (g.task != -1) { Bukkit.getScheduler().cancelTask(g.task); g.task = -1; }
        g.in.clear(); g.ret.clear(); g.team.clear(); g.running = false;
    }

    private void cleanup(GM g) {
        for (UUID u : new ArrayList<>(g.in)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                if (g.ret.containsKey(u)) p.teleport(g.ret.get(u));
                p.setGameMode(GameMode.ADVENTURE);
                p.setMaxHealth(20); p.setHealth(20);
                p.getInventory().clear();
            }
        }
        reset(g);
    }

    private void start(GM g) {
        if (g.in.size() < 2) return;
        g.running = true;
        int baseX = centers.get(g.m.cmd);
        for (int i = 0; i < g.in.size(); i++) {
            UUID u = g.in.get(i);
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            int t = (g.m.teams > 0) ? i % g.m.teams : -1;
            g.team.put(u, t);
            double ang = (2*Math.PI*i)/Math.max(g.in.size(), 1);
            int r = 12;
            int x = baseX + (int)(Math.cos(ang)*r);
            int z = (int)(Math.sin(ang)*r);
            p.teleport(new Location(world, x, 81, z));
            p.setGameMode(GameMode.SURVIVAL);
            if (g.m.id.equals("uhc")) { p.setHealth(20); } else { p.setHealth(20); }
            p.setFoodLevel(20);
            if (g.m.id.equals("uhc")) p.setHealth(20);
            p.getInventory().clear();
            giveKit(p, g.m);
            String where = (t >= 0) ? (t==0?ChatColor.RED+"Team Red":ChatColor.BLUE+"Team Blue") : ChatColor.GRAY+"FFA";
            p.sendMessage(g.m.color + "[" + g.m.label + "] " + where + " - " + g.m.flavor);
            if (g.m.id.equals("quake")) p.sendMessage(ChatColor.GRAY+"Railgun: left-click to fire!");
        }
        broadcast(g, g.m.color + g.m.label + " started! (" + g.in.size() + " players)");
        g.task = Bukkit.getScheduler().runTaskLater(this, () -> endByTime(g), g.m.fight * 20).getTaskId();
    }

    private void giveKit(Player p, Mode m) {
        for (String k : m.kit) {
            try { p.getInventory().addItem(new ItemStack(Material.valueOf(k))); }
            catch (Exception e) { /* ignore bad mat */ }
        }
        if (m.id.equals("quake")) p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 99999, 1));
    }

    private void broadcast(GM g, String msg) {
        for (UUID u : g.in) { Player p = Bukkit.getPlayer(u); if (p != null) p.sendMessage(msg); }
    }

    private int aliveTeams(GM g) {
        Set<Integer> alive = new HashSet<>();
        for (UUID u : g.in) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline() && p.getGameMode() != GameMode.SPECTATOR && p.getHealth() > 0) alive.add(g.team.get(u));
        }
        return alive.size();
    }

    private void checkWin(GM g) {
        if (!g.running) return;
        if (aliveTeams(g) <= 1) {
            UUID winner = null;
            for (UUID u : g.in) { Player p = Bukkit.getPlayer(u); if (p != null && p.isOnline() && p.getHealth() > 0) { winner = u; break; } }
            endGame(g, winner);
        }
    }

    private void endByTime(GM g) { if (g.running) { UUID last=null; for (UUID u:g.in){Player p=Bukkit.getPlayer(u);if(p!=null&&p.isOnline()&&p.getHealth()>0){last=u;break;}} endGame(g, last); } }

    private void endGame(GM g, UUID winner) {
        if (!g.running) return;
        g.running = false;
        if (winner != null) {
            Player wp = Bukkit.getPlayer(winner);
            broadcast(g, g.m.color + g.m.label + " won by " + (wp != null ? wp.getName() : winner) + "! +" + g.m.coins + " Coins");
            com.tuan.coins.CoinsAPI.addCoins(winner, g.m.coins);
        } else broadcast(g, ChatColor.GRAY + g.m.label + " ended - draw.");
        for (UUID u : new ArrayList<>(g.in)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                if (g.ret.containsKey(u)) p.teleport(g.ret.get(u));
                p.setGameMode(GameMode.ADVENTURE);
                p.setMaxHealth(20); p.setHealth(20);
                p.getInventory().clear();
                for (org.bukkit.potion.PotionEffect ef : p.getActivePotionEffects()) p.removePotionEffect(ef.getType());
            }
        }
        reset(g);
    }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        Player d = e.getEntity();
        for (GM g : games.values()) {
            if (g.in.contains(d.getUniqueId())) {
                e.setDeathMessage(ChatColor.RED + d.getName() + " eliminated in " + g.m.label + "!");
                d.setGameMode(GameMode.SPECTATOR);
                Bukkit.getScheduler().runTask(this, () -> checkWin(g));
                return;
            }
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        for (GM g : games.values()) {
            if (g.in.contains(p.getUniqueId())) {
                g.in.remove(p.getUniqueId()); g.ret.remove(p.getUniqueId()); g.team.remove(p.getUniqueId());
                if (g.running) checkWin(g);
                return;
            }
        }
    }
}
