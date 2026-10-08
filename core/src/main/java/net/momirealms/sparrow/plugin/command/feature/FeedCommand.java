package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
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
import org.jspecify.annotations.NonNull;

public final class FeedCommand extends BukkitCommandFeature {

    public FeedCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> command = builder.flag(manager.flagBuilder("value").withComponent(IntegerParser.integerParser(0)));
        manager.command(command.required("player", PlayerParser.playerParser())
                .permission(this.otherPermission(command))
                .handler(this::execute));
        manager.command(command.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        this.plugin().scheduler().platform().run(() -> {
                int food = player.getFoodLevel();
                int value = context.flags().getValue("value", 20);
                int restored = Math.min(value, 20 - food);
                player.setFoodLevel(food + restored);
                player.setSaturation(10.0f);
                boolean self = player == context.sender();
                TranslatableComponent message = context.flags().hasFlag("value")
                        ? (self ? MessageConstants.COMMAND_FEED_RESTORED_SELF : MessageConstants.COMMAND_FEED_RESTORED)
                        : (self ? MessageConstants.COMMAND_FEED_SUCCESS_SELF : MessageConstants.COMMAND_FEED_SUCCESS);
                this.handleFeedback(
                        context,
                        message,
                        Component.text(player.getName()),
                        Component.text(restored)
                );
            },
            () -> {},
            player
        );
    }

    @Override
    public String getFeatureID() {
        return "feed";
    }
}
