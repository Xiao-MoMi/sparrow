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
import org.incendo.cloud.parser.standard.BooleanParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class FlyCommand extends BukkitCommandFeature {

    public FlyCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", PlayerParser.playerParser())
                .optional("enabled", BooleanParser.booleanParser())
                .permission(this.otherPermission(builder))
                .handler(this::execute));
        manager.command(builder.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        this.plugin().scheduler().platform().run(() -> {
                    boolean enabled = context.getOrDefault("enabled", !player.getAllowFlight());
                    if (enabled) {
                        player.setAllowFlight(true);
                        player.setFlying(true);
                    } else {
                        player.setFlying(false);
                        player.setAllowFlight(false);
                    }
                    boolean self = player == context.sender();
                    TranslatableComponent message = enabled
                            ? (self ? MessageConstants.COMMAND_FLY_ENABLED_SELF : MessageConstants.COMMAND_FLY_ENABLED)
                            : (self ? MessageConstants.COMMAND_FLY_DISABLED_SELF : MessageConstants.COMMAND_FLY_DISABLED);
                    this.handleFeedback(context, message, Component.text(player.getName()));
                }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "fly";
    }
}