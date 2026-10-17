package net.momirealms.sparrow.util;

import net.minecraft.world.BossEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class PacketBossEvent extends BossEvent {

    public PacketBossEvent(@NotNull UUID id) {
        this(id, net.minecraft.network.chat.Component.empty(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
    }

    public PacketBossEvent(UUID id, net.minecraft.network.chat.Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        super(id, name, color, overlay);
    }
}