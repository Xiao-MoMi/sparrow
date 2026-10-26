package net.momirealms.sparrow.feature.tpa.command;

import net.momirealms.sparrow.feature.tpa.TpaFeature;
import net.momirealms.sparrow.feature.tpa.TpaRequest;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

public final class TpaCommand extends BukkitCommandFeature {
    private final TpaFeature feature;
    private final String id;
    private final TpaRequest.Direction direction;

    public TpaCommand(@NotNull TpaFeature feature, @NotNull String id, @NotNull TpaRequest.Direction direction) {
        super(SparrowPlugin.instance().commandManager(), SparrowPlugin.instance());
        this.feature = feature;
        this.id = id;
        this.direction = direction;
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class)
                .required("player", GlobalPlayerParser.clusterPlayerParser())
                .handler(this::execute));
    }

    private void execute(CommandContext<Player> context) {
        Player player = context.sender();
        this.plugin().scheduler().platform().run(() -> {
            if (!this.feature.enabled()) return;
            this.feature.service().request(this.plugin().playerManager().getOnlinePlayer(player), context.get("player"), this.direction);
        }, () -> {}, player);
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return this.id;
    }
}