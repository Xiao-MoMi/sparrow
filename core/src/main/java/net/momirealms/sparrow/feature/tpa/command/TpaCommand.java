package net.momirealms.sparrow.feature.tpa.command;

import net.momirealms.sparrow.feature.tpa.TpaFeature;
import net.momirealms.sparrow.feature.tpa.TpaRequest;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

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
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class)
                .required("player", ClusterPlayerParser.clusterPlayerNameParser(this.plugin().playerDirectory()))
                .handler(this::execute));
    }

    private void execute(CommandContext<Player> context) {
        // TODO 重构 TPA 请求流程并接入传送 API
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return this.id;
    }
}
