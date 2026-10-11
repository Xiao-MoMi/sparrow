package net.momirealms.sparrow.feature.tpa.command;

import net.momirealms.sparrow.feature.tpa.TpaFeature;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class TpaReplyCommand extends BukkitCommandFeature {
    private final TpaFeature feature;
    private final String id;
    private final boolean accept;

    public TpaReplyCommand(@NotNull TpaFeature feature, @NotNull String id, boolean accept) {
        super(SparrowPlugin.instance().commandManager(), SparrowPlugin.instance());
        this.feature = feature;
        this.id = id;
        this.accept = accept;
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class)
                .optional("player", TokenParser.tokenParser())
                .handler(this::execute));
    }

    private void execute(CommandContext<Player> context) {
        BukkitSparrowPlayer player = this.plugin().playerManager().getPlayer(context.sender());
        if (player == null) return;
        String requester = context.getOrDefault("player", null);
        if (this.accept) {
            this.feature.service().accept(player, requester);
        } else {
            this.feature.service().deny(player, requester);
        }
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return this.id;
    }
}