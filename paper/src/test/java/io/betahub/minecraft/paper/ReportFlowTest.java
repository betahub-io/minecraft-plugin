package io.betahub.minecraft.paper;

import io.betahub.minecraft.core.CooldownTracker;
import io.betahub.minecraft.core.ReportDraft;
import io.betahub.minecraft.core.api.IssueResult;
import io.betahub.minecraft.core.api.IssueSubmitter;
import io.betahub.minecraft.core.api.NewIssue;
import io.betahub.minecraft.core.api.TokenCheck;
import io.betahub.minecraft.core.config.ReporterConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static io.betahub.minecraft.paper.PluginTest.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportFlowTest {

    private static final URI ISSUE_URL = URI.create("https://app.betahub.io/projects/pr-0690627851/issues/42");
    private static final IssueResult CREATED = new IssueResult.Created(9001, "42", ISSUE_URL, false, List.of());

    @TempDir
    Path tempDir;

    private ServerMock server;
    private org.bukkit.plugin.Plugin plugin;
    private PlayerMock player;
    private final FakeSubmitter submitter = new FakeSubmitter();
    private final List<Opened> opened = new ArrayList<>();
    private final CooldownTracker cooldowns = new CooldownTracker(Duration.ZERO, Clock.systemUTC());
    private PluginState state;
    private Path serverLog;
    private ReportFlow flow;

    record Opened(org.bukkit.entity.Player player, ReportDialog.Content content, ReportDialog.SubmitHandler onSubmit) {
        void submit(String description, String steps) {
            onSubmit.submit(player, new ReportDraft(description, steps));
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = new BrandedPlayerMock(server, "Notch", "vanilla");
        server.addPlayer(player);
        player.setOp(false);
        serverLog = tempDir.resolve("latest.log");
        useConfig(Map.of());
        ContextCollector collector = new ContextCollector(server, uuid -> java.util.Optional.of("1.21.11"), "0.1.0",
                Clock.systemUTC());
        flow = new ReportFlow(plugin, () -> state, collector, cooldowns,
                (target, content, onSubmit) -> opened.add(new Opened(target, content, onSubmit)), serverLog);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bugOpensEmptyDialogWithConfiguredLimits() {
        flow.start(player);

        assertEquals(1, opened.size());
        ReportDialog.Content content = opened.get(0).content();
        assertEquals("Report a bug", plain(content.title()));
        assertEquals(1000, content.limits().descriptionMaxLength());
        assertEquals("", content.initial().description());
        assertNull(content.error());
    }

    @Test
    void contextIsTakenWhenBugIsRun() {
        submitter.results.add(CREATED);
        player.setLocation(new Location(player.getWorld(), 10.5, 70, -20.5));
        flow.start(player);
        player.setLocation(new Location(player.getWorld(), 500, 100, 500));

        opened.get(0).submit("The chest here is empty", "");
        Await.message(server, player);

        String description = submitter.issues.get(0).description();
        assertTrue(description.contains("- Position: 10 70 -21"), description);
    }

    @Test
    void validReportIsSentAndPlayerGetsLink() {
        submitter.results.add(CREATED);
        flow.start(player);

        opened.get(0).submit("  Villager trades reset after relog  ", "1. trade\n2. relog");
        assertEquals("Sending report...", plain(player.nextActionBar()));
        Component reply = Await.message(server, player);

        NewIssue issue = submitter.issues.get(0);
        assertTrue(issue.description().startsWith("Villager trades reset after relog\n\n---\n"), issue.description());
        assertTrue(issue.description().contains("- Player: Notch (" + player.getUniqueId() + ")"));
        assertTrue(issue.description().contains("- Server: survival ("));
        assertTrue(issue.description().contains("- Client: 1.21.11, brand vanilla"), issue.description());
        assertEquals("1. trade\n2. relog", issue.steps());
        assertEquals(server.getMinecraftVersion(), issue.releaseLabel());
        assertNull(issue.logs());

        assertEquals("Thanks! Bug report submitted. View it on BetaHub", plain(reply));
        assertEquals(ClickEvent.openUrl(ISSUE_URL.toString()), findClick(reply));
    }

    @Test
    void tooShortDescriptionReopensDialogWithErrorAndKeepsText() {
        flow.start(player);

        opened.get(0).submit("short", "my steps");

        assertEquals(2, opened.size());
        ReportDialog.Content retry = opened.get(1).content();
        assertEquals("Please describe the bug in at least 10 characters.", plain(retry.error()));
        assertEquals("short", retry.initial().description());
        assertEquals("my steps", retry.initial().steps());
        assertTrue(submitter.issues.isEmpty());
    }

    @Test
    void tooLongStepsAreRejected() {
        flow.start(player);

        opened.get(0).submit("A long enough description", "x".repeat(1001));

        assertEquals("Please keep the steps under 1000 characters.", plain(opened.get(1).content().error()));
    }

    @Test
    void cooldownBlocksSecondReport() {
        useConfig(Map.of("report.cooldown-seconds", 120));
        submitter.results.add(CREATED);
        flow.start(player);
        opened.get(0).submit("First report about lag", "");
        Await.message(server, player);

        flow.start(player);

        assertEquals(1, opened.size());
        String message = plain(player.nextComponentMessage());
        assertTrue(message.matches("Please wait 1[12][0-9]s before sending another report\\."), message);
    }

    @Test
    void bypassPermissionSkipsCooldown() {
        useConfig(Map.of("report.cooldown-seconds", 120));
        player.addAttachment(plugin, ReportFlow.PERMISSION_BYPASS_COOLDOWN, true);
        submitter.results.add(CREATED);
        flow.start(player);
        opened.get(0).submit("First report about lag", "");
        Await.message(server, player);

        flow.start(player);

        assertEquals(2, opened.size());
    }

    @Test
    void failedSubmissionLiftsCooldown() {
        useConfig(Map.of("report.cooldown-seconds", 120));
        submitter.results.add(new IssueResult.Failed("could not connect to BetaHub", null, false));
        flow.start(player);
        opened.get(0).submit("Report that will fail", "");

        assertEquals("Sorry, the report could not be sent. Please try again later.", plain(Await.message(server, player)));
        flow.start(player);
        assertEquals(2, opened.size());
    }

    @Test
    void uncertainOutcomeKeepsCooldownSoPlayerDoesNotResend() {
        useConfig(Map.of("report.cooldown-seconds", 120));
        submitter.results.add(new IssueResult.Failed("BetaHub did not answer in time", null, true));
        flow.start(player);
        opened.get(0).submit("Report that timed out", "");

        assertEquals("BetaHub did not confirm your report, but it may have arrived. Please don't send it again yet.",
                plain(Await.message(server, player)));
        flow.start(player);
        assertEquals(1, opened.size());
    }

    @Test
    void rateLimitKeepsCooldownAndSaysUnavailable() {
        useConfig(Map.of("report.cooldown-seconds", 120));
        submitter.results.add(new IssueResult.Unavailable(IssueResult.Reason.DAILY_LIMIT_OR_NO_PERMISSION,
                "Not allowed to report a bug."));
        flow.start(player);
        opened.get(0).submit("Report hitting the limit", "");

        assertEquals("Bug reporting is temporarily unavailable.", plain(Await.message(server, player)));
        flow.start(player);
        assertEquals(1, opened.size());
    }

    @Test
    void validationRejectionAsksForMoreDetail() {
        submitter.results.add(new IssueResult.Rejected(422, "Description must be at least 50 characters long."));
        flow.start(player);
        opened.get(0).submit("Something is off", "");

        assertEquals("BetaHub could not accept this report. Please describe the problem in more detail.",
                plain(Await.message(server, player)));
    }

    @Test
    void serverLogIsAttachedWithoutIpsOrToken() throws IOException {
        Files.writeString(serverLog, "[10:00:00 INFO]: old line\n"
                + "[10:00:01 INFO]: Notch[/203.0.113.7:51234] logged in\n"
                + "[10:00:02 INFO]: token tkn-abc123 leaked\n");
        useConfig(Map.of("report.attach-server-log", true, "report.server-log-lines", 2));
        submitter.results.add(CREATED);
        flow.start(player);
        opened.get(0).submit("Report with server logs", "");
        Await.message(server, player);

        assertEquals("[10:00:01 INFO]: Notch[/[IP]:51234] logged in\n[10:00:02 INFO]: token [REDACTED] leaked",
                submitter.issues.get(0).logs());
    }

    @Test
    void missingServerLogDoesNotBlockReport() {
        useConfig(Map.of("report.attach-server-log", true));
        submitter.results.add(CREATED);
        flow.start(player);
        opened.get(0).submit("Report without a log file", "");

        assertEquals("Thanks! Bug report submitted. View it on BetaHub", plain(Await.message(server, player)));
        assertNull(submitter.issues.get(0).logs());
    }

    @Test
    void customFieldsAreFilledFromContext() {
        useConfig(Map.of("custom-fields.server_name", "server", "custom-fields.location", "loc"));
        submitter.results.add(CREATED);
        player.setLocation(new Location(player.getWorld(), 1, 64, 2));
        flow.start(player);
        opened.get(0).submit("Report with custom fields", "");
        Await.message(server, player);

        assertEquals(Map.of("server", "survival", "loc", player.getWorld().getName() + " 1 64 2"),
                submitter.issues.get(0).custom());
    }

    @Test
    void playerWhoLeftBeforeAnswerIsSkippedQuietly() {
        submitter.results.add(CREATED);
        flow.start(player);
        opened.get(0).submit("Report then disconnect", "");
        player.disconnect();

        Await.until(server, () -> submitter.answered.get() == 1);
        server.getScheduler().performTicks(2);
        assertEquals(1, submitter.issues.size());
    }

    @Test
    void unexpectedErrorStillAnswersPlayerAndLiftsCooldown() {
        useConfig(Map.of("report.cooldown-seconds", 120));
        flow.start(player);
        opened.get(0).submit("Report hitting a bug in the plugin", "");

        assertEquals("Sorry, the report could not be sent. Please try again later.", plain(Await.message(server, player)));
        flow.start(player);
        assertEquals(2, opened.size());
    }

    @Test
    void misconfiguredPluginTellsPlayer() {
        state = new PluginState(state.messages(), null, null, List.of("betahub.token is not set"));

        flow.start(player);

        assertTrue(opened.isEmpty());
        assertEquals("Bug reporting is not set up on this server yet.", plain(player.nextComponentMessage()));
    }

    private void useConfig(Map<String, Object> overrides) {
        YamlConfiguration yaml = Fixtures.shippedConfig(overrides);
        ReporterConfig config = Fixtures.config(yaml);
        cooldowns.setCooldown(config.cooldown());
        state = new PluginState(Fixtures.messages(yaml), config, submitter, List.of());
    }

    private static ClickEvent findClick(Component component) {
        if (component.clickEvent() != null) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            ClickEvent found = findClick(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    static final class FakeSubmitter implements IssueSubmitter {
        final List<NewIssue> issues = new java.util.concurrent.CopyOnWriteArrayList<>();
        final Deque<IssueResult> results = new java.util.concurrent.ConcurrentLinkedDeque<>();
        final java.util.concurrent.atomic.AtomicInteger answered = new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public IssueResult createIssue(NewIssue issue) {
            issues.add(issue);
            IssueResult result = results.poll();
            answered.incrementAndGet();
            if (result == null) {
                throw new IllegalStateException("no result queued");
            }
            return result;
        }

        @Override
        public TokenCheck verifyToken() {
            throw new AssertionError("not expected");
        }
    }
}
