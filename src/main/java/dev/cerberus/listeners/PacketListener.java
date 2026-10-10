package dev.cerberus.listeners;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import dev.cerberus.CerberusPlugin;
import dev.cerberus.checks.BrandPayloadCheck;
import dev.cerberus.data.PlayerDataManager;
import org.bukkit.Bukkit;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Minimal packet-thread work; Bukkit player access and punishments run on the main thread. */
public final class PacketListener extends PacketListenerAbstract {
    private static final int MAX_BRAND_BYTES = 256;
    private static final int MAX_REGISTERED_CHANNEL_BYTES = 16_384;
    private final CerberusPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final BrandPayloadCheck brandCheck;
    private final ConcurrentMap<UUID, String> pendingDetections = new ConcurrentHashMap<>();
    private final Set<UUID> punishedPlayers = ConcurrentHashMap.newKeySet();

    public PacketListener(CerberusPlugin plugin, PlayerDataManager playerDataManager,
                          BrandPayloadCheck brandCheck) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.brandCheck = brandCheck;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        UUID playerId = event.getUser().getUUID();
        playerDataManager.getOrCreate(playerId).recordPacket(System.nanoTime() / 1_000_000L);

        if (!brandCheck.isEnabled() || event.getPacketType() != PacketType.Play.Client.PLUGIN_MESSAGE) {
            return;
        }

        WrapperPlayClientPluginMessage message = new WrapperPlayClientPluginMessage(event);
        String channel = message.getChannelName();
        if (channel == null) {
            return;
        }
        String reportedIdentifiers = "";
        if (isBrandChannel(channel)) {
            reportedIdentifiers = decodeBrand(message.getData());
        } else if (isChannelRegistration(channel)) {
            reportedIdentifiers = decodeRegisteredChannels(message.getData());
        }
        if (!brandCheck.isBlacklisted(reportedIdentifiers, channel)) {
            return;
        }

        event.setCancelled(true);
        String rawDetected = (reportedIdentifiers.isBlank() ? channel : reportedIdentifiers)
                .replaceAll("[\\p{Cntrl}&§]", "");
        String detected = rawDetected.length() > 128 ? rawDetected.substring(0, 128) : rawDetected;
        if (pendingDetections.putIfAbsent(playerId, detected) != null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            var player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                punish(player, detected);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> {
                            if (Bukkit.getPlayer(playerId) == null) {
                                pendingDetections.remove(playerId, detected);
                            }
                        }, 200L);
            }
        });
    }

    public void handleJoin(org.bukkit.entity.Player player) {
        String detected = pendingDetections.get(player.getUniqueId());
        if (detected != null) {
            punish(player, detected);
        }
    }

    public void handleQuit(UUID playerId) {
        pendingDetections.remove(playerId);
        punishedPlayers.remove(playerId);
    }

    private void punish(org.bukkit.entity.Player player, String detected) {
        if (!punishedPlayers.add(player.getUniqueId())) {
            return;
        }
        plugin.getLogger().warning("Blocked suspicious client identifier from "
                + player.getName() + ": " + detected);
        brandCheck.handleDetection(player, detected);
    }

    private static boolean isBrandChannel(String channel) {
        return "minecraft:brand".equalsIgnoreCase(channel) || "MC|Brand".equalsIgnoreCase(channel);
    }

    private static boolean isChannelRegistration(String channel) {
        return "minecraft:register".equalsIgnoreCase(channel) || "REGISTER".equalsIgnoreCase(channel);
    }

    /** Brand payloads start with a VarInt byte length followed by a UTF-8 string. */
    private static String decodeBrand(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_BRAND_BYTES + 5) {
            return "";
        }

        int length = 0;
        int shift = 0;
        int offset = 0;
        while (offset < data.length && shift < 35) {
            int value = data[offset++] & 0xFF;
            length |= (value & 0x7F) << shift;
            if ((value & 0x80) == 0) {
                if (length < 0 || length > MAX_BRAND_BYTES || offset + length > data.length) {
                    return "";
                }
                return new String(data, offset, length, StandardCharsets.UTF_8);
            }
            shift += 7;
        }
        return "";
    }

    private static String decodeRegisteredChannels(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_REGISTERED_CHANNEL_BYTES) {
            return "";
        }
        return new String(data, StandardCharsets.UTF_8).replace('\0', ' ');
    }
}
