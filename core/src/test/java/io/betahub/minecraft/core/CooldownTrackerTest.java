package io.betahub.minecraft.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CooldownTrackerTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-03T12:00:00Z"));
    private final CooldownTracker tracker = new CooldownTracker(Duration.ofSeconds(120), clock);

    @Test
    void noCooldownBeforeFirstReport() {
        assertEquals(Duration.ZERO, tracker.remaining(PLAYER));
    }

    @Test
    void cooldownCountsDownAndExpires() {
        tracker.start(PLAYER);
        clock.advance(Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(90), tracker.remaining(PLAYER));

        clock.advance(Duration.ofSeconds(90));
        assertEquals(Duration.ZERO, tracker.remaining(PLAYER));
    }

    @Test
    void clearLiftsCooldown() {
        Instant started = tracker.start(PLAYER);
        tracker.clear(PLAYER, started);
        assertEquals(Duration.ZERO, tracker.remaining(PLAYER));
    }

    @Test
    void clearingAnOldReportKeepsANewerCooldown() {
        Instant first = tracker.start(PLAYER);
        clock.advance(Duration.ofSeconds(130));
        tracker.start(PLAYER);

        tracker.clear(PLAYER, first);

        assertEquals(Duration.ofSeconds(120), tracker.remaining(PLAYER));
    }

    @Test
    void newCooldownAppliesToFutureReportsOnly() {
        tracker.start(PLAYER);
        tracker.setCooldown(Duration.ofSeconds(10));
        assertEquals(Duration.ofSeconds(120), tracker.remaining(PLAYER));
    }

    @Test
    void zeroCooldownNeverBlocks() {
        tracker.setCooldown(Duration.ZERO);
        tracker.start(PLAYER);
        assertEquals(Duration.ZERO, tracker.remaining(PLAYER));
    }

    @Test
    void rejectsNegativeCooldown() {
        assertThrows(IllegalArgumentException.class, () -> tracker.setCooldown(Duration.ofSeconds(-1)));
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
