package io.betahub.minecraft.paper;

import net.kyori.adventure.text.Component;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.MessageTarget;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Ticks the mock server until an expected outcome shows up. MockBukkit's async task counter can read 0
 * before a task has started, so waiting on it is flaky; waiting on the outcome is not.
 */
final class Await {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long POLL_MILLIS = 5;

    private Await() {
    }

    static void until(ServerMock server, BooleanSupplier condition) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within " + TIMEOUT);
            }
            server.getScheduler().performOneTick();
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    /** Next chat message to {@code target}, waiting for async work to deliver it. */
    static Component message(ServerMock server, MessageTarget target) {
        Component[] received = new Component[1];
        until(server, () -> (received[0] = target.nextComponentMessage()) != null);
        return received[0];
    }

    /** All plain-text lines sent to {@code target} up to and including the first one matching {@code last}. */
    static List<String> linesUntil(ServerMock server, MessageTarget target, Predicate<String> last) {
        List<String> lines = new ArrayList<>();
        until(server, () -> {
            Component next;
            while ((next = target.nextComponentMessage()) != null) {
                String line = PluginTest.plain(next);
                lines.add(line);
                if (last.test(line)) {
                    return true;
                }
            }
            return false;
        });
        return lines;
    }
}
