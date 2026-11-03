package io.betahub.minecraft.core.api;

import java.util.Map;
import java.util.Objects;

/**
 * Bug report to create.
 *
 * @param steps        player's steps to reproduce, or {@code null}
 * @param releaseLabel release to file the issue under, or {@code null} for the project's latest release
 * @param custom       custom field identifier to value
 * @param logs         server log excerpt, or {@code null}
 */
public record NewIssue(String description, String steps, String releaseLabel, Map<String, String> custom, String logs) {

    public NewIssue {
        Objects.requireNonNull(description, "description");
        custom = Map.copyOf(custom);
    }

    NewIssue withoutReleaseLabel() {
        return new NewIssue(description, steps, null, custom, logs);
    }
}
