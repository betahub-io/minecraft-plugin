package io.betahub.minecraft.core.api;

import java.net.URI;
import java.util.List;

/** Outcome of creating an issue. */
public sealed interface IssueResult {

    /**
     * @param scopedId            per-project issue number; {@code null} when BetaHub hides the issue (e.g. a duplicate)
     * @param releaseLabelIgnored the configured release label was refused, so the issue went to the latest release
     * @param warnings            non-fatal notes from BetaHub, e.g. a dropped custom field value
     */
    record Created(long id, String scopedId, URI url, boolean releaseLabelIgnored, List<String> warnings)
            implements IssueResult {

        public Created {
            warnings = List.copyOf(warnings);
        }

        /** Reference for logs: {@code #12}, or {@code g-345} for hidden issues. */
        public String reference() {
            return scopedId != null ? "#" + scopedId : "g-" + id;
        }
    }

    /** BetaHub refused for now; retrying later may work. */
    record Unavailable(Reason reason, String error) implements IssueResult {
    }

    /** BetaHub refused the request itself (bad token, wrong project, validation). Retrying will not help. */
    record Rejected(int status, String error) implements IssueResult {
    }

    /**
     * No usable answer from BetaHub: network problem, timeout or server error.
     *
     * @param mayHaveBeenCreated the request may have reached BetaHub, so the issue (and a used report) may exist;
     *                           asking the player to resend could create a duplicate
     */
    record Failed(String reason, Throwable cause, boolean mayHaveBeenCreated) implements IssueResult {
    }

    enum Reason {
        /** 403 "Not allowed to report a bug.": the token's daily per-IP limit is used up, or it may not create bug reports. */
        DAILY_LIMIT_OR_NO_PERMISSION,
        /** The organization's plan limit for new reports is reached. */
        PROJECT_NOT_ACCEPTING,
        /** HTTP 429 from BetaHub's request throttling. */
        TOO_MANY_REQUESTS
    }
}
