package net.momirealms.sparrow.feature.home;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.PlayerRef;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

abstract class AbstractHomeCommand extends BukkitCommandFeature {
    protected final HomeFeature feature;

    protected AbstractHomeCommand(HomeFeature feature) {
        super(SparrowPlugin.instance().commandManager(), SparrowPlugin.instance());
        this.feature = feature;
    }

    protected CompletableFuture<Optional<PlayerRef>> owner(CommandSender sender, @Nullable String name) {
        PlayerRef self = sender instanceof Player player ? new PlayerRef(player.getUniqueId(), player.getName()) : null;
        if (name == null) {
            if (self == null) {
                this.handleFeedback(sender, MessageConstants.COMMAND_HOME_OWNER_REQUIRED);
            }
            return CompletableFuture.completedFuture(Optional.ofNullable(self));
        }
        if (self != null && self.name().equalsIgnoreCase(name)) {
            return CompletableFuture.completedFuture(Optional.of(self));
        }
        boolean other = sender.hasPermission(this.commandConfig().getPermission() + ".other");
        if (!other) {
            this.handleFeedback(sender, MessageConstants.COMMAND_HOME_NO_PERMISSION);
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return this.plugin().playerManager().resolvePlayer(name).thenApply(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(sender, MessageConstants.COMMAND_UNKNOWN_PLAYER, Component.text(name));
            }
            return found;
        });
    }

    protected void failed(CommandSender sender, Throwable error) {
        this.plugin().logger().warn("Home operation failed for " + sender.getName(), error);
        this.handleFeedback(sender, MessageConstants.COMMAND_HOME_STORAGE_FAILED);
    }
}
