package dev.cerberus.checks;

import dev.cerberus.CerberusPlugin;
import dev.cerberus.violation.ViolationManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Detects sustained, unusually high client arm-swing rates. */
public final class AutoClickerCheck extends Check {
    private static final long TICKS_PER_WINDOW = 20L;
    private static final int MAX_SWINGS_PER_WINDOW = 1_000;
    private final CerberusPlugin plugin;
    private final Map<UUID, SwingSample> samples = new HashMap<>();

    public AutoClickerCheck(CerberusPlugin plugin, ViolationManager violations) {
        super(violations, "AutoClicker");
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("checks.autoclicker.enabled", true);
    }

    public void recordSwing(Player player, long tick) {
        if (!isEnabled() || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            reset(player.getUniqueId());
            return;
        }

        long window = tick / TICKS_PER_WINDOW;
        UUID playerId = player.getUniqueId();
        SwingSample sample = samples.get(playerId);
        if (sample == null) {
            sample = new SwingSample(window);
            samples.put(playerId, sample);
        } else if (window < sample.window) {
            sample.reset(window);
        }
        if (window > sample.window) {
            long elapsedWindows = window - sample.window;
            if (elapsedWindows == 1) {
                evaluateWindow(player, sample);
            } else {
                sample.consecutiveHighWindows = 0;
            }
            sample.window = window;
            sample.swings = 0;
        }
        if (sample.swings < MAX_SWINGS_PER_WINDOW) {
            sample.swings++;
        }
    }

    public void removePlayer(UUID playerId) {
        samples.remove(playerId);
    }

    private void evaluateWindow(Player player, SwingSample sample) {
        int minimumSwings = Math.max(1,
                plugin.getConfig().getInt("checks.autoclicker.minimum-swings-per-second", 18));
        int requiredWindows = Math.max(2,
                plugin.getConfig().getInt("checks.autoclicker.consecutive-seconds", 6));
        if (sample.swings >= minimumSwings) {
            sample.consecutiveHighWindows++;
            if (sample.consecutiveHighWindows >= requiredWindows) {
                flag(player, 1.0, "sustained arm-swing rate of at least "
                        + minimumSwings + " per second");
                sample.consecutiveHighWindows = 0;
            }
        } else {
            sample.consecutiveHighWindows = 0;
        }
    }

    private void reset(UUID playerId) {
        samples.remove(playerId);
    }

    private static final class SwingSample {
        private long window;
        private int swings;
        private int consecutiveHighWindows;

        private SwingSample(long window) {
            this.window = window;
        }

        private void reset(long nextWindow) {
            window = nextWindow;
            swings = 0;
            consecutiveHighWindows = 0;
        }
    }
}
