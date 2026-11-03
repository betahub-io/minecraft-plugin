package io.betahub.minecraft.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportFormatterTest {

    @Test
    void fullContextIsRenderedAfterPlayerDescription() {
        String text = ReportFormatter.description(new ReportDraft("  The door is stuck  ", ""), TestContexts.full());

        assertEquals("""
                The door is stuck

                ---
                **Game context** (attached automatically by BetaHub Bug Reporter)
                - Player: Notch (069a79f4-44e9-4726-a5be-fca90e38aaf5)
                - Server: survival (Paper 1.21.11-120-abc (MC: 1.21.11))
                - World: world_nether (dimension minecraft:the_nether, environment NETHER)
                - Position: 120 64 -36 (yaw 90.0, pitch 10.5)
                - Teleport: `/execute in minecraft:the_nether run tp @s 120.50 64.00 -35.25 90.0 10.5`
                - Biome: minecraft:nether_wastes
                - Game mode: SURVIVAL, health 18.5, ping 42 ms
                - Client: 1.21.11, brand vanilla
                - Minecraft version: 1.21.11
                - Reported at: 2026-11-03T18:30:00Z
                - Plugin: BetaHub Bug Reporter 0.1.0
                """, text);
    }

    @Test
    void disabledOrMissingPartsAreOmitted() {
        String text = ReportFormatter.description(new ReportDraft("Lag spike near spawn", null), TestContexts.minimal());

        assertFalse(text.contains("Position"));
        assertFalse(text.contains("Teleport"));
        assertFalse(text.contains("Biome"));
        assertFalse(text.contains("Game mode"));
        assertFalse(text.contains("Client"));
        assertTrue(text.contains("- Server: lobby (Paper 26.3-1 (MC: 26.3))"));
    }

    @Test
    void clientWithOnlyBrandIsRendered() {
        ReportContext base = TestContexts.minimal();
        ReportContext ctx = new ReportContext(base.playerName(), base.playerUuid(), base.serverName(), base.worldName(),
                base.dimension(), base.dimensionKey(), null, null, base.serverSoftware(), base.serverVersion(),
                base.minecraftVersion(), new ReportContext.ClientInfo(null, "fabric"), null, base.reportedAt(),
                base.pluginVersion());

        assertTrue(ReportFormatter.contextBlock(ctx).contains("- Client: brand fabric\n"));
    }

    @Test
    void negativeCoordinatesRoundDownToTheBlockThePlayerStandsIn() {
        ReportContext.Position pos = new ReportContext.Position(-0.5, -60.1, -35.25, 0, 0);

        assertEquals("-1 -61 -36", ReportFormatter.blockCoordinates(pos));
    }

    @Test
    void testDescriptionNamesTheServer() {
        String text = ReportFormatter.testDescription("survival", "Paper", "1.21.11", "0.1.0", TestContexts.REPORTED_AT);

        assertTrue(text.startsWith("Test report sent with /bugreport test"));
        assertTrue(text.contains("- Server: survival (Paper 1.21.11)"));
    }
}
