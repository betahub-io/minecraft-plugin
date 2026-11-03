package io.betahub.minecraft.paper;

import com.mojang.brigadier.Command;
import io.betahub.minecraft.core.CooldownTracker;
import io.betahub.minecraft.core.Diagnostics;
import io.betahub.minecraft.core.api.BetaHubClient;
import io.betahub.minecraft.core.api.IssueSubmitter;
import io.betahub.minecraft.core.api.TokenCheck;
import io.betahub.minecraft.core.config.ConfigParser;
import io.betahub.minecraft.core.config.ReporterConfig;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

// Not final: MockBukkit subclasses plugin classes when loading them.
public class BetaHubBugReporterPlugin extends JavaPlugin {

    static final String BUG_COMMAND = "bug";
    static final String REPORT_ALIAS = "report";
    static final String ADMIN_COMMAND = "bugreport";
    static final String REPORT_ALIAS_PATH = "commands.report-alias";
    static final String VIA_VERSION = "ViaVersion";
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

    private final Clock clock = Clock.systemUTC();
    private volatile PluginState state;
    private HttpClient httpClient;
    private CooldownTracker cooldowns;
    private ReportFlow flow;
    private AdminCommands admin;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        httpClient = BetaHubClient.newHttpClient(BetaHubClient.DEFAULT_CONNECT_TIMEOUT);
        cooldowns = new CooldownTracker(Duration.ZERO, clock);
        reloadState();

        ClientVersionLookup clientVersions = getServer().getPluginManager().isPluginEnabled(VIA_VERSION)
                ? new ViaVersionLookup()
                : ClientVersionLookup.NONE;
        ContextCollector collector = new ContextCollector(getServer(), clientVersions, getPluginMeta().getVersion(), clock);
        flow = new ReportFlow(this, () -> state, collector, cooldowns, ReportDialog.opener(this),
                ReportFlow.SERVER_LOG);
        admin = new AdminCommands(this, () -> state, this::reloadState, clock);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> registerCommands(event.registrar()));
    }

    @Override
    public void onDisable() {
        if (httpClient != null) {
            // Do not hold up server shutdown for a report still waiting on BetaHub.
            httpClient.shutdownNow();
        }
    }

    /** Re-reads config.yml; on errors, keeps the plugin running with {@code /bug} disabled. */
    void reloadState() {
        reloadConfig();
        Messages messages = Messages.load(getConfig());
        ConfigParser.Result result = ConfigParser.parse(new PaperConfigSource(getConfig()));
        if (!result.isValid()) {
            state = new PluginState(messages, null, null, result.errors());
            getLogger().warning("config.yml has problems; /bug is disabled until they are fixed and /bugreport reload is run:");
            result.errors().forEach(error -> getLogger().warning(" - " + error));
            return;
        }
        ReporterConfig config = result.config();
        cooldowns.setCooldown(config.cooldown());
        IssueSubmitter submitter = newClient(config);
        state = new PluginState(messages, config, submitter, List.of());
        verifyTokenInBackground(config, submitter);
    }

    private IssueSubmitter newClient(ReporterConfig config) {
        String userAgent = "BetaHubBugReporter/" + getPluginMeta().getVersion()
                + " (" + getServer().getName() + " " + getServer().getMinecraftVersion() + ")";
        return new BetaHubClient(config.betaHub(), httpClient, new BetaHubClient.Options(RETRY_DELAY, userAgent));
    }

    /** Uses no report quota; surfaces token problems at startup instead of at the first player report. */
    private void verifyTokenInBackground(ReporterConfig config, IssueSubmitter submitter) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            TokenCheck check = submitter.verifyToken();
            for (Diagnostics.Line line : Diagnostics.describeToken(check, config.betaHub().projectId())) {
                getLogger().log(ReportFlow.level(line.severity()), line.text());
            }
        });
    }

    private void registerCommands(Commands commands) {
        Predicate<CommandSourceStack> canReport = source -> source.getSender().hasPermission(ReportFlow.PERMISSION_REPORT);
        Predicate<CommandSourceStack> isAdmin = source -> source.getSender().hasPermission(AdminCommands.PERMISSION_ADMIN);

        List<String> aliases = getConfig().getBoolean(REPORT_ALIAS_PATH) ? List.of(REPORT_ALIAS) : List.of();
        commands.register(Commands.literal(BUG_COMMAND)
                        .requires(canReport)
                        .executes(context -> {
                            flow.start(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                        .build(),
                "Report a bug to the developers", aliases);

        commands.register(Commands.literal(ADMIN_COMMAND)
                        .requires(isAdmin)
                        .then(Commands.literal("reload").executes(context -> {
                            admin.reload(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("test").executes(context -> {
                            admin.test(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                        .build(),
                "BetaHub Bug Reporter: reload config or send a test report");
    }
}
