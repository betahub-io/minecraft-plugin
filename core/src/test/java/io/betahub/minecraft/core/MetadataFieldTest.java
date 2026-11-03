package io.betahub.minecraft.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetadataFieldTest {

    @Test
    void valuesComeFromTheContext() {
        ReportContext ctx = TestContexts.full();

        assertEquals(Optional.of("survival"), MetadataField.SERVER_NAME.valueOf(ctx));
        assertEquals(Optional.of("120 64 -36"), MetadataField.COORDINATES.valueOf(ctx));
        assertEquals(Optional.of("world_nether 120 64 -36"), MetadataField.LOCATION.valueOf(ctx));
        assertEquals(Optional.of("42"), MetadataField.PING.valueOf(ctx));
        assertEquals(Optional.of("vanilla"), MetadataField.CLIENT_BRAND.valueOf(ctx));
    }

    @Test
    void uncollectedValuesAreEmpty() {
        ReportContext ctx = TestContexts.minimal();

        assertEquals(Optional.empty(), MetadataField.COORDINATES.valueOf(ctx));
        assertEquals(Optional.empty(), MetadataField.CLIENT_VERSION.valueOf(ctx));
        assertEquals(Optional.empty(), MetadataField.GAME_MODE.valueOf(ctx));
    }

    @Test
    void keysAreMatchedCaseInsensitively() {
        assertEquals(Optional.of(MetadataField.SERVER_NAME), MetadataField.fromKey(" Server_Name "));
        assertEquals(Optional.empty(), MetadataField.fromKey("nope"));
    }
}
