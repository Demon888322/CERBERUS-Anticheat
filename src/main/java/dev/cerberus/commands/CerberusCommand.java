package dev.cerberus.commands;

import dev.cerberus.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/** Administrative entry point for CERBERUS status and configuration reloads. */
public final class CerberusCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("help", "info", "reload");

    private final CerberusPlugin plugin;

    public CerberusCommand(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> sendInfo(sender);
            case "reload" -> reload(sender);
            default -> {
                sender.sendMessage(color("&cUnknown subcommand. Use /" + label + " help."));
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("cerberus.admin")) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(value -> value.startsWith(prefix)).toList();
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(color("&8&m-----&r &c&lCERBERUS &7Anti-Cheat &8&m-----"));
        sender.sendMessage(color("&c/" + label + " info &7- Show client identifier scan and check status."));
        if (sender.hasPermission("cerberus.admin")) {
            sender.sendMessage(color("&c/" + label + " reload &7- Reload CERBERUS configuration."));
        }
        sender.sendMessage(color("&8Permission: &7cerberus.admin"));
    }

    private void sendInfo(CommandSender sender) {
        sender.sendMessage(color("&8&m-----&r &c&lCERBERUS &7" + plugin.getDescription().getVersion()
                + " &8&m-----"));
        sender.sendMessage(color("&7PacketEvents: &f" + (plugin.isEnabled() ? "enabled" : "disabled")));
        sender.sendMessage(color("&7Detection ban duration: &e24 hours (fixed)"));
        sender.sendMessage(checkStatus("Client ID scan", "checks.brand.enabled"));
        sender.sendMessage(checkStatus("AutoClicker", "checks.autoclicker.enabled"));
        sender.sendMessage(checkStatus("AutoTotem", "checks.autototem.enabled"));
        sender.sendMessage(checkStatus("Reach", "checks.reach.enabled"));
        sender.sendMessage(checkStatus("Movement", "checks.movement.enabled"));
        sender.sendMessage(checkStatus("Xray", "checks.xray.enabled"));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("cerberus.admin")) {
            sender.sendMessage(color("&cYou do not have permission to reload CERBERUS."));
            return;
        }
        plugin.reloadConfig();
        sender.sendMessage(color("&8[&cCERBERUS&8] &aConfiguration reloaded."));
        plugin.getLogger().info(sender.getName() + " reloaded the configuration.");
    }

    private String checkStatus(String name, String path) {
        boolean enabled = plugin.getConfig().getBoolean(path, true);
        return color("&7" + name + ": " + (enabled ? "&aenabled" : "&cdisabled"));
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
