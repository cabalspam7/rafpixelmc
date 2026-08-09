package com.tuan.social;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public class SocialPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    // friends: uuid -> set of friend uuids (mutual)
    private final Map<UUID, Set<UUID>> friends = new HashMap<>();
    // pending friend requests: target uuid -> requester uuid
    private final Map<UUID, UUID> friendReq = new HashMap<>();
    // parties: leader uuid -> set of member uuids (includes leader)
    private final Map<UUID, Set<UUID>> parties = new HashMap<>();
    // pending party invites: target -> leader
    private final Map<UUID, UUID> partyInvite = new HashMap<>();
    private File dataFile;

    @Override
    public void onEnable() {
        dataFile = new File(getDataFolder(), "social.json");
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        load();
        Bukkit.getPluginManager().registerEvents(this, this);
        var fc = getCommand("friend");
        var pc = getCommand("party");
        if (fc != null) fc.setExecutor(this);
        if (pc != null) pc.setExecutor(this);
        getLogger().info("SocialPlugin v1.0.0 enabled (" + friends.size() + " friends, " + parties.size() + " parties)");
    }

    @Override
    public void onDisable() {
        save();
    }

    private void load() {
        if (!dataFile.exists()) return;
        try (FileReader r = new FileReader(dataFile)) {
            JsonObject root = gson.fromJson(r, JsonObject.class);
            if (root == null) return;
            if (root.has("friends")) {
                for (var e : root.getAsJsonObject("friends").entrySet()) {
                    UUID u = UUID.fromString(e.getKey());
                    Set<UUID> set = new HashSet<>();
                    for (var f : e.getValue().getAsJsonArray()) set.add(UUID.fromString(f.getAsString()));
                    friends.put(u, set);
                }
            }
        } catch (Exception ex) {
            getLogger().warning("Failed to load social: " + ex.getMessage());
        }
    }

    private void save() {
        try {
            JsonObject root = new JsonObject();
            JsonObject fr = new JsonObject();
            for (var e : friends.entrySet()) {
                JsonArray arr = new JsonArray();
                for (UUID f : e.getValue()) arr.add(f.toString());
                fr.add(e.getKey().toString(), arr);
            }
            root.add("friends", fr);
            try (FileWriter w = new FileWriter(dataFile)) {
                gson.toJson(root, w);
            }
        } catch (Exception ex) {
            getLogger().warning("Failed to save social: " + ex.getMessage());
        }
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, this::save);
    }

    private String name(UUID u) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        return op.getName() != null ? op.getName() : u.toString().substring(0, 8);
    }

    private UUID uuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    // ============ COMMANDS ============
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Player only."); return true; }
        UUID me = p.getUniqueId();
        if (cmd.getName().equalsIgnoreCase("friend")) return friendCmd(p, me, args);
        if (cmd.getName().equalsIgnoreCase("party")) return partyCmd(p, me, args);
        return true;
    }

    private boolean friendCmd(Player p, UUID me, String[] args) {
        if (args.length < 1) {
            p.sendMessage(ChatColor.AQUA + "/friend <add|accept|remove|list|tp>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "/friend add <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { p.sendMessage(ChatColor.RED + "Player not online."); return true; }
                if (target.getUniqueId().equals(me)) { p.sendMessage(ChatColor.RED + "Can't friend yourself."); return true; }
                friendReq.put(target.getUniqueId(), me);
                p.sendMessage(ChatColor.GREEN + "Friend request sent to " + target.getName());
                target.sendMessage(ChatColor.YELLOW + p.getName() + " sent you a friend request. /friend accept " + p.getName());
            }
            case "accept" -> {
                UUID req = friendReq.remove(me);
                if (req == null) { p.sendMessage(ChatColor.RED + "No pending friend request."); return true; }
                friends.computeIfAbsent(me, k -> new HashSet<>()).add(req);
                friends.computeIfAbsent(req, k -> new HashSet<>()).add(me);
                Player rp = Bukkit.getPlayer(req);
                p.sendMessage(ChatColor.GREEN + "You are now friends with " + name(req));
                if (rp != null) rp.sendMessage(ChatColor.GREEN + "You are now friends with " + p.getName());
                saveAsync();
            }
            case "remove" -> {
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "/friend remove <player>"); return true; }
                UUID tu = uuidOf(args[1]);
                if (tu == null || !friends.getOrDefault(me, Set.of()).contains(tu)) {
                    p.sendMessage(ChatColor.RED + "Not in your friends list."); return true;
                }
                friends.get(me).remove(tu);
                if (friends.containsKey(tu)) friends.get(tu).remove(me);
                p.sendMessage(ChatColor.YELLOW + "Removed " + name(tu) + " from friends.");
                saveAsync();
            }
            case "list" -> {
                Set<UUID> fl = friends.getOrDefault(me, Set.of());
                p.sendMessage(ChatColor.AQUA + "=== Friends (" + fl.size() + ") ===");
                for (UUID f : fl) {
                    boolean online = Bukkit.getPlayer(f) != null;
                    p.sendMessage((online ? ChatColor.GREEN : ChatColor.GRAY) + "- " + name(f) + (online ? " [online]" : ""));
                }
            }
            case "tp" -> {
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "/friend tp <player>"); return true; }
                UUID tu = uuidOf(args[1]);
                if (tu == null || !friends.getOrDefault(me, Set.of()).contains(tu)) {
                    p.sendMessage(ChatColor.RED + "Not your friend."); return true;
                }
                Player tp = Bukkit.getPlayer(tu);
                if (tp == null) { p.sendMessage(ChatColor.RED + "Friend not online."); return true; }
                p.teleport(tp);
                p.sendMessage(ChatColor.GREEN + "Teleported to " + tp.getName());
            }
            default -> p.sendMessage(ChatColor.RED + "/friend <add|accept|remove|list|tp>");
        }
        return true;
    }

    private boolean partyCmd(Player p, UUID me, String[] args) {
        if (args.length < 1) {
            p.sendMessage(ChatColor.AQUA + "/party <invite|accept|leave|list|warp|disband>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "invite" -> {
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "/party invite <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { p.sendMessage(ChatColor.RED + "Player not online."); return true; }
                UUID leader = partyLeaderOf(me);
                if (leader == null) {
                    // create party, me is leader
                    Set<UUID> members = new HashSet<>();
                    members.add(me);
                    parties.put(me, members);
                    leader = me;
                } else if (!leader.equals(me)) {
                    p.sendMessage(ChatColor.RED + "Only party leader can invite."); return true;
                }
                partyInvite.put(target.getUniqueId(), leader);
                p.sendMessage(ChatColor.GREEN + "Invited " + target.getName() + " to the party.");
                target.sendMessage(ChatColor.YELLOW + p.getName() + " invited you to a party. /party accept");
            }
            case "accept" -> {
                UUID leader = partyInvite.remove(me);
                if (leader == null) { p.sendMessage(ChatColor.RED + "No pending party invite."); return true; }
                parties.computeIfAbsent(leader, k -> new HashSet<>()).add(me);
                p.sendMessage(ChatColor.GREEN + "Joined the party!");
                Player lp = Bukkit.getPlayer(leader);
                if (lp != null) lp.sendMessage(ChatColor.GREEN + p.getName() + " joined the party.");
            }
            case "leave" -> {
                UUID leader = partyLeaderOf(me);
                if (leader == null) { p.sendMessage(ChatColor.RED + "You are not in a party."); return true; }
                parties.get(leader).remove(me);
                p.sendMessage(ChatColor.YELLOW + "Left the party.");
                if (leader.equals(me)) {
                    // leader left -> disband or pass to next
                    if (parties.get(leader).isEmpty()) {
                        parties.remove(leader);
                    } else {
                        UUID newLeader = parties.get(leader).iterator().next();
                        Set<UUID> rest = parties.get(leader);
                        parties.remove(leader);
                        parties.put(newLeader, rest);
                        Player nl = Bukkit.getPlayer(newLeader);
                        if (nl != null) nl.sendMessage(ChatColor.GOLD + "You are now the party leader.");
                    }
                }
            }
            case "list" -> {
                UUID leader = partyLeaderOf(me);
                if (leader == null) { p.sendMessage(ChatColor.RED + "You are not in a party."); return true; }
                p.sendMessage(ChatColor.AQUA + "=== Party (leader: " + name(leader) + ") ===");
                for (UUID m : parties.get(leader)) {
                    boolean online = Bukkit.getPlayer(m) != null;
                    p.sendMessage((online ? ChatColor.GREEN : ChatColor.GRAY) + "- " + name(m));
                }
            }
            case "warp" -> {
                UUID leader = partyLeaderOf(me);
                if (leader == null) { p.sendMessage(ChatColor.RED + "Not in a party."); return true; }
                Player lp = Bukkit.getPlayer(leader);
                if (lp == null) { p.sendMessage(ChatColor.RED + "Leader not online."); return true; }
                for (UUID m : parties.get(leader)) {
                    Player mp = Bukkit.getPlayer(m);
                    if (mp != null) { mp.teleport(lp); mp.sendMessage(ChatColor.GREEN + "Warped to party leader."); }
                }
            }
            case "disband" -> {
                UUID leader = partyLeaderOf(me);
                if (leader == null) { p.sendMessage(ChatColor.RED + "Not in a party."); return true; }
                if (!leader.equals(me)) { p.sendMessage(ChatColor.RED + "Only leader can disband."); return true; }
                for (UUID m : parties.get(leader)) {
                    Player mp = Bukkit.getPlayer(m);
                    if (mp != null) mp.sendMessage(ChatColor.YELLOW + "Party disbanded.");
                }
                parties.remove(leader);
            }
            default -> p.sendMessage(ChatColor.RED + "/party <invite|accept|leave|list|warp|disband>");
        }
        return true;
    }

    private UUID uuidOf(String s) {
        UUID u = uuid(s);
        if (u != null) return u;
        Player p = Bukkit.getPlayer(s);
        return p != null ? p.getUniqueId() : null;
    }

    private UUID partyLeaderOf(UUID member) {
        for (var e : parties.entrySet()) {
            if (e.getValue().contains(member)) return e.getKey();
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // notify of pending friend requests
        if (friendReq.containsKey(p.getUniqueId())) {
            p.sendMessage(ChatColor.YELLOW + "You have a friend request from " + name(friendReq.get(p.getUniqueId())) + ". /friend accept");
        }
        if (partyInvite.containsKey(p.getUniqueId())) {
            p.sendMessage(ChatColor.YELLOW + "You have a party invite. /party accept");
        }
    }
}
