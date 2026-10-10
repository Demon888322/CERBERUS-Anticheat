package dev.cerberus.data;

import java.util.HashMap;
import java.util.Map;

/** Per-session counters and timing data. Access is synchronized because packet callbacks are asynchronous. */
public final class PlayerData {
    private final Map<String, Double> violationLevels = new HashMap<>();
    private final Map<String, Double> buffers = new HashMap<>();
    private long lastTotemPopAt;
    private long lastPacketAt;
    private long lastPacketDelayMillis;

    public synchronized double getViolationLevel(String check) {
        return violationLevels.getOrDefault(check, 0.0);
    }

    public synchronized double addViolation(String check, double amount) {
        double updated = Math.max(0.0, getViolationLevel(check) + amount);
        violationLevels.put(check, updated);
        return updated;
    }

    public synchronized void decayViolations(double amount) {
        violationLevels.replaceAll((check, level) -> Math.max(0.0, level - amount));
    }

    public synchronized double getBuffer(String check) {
        return buffers.getOrDefault(check, 0.0);
    }

    public synchronized double addBuffer(String check, double amount) {
        double updated = Math.max(0.0, getBuffer(check) + amount);
        buffers.put(check, updated);
        return updated;
    }

    public synchronized void decayBuffers(double amount) {
        buffers.replaceAll((check, buffer) -> Math.max(0.0, buffer - amount));
    }

    public synchronized void setLastTotemPopAt(long timestamp) {
        lastTotemPopAt = timestamp;
    }

    public synchronized long getLastTotemPopAt() {
        return lastTotemPopAt;
    }

    public synchronized void recordPacket(long timestamp) {
        if (lastPacketAt != 0) {
            lastPacketDelayMillis = Math.max(0, timestamp - lastPacketAt);
        }
        lastPacketAt = timestamp;
    }

    public synchronized long getLastPacketDelayMillis() {
        return lastPacketDelayMillis;
    }
}
