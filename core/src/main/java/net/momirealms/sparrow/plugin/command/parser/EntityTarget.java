package net.momirealms.sparrow.plugin.command.parser;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

public sealed interface EntityTarget {

    record Local(@NotNull Entity entity) implements EntityTarget {
    }

    record Player(@NotNull String nameOrUuid) implements EntityTarget {
    }
}