package dev.cerberus.violation;

import dev.cerberus.CerberusPlugin;
import dev.cerberus.checks.Check;
import dev.cerberus.data.PlayerData;
import dev.cerberus.data.PlayerDataManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Central violation accounting, staff notifications, decay, and fixed-duration bans. */
public final class ViolationManager {
    private static final String TEMP_BAN_DURATION = "1d";
    private final CerberusPlugin plugin;
    private final PlayerDataManager playerDataManager;
    public ViolationManager(CerberusPlugin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    public double flag(Player player, Check check, double amount, String detail) {
        PlayerData data = playerDataManager.getOrCreate(player.getUniqueId());
        String checkKey = check.getName().toLowerCase(Locale.ROOT);
        double previousVl = data.getViolationLevel(checkKey);
        double vl = data.addViolation(checkKey, amount);
        String alert = color(plugin.getConfig().getString("messages.alert",
                "&c{player} failed {check} (VL: {vl}) {detail}")
                .replace("{player}", player.getName())
                .replace("{check}", check.getName())
                .replace("{vl}", String.format(Locale.ROOT, "%.1f", vl))
                .replace("{detail}", detail));

        for (Player staff : plugin.getServer().getOnlinePlayers()) {
            if (staff.hasPermission("cerberus.admin")) {
                staff.sendMessage(prefix() + alert);
            }
        }

        double threshold = plugin.getConfig().getDouble("checks." + checkKey + ".vl-threshold",
                Double.POSITIVE_INFINITY);
        if (previousVl < threshold && vl >= threshold) {
            if (plugin.getConfig().getBoolean("checks." + checkKey + ".auto-ban-enabled", true)) {
                punish(player, check, detail);
            } else {
                String message = color(prefix() + "&e" + player.getName() + " reached the "
                        + check.getName() + " alert threshold (VL: "
                        + String.format(Locale.ROOT, "%.1f", vl)
                        + "); automatic bans are disabled for this check.");
                for (Player staff : plugin.getServer().getOnlinePlayers()) {
                    if (staff.hasPermission("cerberus.admin")) {
                        staff.sendMessage(message);
                    }
                }
                plugin.getLogger().warning(player.getName() + " reached the " + check.getName()
                        + " alert threshold; automatic bans are disabled for this check.");
            }
        }
        return vl;
    }

    public void punish(Player player, Check check, String reason) {
        String safeReason = reason.replaceAll("[\\r\\n]", " ").trim();
        String command = "cerberustempban " + player.getName() + " " + TEMP_BAN_DURATION
                + " CERBERUS " + safeReason;
        boolean executed = plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
        if (!executed) {
            plugin.getLogger().severe("The required 24-hour ban command was not handled for "
                    + player.getName() + ".");
        }
    }

    public void startDecayTask() {
        long interval = Math.max(1, plugin.getConfig().getLong("checks.decay.interval-seconds", 30));
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            double amount = Math.max(0.0, plugin.getConfig().getDouble("checks.decay.amount", 1.0));
            double bufferDecay = Math.max(0.0,
                    plugin.getConfig().getDouble("checks.autototem.buffer-decay", 0.25));
            for (PlayerData data : playerDataManager.all()) {
                data.decayViolations(amount);
                data.decayBuffers(bufferDecay);
            }
        }, interval * 20L, interval * 20L);
    }

    private String prefix() {
        return color(plugin.getConfig().getString("messages.prefix", "&8[&cCERBERUS&8] "));
    }

    private static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
