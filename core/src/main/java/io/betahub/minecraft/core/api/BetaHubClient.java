package io.betahub.minecraft.core.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.betahub.minecraft.core.config.ReporterConfig;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * BetaHub REST client for project auth tokens ({@code tkn-...}).
 * Sends JSON ({@code {"issue": {...}}}); BetaHub accepts it on the {@code .json} endpoint like form fields.
 */
public final class BetaHubClient implements IssueSubmitter {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    static final String ISSUE_AUTH_SCHEME = "FormUser ";
    static final String VERIFY_AUTH_SCHEME = "Bearer ";
    static final String PROJECT_HEADER = "BetaHub-Project-ID";
    static final String JSON_CONTENT_TYPE = "application/json";

    static final int HTTP_OK = 200;
    static final int HTTP_CREATED = 201;
    static final int HTTP_UNAUTHORIZED = 401;
    static final int HTTP_FORBIDDEN = 403;
    static final int HTTP_TOO_MANY_REQUESTS = 429;
    /**
     * Statuses that mean the request was not processed, so a retry cannot create a duplicate.
     * 500, 502 and 504 are not retried: a proxy also sends them when the app fails mid-request,
     * after BetaHub may already have created the issue and used up quota.
     */
    static final Set<Integer> RETRYABLE_STATUSES = Set.of(503);
    static final int MAX_ATTEMPTS = 2;

    /** Exact error text BetaHub returns for an exhausted daily limit (also for a token without the permission). */
    static final String DAILY_LIMIT_ERROR = "Not allowed to report a bug.";
    static final String PROJECT_NOT_ACCEPTING_ERROR = "not currently accepting new bug reports";
    static final String RELEASE_PERMISSION_ERROR = "permission to create releases";

    private static final int MAX_ERROR_SNIPPET = 300;
    /** Issue links end up inside chat click events; anything outside plain URL characters is refused. */
    private static final Pattern SAFE_URL = Pattern.compile("https?://[A-Za-z0-9._~:/?#\\[\\]@!$&()*+,;=%-]+");

    /** @param retryDelay pause before the single retry */
    public record Options(Duration retryDelay, String userAgent) {
    }

    private final ReporterConfig.BetaHub settings;
    private final HttpClient http;
    private final Options options;

    public BetaHubClient(ReporterConfig.BetaHub settings, HttpClient http, Options options) {
        this.settings = Objects.requireNonNull(settings);
        this.http = Objects.requireNonNull(http);
        this.options = Objects.requireNonNull(options);
    }

    public static HttpClient newHttpClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public IssueResult createIssue(NewIssue issue) {
        IssueResult result = postIssue(issue);
        if (issue.releaseLabel() != null && isReleasePermissionError(result)) {
            // Public projects refuse unknown release labels unless the token may create releases.
            // BetaHub checks this before counting the report, so retrying costs no quota.
            IssueResult retried = postIssue(issue.withoutReleaseLabel());
            if (retried instanceof IssueResult.Created created) {
                return new IssueResult.Created(created.id(), created.scopedId(), created.url(), true, created.warnings());
            }
            return retried;
        }
        return result;
    }

