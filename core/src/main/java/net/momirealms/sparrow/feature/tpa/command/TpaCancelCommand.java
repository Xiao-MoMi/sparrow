package net.momirealms.sparrow.feature.tpa.command;

import net.momirealms.sparrow.feature.tpa.TpaFeature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class TpaCancelCommand extends BukkitCommandFeature {
    private final TpaFeature feature;

    public TpaCancelCommand(@NotNull TpaFeature feature) {
        super(SparrowPlugin.instance().commandManager(), SparrowPlugin.instance());
        this.feature = feature;
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class)
                .optional(
                        "player",
                        TokenParser.tokenParser(),
                        (context, input) -> !this.feature.enabled() ? CompletableFuture.completedFuture(List.of()) : this.feature.service().suggest(context.sender().getUniqueId(), input.peekString(), false)
                )
                .handler(this::execute));
    }

    private void execute(CommandContext<Player> context) {
        Player player = context.sender();
        this.plugin().scheduler().platform().run(() -> {
            if (!this.feature.enabled()) return;
            this.feature.service().cancel(this.plugin().playerManager().getOnlinePlayer(player), context.getOrDefault("player", null));
        }, () -> {}, player);
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "tpa_cancel";
    }
}