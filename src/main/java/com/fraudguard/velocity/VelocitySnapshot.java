package com.fraudguard.velocity;

/**
 * Foto do estado em cache para uma transação. Quando {@code available} é falso (Redis fora),
 * as regras baseadas em estado simplesmente não disparam e a política fail-safe assume.
 */
public record VelocitySnapshot(
        boolean available,
        long recentCount,
        boolean newDevice,
        boolean newLocation,
        boolean newRecipient) {

    public static VelocitySnapshot unavailable() {
        return new VelocitySnapshot(false, 0, false, false, false);
    }

    public static VelocitySnapshot of(long recentCount, boolean newDevice, boolean newLocation, boolean newRecipient) {
        return new VelocitySnapshot(true, recentCount, newDevice, newLocation, newRecipient);
    }
}
