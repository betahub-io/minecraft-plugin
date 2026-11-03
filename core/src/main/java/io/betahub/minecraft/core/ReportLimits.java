package io.betahub.minecraft.core;

/** Length limits for player input, in characters. */
public record ReportLimits(int descriptionMinLength, int descriptionMaxLength, int stepsMaxLength) {

    public ReportLimits {
        if (descriptionMinLength < 1) {
            throw new IllegalArgumentException("descriptionMinLength must be at least 1");
        }
        if (descriptionMaxLength < descriptionMinLength) {
            throw new IllegalArgumentException("descriptionMaxLength must be >= descriptionMinLength");
        }
        if (stepsMaxLength < 1) {
            throw new IllegalArgumentException("stepsMaxLength must be at least 1");
        }
    }
}
