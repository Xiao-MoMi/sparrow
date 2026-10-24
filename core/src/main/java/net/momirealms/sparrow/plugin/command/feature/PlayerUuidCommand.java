package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class PlayerUuidCommand extends BukkitCommandFeature {

    public PlayerUuidCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required(
                        "name",
                        StringParser.stringParser(),
                        (context, input) -> CompletableFuture.completedFuture(this.plugin().playerManager().registry.suggestGlobalPlayers(input.peekString()))
                )
                .futureHandler(this::execute));
    }

    @NotNull
    private CompletableFuture<Void> execute(@NotNull CommandContext<CommandSender> context) {
        String name = context.get("name");
        return this.plugin().dataStorage().playerNameHistoryStore()
                .findLatestByName(name)
                .thenAccept(found -> {
                    if (found.isEmpty()) {
                        this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_UUID_EMPTY, Component.text(name));
                        return;
                    }
                    String uuid = found.get().uuid().toString();
                    this.handleFeedback(
                            context,
                            MessageConstants.COMMAND_PLAYER_UUID_SUCCESS,
                            Component.text(found.get().name()),
                            Component.text(uuid).hoverEvent(Component.text(uuid)).clickEvent(ClickEvent.copyToClipboard(uuid))
                    );
                });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "player_uuid";
    }
}