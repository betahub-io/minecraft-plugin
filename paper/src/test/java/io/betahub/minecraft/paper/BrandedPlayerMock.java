package io.betahub.minecraft.paper;

import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

/** MockBukkit does not implement the client brand. */
final class BrandedPlayerMock extends PlayerMock {

    private final String brand;

    BrandedPlayerMock(ServerMock server, String name, String brand) {
        super(server, name, UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        this.brand = brand;
    }

    @Override
    public String getClientBrandName() {
        return brand;
    }
}
