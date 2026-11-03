package io.betahub.minecraft.paper;

import java.util.Optional;
import java.util.UUID;

/** Finds the Minecraft version a player's client runs. */
@FunctionalInterface
public interface ClientVersionLookup {

    ClientVersionLookup NONE = player -> Optional.empty();

    Optional<String> clientVersion(UUID player);
}
