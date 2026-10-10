package dev.cerberus;

import com.github.retrooper.packetevents.PacketEvents;
import dev.cerberus.checks.AutoClickerCheck;
import dev.cerberus.checks.AutoTotemCheck;
import dev.cerberus.checks.BrandPayloadCheck;
import dev.cerberus.checks.MovementCheck;
import dev.cerberus.checks.ReachCheck;
import dev.cerberus.checks.XrayCheck;
import dev.cerberus.commands.CerberusCommand;
import dev.cerberus.data.PlayerDataManager;
import dev.cerberus.listeners.GameplayListener;
import dev.cerberus.listeners.PacketListener;
import dev.cerberus.punishment.TempBanCommand;
import dev.cerberus.violation.ViolationManager;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Plugin lifecycle and dependency wiring for CERBERUS. */
public final class CerberusPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 8;
    private PlayerDataManager playerDataManager;

    @Override
    public void onLoad() {
        // PacketEvents requires its API to be created and loaded before plugin enable.
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        PacketEvents.getAPI().init();

        playerDataManager = new PlayerDataManager();
        getServer().getOnlinePlayers().forEach(player ->
                playerDataManager.getOrCreate(player.getUniqueId()));
        ViolationManager violationManager = new ViolationManager(this, playerDataManager);
        BrandPayloadCheck brandCheck = new BrandPayloadCheck(this, violationManager);
        AutoClickerCheck autoClickerCheck = new AutoClickerCheck(this, violationManager);
        AutoTotemCheck autoTotemCheck = new AutoTotemCheck(this, violationManager, playerDataManager);
        MovementCheck movementCheck = new MovementCheck(this, violationManager);
        ReachCheck reachCheck = new ReachCheck(this, violationManager);
        XrayCheck xrayCheck = new XrayCheck(this, violationManager);
        PacketListener packetListener = new PacketListener(this, playerDataManager, brandCheck);

        getServer().getPluginManager().registerEvents(
                new GameplayListener(this, playerDataManager, autoClickerCheck, autoTotemCheck, movementCheck,
                        reachCheck, xrayCheck, packetListener), this);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        if (getCommand("cerberus") == null) {
            throw new IllegalStateException("cerberus is missing from plugin.yml");
        }
        CerberusCommand adminCommand = new CerberusCommand(this);
        getCommand("cerberus").setExecutor(adminCommand);
        getCommand("cerberus").setTabCompleter(adminCommand);
        if (getCommand("cerberustempban") == null) {
            throw new IllegalStateException("cerberustempban is missing from plugin.yml");
        }
        getCommand("cerberustempban").setExecutor(new TempBanCommand(this));
        violationManager.startDecayTask();
        getLogger().info("CERBERUS enabled with PacketEvents packet checks.");
    }

    private void migrateConfig() {
        int currentVersion = getConfig().contains("config-version", true)
                ? getConfig().getInt("config-version") : 0;
        if (currentVersion >= CONFIG_VERSION) {
            return;
        }

        var defaults = getConfig().getDefaults();
        if (defaults == null) {
            throw new IllegalStateException("Default CERBERUS configuration is unavailable");
        }

        var identifiers = new ArrayList<>(getConfig().getStringList("checks.brand.blacklist"));
        Set<String> existing = new HashSet<>();
        identifiers.forEach(value -> existing.add(value.toLowerCase(Locale.ROOT)));
        for (String value : defaults.getStringList("checks.brand.blacklist")) {
            if (existing.add(value.toLowerCase(Locale.ROOT))) {
                identifiers.add(value);
            }
        }

        getConfig().set("checks.brand.blacklist", identifiers);
        getConfig().set("checks.xray.enabled", getConfig().getBoolean("checks.xray.enabled", true));
        getConfig().set("checks.xray.minimum-rare-ore-groups",
                getConfig().getInt("checks.xray.minimum-rare-ore-groups", 4));
        getConfig().set("checks.xray.max-blocks-between-ores",
                getConfig().getInt("checks.xray.max-blocks-between-ores", 24));
        getConfig().set("checks.xray.vl-threshold",
                getConfig().getDouble("checks.xray.vl-threshold", 6.0));
        getConfig().set("checks.reach.maximum-block-distance",
                getConfig().getDouble("checks.reach.maximum-block-distance", 5.0));
        getConfig().set("checks.autoclicker.enabled",
                getConfig().getBoolean("checks.autoclicker.enabled", true));
        getConfig().set("checks.autoclicker.minimum-swings-per-second",
                getConfig().getInt("checks.autoclicker.minimum-swings-per-second", 18));
        getConfig().set("checks.autoclicker.consecutive-seconds",
                getConfig().getInt("checks.autoclicker.consecutive-seconds", 6));
        getConfig().set("checks.autoclicker.vl-threshold",
                getConfig().getDouble("checks.autoclicker.vl-threshold", 8.0));
        getConfig().set("checks.autoclicker.auto-ban-enabled",
                getConfig().getBoolean("checks.autoclicker.auto-ban-enabled", false));
        getConfig().set("checks.xray.auto-ban-enabled",
                getConfig().getBoolean("checks.xray.auto-ban-enabled", false));
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();
        getLogger().info("Migrated CERBERUS configuration to version " + CONFIG_VERSION + ".");
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
    }
}
