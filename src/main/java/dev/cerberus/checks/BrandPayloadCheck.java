package dev.cerberus.checks;

import dev.cerberus.CerberusPlugin;
import dev.cerberus.violation.ViolationManager;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** Matches client-reported brand values and suspicious custom payload channel names. */
public final class BrandPayloadCheck extends Check {
    private final CerberusPlugin plugin;

    public BrandPayloadCheck(CerberusPlugin plugin, ViolationManager violations) {
        super(violations, "Brand");
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("checks.brand.enabled", true);
    }

    public boolean isBlacklisted(String brand, String channel) {
        String normalizedBrand = brand.toLowerCase(Locale.ROOT);
        String normalizedChannel = channel == null ? "" : channel.toLowerCase(Locale.ROOT);
        List<String> blacklist = plugin.getConfig().getStringList("checks.brand.blacklist");
        return blacklist.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> normalizedBrand.contains(value) || normalizedChannel.contains(value));
    }

    public void handleDetection(Player player, String identifier) {
        flag(player, 1.0, "blacklisted client identifier: " + identifier);
        violations.punish(player, this, "blacklisted client identifier: " + identifier);
    }
}
