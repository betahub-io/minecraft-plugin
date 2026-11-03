package io.betahub.minecraft.paper;

import io.betahub.minecraft.core.api.IssueSubmitter;
import io.betahub.minecraft.core.config.ReporterConfig;

import java.util.List;

/**
 * What the plugin currently runs with. Replaced as a whole on reload, so a report in flight keeps
 * the settings it started with.
 *
 * @param config      {@code null} when config.yml has errors
 * @param submitter   {@code null} when config.yml has errors
 * @param configErrors problems found in config.yml; empty when valid
 */
record PluginState(Messages messages, ReporterConfig config, IssueSubmitter submitter, List<String> configErrors) {

    PluginState {
        configErrors = List.copyOf(configErrors);
        if ((config == null) != (submitter == null) || (config == null) == configErrors.isEmpty()) {
            throw new IllegalArgumentException("either a config and submitter, or config errors");
        }
    }

    boolean configured() {
        return config != null;
    }
}
