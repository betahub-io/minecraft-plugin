package io.betahub.minecraft.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the issue description text sent to BetaHub. */
public final class ReportFormatter {

    static final String CONTEXT_SEPARATOR = "\n\n---\n";
    static final String CONTEXT_HEADER = "**Game context** (attached automatically by BetaHub Bug Reporter)\n";
    private static final String BULLET = "- ";

    private ReportFormatter() {
    }

    /** Player's description followed by a readable "Game context" block. */
    public static String description(ReportDraft draft, ReportContext context) {
        return draft.description() + CONTEXT_SEPARATOR + CONTEXT_HEADER + contextBlock(context);
    }

    /** Description of the issue created by {@code /bugreport test}. */
    public static String testDescription(String serverName, String serverSoftware, String serverVersion,
                                         String pluginVersion, Instant sentAt) {
        return "Test report sent with /bugreport test to verify the BetaHub Bug Reporter setup. "
                + "Safe to delete."
                + CONTEXT_SEPARATOR + CONTEXT_HEADER
                + BULLET + "Server: " + serverName + " (" + serverSoftware + " " + serverVersion + ")\n"
                + BULLET + "Sent at: " + sentAt + "\n"
                + BULLET + "Plugin: BetaHub Bug Reporter " + pluginVersion + "\n";
    }

    static String contextBlock(ReportContext ctx) {
        List<String> lines = new ArrayList<>();
        lines.add("Player: " + ctx.playerName() + " (" + ctx.playerUuid() + ")");
        lines.add("Server: " + ctx.serverName() + " (" + ctx.serverSoftware() + " " + ctx.serverVersion() + ")");
        lines.add("World: " + ctx.worldName() + " (dimension " + ctx.dimensionKey() + ", environment " + ctx.dimension() + ")");
        if (ctx.position() != null) {
            ReportContext.Position pos = ctx.position();
            lines.add("Position: " + blockCoordinates(pos)
                    + " (yaw " + oneDecimal(pos.yaw()) + ", pitch " + oneDecimal(pos.pitch()) + ")");
            lines.add("Teleport: `" + teleportCommand(ctx) + "`");
        }
        if (ctx.biome() != null) {
            lines.add("Biome: " + ctx.biome());
        }
        if (ctx.playerState() != null) {
            ReportContext.PlayerState state = ctx.playerState();
            lines.add("Game mode: " + state.gameMode() + ", health " + oneDecimal(state.health())
                    + ", ping " + state.pingMillis() + " ms");
        }
        if (ctx.client() != null) {
            lines.add("Client: " + clientDescription(ctx.client()));
        }
        lines.add("Minecraft version: " + ctx.minecraftVersion());
        lines.add("Reported at: " + ctx.reportedAt());
        lines.add("Plugin: BetaHub Bug Reporter " + ctx.pluginVersion());

        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(BULLET).append(line).append('\n');
        }
        return out.toString();
    }

    static String blockCoordinates(ReportContext.Position pos) {
        return pos.blockX() + " " + pos.blockY() + " " + pos.blockZ();
    }

    /** Ready-to-paste command that puts a developer at the reporter's exact spot, in the right dimension. */
    static String teleportCommand(ReportContext ctx) {
        ReportContext.Position pos = ctx.position();
        return String.format(Locale.ROOT, "/execute in %s run tp @s %.2f %.2f %.2f %.1f %.1f",
                ctx.dimensionKey(), pos.x(), pos.y(), pos.z(), pos.yaw(), pos.pitch());
    }

    private static String clientDescription(ReportContext.ClientInfo client) {
        List<String> parts = new ArrayList<>();
        if (client.version() != null) {
            parts.add(client.version());
        }
        if (client.brand() != null) {
            parts.add("brand " + client.brand());
        }
        return String.join(", ", parts);
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
