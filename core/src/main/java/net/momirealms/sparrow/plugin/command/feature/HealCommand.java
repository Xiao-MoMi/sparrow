package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class HealCommand extends BukkitCommandFeature {

    public HealCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> command = builder.flag(manager.flagBuilder("value").withComponent(DoubleParser.doubleParser(0)));
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
        this.plugin().scheduler().platform().run(
            () -> {
                if (player.isDead()) {
                    this.handleFeedback(
                            context,
                            (player == context.sender() ? MessageConstants.COMMAND_PLAYER_DEAD_SELF : MessageConstants.COMMAND_PLAYER_DEAD),
                            Component.text(player.getName())
                    );
                    return;
                }
                double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                double oldHealth = player.getHealth();
                double value = context.flags().getValue("value", maxHealth);
                double health = Math.min(maxHealth, oldHealth + value);
                player.setHealth(health);
                player.setFoodLevel(20);
                player.setSaturation(10.0f);
                boolean self = player == context.sender();
                TranslatableComponent message = context.flags().hasFlag("value")
                        ? (self ? MessageConstants.COMMAND_HEAL_RESTORED_SELF : MessageConstants.COMMAND_HEAL_RESTORED)
                        : (self ? MessageConstants.COMMAND_HEAL_SUCCESS_SELF : MessageConstants.COMMAND_HEAL_SUCCESS);
                this.handleFeedback(
                        context,
                        message,
                        Component.text(player.getName()),
                        Component.text(health - oldHealth)
                );
            },
            () -> {},
            player
        );
    }

    @Override
    public String getFeatureID() {
        return "heal";
    }
}
