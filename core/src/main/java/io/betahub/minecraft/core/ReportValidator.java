package io.betahub.minecraft.core;

import java.util.Optional;

/** Checks a draft before it is sent. The dialog enforces max lengths too, but a modified client may not. */
public final class ReportValidator {

    public enum Error {
        DESCRIPTION_TOO_SHORT,
        DESCRIPTION_TOO_LONG,
        STEPS_TOO_LONG
    }

    private final ReportLimits limits;

    public ReportValidator(ReportLimits limits) {
        this.limits = limits;
    }

    /** First problem found, or empty when the draft can be sent. */
    public Optional<Error> validate(ReportDraft draft) {
        int descriptionLength = draft.description().length();
        if (descriptionLength < limits.descriptionMinLength()) {
            return Optional.of(Error.DESCRIPTION_TOO_SHORT);
        }
        if (descriptionLength > limits.descriptionMaxLength()) {
            return Optional.of(Error.DESCRIPTION_TOO_LONG);
        }
        if (draft.steps().length() > limits.stepsMaxLength()) {
            return Optional.of(Error.STEPS_TOO_LONG);
        }
        return Optional.empty();
    }

    public ReportLimits limits() {
        return limits;
    }
}
