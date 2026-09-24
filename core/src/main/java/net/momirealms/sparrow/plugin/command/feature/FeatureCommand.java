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
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;

public final class FeatureCommand extends BukkitCommandFeature {

    public FeatureCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        List<String> suggestions = new ArrayList<>(this.plugin().featureManager().ids());
        suggestions.add("list");
        return builder
                .required("feature", StringParser.stringParser(), SuggestionProvider.suggestingStrings(suggestions))
                .optional("state", EnumParser.enumParser(Toggle.class))
                .handler(context -> this.plugin().scheduler().platform().execute(() -> this.execute(context)));
    }

    private void execute(CommandContext<CommandSender> context) {
        FeatureManager manager = this.plugin().featureManager();
        String id = context.get("feature");
        if (id.equals("list")) {
            for (String featureId : manager.ids()) {
                this.status(context, manager.feature(featureId));
            }
            return;
        }
        Feature<?> feature = manager.feature(id);
        if (feature == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_FEATURE_UNKNOWN, Component.text(id));
            return;
        }
        Toggle toggle = context.<Toggle>optional("state").orElse(null);
        if (toggle == null) {
            this.status(context, feature);
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
            manager.setEnabled(id, toggle == Toggle.ON).whenComplete((state, error) -> this.complete(context, id, state, error));
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

    private void status(CommandContext<CommandSender> context, Feature<?> feature) {
        this.handleFeedback(context, MessageConstants.COMMAND_FEATURE_STATUS,
                Component.text(feature.id()),
                Component.translatable("feature.state." + feature.state().get().name().toLowerCase(Locale.ROOT)));
    }

    @Override
    public String getFeatureID() {
        return "feature";
    }

    public enum Toggle {
        ON,
        OFF
    }
}
