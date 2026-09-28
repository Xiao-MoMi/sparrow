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

import java.util.UUID;

// 按 UUID 查询最近使用的玩家名, 只查集群名单和本插件数据库
public final class PlayerNameCommand extends BukkitCommandFeature {

    public PlayerNameCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.required("uuid", StringParser.stringParser())
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String input = context.get("uuid");
        UUID uuid = LookupSupport.parseUuid(input);
        if (uuid == null) {
            this.handleFeedback(sender, MessageConstants.COMMAND_INVALID_UUID, Component.text(input));
            return;
        }
        this.plugin().playerManager().resolvePlayer(uuid).thenAccept(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(sender, MessageConstants.COMMAND_UNKNOWN_PLAYER, Component.text(uuid.toString()));
                return;
            }
            String name = found.get().name();
            this.handleFeedback(sender, MessageConstants.COMMAND_PLAYER_NAME_SUCCESS, Component.text(uuid.toString()),
                    Component.text(name).hoverEvent(Component.text(name)).clickEvent(ClickEvent.copyToClipboard(name)));
        }).exceptionally(error -> LookupSupport.failed(this, sender, error));
    }

    @Override
    public String getFeatureID() {
        return "player-name";
    }
}