    @Override
    public TokenCheck verifyToken() {
        HttpRequest request = requestBuilder("/auth/verify")
                .header("Authorization", VERIFY_AUTH_SCHEME + settings.token())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response;
        try {
            response = send(request);
        } catch (IOException e) {
            return new TokenCheck.Failed(describe(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TokenCheck.Failed("interrupted", e);
        }
        if (response.statusCode() == HTTP_UNAUTHORIZED) {
            return new TokenCheck.Invalid(errorMessage(response.body()));
        }
        if (response.statusCode() != HTTP_OK) {
            return new TokenCheck.Failed("unexpected HTTP " + response.statusCode() + ": " + errorMessage(response.body()), null);
        }
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject project = json.getAsJsonObject("project");
            JsonObject permissions = json.getAsJsonObject("permissions");
            JsonObject limits = json.getAsJsonObject("rate_limits");
            return new TokenCheck.Valid(
                    stringOrNull(json, "token_name"),
                    project.get("slug").getAsString(),
                    permissions.get("can_create_bug_report").getAsBoolean(),
                    permissions.get("can_create_release").getAsBoolean(),
                    limits.get("bug_reports_per_day").getAsInt());
        } catch (RuntimeException e) {
            return new TokenCheck.Failed("unexpected response from /auth/verify", e);
        }
    }

    private IssueResult postIssue(NewIssue issue) {
        HttpRequest request = requestBuilder("/projects/" + settings.projectId() + "/issues.json")
                .header("Authorization", ISSUE_AUTH_SCHEME + settings.token())
                .header(PROJECT_HEADER, settings.projectId())
                .header("Content-Type", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(issueJson(issue), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = send(request);
        } catch (IOException e) {
            return new IssueResult.Failed(describe(e), e, !isConnectFailure(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new IssueResult.Failed("interrupted", e, true);
        }
        return interpret(response);
    }

    static String issueJson(NewIssue issue) {
        JsonObject fields = new JsonObject();
        fields.addProperty("description", issue.description());
        if (issue.steps() != null) {
            fields.addProperty("unformatted_steps_to_reproduce", issue.steps());
        }
        if (issue.releaseLabel() != null) {
            fields.addProperty("release_label", issue.releaseLabel());
        }
        if (!issue.custom().isEmpty()) {
            JsonObject custom = new JsonObject();
            issue.custom().forEach(custom::addProperty);
            fields.add("custom", custom);
        }
        if (issue.logs() != null) {
            fields.addProperty("logs", issue.logs());
        }
        JsonObject root = new JsonObject();
        root.add("issue", fields);
        return root.toString();
    }

    private IssueResult interpret(HttpResponse<String> response) {
        int status = response.statusCode();
        String body = response.body();
        if (status == HTTP_CREATED || status == HTTP_OK) {
            return parseCreated(body);
        }
        String error = errorMessage(body);
        if (status == HTTP_TOO_MANY_REQUESTS) {
            return new IssueResult.Unavailable(IssueResult.Reason.TOO_MANY_REQUESTS, error);
        }
        if (status == HTTP_FORBIDDEN && error.equals(DAILY_LIMIT_ERROR)) {
            return new IssueResult.Unavailable(IssueResult.Reason.DAILY_LIMIT_OR_NO_PERMISSION, error);
        }
        if (status == HTTP_FORBIDDEN && error.contains(PROJECT_NOT_ACCEPTING_ERROR)) {
            return new IssueResult.Unavailable(IssueResult.Reason.PROJECT_NOT_ACCEPTING, error);
        }
        if (status >= 400 && status < 500) {
            return new IssueResult.Rejected(status, error);
        }
        return new IssueResult.Failed("HTTP " + status + ": " + error, null, !RETRYABLE_STATUSES.contains(status));
    }

    private static IssueResult parseCreated(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            long id = json.get("id").getAsLong();
            String scopedId = stringOrNull(json, "scoped_id");
            String rawUrl = json.get("url").getAsString();
            if (!SAFE_URL.matcher(rawUrl).matches()) {
                return new IssueResult.Failed("issue " + id + " was created, but its url is not a plain http(s) link: "
                        + rawUrl, null, true);
            }
            return new IssueResult.Created(id, scopedId, new URI(rawUrl), false, warnings(json.get("warnings")));
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException
                 | URISyntaxException e) {
            return new IssueResult.Failed("issue may have been created, but the response could not be read", e, true);
        }
    }

    private static List<String> warnings(JsonElement element) {
        List<String> result = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            array.forEach(item -> result.add(item.isJsonPrimitive() ? item.getAsString() : item.toString()));
        } else {
            result.add(element.isJsonPrimitive() ? element.getAsString() : element.toString());
        }
        return result;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        for (int attempt = 1; ; attempt++) {
            boolean lastAttempt = attempt >= MAX_ATTEMPTS;
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (lastAttempt || !RETRYABLE_STATUSES.contains(response.statusCode())) {
                    return response;
                }
            } catch (ConnectException | HttpConnectTimeoutException e) {
                // Nothing was sent, so retrying cannot create a duplicate issue.
                if (lastAttempt) {
                    throw e;
                }
            }
            Thread.sleep(options.retryDelay().toMillis());
        }
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder(URI.create(settings.baseUrl() + path))
                .timeout(settings.requestTimeout())
                .header("Accept", JSON_CONTENT_TYPE)
                .header("User-Agent", options.userAgent());
    }

    private static boolean isReleasePermissionError(IssueResult result) {
        return result instanceof IssueResult.Rejected rejected
                && rejected.status() == HTTP_FORBIDDEN
                && rejected.error().contains(RELEASE_PERMISSION_ERROR);
    }

    /** The {@code error} field of a BetaHub error body, or a trimmed snippet of whatever came back. */
    static String errorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "(empty response)";
        }
        try {
            JsonElement json = JsonParser.parseString(body);
            if (json.isJsonObject() && json.getAsJsonObject().has("error")) {
                JsonElement error = json.getAsJsonObject().get("error");
                return (error.isJsonPrimitive() ? error.getAsString() : error.toString()).strip();
            }
        } catch (JsonParseException e) {
            // not JSON, e.g. an HTML error page from a proxy
        }
        String flat = body.strip().replaceAll("\\s+", " ");
        return flat.length() > MAX_ERROR_SNIPPET ? flat.substring(0, MAX_ERROR_SNIPPET) + "..." : flat;
    }

    /** Failures where nothing was sent: BetaHub cannot have created anything. */
    private static boolean isConnectFailure(IOException e) {
        return e instanceof ConnectException || e instanceof HttpConnectTimeoutException;
    }

    private static String describe(IOException e) {
        if (e instanceof HttpConnectTimeoutException) {
            return "could not connect to BetaHub (connect timeout)";
        }
        if (e instanceof HttpTimeoutException) {
            return "BetaHub did not answer in time (the issue may still have been created)";
        }
        if (e instanceof ConnectException) {
            return "could not connect to BetaHub";
        }
        return "network error: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }

    private static String stringOrNull(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }
}
