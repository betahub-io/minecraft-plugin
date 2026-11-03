package io.betahub.minecraft.core;

/** What the player typed into the dialog, normalized (trimmed, {@code \n} line endings). */
public record ReportDraft(String description, String steps) {

    public ReportDraft {
        description = normalize(description);
        steps = normalize(steps);
    }

    public boolean hasSteps() {
        return !steps.isEmpty();
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\r\n", "\n").replace('\r', '\n').strip();
    }
}
