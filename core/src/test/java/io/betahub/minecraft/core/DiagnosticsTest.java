package io.betahub.minecraft.core;

import io.betahub.minecraft.core.Diagnostics.Line;
import io.betahub.minecraft.core.Diagnostics.Severity;
import io.betahub.minecraft.core.api.IssueResult;
import io.betahub.minecraft.core.api.TokenCheck;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsTest {

    private static final String PROJECT = "pr-1";

    @Test
    void defaultDailyLimitTriggersWarning() {
        TokenCheck check = new TokenCheck.Valid("MC", PROJECT, true, true, 8);

        List<Line> lines = Diagnostics.describeToken(check, PROJECT);

        assertEquals(Severity.INFO, lines.get(0).severity());
        assertEquals(Severity.WARNING, lines.get(1).severity());
        assertTrue(lines.get(1).text().contains("only 8 bug reports per day"));
        assertTrue(Diagnostics.tokenUsable(check, PROJECT));
    }

    @Test
    void raisedLimitHasNoWarning() {
        List<Line> lines = Diagnostics.describeToken(new TokenCheck.Valid("MC", PROJECT, true, true, 500), PROJECT);

        assertEquals(List.of(Severity.INFO), lines.stream().map(Line::severity).toList());
    }

    @Test
    void tokenForOtherProjectOrWithoutPermissionIsUnusable() {
        TokenCheck otherProject = new TokenCheck.Valid("MC", "pr-2", true, true, 500);
        TokenCheck noPermission = new TokenCheck.Valid("MC", PROJECT, false, true, 500);

        assertFalse(Diagnostics.tokenUsable(otherProject, PROJECT));
        assertFalse(Diagnostics.tokenUsable(noPermission, PROJECT));
        assertEquals(Severity.ERROR, Diagnostics.describeToken(otherProject, PROJECT).get(0).severity());
        assertTrue(Diagnostics.describeToken(noPermission, PROJECT).get(0).text().contains("Can Create Bug Report"));
    }

    @Test
    void ignoredReleaseLabelIsExplained() {
        IssueResult created = new IssueResult.Created(1, "7", URI.create("https://x.io/7"), true, List.of("dropped field"));

        List<Line> lines = Diagnostics.describeResult(created, "1.21.11");

        assertEquals("Bug report #7 created: https://x.io/7", lines.get(0).text());
        assertTrue(lines.get(1).text().contains("release '1.21.11'"));
        assertEquals("BetaHub: dropped field", lines.get(2).text());
    }

    @Test
    void dailyLimitPointsToTokenSettings() {
        IssueResult result = new IssueResult.Unavailable(IssueResult.Reason.DAILY_LIMIT_OR_NO_PERMISSION, "Not allowed to report a bug.");

        Line line = Diagnostics.describeResult(result, null).get(0);

        assertEquals(Severity.WARNING, line.severity());
        assertTrue(line.text().contains("Limit Per Day"));
        assertTrue(line.text().contains("Settings → Integrations → Auth Tokens"));
    }

    @Test
    void unauthorizedHintsAtToken() {
        Line line = Diagnostics.describeResult(new IssueResult.Rejected(401, "You must be signed in to perform this action."), null).get(0);

        assertEquals("BetaHub rejected the report (HTTP 401): You must be signed in to perform this action. Check betahub.token.", line.text());
    }
}
