package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.database.PlayerNameHistoryStore;
import net.momirealms.sparrow.database.model.PlayerNameRecord;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelText;
import net.momirealms.sparrow.plugin.command.panel.PanelTone;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
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
                .optional("page", IntegerParser.integerParser(1))
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
            return store.history(uuid).thenAccept(records -> this.createPanel(name, uuid, records, context.getOrDefault("page", 1)).send(context.sender()));
        });
    }

    private CommandPanel createPanel(String input, UUID uuid, List<PlayerNameRecord> records, int requestedPage) {
        Map<String, PlayerNameRecord> unique = new LinkedHashMap<>();
        for (int i = records.size() - 1; i >= 0; i--) {
            PlayerNameRecord record = records.get(i);
            unique.putIfAbsent(record.name().toLowerCase(Locale.ROOT), record);
        }
        List<PlayerNameRecord> names = new ArrayList<>(unique.values());
        String recent = records.getLast().name();
        Component identity = Component.translatable("command.name-history.panel.identity", Component.text(input), Component.text(recent), Component.text(uuid.toString()));
        CommandPanel.Builder panel = CommandPanel.list()
                .title(PanelText.of(Component.translatable("command.name-history.panel.title", Component.text(recent))).copy(uuid.toString()).hover(identity))
                .status(PanelText.of(Component.translatable("command.panel.total", Component.text(names.size()))).tone(PanelTone.MUTED))
                .titleActions(PanelButton.run(Component.translatable("command.panel.profile"), "player_info", uuid.toString()));
        int pageSize = panel.getPageCapacity();
        int pages = Math.max(1, (names.size() + pageSize - 1) / pageSize);
        int page = Math.min(requestedPage, pages);
        for (int i = (page - 1) * pageSize, end = Math.min(page * pageSize, names.size()); i < end; i++) {
            PlayerNameRecord record = names.get(i);
            PanelText badge = i == 0
                    ? PanelText.of(Component.translatable("command.panel.recent")).tone(PanelTone.SUCCESS)
                    : PanelText.of(record.name().equalsIgnoreCase(input) ? Component.translatable("command.name-history.panel.query") : Component.empty());
            panel.historyEntry(
                    PanelText.of(record.name()).copy(record.name()),
                    badge,
                    PanelText.time(record.observedAt()).hover(Component.translatable("command.panel.observed-at", PanelText.fullTime(record.observedAt()).getComponent()))
            );
        }
        if (names.size() == 1) {
            panel.entry(PanelText.of(MessageConstants.COMMAND_NAME_HISTORY_NONE).tone(PanelTone.MUTED));
        }
        return panel.pagination(page, pages, this.getFeatureID(), number -> input + " " + number).build();
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "name_history";
    }
}