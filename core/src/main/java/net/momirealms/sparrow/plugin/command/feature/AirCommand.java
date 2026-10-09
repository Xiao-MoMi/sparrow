package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

public final class AirCommand extends BukkitCommandFeature {

    public AirCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", PlayerParser.playerParser())
                .flag(manager.flagBuilder("value").withComponent(IntegerParser.integerParser(0)))
                .handler(this::execute));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        Player player = context.get("player");
        this.plugin().scheduler().platform().run(() -> {
            int maximum = player.getMaximumAir();
            int air = Math.min(context.flags().getValue("value", maximum), maximum);
            player.setRemainingAir(air);
            this.handleFeedback(
                    context,
                    MessageConstants.COMMAND_AIR_SUCCESS,
                    Component.text(player.getName()),
                    Component.text(air),
                    Component.text(maximum)
            );
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "air";
    }
}
