package net.momirealms.sparrow.proxy.common.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public abstract class ProxyPlayerManager {
    protected ProxyPlayerDirectory directory;

    public final void enable(@NotNull ProxyPlayerDirectory directory) {
        this.directory = directory;
        this.registerListeners();
    }

    protected abstract void registerListeners();

    public abstract void disable();

    @NotNull
    public abstract List<PlayerPresence> getPlayers();

    @Nullable
    public abstract PlayerPresence findPlayer(@NotNull UUID player);

    public abstract boolean disconnect(@NotNull UUID player, @NotNull String jsonReason);
}