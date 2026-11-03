package io.betahub.minecraft.paper;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

import java.util.Optional;
import java.util.UUID;

/**
 * Client version from ViaVersion. Only load this class when ViaVersion is enabled:
 * it references ViaVersion types, which are absent otherwise.
 */
final class ViaVersionLookup implements ClientVersionLookup {

    @Override
    public Optional<String> clientVersion(UUID player) {
        ProtocolVersion version = Via.getAPI().getPlayerProtocolVersion(player);
        if (version == null || !version.isKnown()) {
            return Optional.empty();
        }
        return Optional.of(version.getName());
    }
}
