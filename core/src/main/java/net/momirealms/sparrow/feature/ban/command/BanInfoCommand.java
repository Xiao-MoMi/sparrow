package net.momirealms.sparrow.feature.ban.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.ban.BanFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.LongParser;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class BanInfoCommand extends AbstractBanCommand {

    public BanInfoCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin, @NotNull BanFeature feature) {
        super(manager, plugin, feature);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("id", LongParser.longParser(1)).futureHandler(this::execute));
    }

    private CompletableFuture<Void> execute(CommandContext<CommandSender> context) {
        long id = context.get("id");
        return this.feature.service().find(id).thenAccept(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_BAN_INFO_UNKNOWN, Component.text(id));
                return;
            }
            BanPanels.detail(context.sender(), found.get());
        });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "ban_info";
    }
}