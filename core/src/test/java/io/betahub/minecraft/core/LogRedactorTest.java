package io.betahub.minecraft.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogRedactorTest {

    private final LogRedactor redactor = new LogRedactor(List.of("tkn-secret123"));

    @Test
    void redactsIpv4WithPortInPaperLoginLine() {
        String line = "[19:30:00 INFO]: Notch[/203.0.113.7:51234] logged in with entity id 42 at ([world]1.5, 64.0, 2.5)";

        assertEquals("[19:30:00 INFO]: Notch[/[IP]:51234] logged in with entity id 42 at ([world]1.5, 64.0, 2.5)",
                redactor.redact(line));
    }

    @Test
    void redactsBracketedIpv6WithPort() {
        assertEquals("Notch[/[[IP]]:51234] logged in", redactor.redact("Notch[/[2001:db8:85a3::8a2e:370:7334]:51234] logged in"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            "2001:db8::1",
            "::1",
            "fe80::1%eth0",
            "::ffff:192.168.1.10",
            "64:ff9b::192.0.2.33"
    })
    void redactsIpv6Forms(String address) {
        assertEquals("from [IP] now", redactor.redact("from " + address + " now"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[19:30:00 INFO]: Done (3.123s)!",
            "Running Paper version 1.21.11-120 (MC: 1.21.11)",
            "Method reference Main::run failed",
            "MAC aa:bb:cc:dd:ee:ff",
            "ratio 3:2 and C++ :: scope",
            "Loaded 1.2.3 of library"
    })
    void leavesNonAddressesAlone(String line) {
        assertEquals(line, redactor.redact(line));
    }

    @Test
    void doesNotRedactPartOfLongerDottedNumber() {
        assertEquals("build 10.20.30.40.50", redactor.redact("build 10.20.30.40.50"));
    }

    @Test
    void redactsIpv6FollowedByPortWithoutBrackets() {
        assertEquals("Connection from [IP]:54321", redactor.redact("Connection from 2001:db8:0:0:0:0:0:1:54321"));
    }

    @Test
    void redactsIpv6AfterKeyAndColon() {
        assertEquals("ip:[IP] joined", redactor.redact("ip:2001:db8::1 joined"));
    }

    @Test
    void redactsIpv6AtEndOfSentence() {
        assertEquals("from [IP].", redactor.redact("from 2001:db8::1."));
    }

    @Test
    void longColonRunsAreHandledInLinearTime() {
        String line = "a:".repeat(20_000) + "g";

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> redactor.redact(line));
    }

    @Test
    void redactsGivenSecrets() {
        String redacted = redactor.redact("Authorization: FormUser tkn-secret123");

        assertFalse(redacted.contains("tkn-secret123"));
        assertTrue(redacted.endsWith(LogRedactor.SECRET_PLACEHOLDER));
    }

    @Test
    void redactsEveryAddressInMultilineText() {
        String text = "a 10.0.0.1\nb 10.0.0.2\nc ::1\n";

        assertEquals("a [IP]\nb [IP]\nc [IP]\n", redactor.redact(text));
    }
}
