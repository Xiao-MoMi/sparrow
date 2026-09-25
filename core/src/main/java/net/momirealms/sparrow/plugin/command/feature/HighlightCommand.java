package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.momirealms.sparrow.feature.highlight.HighlightFeature;
import net.momirealms.sparrow.feature.highlight.HighlightSettings;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.NamedTextColorParser;
import org.bukkit.Location;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.WorldParser;
import org.incendo.cloud.bukkit.parser.location.LocationParser;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HighlightCommand extends BukkitCommandFeature {
    public HighlightCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.optional("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .flag(manager.flagBuilder("from").withComponent(LocationParser.locationParser()))
                .flag(manager.flagBuilder("to").withComponent(LocationParser.locationParser()))
                .flag(manager.flagBuilder("world").withComponent(WorldParser.worldParser()))
                .flag(manager.flagBuilder("highlight-duration").withAliases("d").withComponent(IntegerParser.integerParser(0, 300)))
                .flag(manager.flagBuilder("highlight-color").withAliases("c").withComponent(NamedTextColorParser.namedTextColorParser()))
                .flag(manager.flagBuilder("solid-only"))
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        HighlightFeature feature = this.plugin().featureManager().feature(HighlightFeature.ID, HighlightFeature.class);
        if (!feature.enabled()) {
            this.handleFeedback(context, MessageConstants.COMMAND_FEATURE_DISABLED, Component.text(HighlightFeature.ID));
            return;
        }
        Location first = context.flags().getValue("from", null);
        Location second = context.flags().getValue("to", null);
        if ((first == null) != (second == null)) {
            this.handleFeedback(context, MessageConstants.COMMAND_HIGHLIGHT_POINTS);
            return;
        }
        Player sender = context.sender() instanceof Player player ? player : null;
        if (first == null && sender == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_HIGHLIGHT_POINTS);
            return;
        }
        if (first == null && feature.cancelSelection(sender)) {
            this.handleFeedback(context, MessageConstants.COMMAND_HIGHLIGHT_CANCELLED);
            return;
        }
        if (first == null && context.flags().hasFlag("world")) {
            this.handleFeedback(context, MessageConstants.COMMAND_HIGHLIGHT_POINTS);
            return;
        }
        World world = context.flags().getValue("world", sender == null ? null : sender.getWorld());
        if (world == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_HIGHLIGHT_WORLD);
            return;
        }
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            this.handleFeedback(context, MessageConstants.COMMAND_HIGHLIGHT_PEACEFUL);
            return;
        }
        MultiplePlayerSelector selector = context.getOrDefault("targets", null);
        List<Player> viewers = selector == null ? world.getPlayers() : new ArrayList<>(selector.values());
        viewers = viewers.stream().filter(player -> player.isOnline() && player.getWorld() == world).toList();
        if (viewers.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        HighlightSettings settings = feature.config();
        NamedTextColor color = context.flags().getValue("highlight-color", NamedTextColor.NAMES.value(settings.defaultColor().toLowerCase(Locale.ROOT)));
        int duration = context.flags().getValue("highlight-duration", settings.defaultDuration());
        HighlightFeature.Options options = new HighlightFeature.Options(viewers, color, duration, settings.solidOnly() || context.flags().hasFlag("solid-only"));
        HighlightFeature.Feedback feedback = (key, arguments) -> this.handleFeedback(context, key, arguments);
        if (first == null) {
            feature.select(sender, options, feedback);
        } else {
            first.setWorld(world);
            second.setWorld(world);
            feature.show(first, second, options, feedback);
        }
    }

    @Override
    public String getFeatureID() {
        return HighlightFeature.ID;
    }
}
