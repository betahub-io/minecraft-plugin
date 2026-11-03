package io.betahub.minecraft.paper;

import io.betahub.minecraft.core.ReportContext;
import io.betahub.minecraft.core.config.ReporterConfig;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.time.Clock;

/** Snapshots a player's situation into a {@link ReportContext}. Main thread only. */
public final class ContextCollector {

    private final Server server;
    private final ClientVersionLookup clientVersions;
    private final String pluginVersion;
    private final Clock clock;

    public ContextCollector(Server server, ClientVersionLookup clientVersions, String pluginVersion, Clock clock) {
        this.server = server;
        this.clientVersions = clientVersions;
        this.pluginVersion = pluginVersion;
        this.clock = clock;
    }

    public ReportContext collect(Player player, ReporterConfig config) {
        ReporterConfig.MetadataToggles toggles = config.metadata();
        Location location = player.getLocation();
        World world = location.getWorld();

        ReportContext.Position position = toggles.coordinates()
                ? new ReportContext.Position(location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch())
                : null;
        String biome = toggles.biome() ? biomeKey(world, location) : null;
        ReportContext.PlayerState state = toggles.gameModeHealthPing()
                ? new ReportContext.PlayerState(player.getGameMode().name(), player.getHealth(), player.getPing())
                : null;

        return new ReportContext(
                player.getName(),
                player.getUniqueId(),
                config.serverName(),
                world.getName(),
                world.getEnvironment().name(),
                world.getKey().asString(),
                position,
                biome,
                server.getName(),
                server.getVersion(),
                server.getMinecraftVersion(),
                toggles.clientVersion() ? client(player) : null,
                state,
                clock.instant(),
                pluginVersion);
    }

    private ReportContext.ClientInfo client(Player player) {
        String version = clientVersions.clientVersion(player.getUniqueId()).orElse(null);
        String brand = player.getClientBrandName();
        if (version == null && brand == null) {
            return null;
        }
        return new ReportContext.ClientInfo(version, brand);
    }

    private static String biomeKey(World world, Location location) {
        Biome biome = world.getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        NamespacedKey key = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).getKey(biome);
        return key == null ? null : key.asString();
    }
}
