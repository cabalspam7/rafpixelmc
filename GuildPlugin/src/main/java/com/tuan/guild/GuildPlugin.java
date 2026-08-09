package com.tuan.guild;

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

public class GuildPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // guild id -> guild data
    private static class Guild {
        String id;
        String name;
        String tag;
        UUID owner;
        Set<UUID> members = new HashSet<>();
        long coins = 0;
    }

    private final Map<String, Guild> guilds = new HashMap<>();      // guildId -> guild
    private final Map<UUID, String> memberGuild = new HashMap<>();  // player -> guildId
    private final Map<UUID, UUID> inviteFrom = new HashMap<>();     // target -> guild owner (inviter)
    private File dataFile;

    @Override
    public void onEnable() {
        dataFile = new File(getDataFolder(), "guilds.json");
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        load();
        Bukkit.getPluginManager().registerEvents(this, this);
        var c = getCommand("guild");
        if (c != null) c.setExecutor(this);
        getLogger().info("GuildPlugin v1.0.0 enabled (" + guilds.size() + " guilds)");
    }

    @Override
    public void onDisable() { save(); }

    private void load() {
        if (!dataFile.exists()) return;
        try (FileReader r = new FileReader(dataFile)) {
            JsonObject root = gson.fromJson(r, JsonObject.class);
            if (root == null) return;
            if (root.has("guilds")) {
                for (var e : root.getAsJsonObject("guilds").entrySet()) {
                    JsonObject g = e.getValue().getAsJsonObject();
                    Guild guild = new Guild();
                    guild.id = e.getKey();
                    guild.name = g.has("name") ? g.get("name").getAsString() : guild.id;
                    guild.tag = g.has("tag") ? g.get("tag").getAsString() : "";
                    guild.owner = UUID.fromString(g.get("owner").getAsString());
                    guild.coins = g.has("coins") ? g.get("coins").getAsLong() : 0;
                    for (var m : g.getAsJsonArray("members")) guild.members.add(UUID.fromString(m.getAsString()));
                    guilds.put(guild.id, guild);
                    for (UUID m : guild.members) memberGuild.put(m, guild.id);
                }
            }
        } catch (Exception ex) {
            getLogger().warning("Failed to load guilds: " + ex.getMessage());
        }
    }

    private void save() {
        try {
            JsonObject root = new JsonObject();
            JsonObject gs = new JsonObject();
            for (Guild g : guilds.values()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", g.name);
                o.addProperty("tag", g.tag);
                o.addProperty("owner", g.owner.toString());
                o.addProperty("coins", g.coins);
                JsonArray arr = new JsonArray();
                for (UUID m : g.members) arr.add(m.toString());
                o.add("members", arr);
                gs.add(g.id, o);
            }
            root.add("guilds", gs);
            try (FileWriter w = new FileWriter(dataFile)) { gson.toJson(root, w); }
        } catch (Exception ex) {
            getLogger().warning("Failed to save guilds: " + ex.getMessage());
        }
    }

    private void saveAsync() { Bukkit.getScheduler().runTaskAsynchronously(this, this::save); }

    private String name(UUID u) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        return op.getName() != null ? op.getName() : u.toString().substring(0, 8);
    }

    private String newId() { return "g" + System.currentTimeMillis(); }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Player only."); return true; }
        UUID me = p.getUniqueId();
        if (args.length < 1) {
            p.sendMessage(ChatColor.AQUA + "/guild <create|invite|accept|leave|info|list|disband>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "/guild create <name> [tag]"); return true; }
                if (memberGuild.containsKey(me)) { p.sendMessage(ChatColor.RED + "You are already in a guild."); return true; }
                String gname = args[1];
                String tag = args.length > 2 ? args[2].toUpperCase().substring(0, Math.min(4, args[2].length())) : "";
                String gid = newId();
                Guild g = new Guild();
                g.id = gid; g.name = gname; g.tag = tag; g.owner = me; g.members.add(me);
                guilds.put(gid, g);
                memberGuild.put(me, gid);
                saveAsync();
                p.sendMessage(ChatColor.GREEN + "Guild '" + gname + "' created! Tag: [" + tag + "]");
            }
            case "invite" -> {
                String gid = memberGuild.get(me);
                if (gid == null) { p.sendMessage(ChatColor.RED + "You are not in a guild."); return true; }
                Guild g = guilds.get(gid);
                if (!g.owner.equals(me)) { p.sendMessage(ChatColor.RED + "Only guild owner can invite."); return true; }
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "/guild invite <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { p.sendMessage(ChatColor.RED + "Player not online."); return true; }
                inviteFrom.put(target.getUniqueId(), me);
                p.sendMessage(ChatColor.GREEN + "Invited " + target.getName() + " to the guild.");
                target.sendMessage(ChatColor.YELLOW + p.getName() + " invited you to guild '" + g.name + "'. /guild accept");
            }
            case "accept" -> {
                UUID inviter = inviteFrom.remove(me);
                if (inviter == null) { p.sendMessage(ChatColor.RED + "No pending guild invite."); return true; }
                // find guild owned by inviter
                String gid = null;
                for (Guild g : guilds.values()) if (g.owner.equals(inviter)) { gid = g.id; break; }
                if (gid == null) { p.sendMessage(ChatColor.RED + "Guild no longer exists."); return true; }
                Guild g = guilds.get(gid);
                g.members.add(me);
                memberGuild.put(me, gid);
                saveAsync();
                p.sendMessage(ChatColor.GREEN + "Joined guild '" + g.name + "'!");
                Player op = Bukkit.getPlayer(inviter);
                if (op != null) op.sendMessage(ChatColor.GREEN + p.getName() + " joined the guild.");
            }
            case "leave" -> {
                String gid = memberGuild.get(me);
                if (gid == null) { p.sendMessage(ChatColor.RED + "Not in a guild."); return true; }
                Guild g = guilds.get(gid);
                if (g.owner.equals(me)) { p.sendMessage(ChatColor.RED + "Owner cannot leave. Use /guild disband."); return true; }
                g.members.remove(me);
                memberGuild.remove(me);
                saveAsync();
                p.sendMessage(ChatColor.YELLOW + "Left guild '" + g.name + "'.");
            }
            case "info" -> {
                String gid = memberGuild.get(me);
                if (gid == null) { p.sendMessage(ChatColor.RED + "Not in a guild."); return true; }
                Guild g = guilds.get(gid);
                p.sendMessage(ChatColor.GOLD + "=== Guild " + g.name + " [" + g.tag + "] ===");
                p.sendMessage(ChatColor.GRAY + "Owner: " + ChatColor.YELLOW + name(g.owner));
                p.sendMessage(ChatColor.GRAY + "Members (" + g.members.size() + "): " + ChatColor.YELLOW + String.join(", ", g.members.stream().map(this::name).toList()));
                p.sendMessage(ChatColor.GRAY + "Guild coins: " + ChatColor.GOLD + g.coins);
            }
            case "list" -> {
                p.sendMessage(ChatColor.GOLD + "=== Guilds (" + guilds.size() + ") ===");
                for (Guild g : guilds.values()) {
                    p.sendMessage(ChatColor.YELLOW + "- " + g.name + " [" + g.tag + "] (" + g.members.size() + " members)");
                }
            }
            case "disband" -> {
                String gid = memberGuild.get(me);
                if (gid == null) { p.sendMessage(ChatColor.RED + "Not in a guild."); return true; }
                Guild g = guilds.get(gid);
                if (!g.owner.equals(me)) { p.sendMessage(ChatColor.RED + "Only owner can disband."); return true; }
                for (UUID m : g.members) memberGuild.remove(m);
                guilds.remove(gid);
                saveAsync();
                p.sendMessage(ChatColor.YELLOW + "Guild '" + g.name + "' disbanded.");
            }
            default -> p.sendMessage(ChatColor.RED + "/guild <create|invite|accept|leave|info|list|disband>");
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (inviteFrom.containsKey(p.getUniqueId())) {
            p.sendMessage(ChatColor.YELLOW + "You have a guild invite. /guild accept");
        }
    }
}
