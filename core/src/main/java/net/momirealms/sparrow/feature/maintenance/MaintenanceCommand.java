package net.momirealms.sparrow.feature.maintenance;

import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.server.ServerStatus;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.standard.BooleanParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class MaintenanceCommand extends BukkitCommandFeature {

    public MaintenanceCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.optional("active", BooleanParser.booleanParser())
                .flag(manager.flagBuilder("all"))
                .flag(manager.flagBuilder("tag").withComponent(CommandComponent.<CommandSender, String>builder("tag", StringParser.greedyFlagYieldingStringParser())
                        .suggestionProvider((context, input) -> CompletableFuture.completedFuture(this.tagSuggestions(input)))))
                .handler(context -> this.plugin().scheduler().platform().execute(() -> this.execute(context))));
    }

    @NotNull
    private List<Suggestion> tagSuggestions(@NotNull CommandInput input) {
        String remaining = input.remainingInput();
        int separator = remaining.lastIndexOf(' ');
        String selected = remaining.substring(0, separator + 1);
        String prefix = remaining.substring(separator + 1);
        Set<String> selectedTags = new HashSet<>(Arrays.asList(selected.trim().split("\\s+")));
        return this.plugin().serverManager().getOnlineServers().stream()
                .flatMap(server -> server.tags().stream())
                .distinct()
                .filter(tag -> !selectedTags.contains(tag) && tag.startsWith(prefix))
                .sorted()
                .map(tag -> Suggestion.suggestion(selected + tag))
                .toList();
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        boolean all = context.flags().hasFlag("all");
        String tags = context.flags().getValue("tag", null);
        Boolean requested = context.getOrDefault("active", null);
        if (!all && tags == null) {
            MaintenanceFeature feature = this.plugin().featureManager().feature(MaintenanceFeature.ID, MaintenanceFeature.class);
            boolean active = requested == null ? !feature.active() : requested;
            feature.setActive(active);
            this.handleFeedback(context, active ? MessageConstants.COMMAND_MAINTENANCE_ENABLED : MessageConstants.COMMAND_MAINTENANCE_DISABLED);
            return;
        }
        boolean active = requested == null || requested;
        List<ServerStatus> servers = all
                ? this.plugin().serverManager().getOnlineServers()
                : this.plugin().serverManager().getOnlineServersByTags(new HashSet<>(Arrays.asList(tags.trim().split("\\s+"))));
        if (servers.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_MAINTENANCE_NO_SERVERS);
            return;
        }
        for (int i = 0, size = servers.size(); i < size; i++) {
            this.plugin().messageBrokerManager().publishOneWay(new MaintenanceMessage(active), servers.get(i).serverId());
        }
        this.handleFeedback(context, active ? MessageConstants.COMMAND_MAINTENANCE_ENABLE_SENT : MessageConstants.COMMAND_MAINTENANCE_DISABLE_SENT);
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return MaintenanceFeature.ID;
    }
}