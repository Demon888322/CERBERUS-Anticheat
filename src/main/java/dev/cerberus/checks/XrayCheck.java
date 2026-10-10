package dev.cerberus.checks;

import dev.cerberus.CerberusPlugin;
import dev.cerberus.violation.ViolationManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Flags repeated, unusually close finds of concealed high-value ores. */
public final class XrayCheck extends Check {
    private final CerberusPlugin plugin;
    private final Map<UUID, MiningSample> samples = new HashMap<>();

    public XrayCheck(CerberusPlugin plugin, ViolationManager violations) {
        super(violations, "Xray");
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("checks.xray.enabled", true);
    }

    public void handleBlockBreak(Player player, Block block) {
        if (!isEnabled() || (player.getGameMode() != GameMode.SURVIVAL
                && player.getGameMode() != GameMode.ADVENTURE)) {
            return;
        }

        MiningSample sample = samples.computeIfAbsent(player.getUniqueId(), ignored -> new MiningSample());
        Material type = block.getType();
        if (!isHighValueOre(type)) {
            if (type.isOccluding() && sample.blocksSinceRareOre < Integer.MAX_VALUE) {
                sample.blocksSinceRareOre++;
            }
            return;
        }

        if (sample.lastRareOre != null && !sample.lastRareOre.getWorld().equals(block.getWorld())) {
            sample.lastRareOre = null;
            sample.lastOreWasConcealed = false;
            sample.rareOreGroups = 0;
            sample.blocksSinceRareOre = 0;
        }

        boolean concealed = isConcealed(block);
        boolean sameVein = sample.lastRareOre != null && isSameVein(sample.lastRareOre, block);
        if (!sameVein) {
            sample.rareOreGroups++;
            int minimumGroups = Math.max(2,
                    plugin.getConfig().getInt("checks.xray.minimum-rare-ore-groups", 4));
            int maximumBlocks = Math.max(1,
                    plugin.getConfig().getInt("checks.xray.max-blocks-between-ores", 24));
            if (concealed && sample.lastOreWasConcealed
                    && sample.rareOreGroups >= minimumGroups
                    && sample.blocksSinceRareOre <= maximumBlocks) {
                flag(player, 1.0, "repeated concealed high-value ores found within "
                        + sample.blocksSinceRareOre + " mined blocks");
            }
        }

        sample.lastRareOre = block;
        sample.lastOreWasConcealed = concealed;
        sample.blocksSinceRareOre = 0;
    }

    public void removePlayer(UUID playerId) {
        samples.remove(playerId);
    }

    private static boolean isConcealed(Block block) {
        for (BlockFace face : BlockFace.values()) {
            int axesChanged = Math.abs(face.getModX()) + Math.abs(face.getModY()) + Math.abs(face.getModZ());
            if (axesChanged != 1) {
                continue;
            }
            if (!block.getRelative(face).getType().isOccluding()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSameVein(Block previous, Block current) {
        if (!previous.getWorld().equals(current.getWorld())) {
            return false;
        }
        int deltaX = previous.getX() - current.getX();
        int deltaY = previous.getY() - current.getY();
        int deltaZ = previous.getZ() - current.getZ();
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= 9;
    }

    private static boolean isHighValueOre(Material material) {
        return material == Material.DIAMOND_ORE
                || material == Material.DEEPSLATE_DIAMOND_ORE
                || material == Material.EMERALD_ORE
                || material == Material.DEEPSLATE_EMERALD_ORE
                || material == Material.ANCIENT_DEBRIS;
    }

    private static final class MiningSample {
        private Block lastRareOre;
        private boolean lastOreWasConcealed;
        private int rareOreGroups;
        private long blocksSinceRareOre;
    }
}
