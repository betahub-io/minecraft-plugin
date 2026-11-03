package io.betahub.minecraft.core;

import io.betahub.minecraft.core.api.IssueResult;
import io.betahub.minecraft.core.api.TokenCheck;

import java.util.ArrayList;
import java.util.List;

/** Admin-facing explanations of BetaHub answers, for the console and {@code /bugreport test}. */
public final class Diagnostics {

    /** BetaHub's default "Limit Per Day" for bug reports on a new project auth token. */
    public static final int DEFAULT_DAILY_BUG_REPORT_LIMIT = 8;
    static final String TOKEN_SETTINGS_PATH = "Settings → Integrations → Auth Tokens";

    public enum Severity {
        INFO, WARNING, ERROR
    }

    public record Line(Severity severity, String text) {
    }

    private Diagnostics() {
    }

    /** @return lines describing the token; {@link #tokenUsable} says whether reports can be sent with it */
    public static List<Line> describeToken(TokenCheck check, String configuredProjectId) {
        List<Line> lines = new ArrayList<>();
        switch (check) {
            case TokenCheck.Valid valid -> {
                if (!valid.projectId().equals(configuredProjectId)) {
                    lines.add(error("The token belongs to project " + valid.projectId()
                            + ", but betahub.project-id is " + configuredProjectId + "."));
                    return lines;
                }
                if (!valid.canCreateBugReport()) {
                    lines.add(error("The token may not create bug reports. Enable \"Can Create Bug Report\" in BetaHub ("
                            + TOKEN_SETTINGS_PATH + ")."));
                    return lines;
                }
                lines.add(info("BetaHub token " + quoted(valid.tokenName()) + " is valid for project " + valid.projectId()
                        + ". Daily bug report limit per IP: " + valid.bugReportsPerDay() + "."));
                if (valid.bugReportsPerDay() <= DEFAULT_DAILY_BUG_REPORT_LIMIT) {
                    lines.add(warning("All players on this server share its IP address, so the whole server can send only "
                            + valid.bugReportsPerDay() + " bug reports per day. Raise \"Limit Per Day\" for this token in BetaHub ("
                            + TOKEN_SETTINGS_PATH + ")."));
                }
                if (!valid.canCreateRelease()) {
                    lines.add(info("The token cannot create releases. On a public project, reports for a release label "
                            + "that does not exist yet go to the latest release."));
                }
            }
            case TokenCheck.Invalid invalid -> lines.add(error("BetaHub does not accept the token (" + invalid.error()
                    + "). Check betahub.token."));
            case TokenCheck.Failed failed -> lines.add(warning("Could not verify the token with BetaHub: " + failed.reason()));
        }
        return lines;
    }

    public static boolean tokenUsable(TokenCheck check, String configuredProjectId) {
        return check instanceof TokenCheck.Valid valid
                && valid.projectId().equals(configuredProjectId)
                && valid.canCreateBugReport();
    }

    /** @param releaseLabel label that was sent, or {@code null} */
    public static List<Line> describeResult(IssueResult result, String releaseLabel) {
        List<Line> lines = new ArrayList<>();
        switch (result) {
            case IssueResult.Created created -> {
                lines.add(info("Bug report " + created.reference() + " created: " + created.url()));
                if (created.releaseLabelIgnored()) {
                    lines.add(warning("BetaHub refused to create release '" + releaseLabel + "' (the token lacks \"Can Create "
                            + "Release\"), so the report went to the latest release. Set betahub.release-label to an "
                            + "existing release, or allow the token to create releases."));
                }
                created.warnings().forEach(warning -> lines.add(warning("BetaHub: " + warning)));
            }
            case IssueResult.Unavailable unavailable -> lines.add(warning(switch (unavailable.reason()) {
                case DAILY_LIMIT_OR_NO_PERMISSION -> "BetaHub refused the report (\"" + unavailable.error() + "\"). "
                        + "Most likely the token's daily bug report limit for this server's IP is used up; all players "
                        + "on this server share it. Raise \"Limit Per Day\" for the token in BetaHub (" + TOKEN_SETTINGS_PATH
                        + "). If that is not it, check that the token has \"Can Create Bug Report\". "
                        + "/bugreport test shows the current limit.";
                case PROJECT_NOT_ACCEPTING -> "The BetaHub project does not accept new bug reports right now (\""
                        + unavailable.error() + "\"). Check the organization's plan limits.";
                case TOO_MANY_REQUESTS -> "BetaHub is throttling requests from this server (\"" + unavailable.error()
                        + "\"). Try again shortly.";
            }));
            case IssueResult.Rejected rejected -> lines.add(error("BetaHub rejected the report (HTTP " + rejected.status()
                    + "): " + sentence(rejected.error()) + hint(rejected)));
            case IssueResult.Failed failed -> lines.add(error("Could not send the report to BetaHub: " + failed.reason()
                    + (failed.cause() == null || failed.cause().getMessage() == null ? "" : " (" + failed.cause().getMessage() + ")")
                    + (failed.mayHaveBeenCreated() ? ". The report may exist in BetaHub anyway; the player was told not to resend yet." : "")));
        }
        return lines;
    }

    private static String hint(IssueResult.Rejected rejected) {
        return switch (rejected.status()) {
            case 401 -> " Check betahub.token.";
            case 404 -> " Check betahub.project-id and betahub.base-url.";
            default -> "";
        };
    }

    private static String sentence(String text) {
        return text.endsWith(".") ? text : text + ".";
    }

    private static String quoted(String name) {
        return name == null ? "(unnamed)" : "'" + name + "'";
    }

    private static Line info(String text) {
        return new Line(Severity.INFO, text);
    }

    private static Line warning(String text) {
        return new Line(Severity.WARNING, text);
    }

    private static Line error(String text) {
        return new Line(Severity.ERROR, text);
    }
}
