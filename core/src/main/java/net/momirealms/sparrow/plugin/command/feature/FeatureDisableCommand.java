package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.feature.FeatureManager;
import net.momirealms.sparrow.feature.FeatureState;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;

public final class FeatureDisableCommand extends BukkitCommandFeature {

    public FeatureDisableCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .required("feature", StringParser.stringParser(), SuggestionProvider.blockingStrings((context, input) -> this.suggestions()))
                .handler(context -> this.plugin().scheduler().platform().execute(() -> this.execute(context)));
    }

    private List<String> suggestions() {
        FeatureManager manager = this.plugin().featureManager();
        List<String> suggestions = new ArrayList<>();
        for (String id : manager.ids()) {
            Feature<?> feature = manager.feature(id);
            if (feature.hotToggleable() && feature.enabled()) {
                suggestions.add(id);
            }
        }
        return suggestions;
    }

    private void execute(CommandContext<CommandSender> context) {
        FeatureManager manager = this.plugin().featureManager();
        String id = context.get("feature");
        Feature<?> feature = manager.feature(id);
        if (feature == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_FEATURE_UNKNOWN, Component.text(id));
            return;
        }
        if (!feature.hotToggleable()) {
            this.handleFeedback(context, MessageConstants.COMMAND_FEATURE_RESTART_REQUIRED, Component.text(id));
            return;
        }
        if (this.plugin().isReloading()) {
            this.handleFeedback(context, MessageConstants.COMMAND_FEATURE_BUSY);
            return;
        }
        try {
            manager.setEnabled(id, false).whenComplete((state, error) -> this.complete(context, id, state, error));
        } catch (RuntimeException exception) {
            this.complete(context, id, null, exception);
        }
    }

    private void complete(CommandContext<CommandSender> context, String id, FeatureState state, Throwable error) {
        if (error != null) {
            Throwable cause = error instanceof CompletionException ? error.getCause() : error;
            this.plugin().logger().warn("Failed to switch feature " + id, cause);
        }
        Runnable feedback = () -> {
            if (error != null) {
                this.handleFeedback(context, MessageConstants.COMMAND_FEATURE_FAILURE, Component.text(id));
            } else {
                this.handleFeedback(context, MessageConstants.COMMAND_FEATURE_SUCCESS,
                        Component.text(id), Component.translatable("feature.state." + state.name().toLowerCase(Locale.ROOT)));
            }
        };
        if (context.sender() instanceof Player player) {
            this.plugin().scheduler().platform().run(feedback, () -> {}, player);
        } else {
            feedback.run();
        }
    }

    @Override
    public String getFeatureID() {
        return "feature_disable";
    }
}
