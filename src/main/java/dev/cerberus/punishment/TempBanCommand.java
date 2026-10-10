package dev.cerberus.punishment;

import dev.cerberus.CerberusPlugin;
import org.bukkit.BanList;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Console command used by CERBERUS to apply a timed name ban without another plugin. */
public final class TempBanCommand implements CommandExecutor {
    private static final Pattern DURATION = Pattern.compile("([1-9][0-9]*)([smhd])");
    private final CerberusPlugin plugin;

    public TempBanCommand(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("This command is reserved for the server console.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /cerberustempban <player> <duration> <reason>");
            return true;
        }

        long seconds;
        try {
            seconds = parseDuration(args[1]);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().severe("Invalid duration for CERBERUS temp ban: " + args[1]);
            sender.sendMessage("Invalid duration. Use a positive value followed by s, m, h, or d.");
            return true;
        }

        Instant expiry;
        try {
            expiry = Instant.now().plusSeconds(seconds);
        } catch (ArithmeticException exception) {
            plugin.getLogger().severe("Temporary ban duration is too large: " + args[1]);
            return true;
        }

        String reason = String.join(" ", args).substring(args[0].length() + args[1].length() + 2);
        String playerName = args[0];
        plugin.getServer().getBanList(BanList.Type.NAME)
                .addBan(playerName, reason, Date.from(expiry), "CERBERUS");
        Player onlinePlayer = plugin.getServer().getPlayerExact(playerName);
        if (onlinePlayer != null) {
            onlinePlayer.kickPlayer(reason);
        }
        sender.sendMessage("Temporarily banned " + playerName + " for " + args[1] + ".");
        return true;
    }

    private static long parseDuration(String input) {
        Matcher matcher = DURATION.matcher(input.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported duration format");
        }

        long value = Long.parseLong(matcher.group(1));
        long multiplier = switch (matcher.group(2)) {
            case "s" -> 1;
            case "m" -> 60;
            case "h" -> 3_600;
            case "d" -> 86_400;
            default -> throw new IllegalArgumentException("Unsupported duration unit");
        };
        if (value > 3_153_600_000L / multiplier) {
            throw new IllegalArgumentException("Duration exceeds 100 years");
        }
        return value * multiplier;
    }
}
