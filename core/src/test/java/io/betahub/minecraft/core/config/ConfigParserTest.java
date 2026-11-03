package io.betahub.minecraft.core.config;

import io.betahub.minecraft.core.MetadataField;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigParserTest {

    private static final String TOKEN = "tkn-0123456789abcdef";

    private final Map<String, Object> values = validValues();
    private final Map<String, Object> customFields = new LinkedHashMap<>();

    private final ConfigSource source = new ConfigSource() {
        @Override
        public Object get(String path) {
            return values.get(path);
        }

        @Override
        public Map<String, Object> section(String path) {
            return path.equals("custom-fields") ? customFields : Map.of();
        }

        @Override
        public boolean isSection(String path) {
            return values.get(path) == null;
        }
    };

    @Test
    void parsesValidConfig() {
        customFields.put("server_name", "server");
        customFields.put("location", "location");

        ConfigParser.Result result = ConfigParser.parse(source);

        assertTrue(result.isValid(), () -> result.errors().toString());
        ReporterConfig config = result.config();
        assertEquals(URI.create("https://app.betahub.io"), config.betaHub().baseUrl());
        assertEquals("pr-0690627851", config.betaHub().projectId());
        assertEquals(TOKEN, config.betaHub().token());
        assertNull(config.betaHub().releaseLabel());
        assertEquals(Duration.ofSeconds(60), config.betaHub().requestTimeout());
        assertEquals("survival", config.serverName());
        assertEquals(10, config.limits().descriptionMinLength());
        assertEquals(1000, config.limits().descriptionMaxLength());
        assertEquals(Duration.ofSeconds(120), config.cooldown());
        assertEquals(new ReporterConfig.ServerLog(false, 200), config.serverLog());
        assertEquals(new ReporterConfig.MetadataToggles(true, true, true, true), config.metadata());
        assertEquals(Map.of(MetadataField.SERVER_NAME, "server", MetadataField.LOCATION, "location"), config.customFields());
        assertFalse(config.reportAlias());
    }

    @Test
    void shippedPlaceholdersCountAsMissing() {
        values.put("betahub.project-id", "pr-XXXXXXXXXX");
        values.put("betahub.token", "tkn-XXXX");

        ConfigParser.Result result = ConfigParser.parse(source);

        assertNull(result.config());
        assertEquals(java.util.List.of("betahub.project-id is not set", "betahub.token is not set"), result.errors());
    }

    @Test
    void wrongTokenTypeIsRejectedWithoutEchoingIt() {
        values.put("betahub.token", "pat-personal-secret");

        ConfigParser.Result result = ConfigParser.parse(source);

        assertEquals(java.util.List.of("betahub.token must start with 'tkn-'"), result.errors());
        assertFalse(result.errors().toString().contains("personal-secret"));
    }

    @Test
    void tokenWithUnexpectedCharactersIsRejected() {
        values.put("betahub.token", "tkn-abc def");
        values.put("betahub.project-id", "pr-1/../x");

        assertEquals(java.util.List.of(
                "betahub.project-id may only contain letters and digits after 'pr-'",
                "betahub.token may only contain letters and digits after 'tkn-'"), ConfigParser.parse(source).errors());
    }

    @Test
    void trailingSlashIsRemovedFromBaseUrl() {
        values.put("betahub.base-url", "http://localhost:8080/");

        assertEquals(URI.create("http://localhost:8080"), ConfigParser.parse(source).config().betaHub().baseUrl());
    }

    @Test
    void rejectsNonHttpBaseUrl() {
        values.put("betahub.base-url", "ftp://example.com");

        assertEquals(java.util.List.of("betahub.base-url must be an http(s) URL, got 'ftp://example.com'"),
                ConfigParser.parse(source).errors());
    }

    @Test
    void reportsEveryProblemAtOnce() {
        values.put("server-name", " ");
        values.put("report.cooldown-seconds", -5);
        values.put("report.description-min-length", 50);
        values.put("report.description-max-length", 20);
        values.put("metadata.biome", "yes");
        customFields.put("favourite_color", "color");
        customFields.put("biome", "");

        ConfigParser.Result result = ConfigParser.parse(source);

        assertEquals(6, result.errors().size(), result.errors()::toString);
        assertTrue(result.errors().contains("server-name is not set"));
        assertTrue(result.errors().contains("report.cooldown-seconds must be between 0 and 2147483647, got -5"));
        assertTrue(result.errors().contains(
                "report.description-min-length (50) must not be greater than report.description-max-length (20)"));
        assertTrue(result.errors().contains("metadata.biome must be true or false"));
        assertTrue(result.errors().get(4).startsWith("custom-fields: unknown metadata key 'favourite_color'. Valid keys: player_name"));
        assertTrue(result.errors().contains("custom-fields.biome must be the identifier of a BetaHub custom field"));
    }

    @Test
    void sectionsWrittenAsOtherTypesAreErrors() {
        values.put("custom-fields", java.util.List.of("server_name"));
        values.put("messages", "hello");

        assertEquals(java.util.List.of("custom-fields must be a mapping of metadata key to custom field identifier",
                "messages must be a section with one entry per message"), ConfigParser.parse(source).errors());
    }

    @Test
    void explicitReleaseLabelIsKept() {
        values.put("betahub.release-label", " season-3 ");

        assertEquals("season-3", ConfigParser.parse(source).config().betaHub().releaseLabel());
    }

    @Test
    void betaHubSettingsNeverPrintTheToken() {
        ReporterConfig config = ConfigParser.parse(source).config();

        assertFalse(config.toString().contains(TOKEN));
    }

    private static Map<String, Object> validValues() {
        Map<String, Object> map = new HashMap<>();
        map.put("betahub.base-url", "https://app.betahub.io");
        map.put("betahub.project-id", "pr-0690627851");
        map.put("betahub.token", TOKEN);
        map.put("betahub.release-label", "");
        map.put("betahub.request-timeout-seconds", 60);
        map.put("server-name", "survival");
        map.put("report.description-max-length", 1000);
        map.put("report.steps-max-length", 1000);
        map.put("report.description-min-length", 10);
        map.put("report.cooldown-seconds", 120);
        map.put("report.attach-server-log", false);
        map.put("report.server-log-lines", 200);
        map.put("metadata.coordinates", true);
        map.put("metadata.biome", true);
        map.put("metadata.client-version", true);
        map.put("metadata.gamemode-health-ping", true);
        map.put("commands.report-alias", false);
        return map;
    }
}
