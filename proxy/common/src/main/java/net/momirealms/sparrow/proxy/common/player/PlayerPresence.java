package net.momirealms.sparrow.proxy.common.player;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

public record PlayerPresence(@NotNull UUID uuid, @NotNull String name, @NotNull String server, @NotNull Locale locale) {
}
