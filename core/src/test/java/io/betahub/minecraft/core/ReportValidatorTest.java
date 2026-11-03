package io.betahub.minecraft.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportValidatorTest {

    private final ReportValidator validator = new ReportValidator(new ReportLimits(10, 30, 20));

    @Test
    void acceptsDescriptionWithinLimits() {
        assertEquals(Optional.empty(), validator.validate(new ReportDraft("Chest is empty", "")));
    }

    @Test
    void blankDescriptionIsTooShort() {
        assertEquals(Optional.of(ReportValidator.Error.DESCRIPTION_TOO_SHORT), validator.validate(new ReportDraft("   \n ", "")));
    }

    @Test
    void lengthIsCountedAfterTrimming() {
        assertEquals(Optional.of(ReportValidator.Error.DESCRIPTION_TOO_SHORT),
                validator.validate(new ReportDraft("     short    ", "")));
    }

    @Test
    void rejectsTooLongDescriptionAndSteps() {
        assertEquals(Optional.of(ReportValidator.Error.DESCRIPTION_TOO_LONG),
                validator.validate(new ReportDraft("x".repeat(31), "")));
        assertEquals(Optional.of(ReportValidator.Error.STEPS_TOO_LONG),
                validator.validate(new ReportDraft("Chest is empty", "y".repeat(21))));
    }

    @Test
    void draftNormalizesLineEndingsAndNulls() {
        ReportDraft draft = new ReportDraft(" a\r\nb\rc ", null);

        assertEquals("a\nb\nc", draft.description());
        assertEquals("", draft.steps());
        assertEquals(false, draft.hasSteps());
    }
}
