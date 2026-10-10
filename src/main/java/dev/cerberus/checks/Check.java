package dev.cerberus.checks;

import dev.cerberus.violation.ViolationManager;
import org.bukkit.entity.Player;

public abstract class Check {
    protected final ViolationManager violations;
    private final String name;

    protected Check(ViolationManager violations, String name) {
        this.violations = violations;
        this.name = name;
    }

    public final String getName() {
        return name;
    }

    public abstract boolean isEnabled();

    protected final double flag(Player player, double amount, String detail) {
        return violations.flag(player, this, amount, detail);
    }
}
