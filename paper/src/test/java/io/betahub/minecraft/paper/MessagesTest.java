package io.betahub.minecraft.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static io.betahub.minecraft.paper.PluginTest.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagesTest {

    private final Messages messages = Fixtures.messages(Fixtures.shippedConfig(Map.of()));

    @Test
    void submittedMessageLinksToIssue() {
        URI url = URI.create("https://app.betahub.io/projects/pr-0690627851/issues/42");

        Component message = messages.submitted(url, "#42");

        assertEquals("Thanks! Bug report submitted. View it on BetaHub", plain(message));
        assertEquals(ClickEvent.openUrl(url.toString()), clickOf(message));
    }

    @Test
    void customSubmittedMessageCanShowIssueNumber() {
        YamlConfiguration yaml = Fixtures.shippedConfig(Map.of("messages.submitted", "Filed <id>: <url>"));

        Component message = Fixtures.messages(yaml).submitted(URI.create("https://x.io/i/7"), "#7");

        assertEquals("Filed #7: https://x.io/i/7", plain(message));
    }

    @Test
    void cooldownRoundsUpToWholeSeconds() {
        assertEquals("Please wait 2s before sending another report.", plain(messages.cooldown(Duration.ofMillis(1001))));
        assertEquals("Please wait 1s before sending another report.", plain(messages.cooldown(Duration.ofMillis(1))));
    }

    @Test
    void limitsAreFilledIn() {
        assertEquals("Please describe the bug in at least 10 characters.",
                plain(messages.withLimits(Messages.Key.DESCRIPTION_TOO_SHORT, 10, 1000)));
    }

    @Test
    void playerTextInPlaceholdersIsNotParsed() {
        YamlConfiguration yaml = Fixtures.shippedConfig(Map.of("messages.submitted", "<id>"));

        assertEquals("<red>#1", plain(Fixtures.messages(yaml).submitted(URI.create("https://x.io"), "<red>#1")));
    }

    @Test
    void missingMessageWithoutDefaultsFailsFast() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("messages.dialog-title", "x");

        assertThrows(IllegalStateException.class, () -> Messages.load(yaml));
    }

    @Test
    void brokenMessagesSectionFallsBackToShippedDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.setDefaults(Fixtures.shippedConfig(Map.of()));
        yaml.set("messages", "oops");

        assertEquals("Report a bug", plain(Messages.load(yaml).get(Messages.Key.DIALOG_TITLE)));
    }

    private static ClickEvent clickOf(Component component) {
        if (component.clickEvent() != null) {
            return component.clickEvent();
        }
        return component.children().stream().map(MessagesTest::clickOf).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
}
