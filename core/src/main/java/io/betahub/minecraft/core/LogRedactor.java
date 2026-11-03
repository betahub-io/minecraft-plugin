package io.betahub.minecraft.core;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes IP addresses (and given secrets) from server log text before it leaves the server.
 * BetaHub redacts secrets too, but player IPs must never be sent in the first place.
 */
public final class LogRedactor {

    public static final String IP_PLACEHOLDER = "[IP]";
    public static final String SECRET_PLACEHOLDER = "[REDACTED]";

    private static final String OCTET = "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![\\d.])" + OCTET + "(?:\\." + OCTET + "){3}(?![\\d]|\\.\\d)");

    /**
     * Runs of characters an IPv6 address, its zone and its neighbours in {@code key:value} or {@code address:port}
     * text are made of. A single character class keeps matching linear on long lines.
     */
    private static final Pattern IPV6_RUN = Pattern.compile("[0-9A-Za-z:.%]+");
    /** Longest IPv6 text: 45 characters with an embedded IPv4, plus room for a zone like {@code %eth0}. */
    private static final int MAX_IPV6_TEXT = 64;

    private static final int IPV6_GROUPS = 8;
    private static final int IPV6_GROUPS_WITH_IPV4_SUFFIX = 6;
    private static final int MAX_HEX_DIGITS_PER_GROUP = 4;

    private final List<String> secrets;

    /** @param secrets literal values to blank out as well (e.g. the auth token); blanks are ignored */
    public LogRedactor(List<String> secrets) {
        this.secrets = secrets.stream().filter(secret -> secret != null && !secret.isBlank()).toList();
    }

    public String redact(String text) {
        String result = text;
        for (String secret : secrets) {
            result = result.replace(secret, SECRET_PLACEHOLDER);
        }
        result = redactIpv6(result);
        return IPV4.matcher(result).replaceAll(Matcher.quoteReplacement(IP_PLACEHOLDER));
    }

    private static String redactIpv6(String text) {
        Matcher matcher = IPV6_RUN.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        int copied = 0;
        while (matcher.find()) {
            String run = matcher.group();
            if (run.indexOf(':') < 0) {
                continue;
            }
            out.append(text, copied, matcher.start()).append(redactRun(run));
            copied = matcher.end();
        }
        return out.append(text, copied, text.length()).toString();
    }

    /**
     * Replaces addresses inside one run. An address may start at the run start or right after ':' or '.'
     * ({@code ip:2001:db8::1}) and end at the run end or right before ':' or '.' ({@code 2001:db8::1:54321},
     * {@code 2001:db8::1.}); the longest valid one wins.
     */
    static String redactRun(String run) {
        StringBuilder out = new StringBuilder(run.length());
        int i = 0;
        while (i < run.length()) {
            int end = isSeparatorBefore(run, i) ? longestAddressEnd(run, i) : -1;
            if (end > i) {
                out.append(IP_PLACEHOLDER);
                i = end;
            } else {
                out.append(run.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    private static boolean isSeparatorBefore(String run, int index) {
        return index == 0 || isSeparator(run.charAt(index - 1));
    }

    private static int longestAddressEnd(String run, int start) {
        for (int end = Math.min(run.length(), start + MAX_IPV6_TEXT); end > start; end--) {
            boolean boundary = end == run.length() || isSeparator(run.charAt(end));
            if (boundary && isIpv6(run.substring(start, end))) {
                return end;
            }
        }
        return -1;
    }

    private static boolean isSeparator(char c) {
        return c == ':' || c == '.';
    }

    /** Strict syntactic check, so times like {@code 19:30:00} are left alone. Never does DNS lookups. */
    static boolean isIpv6(String candidate) {
        if (candidate.indexOf(':') < 0) {
            return false;
        }
        String address = candidate;
        int zoneStart = address.indexOf('%');
        if (zoneStart >= 0) {
            address = address.substring(0, zoneStart);
        }
        int maxGroups = IPV6_GROUPS;
        if (address.indexOf('.') >= 0) {
            int lastColon = address.lastIndexOf(':');
            if (!IPV4.matcher(address.substring(lastColon + 1)).matches()) {
                return false;
            }
            address = address.substring(0, lastColon + 1) + "0";
            maxGroups = IPV6_GROUPS_WITH_IPV4_SUFFIX;
        }
        int doubleColon = address.indexOf("::");
        if (doubleColon >= 0 && address.indexOf("::", doubleColon + 1) >= 0) {
            return false;
        }
        if (doubleColon < 0) {
            return hexGroups(address) == maxGroups;
        }
        String head = address.substring(0, doubleColon);
        String tail = address.substring(doubleColon + 2);
        if (head.isEmpty() && tail.isEmpty()) {
            // A bare "::" is far more likely punctuation than the unspecified address.
            return false;
        }
        int headGroups = head.isEmpty() ? 0 : hexGroups(head);
        int tailGroups = tail.isEmpty() ? 0 : hexGroups(tail);
        return headGroups >= 0 && tailGroups >= 0 && headGroups + tailGroups < maxGroups;
    }

    /** Number of colon-separated groups when all are 1-4 hex digits, otherwise -1. */
    private static int hexGroups(String part) {
        String[] groups = part.split(":", -1);
        for (String group : groups) {
            if (group.isEmpty() || group.length() > MAX_HEX_DIGITS_PER_GROUP) {
                return -1;
            }
            for (int i = 0; i < group.length(); i++) {
                if (Character.digit(group.charAt(i), 16) < 0) {
                    return -1;
                }
            }
        }
        return groups.length;
    }
}
