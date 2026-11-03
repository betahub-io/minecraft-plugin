package io.betahub.minecraft.core.config;

import io.betahub.minecraft.core.MetadataField;
import io.betahub.minecraft.core.ReportLimits;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Turns raw config values into a {@link ReporterConfig}, collecting every problem instead of stopping at the first. */
public final class ConfigParser {

    public static final String PROJECT_ID_PREFIX = "pr-";
    public static final String TOKEN_PREFIX = "tkn-";
    /** Same shape BetaHub accepts; anything else is rejected there anyway. */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("tkn-[0-9A-Za-z]+");
    /** Also keeps the value safe to put into a URL path. */
    private static final Pattern PROJECT_ID_PATTERN = Pattern.compile("pr-[0-9A-Za-z]+");
    /** The shipped config.yml uses values like {@code tkn-XXXX}; they mean "not configured yet". */
    static final String PLACEHOLDER_MARKER = "XXXX";

    static final int MAX_TEXT_LENGTH = 10_000;
    static final int MAX_SERVER_LOG_LINES = 5_000;
    static final int MIN_REQUEST_TIMEOUT_SECONDS = 5;
    static final int MAX_REQUEST_TIMEOUT_SECONDS = 300;

    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    /** Either a config or the list of problems that prevented building one. */
    public record Result(ReporterConfig config, List<String> errors) {

        public Result {
            errors = List.copyOf(errors);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    private final ConfigSource source;
    private final List<String> errors = new ArrayList<>();

    private ConfigParser(ConfigSource source) {
        this.source = source;
    }

    public static Result parse(ConfigSource source) {
        return new ConfigParser(source).parse();
    }

    private Result parse() {
        URI baseUrl = baseUrl("betahub.base-url");
        String projectId = credential("betahub.project-id", PROJECT_ID_PREFIX, PROJECT_ID_PATTERN);
        String token = credential("betahub.token", TOKEN_PREFIX, TOKEN_PATTERN);
        String releaseLabel = optionalString("betahub.release-label");
        int requestTimeoutSeconds = integer("betahub.request-timeout-seconds",
                MIN_REQUEST_TIMEOUT_SECONDS, MAX_REQUEST_TIMEOUT_SECONDS);
        String serverName = requiredString("server-name");

        int descriptionMin = integer("report.description-min-length", 1, MAX_TEXT_LENGTH);
        int descriptionMax = integer("report.description-max-length", 1, MAX_TEXT_LENGTH);
        int stepsMax = integer("report.steps-max-length", 1, MAX_TEXT_LENGTH);
        if (descriptionMin > descriptionMax) {
            errors.add("report.description-min-length (" + descriptionMin
                    + ") must not be greater than report.description-max-length (" + descriptionMax + ")");
        }
        int cooldownSeconds = integer("report.cooldown-seconds", 0, Integer.MAX_VALUE);
        boolean attachLog = bool("report.attach-server-log");
        int logLines = integer("report.server-log-lines", 1, MAX_SERVER_LOG_LINES);

        ReporterConfig.MetadataToggles toggles = new ReporterConfig.MetadataToggles(
                bool("metadata.coordinates"),
                bool("metadata.biome"),
                bool("metadata.client-version"),
                bool("metadata.gamemode-health-ping"));
        Map<MetadataField, String> customFields = customFields();
        boolean reportAlias = bool("commands.report-alias");
        if (!source.isSection("messages")) {
            errors.add("messages must be a section with one entry per message");
        }

        if (!errors.isEmpty()) {
            return new Result(null, errors);
        }
        ReporterConfig config = new ReporterConfig(
                new ReporterConfig.BetaHub(baseUrl, projectId, token, releaseLabel,
                        Duration.ofSeconds(requestTimeoutSeconds)),
                serverName,
                new ReportLimits(descriptionMin, descriptionMax, stepsMax),
                Duration.ofSeconds(cooldownSeconds),
                new ReporterConfig.ServerLog(attachLog, logLines),
                toggles,
                customFields,
                reportAlias);
        return new Result(config, errors);
    }

    private URI baseUrl(String path) {
        String raw = requiredString(path);
        if (raw == null) {
            return null;
        }
        String trimmed = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals(HTTPS) || scheme.equals(HTTP)) || uri.getHost() == null) {
                errors.add(path + " must be an http(s) URL, got '" + raw + "'");
                return null;
            }
            return uri;
        } catch (URISyntaxException e) {
            errors.add(path + " is not a valid URL: '" + raw + "'");
            return null;
        }
    }

    /** Never echoes the value back: a wrong token pasted here is still a secret. */
    private String credential(String path, String prefix, Pattern pattern) {
        String value = optionalString(path);
        if (value == null || value.contains(PLACEHOLDER_MARKER)) {
            errors.add(path + " is not set");
            return null;
        }
        if (!value.startsWith(prefix)) {
            errors.add(path + " must start with '" + prefix + "'");
            return null;
        }
        if (!pattern.matcher(value).matches()) {
            errors.add(path + " may only contain letters and digits after '" + prefix + "'");
            return null;
        }
        return value;
    }

    private String requiredString(String path) {
        String value = optionalString(path);
        if (value == null) {
            errors.add(path + " is not set");
        }
        return value;
    }

    /** Trimmed string, or {@code null} when absent or blank. */
    private String optionalString(String path) {
        Object raw = source.get(path);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String) && !(raw instanceof Number)) {
            errors.add(path + " must be text");
            return null;
        }
        String value = raw.toString().strip();
        return value.isEmpty() ? null : value;
    }

    private int integer(String path, int min, int max) {
        Object raw = source.get(path);
        if (!(raw instanceof Integer value)) {
            errors.add(path + (raw == null ? " is not set" : " must be a whole number"));
            return min;
        }
        if (value < min || value > max) {
            errors.add(path + " must be between " + min + " and " + max + ", got " + value);
            return min;
        }
        return value;
    }

    private boolean bool(String path) {
        Object raw = source.get(path);
        if (!(raw instanceof Boolean value)) {
            errors.add(path + (raw == null ? " is not set" : " must be true or false"));
            return false;
        }
        return value;
    }

    private Map<MetadataField, String> customFields() {
        Map<MetadataField, String> result = new EnumMap<>(MetadataField.class);
        if (!source.isSection("custom-fields")) {
            errors.add("custom-fields must be a mapping of metadata key to custom field identifier");
            return result;
        }
        for (Map.Entry<String, Object> entry : source.section("custom-fields").entrySet()) {
            Optional<MetadataField> field = MetadataField.fromKey(entry.getKey());
            if (field.isEmpty()) {
                errors.add("custom-fields: unknown metadata key '" + entry.getKey()
                        + "'. Valid keys: " + MetadataField.allKeys());
                continue;
            }
            if (!(entry.getValue() instanceof String ident) || ident.isBlank()) {
                errors.add("custom-fields." + entry.getKey() + " must be the identifier of a BetaHub custom field");
                continue;
            }
            result.put(field.get(), ident.strip());
        }
        return result;
    }
}
