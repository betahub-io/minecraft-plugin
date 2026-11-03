package io.betahub.minecraft.core.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.betahub.minecraft.core.config.ReporterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetaHubClientTest {

    private static final String PROJECT = "pr-0690627851";
    private static final String TOKEN = "tkn-abc123";
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(500);
    private static final String CREATED = """
            {"id": 9001, "scoped_id": 42, "title": "Door stuck",
             "url": "https://app.betahub.io/projects/pr-0690627851/issues/42"}""";
    private static final NewIssue ISSUE = new NewIssue(
            "Drzwi się zacinają 🚪\nline two \"quoted\"", "1. open door\n2. stuck", "1.21.11",
            Map.of("server", "survival"), null);

    private StubBetaHub stub;
    private BetaHubClient client;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubBetaHub();
        client = clientFor(stub.baseUrl());
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    void createsIssueWithExpectedRequest() {
        stub.reply(201, CREATED);

        IssueResult result = client.createIssue(ISSUE);

        IssueResult.Created created = assertInstanceOf(IssueResult.Created.class, result);
        assertEquals(URI.create("https://app.betahub.io/projects/pr-0690627851/issues/42"), created.url());
        assertEquals("#42", created.reference());
        assertFalse(created.releaseLabelIgnored());

        StubBetaHub.Received request = stub.received().get(0);
        assertEquals("POST", request.method());
        assertEquals("/projects/pr-0690627851/issues.json", request.path());
        assertEquals("FormUser tkn-abc123", request.header("Authorization"));
        assertEquals(PROJECT, request.header("BetaHub-Project-ID"));
        assertEquals("application/json", request.header("Content-Type"));
        assertEquals("application/json", request.header("Accept"));
        assertEquals("BetaHubBugReporter/test", request.header("User-Agent"));

        JsonObject issue = JsonParser.parseString(request.body()).getAsJsonObject().getAsJsonObject("issue");
        assertEquals("Drzwi się zacinają 🚪\nline two \"quoted\"", issue.get("description").getAsString());
        assertEquals("1. open door\n2. stuck", issue.get("unformatted_steps_to_reproduce").getAsString());
        assertEquals("1.21.11", issue.get("release_label").getAsString());
        assertEquals("survival", issue.getAsJsonObject("custom").get("server").getAsString());
        assertFalse(issue.has("title"));
        assertFalse(issue.has("logs"));
    }

    @Test
    void omitsOptionalFieldsWhenAbsent() {
        stub.reply(201, CREATED);

        client.createIssue(new NewIssue("Only a description here", null, null, Map.of(), null));

        JsonObject issue = JsonParser.parseString(stub.received().get(0).body()).getAsJsonObject().getAsJsonObject("issue");
        assertEquals(List.of("description"), List.copyOf(issue.keySet()));
    }

    @Test
    void sendsLogsWhenGiven() {
        stub.reply(201, CREATED);

        client.createIssue(new NewIssue("desc", null, null, Map.of(), "[12:00:00 INFO]: Done\n"));

        JsonObject issue = JsonParser.parseString(stub.received().get(0).body()).getAsJsonObject().getAsJsonObject("issue");
        assertEquals("[12:00:00 INFO]: Done\n", issue.get("logs").getAsString());
    }

    @Test
    void hiddenIssueHasGlobalReferenceAndWarningsAreKept() {
        stub.reply(201, """
                {"id": 77, "scoped_id": null, "url": "https://app.betahub.io/projects/pr-1/issues/g-77",
                 "warnings": ["Custom field 'notes' value too long, dropped"]}""");

        IssueResult.Created created = assertInstanceOf(IssueResult.Created.class, client.createIssue(ISSUE));

        assertNull(created.scopedId());
        assertEquals("g-77", created.reference());
        assertEquals(List.of("Custom field 'notes' value too long, dropped"), created.warnings());
    }

    @Test
    void dailyLimitIsReportedAsUnavailableWithoutRetry() {
        stub.reply(403, "{\"error\":\"Not allowed to report a bug.\"}");

        IssueResult result = client.createIssue(ISSUE);

        assertEquals(new IssueResult.Unavailable(IssueResult.Reason.DAILY_LIMIT_OR_NO_PERMISSION, "Not allowed to report a bug."), result);
        assertEquals(1, stub.received().size());
    }

    @Test
    void planLimitIsReportedAsUnavailable() {
        stub.reply(403, "{\"error\":\"This project is not currently accepting new bug reports. Please try again later.\"}");

        IssueResult.Unavailable result = assertInstanceOf(IssueResult.Unavailable.class, client.createIssue(ISSUE));

        assertEquals(IssueResult.Reason.PROJECT_NOT_ACCEPTING, result.reason());
    }

    @Test
    void throttlingIsReportedAsUnavailable() {
        stub.reply(429, "{\"error\":\"Too many requests. Please retry shortly.\"}");

        IssueResult.Unavailable result = assertInstanceOf(IssueResult.Unavailable.class, client.createIssue(ISSUE));

        assertEquals(IssueResult.Reason.TOO_MANY_REQUESTS, result.reason());
        assertEquals(1, stub.received().size());
    }

    @Test
    void refusedReleaseLabelIsDroppedAndRequestRepeated() {
        stub.reply(403, "{\"error\":\"Auth token does not have permission to create releases\"}")
                .reply(201, CREATED);

        IssueResult.Created created = assertInstanceOf(IssueResult.Created.class, client.createIssue(ISSUE));

        assertTrue(created.releaseLabelIgnored());
        assertEquals(2, stub.received().size());
        JsonObject retried = JsonParser.parseString(stub.received().get(1).body()).getAsJsonObject().getAsJsonObject("issue");
        assertFalse(retried.has("release_label"));
    }

    @Test
    void validationErrorIsRejectedWithoutRetry() {
        stub.reply(422, "{\"error\":\"Description must be at least 50 characters long. \",\"status\":422}");

        IssueResult result = client.createIssue(ISSUE);

        assertEquals(new IssueResult.Rejected(422, "Description must be at least 50 characters long."), result);
        assertEquals(1, stub.received().size());
    }

    @Test
    void invalidTokenIsRejected() {
        stub.reply(401, "{\"error\":\"You must be signed in to perform this action.\"}");

        assertEquals(new IssueResult.Rejected(401, "You must be signed in to perform this action."), client.createIssue(ISSUE));
    }

    @Test
    void wrongProjectIsRejected() {
        stub.reply(403, "{\"error\":\"Auth token does not belong to this project.\"}");

        assertEquals(new IssueResult.Rejected(403, "Auth token does not belong to this project."), client.createIssue(ISSUE));
        assertEquals(1, stub.received().size());
    }

    @Test
    void badGatewayIsNotRetriedBecauseIssueMayExist() {
        stub.reply(502, "<html>Bad Gateway</html>");

        IssueResult.Failed failed = assertInstanceOf(IssueResult.Failed.class, client.createIssue(ISSUE));

        assertTrue(failed.mayHaveBeenCreated());
        assertEquals(1, stub.received().size());
    }

    @Test
    void unavailableGatewayIsRetriedOnce() {
        stub.reply(503, "<html>Service Unavailable</html>").reply(201, CREATED);

        assertInstanceOf(IssueResult.Created.class, client.createIssue(ISSUE));
        assertEquals(2, stub.received().size());
    }

    @Test
    void persistentUnavailabilityFailsAfterOneRetry() {
        stub.reply(503, "<html>\n<body>Service   Unavailable</body>\n</html>").reply(503, "<html>Service Unavailable</html>");

        IssueResult.Failed failed = assertInstanceOf(IssueResult.Failed.class, client.createIssue(ISSUE));

        assertEquals("HTTP 503: <html>Service Unavailable</html>", failed.reason());
        assertFalse(failed.mayHaveBeenCreated());
        assertEquals(2, stub.received().size());
    }

    @Test
    void internalServerErrorIsNotRetriedBecauseIssueMayExist() {
        stub.reply(500, "{\"error\":\"boom\"}");

        IssueResult.Failed failed = assertInstanceOf(IssueResult.Failed.class, client.createIssue(ISSUE));
        assertTrue(failed.mayHaveBeenCreated());
        assertEquals(1, stub.received().size());
    }

    @Test
    void slowAnswerTimesOutWithoutRetry() {
        stub.reply(new StubBetaHub.Reply(201, CREATED, SHORT_TIMEOUT.toMillis() * 4));

        IssueResult.Failed failed = assertInstanceOf(IssueResult.Failed.class, client.createIssue(ISSUE));

        assertTrue(failed.reason().contains("did not answer in time"), failed.reason());
        assertTrue(failed.mayHaveBeenCreated());
        assertEquals(1, stub.received().size());
    }

    @Test
    void unreachableServerFailsAfterRetry() {
        URI deadUrl = stub.baseUrl();
        stub.close();

        IssueResult.Failed failed = assertInstanceOf(IssueResult.Failed.class, clientFor(deadUrl).createIssue(ISSUE));

        assertEquals("could not connect to BetaHub", failed.reason());
        assertFalse(failed.mayHaveBeenCreated());
    }

    @Test
    void createdIssueWithoutHttpUrlIsTreatedAsFailure() {
        stub.reply(201, "{\"id\": 1, \"scoped_id\": 1, \"url\": \"javascript:alert(1)\"}");

        assertInstanceOf(IssueResult.Failed.class, client.createIssue(ISSUE));
    }

    @Test
    void createdIssueUrlWithQuoteIsRefused() {
        stub.reply(201, "{\"id\": 1, \"scoped_id\": 1, \"url\": \"https://x.io/a'><b>\"}");

        assertInstanceOf(IssueResult.Failed.class, client.createIssue(ISSUE));
    }

    @Test
    void unreadableSuccessBodyIsFailure() {
        stub.reply(201, "not json");

        IssueResult.Failed failed = assertInstanceOf(IssueResult.Failed.class, client.createIssue(ISSUE));

        assertTrue(failed.reason().contains("may have been created"));
        assertTrue(failed.mayHaveBeenCreated());
    }

    @Test
    void verifiesTokenWithBearerHeader() {
        stub.reply(200, """
                {"valid": true, "token_type": "project_auth_token",
                 "project": {"id": 5, "name": "Network", "slug": "pr-0690627851"},
                 "permissions": {"can_create_bug_report": true, "can_create_feature_request": false,
                                 "can_create_ticket": false, "can_read_release_list": false, "can_create_release": false},
                 "rate_limits": {"bug_reports_per_day": 8, "feature_requests_per_day": 8, "tickets_per_day": 8},
                 "token_name": "Minecraft"}""");

        TokenCheck check = client.verifyToken();

        assertEquals(new TokenCheck.Valid("Minecraft", PROJECT, true, false, 8), check);
        StubBetaHub.Received request = stub.received().get(0);
        assertEquals("POST", request.method());
        assertEquals("/auth/verify", request.path());
        assertEquals("Bearer tkn-abc123", request.header("Authorization"));
    }

    @Test
    void unknownTokenIsInvalid() {
        stub.reply(401, "{\"valid\":false,\"error\":\"Invalid or expired token\"}");

        assertEquals(new TokenCheck.Invalid("Invalid or expired token"), client.verifyToken());
    }

    private static BetaHubClient clientFor(URI baseUrl) {
        ReporterConfig.BetaHub settings = new ReporterConfig.BetaHub(baseUrl, PROJECT, TOKEN, null, SHORT_TIMEOUT);
        return new BetaHubClient(settings, BetaHubClient.newHttpClient(BetaHubClient.DEFAULT_CONNECT_TIMEOUT),
                new BetaHubClient.Options(Duration.ofMillis(10), "BetaHubBugReporter/test"));
    }
}
