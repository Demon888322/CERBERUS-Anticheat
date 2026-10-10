package dev.cerberus.checks;

import dev.cerberus.CerberusPlugin;
import dev.cerberus.violation.ViolationManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Conservative movement heuristics for clearly excessive horizontal speed and sustained flight. */
public final class MovementCheck extends Check {
    private final CerberusPlugin plugin;
    private final Map<UUID, MovementSample> samples = new HashMap<>();

    public MovementCheck(CerberusPlugin plugin, ViolationManager violations) {
        super(violations, "Movement");
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("checks.movement.enabled", true);
    }

    public void startTracking(Player player, long tick) {
        samples.put(player.getUniqueId(), new MovementSample(tick, player.getLocation()));
    }

    public void stopTracking(UUID playerId) {
        samples.remove(playerId);
    }

    public void resetTracking(Player player, Location location, long tick) {
        samples.put(player.getUniqueId(), new MovementSample(tick, location));
    }

    public void handleMove(Player player, Location from, Location to, long tick) {
        UUID playerId = player.getUniqueId();
        if (!isEnabled() || isExempt(player, to)) {
            startTracking(player, tick);
            return;
        }

        MovementSample sample = samples.computeIfAbsent(playerId,
                ignored -> new MovementSample(tick, from));
        if (tick > sample.tick) {
            long elapsedTicks = tick - sample.tick;
            if (player.isOnGround()) {
                sample.airTicks = 0;
                sample.airStartY = to.getY();
                sample.highestAirY = to.getY();
            } else {
                sample.airTicks += (int) Math.min(Integer.MAX_VALUE, elapsedTicks);
                if (Math.abs(to.getY() - sample.airLastY) < 0.03) {
                    sample.hoverTicks++;
                } else {
                    sample.hoverTicks = 0;
                }
                sample.highestAirY = Math.max(sample.highestAirY, to.getY());
            }
            evaluate(player, sample, elapsedTicks);
            sample.tick = tick;
            sample.horizontalDistance = 0;
            sample.airLastY = to.getY();
        }

        sample.horizontalDistance += Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        if (player.isOnGround()) {
            sample.airTicks = 0;
            sample.hoverTicks = 0;
            sample.airStartY = to.getY();
            sample.highestAirY = to.getY();
            sample.airLastY = to.getY();
        } else {
            if (sample.airTicks == 0) {
                sample.airStartY = from.getY();
                sample.highestAirY = from.getY();
                sample.airLastY = from.getY();
            }
            sample.highestAirY = Math.max(sample.highestAirY, to.getY());
        }
    }

    private void evaluate(Player player, MovementSample sample, long elapsedTicks) {
        double speedEffect = 1.0;
        var speed = player.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) {
            speedEffect += 0.2 * (speed.getAmplifier() + 1);
        }
        double maximumDistance = plugin.getConfig().getDouble(
                "checks.movement.max-horizontal-per-tick", 0.75) * speedEffect * elapsedTicks;
        if (sample.horizontalDistance > maximumDistance + 0.15
                && player.getVelocity().lengthSquared() < 0.49) {
            flag(player, 1.0, "horizontal movement exceeded the allowed distance");
        }

        int flightAirTicks = Math.max(1,
                plugin.getConfig().getInt("checks.movement.flight-air-ticks", 12));
        double maximumRise = plugin.getConfig().getDouble("checks.movement.flight-max-rise", 1.8);
        var jumpBoost = player.getPotionEffect(PotionEffectType.JUMP);
        if (jumpBoost != null) {
            maximumRise += 0.5 * (jumpBoost.getAmplifier() + 1);
        }
        boolean excessiveRise = sample.highestAirY - sample.airStartY > maximumRise;
        boolean prolongedHover = sample.hoverTicks > flightAirTicks * 2L;
        if (sample.airTicks > flightAirTicks && (excessiveRise || prolongedHover)) {
            flag(player, 1.0, "sustained airborne movement inconsistent with a normal jump");
        }
    }

    private static boolean isExempt(Player player, Location location) {
        if ((player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)
                || player.getAllowFlight() || player.isFlying() || player.isInsideVehicle()
                || player.isGliding() || player.isSwimming()
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || location.getBlock().isLiquid() || location.clone().subtract(0, 1, 0).getBlock().isLiquid()) {
            return true;
        }

        Material below = location.clone().subtract(0, 1, 0).getBlock().getType();
        return below == Material.ICE || below == Material.PACKED_ICE || below == Material.BLUE_ICE
                || below == Material.FROSTED_ICE || below == Material.SLIME_BLOCK
                || below == Material.HONEY_BLOCK;
    }

    private static final class MovementSample {
        private long tick;
        private double horizontalDistance;
        private int airTicks;
        private int hoverTicks;
        private double airStartY;
        private double highestAirY;
        private double airLastY;

        private MovementSample(long tick, Location location) {
            this.tick = tick;
            this.airStartY = location.getY();
            this.highestAirY = location.getY();
            this.airLastY = location.getY();
        }
    }
}
