package net.momirealms.sparrow.feature.deathmessages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

record DeathMessageContext(
        @NotNull Player victim,
        @NotNull TranslatableComponent original,
        @NotNull String server,
        @NotNull String world,
        int x,
        int y,
        int z
) {

    @NotNull
    static DeathMessageContext capture(@NotNull Player victim) {
        String json = CraftChatMessage.toJSON(((CraftPlayer) victim).getHandle().getCombatTracker().getDeathMessage());
        TranslatableComponent original = (TranslatableComponent) AdventureHelper.jsonToComponent(json);
        Location location = victim.getLocation();
        return new DeathMessageContext(victim, original, ServerConfig.serverId(), location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @NotNull
    String getMessageKey() {
        return this.original.key();
    }

    @NotNull
    Component getVictimName() {
        return (Component) this.original.arguments().getFirst().value();
    }

    @Nullable
    Component getAttackerName() {
        if (this.original.arguments().size() < 2 || this.getDeathReason() != null) return null;
        return (Component) this.original.arguments().get(1).value();
    }

    @Nullable
    Component getWeapon() {
        if (!this.getMessageKey().endsWith(".item")) return null;
        return (Component) this.original.arguments().get(2).value();
    }

    @Nullable
    Component getDeathReason() {
        // 床和重生锚爆炸的第二个参数是带链接的死亡原因组件.
        if (!this.getMessageKey().equals("death.attack.badRespawnPoint.message")) return null;
        return (Component) this.original.arguments().get(1).value();
    }
}