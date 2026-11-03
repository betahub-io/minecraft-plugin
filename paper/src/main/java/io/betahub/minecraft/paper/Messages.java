package io.betahub.minecraft.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/** Player-facing text from the {@code messages} section of config.yml, rendered with MiniMessage. */
public final class Messages {

    public enum Key {
        DIALOG_TITLE("dialog-title"),
        DIALOG_BODY("dialog-body"),
        DIALOG_DESCRIPTION_LABEL("dialog-description-label"),
        DIALOG_STEPS_LABEL("dialog-steps-label"),
        DIALOG_SUBMIT("dialog-submit"),
        DIALOG_CANCEL("dialog-cancel"),
        DESCRIPTION_TOO_SHORT("description-too-short"),
        DESCRIPTION_TOO_LONG("description-too-long"),
        STEPS_TOO_LONG("steps-too-long"),
        SENDING("sending"),
        SUBMITTED("submitted"),
        COOLDOWN("cooldown"),
        FAILED("failed"),
        UNCERTAIN("uncertain"),
        REJECTED("rejected"),
        RATE_LIMITED("rate-limited"),
        NOT_CONFIGURED("not-configured"),
        PLAYERS_ONLY("players-only");

        private final String path;

        Key(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }
    }

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Map<Key, String> templates;

    private Messages(Map<Key, String> templates) {
        this.templates = templates;
    }

    static final String SECTION = "messages";

    /**
     * Reads every message from {@code messages.*} of the root config. The jar's config.yml (Bukkit config
     * defaults) fills in messages the admin removed or broke.
     *
     * @throws IllegalStateException when a message is missing even from the defaults (a broken jar)
     */
    public static Messages load(ConfigurationSection root) {
        Map<Key, String> templates = new EnumMap<>(Key.class);
        for (Key key : Key.values()) {
            String path = SECTION + "." + key.path();
            String template = root.getString(path);
            if (template == null) {
                throw new IllegalStateException(path + " is missing from config.yml");
            }
            templates.put(key, template);
        }
        return new Messages(templates);
    }

    public Component get(Key key, TagResolver... placeholders) {
        return MINI_MESSAGE.deserialize(templates.get(key), placeholders);
    }

    public Component submitted(URI url, String issueReference) {
        // Parsed (not unparsed) so the URL also works inside <click:open_url:'<url>'>.
        // BetaHubClient only accepts plain http(s) URLs, so it cannot inject tags.
        return get(Key.SUBMITTED,
                Placeholder.parsed("url", url.toString()),
                Placeholder.unparsed("id", issueReference));
    }

    public Component cooldown(Duration remaining) {
        long seconds = Math.max(1, (remaining.toMillis() + 999) / 1000);
        return get(Key.COOLDOWN, Placeholder.unparsed("seconds", Long.toString(seconds)));
    }

    public Component withLimits(Key key, int min, int max) {
        return get(key,
                Placeholder.unparsed("min", Integer.toString(min)),
                Placeholder.unparsed("max", Integer.toString(max)));
    }
}
