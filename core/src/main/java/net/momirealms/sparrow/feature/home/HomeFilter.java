package net.momirealms.sparrow.feature.home;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record HomeFilter(@Nullable UUID owner, @Nullable String server, @Nullable String world) {

    public boolean matches(@NotNull Home home) {
        return (this.owner == null || this.owner.equals(home.owner()))
                && (this.server == null || this.server.equals(home.server()))
                && (this.world == null || this.world.equals(home.location().world()));
    }
}