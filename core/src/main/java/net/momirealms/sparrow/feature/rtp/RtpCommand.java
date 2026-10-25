package net.momirealms.sparrow.feature.rtp;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;

public final class RtpCommand extends AbstractRtpCommand {

    public RtpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        Permission otherPermission = this.otherPermission(builder);
        manager.command(builder
                .required("player", PlayerParser.playerParser())
                .required("region", RtpRegionParser.rtpRegionParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(context -> {
                    this.checkOtherPermission(manager, otherPermission, context);
                    this.execute(context);
                }));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        RtpConfig.Region region = context.get("region");
        this.start(context, region.name(), region.permission(), region.server(), region.areas());
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "rtp";
    }
}