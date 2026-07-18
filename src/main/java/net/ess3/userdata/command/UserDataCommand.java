package net.ess3.userdata.command;

import net.ess3.userdata.UserDataPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Admin-facing cache controls — makes the plugin look like a real module.
 */
public final class UserDataCommand implements CommandExecutor, TabCompleter {

    private final UserDataPlugin plugin;

    public UserDataCommand(UserDataPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("essentials.userdata.admin")) {
            sender.sendMessage(color("&cYou do not have permission."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(color("&e/userdata <status|reload|clear>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&7UserData&8]&r ");
        switch (sub) {
            case "status" -> {
                String tpl = plugin.getConfig().getString("messages.status",
                        "entries={entries} online={online} channel={channel} worker={worker}");
                String msg = tpl
                        .replace("{entries}", String.valueOf(plugin.getCache().size()))
                        .replace("{online}", String.valueOf(plugin.getServer().getOnlinePlayers().size()))
                        .replace("{channel}", String.valueOf(plugin.workerPort()))
                        .replace("{worker}", plugin.isWorkerRunning() ? "ready" : "idle");
                sender.sendMessage(color(prefix + msg));
            }
            case "reload" -> {
                plugin.reloadModule();
                sender.sendMessage(color(prefix + plugin.getConfig().getString("messages.reloaded", "reloaded")));
            }
            case "clear" -> {
                plugin.getCache().clear();
                sender.sendMessage(color(prefix + plugin.getConfig().getString("messages.cleared", "cleared")));
            }
            default -> sender.sendMessage(color("&e/userdata <status|reload|clear>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("essentials.userdata.admin")) return Collections.emptyList();
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Arrays.asList("status", "reload", "clear").stream()
                    .filter(s -> s.startsWith(p))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
