package dev.cerberus.listeners;

import dev.cerberus.CerberusPlugin;
import dev.cerberus.checks.AutoClickerCheck;
import dev.cerberus.checks.AutoTotemCheck;
import dev.cerberus.checks.MovementCheck;
import dev.cerberus.checks.ReachCheck;
import dev.cerberus.checks.XrayCheck;
import dev.cerberus.data.PlayerDataManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Main-thread Paper event integration for gameplay checks and session cleanup. */
public final class GameplayListener implements Listener {
    private final CerberusPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final AutoClickerCheck autoClickerCheck;
    private final AutoTotemCheck autoTotemCheck;
    private final MovementCheck movementCheck;
    private final ReachCheck reachCheck;
    private final XrayCheck xrayCheck;
    private final PacketListener packetListener;
    private final Map<UUID, BukkitTask> totemMonitors = new HashMap<>();

    public GameplayListener(CerberusPlugin plugin, PlayerDataManager playerDataManager,
                            AutoClickerCheck autoClickerCheck, AutoTotemCheck autoTotemCheck,
                            MovementCheck movementCheck, ReachCheck reachCheck, XrayCheck xrayCheck,
                            PacketListener packetListener) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.autoClickerCheck = autoClickerCheck;
        this.autoTotemCheck = autoTotemCheck;
        this.movementCheck = movementCheck;
        this.reachCheck = reachCheck;
        this.xrayCheck = xrayCheck;
        this.packetListener = packetListener;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        playerDataManager.getOrCreate(event.getPlayer().getUniqueId());
        movementCheck.startTracking(event.getPlayer(), plugin.getServer().getCurrentTick());
        packetListener.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packetListener.handleQuit(event.getPlayer().getUniqueId());
        autoClickerCheck.removePlayer(event.getPlayer().getUniqueId());
        movementCheck.stopTracking(event.getPlayer().getUniqueId());
        xrayCheck.removePlayer(event.getPlayer().getUniqueId());
        reachCheck.removePlayer(event.getPlayer().getUniqueId());
        cancelTotemMonitor(event.getPlayer().getUniqueId());
        playerDataManager.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onResurrect(EntityResurrectEvent event) {
        if (autoTotemCheck.isEnabled() && event.getEntity() instanceof Player player) {
            long poppedAt = System.nanoTime() / 1_000_000L;
            autoTotemCheck.recordTotemPop(player, poppedAt);
            UUID playerId = player.getUniqueId();
            cancelTotemMonitor(playerId);
            BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                long lastPop = playerDataManager.getOrCreate(playerId).getLastTotemPopAt();
                long now = System.nanoTime() / 1_000_000L;
                if (!player.isOnline() || lastPop == 0 || now - lastPop > 2_000) {
                    cancelTotemMonitor(playerId);
                } else if (isTotem(player.getInventory().getItemInOffHand())) {
                    autoTotemCheck.recordOffhandRestock(player, now);
                    cancelTotemMonitor(playerId);
                }
            }, 1L, 1L);
            totemMonitors.put(playerId, task);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && hasPendingTotemPop(player)) {
            inspectNextTick(player);
        }
    }

    @EventHandler
    public void onHandSwap(PlayerSwapHandItemsEvent event) {
        if (hasPendingTotemPop(event.getPlayer())) {
            inspectNextTick(event.getPlayer());
        }
    }

    private boolean hasPendingTotemPop(Player player) {
        return playerDataManager.getOrCreate(player.getUniqueId()).getLastTotemPopAt() > 0;
    }

    private void inspectNextTick(Player player) {
        // Inventory events run before the inventory transaction has been applied.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && isTotem(player.getInventory().getItemInOffHand())) {
                autoTotemCheck.recordOffhandRestock(player, System.nanoTime() / 1_000_000L);
            }
        });
    }

    private void cancelTotemMonitor(UUID playerId) {
        BukkitTask task = totemMonitors.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private static boolean isTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isSurvivalOrAdventure(player) && reachCheck.isEnabled()
                && reachCheck.exceedsBlockLimit(player, event.getBlock())) {
            event.setCancelled(true);
            reachCheck.flagRemoteBlockAction(player, event.getBlock(), "block break");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onXrayBlockBreak(BlockBreakEvent event) {
        xrayCheck.handleBlockBreak(event.getPlayer(), event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (isSurvivalOrAdventure(player) && reachCheck.isEnabled()
                && reachCheck.exceedsBlockLimit(player, event.getBlock())) {
            event.setCancelled(true);
            reachCheck.flagRemoteBlockAction(player, event.getBlock(), "block damage");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (isSurvivalOrAdventure(player) && reachCheck.isEnabled()
                && reachCheck.exceedsBlockLimit(player, event.getClickedBlock())) {
            event.setCancelled(true);
            reachCheck.flagRemoteBlockAction(player, event.getClickedBlock(), "block interaction");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        var target = event.getBlockPlaced().getLocation().add(0.5, 0.5, 0.5);
        if (isSurvival(player) && reachCheck.isEnabled() && reachCheck.exceedsLimit(player, target)) {
            event.setCancelled(true);
            reachCheck.flagReach(player, target, "block place");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        var target = event.getRightClicked().getLocation().add(0, event.getRightClicked().getHeight() / 2.0, 0);
        if (isSurvival(player) && reachCheck.isEnabled() && reachCheck.exceedsLimit(player, target)) {
            event.setCancelled(true);
            reachCheck.flagReach(player, target, "entity interact");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !isSurvivalOrAdventure(player)
                || !reachCheck.isEnabled()
                || !reachCheck.exceedsEntityAttackLimit(player, event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        reachCheck.flagEntityAttack(player, event.getEntity());
    }

    private static boolean isSurvival(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL;
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        autoClickerCheck.recordSwing(event.getPlayer(), plugin.getServer().getCurrentTick());
    }

    private static boolean isSurvivalOrAdventure(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) {
            return;
        }
        movementCheck.handleMove(event.getPlayer(), event.getFrom(), event.getTo(),
                plugin.getServer().getCurrentTick());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null) {
            movementCheck.resetTracking(event.getPlayer(), event.getTo(), plugin.getServer().getCurrentTick());
        }
    }
}
