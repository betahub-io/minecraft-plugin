package io.betahub.minecraft.core;

import io.betahub.minecraft.core.api.NewIssue;
import io.betahub.minecraft.core.config.ReporterConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssueAssemblerTest {

    @Test
    void usesMinecraftVersionAsDefaultReleaseAndMapsCustomFields() {
        ReporterConfig config = config(null, Map.of(MetadataField.SERVER_NAME, "server", MetadataField.BIOME, "biome"));

        NewIssue issue = IssueAssembler.assemble(config, new ReportDraft("Mobs float in the air", ""), TestContexts.full(), null);

        assertEquals("1.21.11", issue.releaseLabel());
        assertNull(issue.steps());
        assertEquals(Map.of("server", "survival", "biome", "minecraft:nether_wastes"), issue.custom());
        assertTrue(issue.description().startsWith("Mobs float in the air\n\n---\n"));
    }

    @Test
    void explicitReleaseLabelWinsAndMissingValuesAreSkipped() {
        ReporterConfig config = config("season-3", Map.of(MetadataField.COORDINATES, "coords"));

        NewIssue issue = IssueAssembler.assemble(config, new ReportDraft("Lag near spawn area", "walk"), TestContexts.minimal(), "log");

        assertEquals("season-3", issue.releaseLabel());
        assertEquals("walk", issue.steps());
        assertEquals(Map.of(), issue.custom());
        assertEquals("log", issue.logs());
    }

    private static ReporterConfig config(String releaseLabel, Map<MetadataField, String> customFields) {
        return new ReporterConfig(
                new ReporterConfig.BetaHub(URI.create("https://app.betahub.io"), "pr-1", "tkn-1", releaseLabel, Duration.ofSeconds(60)),
                "survival",
                new ReportLimits(10, 1000, 1000),
                Duration.ofSeconds(120),
                new ReporterConfig.ServerLog(false, 200),
                new ReporterConfig.MetadataToggles(true, true, true, true),
                customFields,
                false);
    }
}
