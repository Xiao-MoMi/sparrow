package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.database.PlayerNameHistoryStore;
import net.momirealms.sparrow.database.model.PlayerNameRecord;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.util.DateTimeUtils;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NameHistoryCommand extends BukkitCommandFeature {

    public NameHistoryCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
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
        PlayerNameHistoryStore store = this.plugin().dataStorage().playerNameHistoryStore();
        return store.findLatestByName(name).thenCompose(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_NAME_HISTORY_UNKNOWN, Component.text(name));
                return CompletableFuture.completedFuture(null);
            }
            UUID uuid = found.get().uuid();
            return store.history(uuid).thenAccept(records -> this.show(context.sender(), name, uuid, records));
        });
    }

    private void show(@NotNull CommandSender sender, @NotNull String input, @NotNull UUID uuid, @NotNull List<PlayerNameRecord> records) {
        Set<String> seen = new HashSet<>();
        seen.add(input.toLowerCase(Locale.ROOT));
        List<PlayerNameRecord> otherNames = new ArrayList<>();
        for (int i = records.size() - 1; i >= 0; i--) {
            PlayerNameRecord record = records.get(i);
            if (seen.add(record.name().toLowerCase(Locale.ROOT))) {
                otherNames.add(record);
            }
        }
        CommandPanel.Builder panel = CommandPanel.builder()
                .title(MessageConstants.COMMAND_NAME_HISTORY_TITLE)
                .copyField(MessageConstants.COMMAND_NAME_HISTORY_QUERY, input)
                .copyField("UUID", uuid.toString())
                .copyField(MessageConstants.COMMAND_NAME_HISTORY_RECENT, records.getLast().name());
        if (otherNames.isEmpty()) {
            panel.field(MessageConstants.COMMAND_NAME_HISTORY_OTHERS, MessageConstants.COMMAND_NAME_HISTORY_NONE);
        } else {
            panel.section(MessageConstants.COMMAND_NAME_HISTORY_OTHERS);
            for (int i = 0, size = otherNames.size(); i < size; i++) {
                PlayerNameRecord record = otherNames.get(i);
                panel.copyField(DateTimeUtils.fullTime(record.observedAt()), record.name());
            }
        }
        panel.build().send(sender);
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "name_history";
    }
}