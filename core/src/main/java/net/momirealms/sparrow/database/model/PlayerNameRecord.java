package net.momirealms.sparrow.database.model;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record PlayerNameRecord(long id, @NotNull UUID uuid, @NotNull String name, long observedAt) {
}