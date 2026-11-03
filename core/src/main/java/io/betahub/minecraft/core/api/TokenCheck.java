package io.betahub.minecraft.core.api;

/** Outcome of verifying the auth token without creating anything. */
public sealed interface TokenCheck {

    /**
     * @param projectId         project the token belongs to ({@code pr-...})
     * @param bugReportsPerDay  daily bug report limit per IP address
     */
    record Valid(String tokenName, String projectId, boolean canCreateBugReport, boolean canCreateRelease,
                 int bugReportsPerDay) implements TokenCheck {
    }

    record Invalid(String error) implements TokenCheck {
    }

    record Failed(String reason, Throwable cause) implements TokenCheck {
    }
}
