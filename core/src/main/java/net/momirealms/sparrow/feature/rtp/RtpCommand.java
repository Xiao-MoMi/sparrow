package net.momirealms.sparrow.feature.rtp;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.ServerParser;
import net.momirealms.sparrow.plugin.command.parser.ServerWorldParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

public final class RtpCommand extends BukkitCommandFeature {

    public RtpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NotNull CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class)
                .required("server", ParserDescriptor.of(new ServerParser<>(server -> true, true), String.class))
                .required("world", ServerWorldParser.serverWorldParser("server"))
                .required("min-radius", DoubleParser.doubleParser(0))
                .required("max-radius", DoubleParser.doubleParser(0))
                .flag(manager.flagBuilder("center-x").withComponent(DoubleParser.doubleParser()).build())
                .flag(manager.flagBuilder("center-z").withComponent(DoubleParser.doubleParser()).build())
                .flag(manager.flagBuilder("min-y").withComponent(IntegerParser.integerParser()).build())
                .flag(manager.flagBuilder("max-y").withComponent(IntegerParser.integerParser()).build())
                .handler(this::execute));
    }

    private void execute(CommandContext<Player> context) {
        // TODO 接入新的传送配置与处理器后实现
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return RtpFeature.ID;
    }
}