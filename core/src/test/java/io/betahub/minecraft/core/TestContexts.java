package io.betahub.minecraft.core;

import java.time.Instant;
import java.util.UUID;

/** Shared fixtures for core tests. */
public final class TestContexts {

    public static final UUID PLAYER_UUID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    public static final Instant REPORTED_AT = Instant.parse("2026-11-03T18:30:00Z");

    private TestContexts() {
    }

    public static ReportContext full() {
        return new ReportContext(
                "Notch", PLAYER_UUID, "survival",
                "world_nether", "NETHER", "minecraft:the_nether",
                new ReportContext.Position(120.5, 64.0, -35.25, 90.0f, 10.5f),
                "minecraft:nether_wastes",
                "Paper", "1.21.11-120-abc (MC: 1.21.11)", "1.21.11",
                new ReportContext.ClientInfo("1.21.11", "vanilla"),
                new ReportContext.PlayerState("SURVIVAL", 18.5, 42),
                REPORTED_AT, "0.1.0");
    }

    public static ReportContext minimal() {
        return new ReportContext(
                "Notch", PLAYER_UUID, "lobby",
                "world", "NORMAL", "minecraft:overworld",
                null, null,
                "Paper", "26.3-1 (MC: 26.3)", "26.3",
                null, null,
                REPORTED_AT, "0.1.0");
    }
}
