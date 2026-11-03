package io.betahub.minecraft.paper;

import io.betahub.minecraft.core.config.ConfigSource;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;

/** Adapts Bukkit's config to the Bukkit-free parser. */
final class PaperConfigSource implements ConfigSource {

    private final ConfigurationSection config;

    PaperConfigSource(ConfigurationSection config) {
        this.config = config;
    }

    @Override
    public Object get(String path) {
        return config.get(path);
    }

    @Override
    public Map<String, Object> section(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section == null ? Map.of() : new LinkedHashMap<>(section.getValues(false));
    }

    @Override
    public boolean isSection(String path) {
        return !config.isSet(path) || config.isConfigurationSection(path);
    }
}
