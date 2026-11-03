package io.betahub.minecraft.paper;

import io.betahub.minecraft.core.CooldownTracker;
import io.betahub.minecraft.core.Diagnostics;
import io.betahub.minecraft.core.IssueAssembler;
import io.betahub.minecraft.core.LogRedactor;
import io.betahub.minecraft.core.LogTail;
import io.betahub.minecraft.core.ReportContext;
import io.betahub.minecraft.core.ReportDraft;
import io.betahub.minecraft.core.ReportValidator;
import io.betahub.minecraft.core.api.IssueResult;
import io.betahub.minecraft.core.api.NewIssue;
import io.betahub.minecraft.core.config.ReporterConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The {@code /bug} flow: checks, dialog, validation, async submission, player feedback. */
public final class ReportFlow {

    public static final String PERMISSION_REPORT = "betahub.report";
    public static final String PERMISSION_BYPASS_COOLDOWN = "betahub.bypasscooldown";

    /** Paper writes its log here, relative to the server's working directory. */
    static final Path SERVER_LOG = Path.of("logs", "latest.log");
    /** Upper bound for the log excerpt, so a runaway line cannot produce a huge request. */
    static final int MAX_LOG_EXCERPT_BYTES = 512 * 1024;

    private final Plugin plugin;
    private final Supplier<PluginState> state;
    private final ContextCollector collector;
    private final CooldownTracker cooldowns;
    private final ReportDialog.Opener dialogs;
    private final Path serverLog;
    private final Logger log;

    public ReportFlow(Plugin plugin, Supplier<PluginState> state, ContextCollector collector, CooldownTracker cooldowns,
                      ReportDialog.Opener dialogs, Path serverLog) {
        this.plugin = plugin;
        this.state = state;
        this.collector = collector;
        this.cooldowns = cooldowns;
        this.dialogs = dialogs;
        this.serverLog = serverLog;
        this.log = plugin.getLogger();
    }

