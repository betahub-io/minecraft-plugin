package io.betahub.minecraft.core.api;

import io.betahub.minecraft.core.config.ReporterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
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
