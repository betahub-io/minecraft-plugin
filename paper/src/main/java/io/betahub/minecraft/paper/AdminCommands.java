package io.betahub.minecraft.paper;

import io.betahub.minecraft.core.Diagnostics;
import io.betahub.minecraft.core.IssueAssembler;
import io.betahub.minecraft.core.ReportDraft;
import io.betahub.minecraft.core.ReportFormatter;
import io.betahub.minecraft.core.api.IssueResult;
import io.betahub.minecraft.core.api.IssueSubmitter;
import io.betahub.minecraft.core.api.NewIssue;
import io.betahub.minecraft.core.api.TokenCheck;
import io.betahub.minecraft.core.config.ReporterConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** {@code /bugreport reload} and {@code /bugreport test}. Output goes to the sender, so it works from the console too. */
public final class AdminCommands {

    public static final String PERMISSION_ADMIN = "betahub.admin";

    private final Plugin plugin;
    private final Supplier<PluginState> state;
    private final Runnable reload;
    private final Clock clock;

    public AdminCommands(Plugin plugin, Supplier<PluginState> state, Runnable reload, Clock clock) {
        this.plugin = plugin;
        this.state = state;
        this.reload = reload;
        this.clock = clock;
    }

    public void reload(CommandSender sender) {
        reload.run();
        PluginState current = state.get();
        if (current.configured()) {
            sender.sendMessage(Component.text("BetaHub Bug Reporter config reloaded.", NamedTextColor.GREEN));
        } else {
            sendConfigErrors(sender, current);
        }
    }

    /** Checks config, the dialog API on this server version, the token, then files a real test issue. */
    @SuppressWarnings("UnstableApiUsage")
    public void test(CommandSender sender) {
        PluginState current = state.get();
        if (!current.configured()) {
            sendConfigErrors(sender, current);
            return;
        }
        sender.sendMessage(Component.text("Config OK.", NamedTextColor.GREEN));
        try {
            ReportDialog.build(ReportDialog.Content.of(current.messages(), current.config().limits(),
                    new ReportDraft("", ""), null), (view, audience) -> { });
            sender.sendMessage(Component.text("Dialog API OK on " + plugin.getServer().getName() + " "
                    + plugin.getServer().getMinecraftVersion() + ".", NamedTextColor.GREEN));
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Dialog API check failed", e);
            sender.sendMessage(Component.text("Dialog API check failed: " + e + ". /bug will not work on this server "
                    + "version; see the console.", NamedTextColor.RED));
        }
        sender.sendMessage(Component.text("Checking the token with BetaHub...", NamedTextColor.GRAY));

        ReporterConfig config = current.config();
        IssueSubmitter submitter = current.submitter();
        String minecraftVersion = plugin.getServer().getMinecraftVersion();
        String software = plugin.getServer().getName();
        String version = plugin.getServer().getVersion();
        String pluginVersion = plugin.getPluginMeta().getVersion();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            TokenCheck check = submitter.verifyToken();
            List<Diagnostics.Line> tokenLines = Diagnostics.describeToken(check, config.betaHub().projectId());
            if (!Diagnostics.tokenUsable(check, config.betaHub().projectId())) {
                reply(sender, tokenLines);
                return;
            }
            String releaseLabel = IssueAssembler.releaseLabel(config, minecraftVersion);
            NewIssue issue = new NewIssue(
                    ReportFormatter.testDescription(config.serverName(), software, version, pluginVersion, clock.instant()),
                    null, releaseLabel, Map.of(), null);
            IssueResult result = submitter.createIssue(issue);
            List<Diagnostics.Line> resultLines = Diagnostics.describeResult(result, releaseLabel);
            reply(sender, concat(tokenLines, List.of(new Diagnostics.Line(Diagnostics.Severity.INFO,
                    "Sent a test issue (uses one report from today's limit)."))), resultLines, result);
        });
    }

    private void reply(CommandSender sender, List<Diagnostics.Line> lines) {
        reply(sender, lines, List.of(), null);
    }

    private void reply(CommandSender sender, List<Diagnostics.Line> first, List<Diagnostics.Line> second, IssueResult result) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            concat(first, second).forEach(line -> sender.sendMessage(Component.text(line.text(), color(line.severity()))));
            if (result instanceof IssueResult.Created created) {
                sender.sendMessage(Component.text("Open test issue", NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(created.url().toString())));
            }
        });
    }

    private static void sendConfigErrors(CommandSender sender, PluginState current) {
        sender.sendMessage(Component.text("config.yml has problems; /bug is disabled until they are fixed:",
                NamedTextColor.RED));
        current.configErrors().forEach(error -> sender.sendMessage(Component.text(" - " + error, NamedTextColor.RED)));
    }

    private static NamedTextColor color(Diagnostics.Severity severity) {
        return switch (severity) {
            case INFO -> NamedTextColor.GREEN;
            case WARNING -> NamedTextColor.GOLD;
            case ERROR -> NamedTextColor.RED;
        };
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
