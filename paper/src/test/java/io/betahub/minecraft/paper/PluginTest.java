package io.betahub.minecraft.paper;

import io.betahub.minecraft.core.api.StubBetaHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginTest {

    private ServerMock server;
    private BetaHubBugReporterPlugin plugin;
    private StubBetaHub stub;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BetaHubBugReporterPlugin.class);
        stub = new StubBetaHub();
    }

    @AfterEach
    void tearDown() {
        stub.close();
        MockBukkit.unmock();
    }

    @Test
    void shippedConfigLeavesBugDisabledWithClearErrors() {
        ConsoleCommandSenderMock console = server.getConsoleSender();
        server.dispatchCommand(console, "bugreport reload");
        assertEquals(List.of("config.yml has problems; /bug is disabled until they are fixed:",
                " - betahub.project-id is not set", " - betahub.token is not set", " - server-name is not set"), drain(console));

        PlayerMock player = server.addPlayer();
        player.performCommand("bug");

        assertEquals("Bug reporting is not set up on this server yet.", plain(player.nextComponentMessage()));
    }

    @Test
    void reportAliasIsOffByDefault() {
        PlayerMock player = server.addPlayer();

        assertFalse(player.performCommand("report"));
    }

    @Test
    void consoleCannotReportBugs() {
        configureForStub();
        ConsoleCommandSenderMock console = server.getConsoleSender();
        drain(console);

        server.dispatchCommand(console, "bug");

        assertEquals("Only players can report bugs.", plain(console.nextComponentMessage()));
    }

    @Test
    void playersWithoutAdminPermissionDoNotSeeBugreport() {
        PlayerMock player = server.addPlayer();
        player.setOp(false);

        player.performCommand("bugreport reload");

        assertEquals(List.of("Unknown command"), drain(player));
    }

    @Test
    void reloadPicksUpFixedConfig() {
        ConsoleCommandSenderMock console = server.getConsoleSender();

        configureForStub();

        assertEquals("BetaHub Bug Reporter config reloaded.", plain(console.nextComponentMessage()));
    }

    @Test
    void testCommandVerifiesTokenAndCreatesTestIssue() {
        configureForStub();
        stub.reply(200, TestData.VERIFY_OK).reply(201, TestData.CREATED);
        ConsoleCommandSenderMock console = server.getConsoleSender();
        drain(console);

        server.dispatchCommand(console, "bugreport test");

        List<String> lines = Await.linesUntil(server, console, line -> line.equals("Open test issue"));
        assertEquals("Config OK.", lines.get(0));
        // MockBukkit has no dialog implementation; on a real server this line says "Dialog API OK".
        assertTrue(lines.get(1).startsWith("Dialog API check failed"), lines::toString);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("BetaHub token 'Minecraft' is valid for project pr-0690627851")), lines::toString);
        assertTrue(lines.contains("Bug report #42 created: https://app.betahub.io/projects/pr-0690627851/issues/42"), lines::toString);
        List<String> paths = stub.received().stream().map(StubBetaHub.Received::path).toList();
        assertEquals(List.of("/auth/verify", "/auth/verify", "/projects/pr-0690627851/issues.json"), paths);
        assertTrue(stub.received().get(2).body().contains("survival"));
    }

    @Test
    void testCommandStopsBeforeCreatingIssueWhenTokenIsForAnotherProject() {
        configureForStub();
        stub.reply(200, TestData.VERIFY_OK.replace("pr-0690627851", "pr-111"));
        ConsoleCommandSenderMock console = server.getConsoleSender();
        drain(console);

        server.dispatchCommand(console, "bugreport test");

        List<String> lines = Await.linesUntil(server, console, line -> line.startsWith("The token belongs"));
        assertEquals("The token belongs to project pr-111, but betahub.project-id is pr-0690627851.", lines.get(lines.size() - 1));
        Await.until(server, () -> stub.received().size() == 2);
        assertTrue(stub.received().stream().noneMatch(request -> request.path().endsWith("issues.json")));
    }

    /** Points the plugin at the stub and reloads; the reload verifies the token in the background. */
    private void configureForStub() {
        stub.reply(200, TestData.VERIFY_OK);
        plugin.getConfig().set("betahub.base-url", stub.baseUrl().toString());
        plugin.getConfig().set("betahub.project-id", "pr-0690627851");
        plugin.getConfig().set("betahub.token", "tkn-abc123");
        plugin.getConfig().set("server-name", "survival");
        plugin.saveConfig();
        server.dispatchCommand(server.getConsoleSender(), "bugreport reload");
        Await.until(server, () -> stub.received().size() == 1);
    }

    private static List<String> drain(org.mockbukkit.mockbukkit.command.MessageTarget target) {
        List<String> lines = new ArrayList<>();
        Component next;
        while ((next = target.nextComponentMessage()) != null) {
            lines.add(plain(next));
        }
        return lines;
    }

    static String plain(Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }
}
