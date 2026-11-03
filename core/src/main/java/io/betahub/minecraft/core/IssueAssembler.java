package io.betahub.minecraft.core;

import io.betahub.minecraft.core.api.NewIssue;
import io.betahub.minecraft.core.config.ReporterConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/** Combines the player's draft, the game context and config into the request sent to BetaHub. */
public final class IssueAssembler {

    private IssueAssembler() {
    }

    /**
     * @param redactedLogs server log excerpt already passed through {@link LogRedactor}, or {@code null}
     */
    // TODO(open question): reporter identity. BetaHub links reporters by email or Discord ID only; the player's
    //  name and UUID go into the description until a dedicated field or mapping is decided.
    public static NewIssue assemble(ReporterConfig config, ReportDraft draft, ReportContext context, String redactedLogs) {
        Map<String, String> custom = new LinkedHashMap<>();
        config.customFields().forEach((field, ident) -> field.valueOf(context).ifPresent(value -> custom.put(ident, value)));
        return new NewIssue(
                ReportFormatter.description(draft, context),
                draft.hasSteps() ? draft.steps() : null,
                releaseLabel(config, context.minecraftVersion()),
                custom,
                redactedLogs);
    }

    /** Configured release label, or the Minecraft version when none is set. */
    public static String releaseLabel(ReporterConfig config, String minecraftVersion) {
        String configured = config.betaHub().releaseLabel();
        return configured != null ? configured : minecraftVersion;
    }
}
