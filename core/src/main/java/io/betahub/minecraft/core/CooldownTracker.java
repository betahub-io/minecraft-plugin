package io.betahub.minecraft.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player cooldown between reports. Thread-safe. */
public final class CooldownTracker {

    private final Clock clock;
    private final Map<UUID, Instant> blockedUntil = new ConcurrentHashMap<>();
    private volatile Duration cooldown;

    public CooldownTracker(Duration cooldown, Clock clock) {
        this.clock = clock;
        setCooldown(cooldown);
    }

    /** Changes the cooldown for future reports (config reload). Running cooldowns keep their end time. */
    public void setCooldown(Duration cooldown) {
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must not be negative");
        }
        this.cooldown = cooldown;
    }

    /** Time left before the player may report again; {@link Duration#ZERO} when not on cooldown. */
    public Duration remaining(UUID player) {
        Instant until = blockedUntil.get(player);
        if (until == null) {
            return Duration.ZERO;
        }
        Duration left = Duration.between(clock.instant(), until);
        if (left.isNegative() || left.isZero()) {
            blockedUntil.remove(player, until);
            return Duration.ZERO;
        }
        return left;
    }

    /** @return token identifying this cooldown, for {@link #clear(UUID, Instant)} */
    public Instant start(UUID player) {
        pruneExpired();
        Instant until = clock.instant().plus(cooldown);
        blockedUntil.put(player, until);
        return until;
    }

    /**
     * Lifts the cooldown started with {@code token}, e.g. when that submission failed and the player should be
     * able to retry. A newer cooldown (from a later report) stays.
     */
    public void clear(UUID player, Instant token) {
        blockedUntil.remove(player, token);
    }

    private void pruneExpired() {
        Instant now = clock.instant();
        blockedUntil.values().removeIf(until -> !until.isAfter(now));
    }
}
