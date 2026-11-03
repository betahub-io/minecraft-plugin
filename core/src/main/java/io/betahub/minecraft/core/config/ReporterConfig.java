package io.betahub.minecraft.core.config;

import io.betahub.minecraft.core.MetadataField;
import io.betahub.minecraft.core.ReportLimits;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** Validated plugin settings. */
public record ReporterConfig(
        BetaHub betaHub,
        String serverName,
        ReportLimits limits,
        Duration cooldown,
        ServerLog serverLog,
        MetadataToggles metadata,
        Map<MetadataField, String> customFields,
        boolean reportAlias) {

    public ReporterConfig {
        customFields = Map.copyOf(customFields);
    }

    /**
     * @param releaseLabel   explicit release label, or {@code null} to use the Minecraft server version
     * @param requestTimeout how long to wait for BetaHub to answer one request
     */
    public record BetaHub(URI baseUrl, String projectId, String token, String releaseLabel, Duration requestTimeout) {

        @Override
        public String toString() {
            // Keeps the token out of logs and exception messages.
            return "BetaHub[baseUrl=" + baseUrl + ", projectId=" + projectId + ", releaseLabel=" + releaseLabel
                    + ", requestTimeout=" + requestTimeout + "]";
        }
    }

    public record ServerLog(boolean attach, int lines) {
    }

    public record MetadataToggles(boolean coordinates, boolean biome, boolean clientVersion, boolean gameModeHealthPing) {
    }
}
