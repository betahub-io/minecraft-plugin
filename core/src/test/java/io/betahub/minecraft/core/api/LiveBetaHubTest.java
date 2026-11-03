package io.betahub.minecraft.core.api;

import io.betahub.minecraft.core.IssueAssembler;
import io.betahub.minecraft.core.LogRedactor;
import io.betahub.minecraft.core.MetadataField;
import io.betahub.minecraft.core.ReportDraft;
import io.betahub.minecraft.core.ReportLimits;
import io.betahub.minecraft.core.TestContexts;
import io.betahub.minecraft.core.config.ReporterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Talks to the real BetaHub. Runs only when BETAHUB_TEST_PROJECT and BETAHUB_TEST_TOKEN are set, and then
 * creates one real issue (using one report from the token's daily limit). BETAHUB_TEST_BASE_URL is optional.
 */
@EnabledIfEnvironmentVariable(named = "BETAHUB_TEST_PROJECT", matches = "pr-.+")
@EnabledIfEnvironmentVariable(named = "BETAHUB_TEST_TOKEN", matches = "tkn-.+")
class LiveBetaHubTest {

    private static final String DEFAULT_BASE_URL = "https://app.betahub.io";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final String project = System.getenv("BETAHUB_TEST_PROJECT");
    private final BetaHubClient client = new BetaHubClient(
            new ReporterConfig.BetaHub(
                    URI.create(System.getenv().getOrDefault("BETAHUB_TEST_BASE_URL", DEFAULT_BASE_URL)),
                    project, System.getenv("BETAHUB_TEST_TOKEN"), null, REQUEST_TIMEOUT),
            BetaHubClient.newHttpClient(BetaHubClient.DEFAULT_CONNECT_TIMEOUT),
            new BetaHubClient.Options(Duration.ofSeconds(2), "BetaHubBugReporter/live-test"));

    @Test
    void tokenIsValidForProject() {
        TokenCheck.Valid valid = assertInstanceOf(TokenCheck.Valid.class, client.verifyToken());

        assertEquals(project, valid.projectId());
    }

    /** Exactly what the plugin sends for a player report: context block, steps, release label, custom fields, logs. */
    @Test
    void acceptsFullPluginReport() {
        ReporterConfig config = new ReporterConfig(
                new ReporterConfig.BetaHub(URI.create("https://unused"), project, "tkn-unused", null, REQUEST_TIMEOUT),
                "survival", new ReportLimits(10, 1000, 1000), Duration.ofSeconds(120),
                new ReporterConfig.ServerLog(true, 200),
                new ReporterConfig.MetadataToggles(true, true, true, true),
                Map.of(MetadataField.SERVER_NAME, "mc_server", MetadataField.LOCATION, "mc_location"),
                false);
        String logs = new LogRedactor(List.of()).redact(
                "[18:29:58 INFO]: Notch[/203.0.113.7:51234] logged in\n[18:30:00 WARN]: Chest at 120 64 -36 lost items");
        NewIssue issue = IssueAssembler.assemble(config,
                new ReportDraft("The chest next to the nether portal empties itself after a relog. Live test, safe to delete.",
                        "1. Put items in the chest\n2. Log out and back in\n3. The chest is empty"),
                TestContexts.full(), logs);

        IssueResult result = client.createIssue(issue);

        IssueResult.Created created = assertInstanceOf(IssueResult.Created.class, result, result::toString);
        System.out.println("Live plugin report created: " + created.reference() + " " + created.url()
                + " releaseLabelIgnored=" + created.releaseLabelIgnored() + " warnings=" + created.warnings());
    }

    @Test
    void createsIssueWithStepsAndUnicode() {
        NewIssue issue = new NewIssue(
                "Live test from the BetaHub Bug Reporter build (" + Instant.now() + "). Ünïcödé ✓, newline below.\n"
                        + "The player fell through the floor next to the spawn fountain. Safe to delete.",
                "1. Walk to the fountain\n2. Jump twice", null, Map.of(), "[12:00:00 INFO]: live test log line");

        IssueResult result = client.createIssue(issue);

        IssueResult.Created created = assertInstanceOf(IssueResult.Created.class, result, result::toString);
        assertTrue(created.url().toString().startsWith("http"), created::toString);
        System.out.println("Live issue created: " + created.reference() + " " + created.url());
    }
}
