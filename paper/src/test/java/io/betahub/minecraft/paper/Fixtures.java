package io.betahub.minecraft.paper;

import io.betahub.minecraft.core.config.ConfigParser;
import io.betahub.minecraft.core.config.ReporterConfig;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** The shipped config.yml with test overrides. */
final class Fixtures {

    private Fixtures() {
    }

    static YamlConfiguration shippedConfig(Map<String, Object> overrides) {
        try (Reader reader = new InputStreamReader(Fixtures.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
            yaml.set("betahub.project-id", "pr-0690627851");
            yaml.set("betahub.token", "tkn-abc123");
            yaml.set("server-name", "survival");
            overrides.forEach(yaml::set);
            return yaml;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static ReporterConfig config(YamlConfiguration yaml) {
        ConfigParser.Result result = ConfigParser.parse(new PaperConfigSource(yaml));
        if (!result.isValid()) {
            throw new IllegalStateException(result.errors().toString());
        }
        return result.config();
    }

    static Messages messages(YamlConfiguration yaml) {
        return Messages.load(yaml);
    }
}
