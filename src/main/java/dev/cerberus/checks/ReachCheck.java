package dev.cerberus.checks;

import dev.cerberus.CerberusPlugin;
import dev.cerberus.violation.ViolationManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Event-level reach heuristic for survival interactions. */
public final class ReachCheck extends Check {
    private static final long BLOCK_FLAG_INTERVAL_TICKS = 20L;
    private final CerberusPlugin plugin;
    private final Map<UUID, Map<BlockKey, Long>> recentBlockFlags = new HashMap<>();

    public ReachCheck(CerberusPlugin plugin, ViolationManager violations) {
        super(violations, "Reach");
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("checks.reach.enabled", true);
    }

    public boolean exceedsLimit(Player player, Location target) {
        double maximum = plugin.getConfig().getDouble("checks.reach.maximum-distance", 5.2);
        return player.getEyeLocation().distanceSquared(target) > maximum * maximum;
    }

    public boolean exceedsBlockLimit(Player player, Block block) {
        Location eyes = player.getEyeLocation();
        if (!eyes.getWorld().equals(block.getWorld())) {
            return true;
        }

        double x = Math.max(block.getX(), Math.min(block.getX() + 1.0, eyes.getX()));
        double y = Math.max(block.getY(), Math.min(block.getY() + 1.0, eyes.getY()));
        double z = Math.max(block.getZ(), Math.min(block.getZ() + 1.0, eyes.getZ()));
        double maximum = plugin.getConfig().getDouble("checks.reach.maximum-block-distance", 5.0);
        return eyes.distanceSquared(new Location(block.getWorld(), x, y, z)) > maximum * maximum;
    }

    public boolean exceedsEntityAttackLimit(Player player, Entity target) {
        double maximum = plugin.getConfig().getDouble("checks.reach.maximum-entity-distance", 3.5);
        return entityDistanceSquared(player, target) > maximum * maximum;
    }

    public void flagReach(Player player, Location target, String targetType) {
        double distance = Math.sqrt(player.getEyeLocation().distanceSquared(target));
        flag(player, 1.0, targetType + " distance " + String.format(Locale.ROOT, "%.2f", distance));
    }

    public void flagEntityAttack(Player player, Entity target) {
        double distance = Math.sqrt(entityDistanceSquared(player, target));
        flag(player, 1.0, "entity attack distance " + String.format(Locale.ROOT, "%.2f", distance));
    }

    public void flagRemoteBlockAction(Player player, Block block, String action) {
        long tick = plugin.getServer().getCurrentTick();
        BlockKey key = new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        Map<BlockKey, Long> playerFlags = recentBlockFlags.computeIfAbsent(
                player.getUniqueId(), ignored -> new HashMap<>());
        Long previousTick = playerFlags.get(key);
        if (previousTick != null && tick - previousTick < BLOCK_FLAG_INTERVAL_TICKS) {
            return;
        }
        playerFlags.entrySet().removeIf(entry -> tick - entry.getValue() >= BLOCK_FLAG_INTERVAL_TICKS);
        if (playerFlags.size() >= 128) {
            BlockKey oldestKey = playerFlags.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldestKey != null) {
                playerFlags.remove(oldestKey);
            }
        }
        playerFlags.put(key, tick);

        double distance = Math.sqrt(distanceSquaredToBlock(player, block));
        flag(player, 1.0, action + " beyond block reach at "
                + String.format(Locale.ROOT, "%.2f", distance) + " blocks");
    }

    public void removePlayer(UUID playerId) {
        recentBlockFlags.remove(playerId);
    }

    private static double distanceSquaredToBlock(Player player, Block block) {
        Location eyes = player.getEyeLocation();
        double x = Math.max(block.getX(), Math.min(block.getX() + 1.0, eyes.getX()));
        double y = Math.max(block.getY(), Math.min(block.getY() + 1.0, eyes.getY()));
        double z = Math.max(block.getZ(), Math.min(block.getZ() + 1.0, eyes.getZ()));
        return eyes.distanceSquared(new Location(block.getWorld(), x, y, z));
    }

    private static double entityDistanceSquared(Player player, Entity target) {
        Location eyes = player.getEyeLocation();
        if (!eyes.getWorld().equals(target.getWorld())) {
            return Double.POSITIVE_INFINITY;
        }
        var bounds = target.getBoundingBox();
        double closestX = Math.max(bounds.getMinX(), Math.min(bounds.getMaxX(), eyes.getX()));
        double closestY = Math.max(bounds.getMinY(), Math.min(bounds.getMaxY(), eyes.getY()));
        double closestZ = Math.max(bounds.getMinZ(), Math.min(bounds.getMaxZ(), eyes.getZ()));
        return eyes.distanceSquared(new Location(target.getWorld(), closestX, closestY, closestZ));
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
    }
}