    /** {@code /bug}: snapshot the context now (where the bug was noticed), then ask for details. */
    public void start(CommandSender sender) {
        PluginState current = state.get();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(current.messages().get(Messages.Key.PLAYERS_ONLY));
            return;
        }
        if (!current.configured()) {
            player.sendMessage(current.messages().get(Messages.Key.NOT_CONFIGURED));
            return;
        }
        if (sendCooldownIfBlocked(player, current)) {
            return;
        }
        ReportContext context = collector.collect(player, current.config());
        openDialog(player, context, new ReportDraft("", ""), null, current);
    }

    void submit(Player player, ReportContext context, ReportDraft draft) {
        PluginState current = state.get();
        Messages messages = current.messages();
        if (!current.configured()) {
            player.sendMessage(messages.get(Messages.Key.NOT_CONFIGURED));
            return;
        }
        ReporterConfig config = current.config();
        Optional<ReportValidator.Error> error = new ReportValidator(config.limits()).validate(draft);
        if (error.isPresent()) {
            openDialog(player, context, draft, errorMessage(error.get(), config, messages), current);
            return;
        }
        if (sendCooldownIfBlocked(player, current)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Instant cooldown = cooldowns.start(playerId);
        player.sendActionBar(messages.get(Messages.Key.SENDING));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            NewIssue issue = IssueAssembler.assemble(config, draft, context, logExcerpt(config));
            IssueResult result;
            try {
                result = current.submitter().createIssue(issue);
            } catch (RuntimeException e) {
                // A bug on our side must still end with an answer to the player and a lifted cooldown.
                log.log(Level.SEVERE, "Unexpected error while sending a bug report", e);
                result = new IssueResult.Failed("unexpected error: " + e, e, false);
            }
            IssueResult outcome = result;
            if (plugin.isEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> finish(playerId, cooldown, context.playerName(), issue, outcome, messages));
            }
        });
    }

    private void finish(UUID playerId, Instant cooldown, String playerName, NewIssue issue, IssueResult result,
                        Messages messages) {
        logResult(playerName, issue, result);
        Component reply = switch (result) {
            case IssueResult.Created created -> messages.submitted(created.url(), created.reference());
            case IssueResult.Unavailable ignored -> messages.get(Messages.Key.RATE_LIMITED);
            case IssueResult.Rejected rejected when rejected.status() == HttpStatus.UNPROCESSABLE_CONTENT ->
                    messages.get(Messages.Key.REJECTED);
            case IssueResult.Rejected ignored -> messages.get(Messages.Key.FAILED);
            case IssueResult.Failed failed when failed.mayHaveBeenCreated() -> messages.get(Messages.Key.UNCERTAIN);
            case IssueResult.Failed ignored -> messages.get(Messages.Key.FAILED);
        };
        if (allowsImmediateRetry(result)) {
            cooldowns.clear(playerId, cooldown);
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(reply);
        }
    }

    /**
     * Retrying is fine when nothing was filed. Not after a filed or possibly filed report (duplicates),
     * and not when BetaHub asked us to back off.
     */
    private static boolean allowsImmediateRetry(IssueResult result) {
        return switch (result) {
            case IssueResult.Created ignored -> false;
            case IssueResult.Unavailable ignored -> false;
            case IssueResult.Rejected ignored -> true;
            case IssueResult.Failed failed -> !failed.mayHaveBeenCreated();
        };
    }

    private void logResult(String playerName, NewIssue issue, IssueResult result) {
        List<Diagnostics.Line> lines = Diagnostics.describeResult(result, issue.releaseLabel());
        for (Diagnostics.Line line : lines) {
            log.log(level(line.severity()), "[" + playerName + "] " + line.text());
        }
        if (result instanceof IssueResult.Failed failed && failed.cause() != null) {
            log.log(Level.FINE, "Submission failure details", failed.cause());
        }
    }

    static Level level(Diagnostics.Severity severity) {
        return switch (severity) {
            case INFO -> Level.INFO;
            case WARNING -> Level.WARNING;
            case ERROR -> Level.SEVERE;
        };
    }

    /** Runs on the async thread. A missing log never blocks the report itself. */
    private String logExcerpt(ReporterConfig config) {
        if (!config.serverLog().attach()) {
            return null;
        }
        try {
            String tail = LogTail.read(serverLog, config.serverLog().lines(), MAX_LOG_EXCERPT_BYTES);
            return new LogRedactor(List.of(config.betaHub().token())).redact(tail);
        } catch (IOException e) {
            log.warning("Could not read " + serverLog + " for the report, sending it without logs: " + e.getMessage());
            return null;
        }
    }

    private boolean sendCooldownIfBlocked(Player player, PluginState current) {
        if (player.hasPermission(PERMISSION_BYPASS_COOLDOWN)) {
            return false;
        }
        Duration remaining = cooldowns.remaining(player.getUniqueId());
        if (remaining.isZero()) {
            return false;
        }
        player.sendMessage(current.messages().cooldown(remaining));
        return true;
    }

    private void openDialog(Player player, ReportContext context, ReportDraft draft, Component error, PluginState current) {
        ReportDialog.Content content = ReportDialog.Content.of(current.messages(), current.config().limits(), draft, error);
        dialogs.open(player, content, (submitter, submitted) -> submit(submitter, context, submitted));
    }

    private static Component errorMessage(ReportValidator.Error error, ReporterConfig config, Messages messages) {
        Messages.Key key = switch (error) {
            case DESCRIPTION_TOO_SHORT -> Messages.Key.DESCRIPTION_TOO_SHORT;
            case DESCRIPTION_TOO_LONG -> Messages.Key.DESCRIPTION_TOO_LONG;
            case STEPS_TOO_LONG -> Messages.Key.STEPS_TOO_LONG;
        };
        int max = error == ReportValidator.Error.STEPS_TOO_LONG
                ? config.limits().stepsMaxLength()
                : config.limits().descriptionMaxLength();
        return messages.withLimits(key, config.limits().descriptionMinLength(), max);
    }

    private static final class HttpStatus {
        static final int UNPROCESSABLE_CONTENT = 422;

        private HttpStatus() {
        }
    }
}
