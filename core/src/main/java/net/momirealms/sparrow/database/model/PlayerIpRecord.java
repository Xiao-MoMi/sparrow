package net.momirealms.sparrow.database.model;

import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.UUID;

public record PlayerIpRecord(long id, @NotNull UUID uuid, @NotNull InetAddress ip, long observedAt) {
}