package io.betahub.minecraft.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot of the game state at the moment a player started a report.
 * Optional parts are {@code null} when disabled in config or unavailable.
 */
public record ReportContext(
        String playerName,
        UUID playerUuid,
        String serverName,
        String worldName,
        String dimension,
        String dimensionKey,
        Position position,
        String biome,
        String serverSoftware,
        String serverVersion,
        String minecraftVersion,
        ClientInfo client,
        PlayerState playerState,
        Instant reportedAt,
        String pluginVersion) {

    public ReportContext {
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(serverName, "serverName");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(dimensionKey, "dimensionKey");
        Objects.requireNonNull(serverSoftware, "serverSoftware");
        Objects.requireNonNull(serverVersion, "serverVersion");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(reportedAt, "reportedAt");
        Objects.requireNonNull(pluginVersion, "pluginVersion");
    }

    /** Player position. Block coordinates are what players and devs talk about; exact ones feed the teleport command. */
    public record Position(double x, double y, double z, float yaw, float pitch) {

        public int blockX() {
            return (int) Math.floor(x);
        }

        public int blockY() {
            return (int) Math.floor(y);
        }

        public int blockZ() {
            return (int) Math.floor(z);
        }
    }

    /** Client details. Either field may be {@code null} when unknown, but not both. */
    public record ClientInfo(String version, String brand) {

        public ClientInfo {
            if (version == null && brand == null) {
                throw new IllegalArgumentException("ClientInfo needs a version or a brand; use null ClientInfo instead");
            }
        }
    }

    public record PlayerState(String gameMode, double health, int pingMillis) {

        public PlayerState {
            Objects.requireNonNull(gameMode, "gameMode");
        }
    }
}
