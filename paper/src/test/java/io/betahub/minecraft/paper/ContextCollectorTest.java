package io.betahub.minecraft.paper;

import io.betahub.minecraft.core.ReportContext;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContextCollectorTest {

    private static final Instant NOW = Instant.parse("2026-11-03T18:30:00Z");

    private ServerMock server;
    private BrandedPlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = new BrandedPlayerMock(server, "Notch", "fabric");
        server.addPlayer(player);
        player.setGameMode(GameMode.CREATIVE);
        player.setLocation(new Location(player.getWorld(), -3.5, 72, 8.25, 45f, -10f));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void collectsEverythingWhenEnabled() {
        ReportContext ctx = collector(uuid -> Optional.of("1.21.4")).collect(player, Fixtures.config(Fixtures.shippedConfig(Map.of())));

        assertEquals("Notch", ctx.playerName());
        assertEquals("survival", ctx.serverName());
        assertEquals(player.getWorld().getName(), ctx.worldName());
        assertEquals(player.getWorld().getKey().asString(), ctx.dimensionKey());
        assertEquals(new ReportContext.Position(-3.5, 72, 8.25, 45f, -10f), ctx.position());
        assertNotNull(ctx.biome());
        assertEquals(new ReportContext.ClientInfo("1.21.4", "fabric"), ctx.client());
        assertEquals("CREATIVE", ctx.playerState().gameMode());
        assertEquals(server.getMinecraftVersion(), ctx.minecraftVersion());
        assertEquals(NOW, ctx.reportedAt());
        assertEquals("0.1.0", ctx.pluginVersion());
    }

    @Test
    void disabledMetadataIsNotCollected() {
        ReportContext ctx = collector(uuid -> Optional.of("1.21.4")).collect(player, Fixtures.config(Fixtures.shippedConfig(Map.of(
                "metadata.coordinates", false,
                "metadata.biome", false,
                "metadata.client-version", false,
                "metadata.gamemode-health-ping", false))));

        assertNull(ctx.position());
        assertNull(ctx.biome());
        assertNull(ctx.client());
        assertNull(ctx.playerState());
    }

    @Test
    void withoutViaVersionOnlyBrandIsKnown() {
        ReportContext ctx = collector(ClientVersionLookup.NONE).collect(player, Fixtures.config(Fixtures.shippedConfig(Map.of())));

        assertEquals(new ReportContext.ClientInfo(null, "fabric"), ctx.client());
    }

    private ContextCollector collector(ClientVersionLookup lookup) {
        return new ContextCollector(server, lookup, "0.1.0", Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
