package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.database.model.PlayerNameRecord;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.util.DateTimeUtils;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerNameCommand extends BukkitCommandFeature {

    public PlayerNameCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("uuid", StringParser.stringParser())
                .futureHandler(this::execute));
    }

    @NotNull
    private CompletableFuture<Void> execute(@NotNull CommandContext<CommandSender> context) {
        String input = context.get("uuid");
        UUID uuid = UUIDUtils.parse(input);
        if (uuid == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_NAME_INVALID_UUID, Component.text(input));
            return CompletableFuture.completedFuture(null);
        }
        return this.plugin().dataStorage().playerNameHistoryStore()
                .history(uuid)
                .thenAccept(records -> {
                    if (records.isEmpty()) {
                        this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_NAME_EMPTY, Component.text(uuid.toString()));
                        return;
                    }
                    CommandPanel.Builder panel = CommandPanel.builder()
                            .title(MessageConstants.COMMAND_PLAYER_NAME_TITLE)
                            .copyField("UUID", uuid.toString())
                            .copyField(MessageConstants.COMMAND_PLAYER_NAME_RECENT, records.getLast().name())
                            .section(MessageConstants.COMMAND_PLAYER_NAME_HISTORY);
                    for (int i = records.size() - 1; i >= 0; i--) {
                        PlayerNameRecord record = records.get(i);
                        panel.copyField(DateTimeUtils.fullTime(record.observedAt()), record.name());
                    }
                    panel.build().send(context.sender());
                });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "player_name";
    }
}