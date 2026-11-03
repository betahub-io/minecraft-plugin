package io.betahub.minecraft.core;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Metadata values that can be mapped to BetaHub custom fields ({@code custom-fields} in config.yml).
 * The {@link #key()} is the stable name admins use in config.
 */
public enum MetadataField {
    PLAYER_NAME("player_name", ReportContext::playerName),
    PLAYER_UUID("player_uuid", ctx -> ctx.playerUuid().toString()),
    SERVER_NAME("server_name", ReportContext::serverName),
    WORLD("world", ReportContext::worldName),
    DIMENSION("dimension", ReportContext::dimensionKey),
    COORDINATES("coordinates", ctx -> ctx.position() == null ? null : ReportFormatter.blockCoordinates(ctx.position())),
    LOCATION("location", ctx -> ctx.position() == null ? null
            : ctx.worldName() + " " + ReportFormatter.blockCoordinates(ctx.position())),
    TP_COMMAND("tp_command", ctx -> ctx.position() == null ? null : ReportFormatter.teleportCommand(ctx)),
    BIOME("biome", ReportContext::biome),
    MINECRAFT_VERSION("minecraft_version", ReportContext::minecraftVersion),
    SERVER_VERSION("server_version", ReportContext::serverVersion),
    CLIENT_VERSION("client_version", ctx -> ctx.client() == null ? null : ctx.client().version()),
    CLIENT_BRAND("client_brand", ctx -> ctx.client() == null ? null : ctx.client().brand()),
    GAME_MODE("game_mode", ctx -> ctx.playerState() == null ? null : ctx.playerState().gameMode()),
    PING("ping", ctx -> ctx.playerState() == null ? null : Integer.toString(ctx.playerState().pingMillis())),
    REPORTED_AT("reported_at", ctx -> ctx.reportedAt().toString()),
    PLUGIN_VERSION("plugin_version", ReportContext::pluginVersion);

    private final String key;
    private final Function<ReportContext, String> extractor;

    MetadataField(String key, Function<ReportContext, String> extractor) {
        this.key = key;
        this.extractor = extractor;
    }

    public String key() {
        return key;
    }

    /** Value for this field, empty when the data was not collected. */
    public Optional<String> valueOf(ReportContext context) {
        return Optional.ofNullable(extractor.apply(context));
    }

    public static Optional<MetadataField> fromKey(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(field -> field.key.equals(normalized)).findFirst();
    }

    public static String allKeys() {
        return Arrays.stream(values()).map(MetadataField::key).collect(Collectors.joining(", "));
    }
}
