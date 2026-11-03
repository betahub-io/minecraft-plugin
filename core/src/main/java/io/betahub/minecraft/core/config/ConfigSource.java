package io.betahub.minecraft.core.config;

import java.util.Map;

/** Read-only view of the plugin config, so parsing can be tested without Bukkit. */
public interface ConfigSource {

    /** Value at a dotted path ({@code betahub.token}), or {@code null} when absent. */
    Object get(String path);

    /** Direct children of the section at {@code path}; empty map when absent or not a section. */
    Map<String, Object> section(String path);

    /** Whether {@code path} is absent or a section (a mapping), as opposed to a value of another type. */
    boolean isSection(String path);
}
