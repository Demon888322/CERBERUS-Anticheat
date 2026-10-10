package dev.cerberus.checks;

import dev.cerberus.CerberusPlugin;
import dev.cerberus.data.PlayerData;
import dev.cerberus.data.PlayerDataManager;
import dev.cerberus.violation.ViolationManager;
import org.bukkit.entity.Player;

/** Uses a bounded per-player buffer to reduce single-event false positives. */
public final class AutoTotemCheck extends Check {
    private static final String BUFFER_KEY = "autototem";
    private final CerberusPlugin plugin;
    private final PlayerDataManager playerDataManager;

    public AutoTotemCheck(CerberusPlugin plugin, ViolationManager violations,
                         PlayerDataManager playerDataManager) {
        super(violations, "AutoTotem");
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("checks.autototem.enabled", true);
    }

    public void recordTotemPop(Player player, long timestamp) {
        playerDataManager.getOrCreate(player.getUniqueId()).setLastTotemPopAt(timestamp);
    }

    public void recordOffhandRestock(Player player, long timestamp) {
        if (!isEnabled()) {
            return;
        }
        PlayerData data = playerDataManager.getOrCreate(player.getUniqueId());
        long poppedAt = data.getLastTotemPopAt();
        if (poppedAt == 0 || timestamp < poppedAt) {
            return;
        }

        data.setLastTotemPopAt(0);
        long elapsed = timestamp - poppedAt;
        if (elapsed > 2_000) {
            return;
        }
        long minimum = Math.max(0, plugin.getConfig().getLong("checks.autototem.minimum-restock-ms", 100));
        if (elapsed >= minimum) {
            data.addBuffer(BUFFER_KEY, -plugin.getConfig().getDouble("checks.autototem.buffer-decay", 0.25));
            return;
        }

        double buffer = data.addBuffer(BUFFER_KEY,
                plugin.getConfig().getDouble("checks.autototem.buffer-increment", 1.0));
        double threshold = plugin.getConfig().getDouble("checks.autototem.buffer-threshold", 5.0);
        if (buffer >= threshold) {
            data.addBuffer(BUFFER_KEY, -threshold);
            flag(player, 1.0, "offhand totem restored in " + elapsed + "ms");
        }
    }
}
