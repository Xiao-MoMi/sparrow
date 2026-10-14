package net.momirealms.sparrow.feature.warp.command;

import net.momirealms.sparrow.feature.warp.WarpFeature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class WarpCommand extends BukkitCommandFeature {
    private final WarpFeature feature;

    public WarpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull WarpFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    // /warp <name> 传送自己, /warp <name> <player> 送别人; 后者还需要命令权限加 .other, 没有时客户端看不到目标参数
    // 名称读到空格为止, 中文名称不受 Brigadier 单词规则限制
    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> named = builder.required(
                "name",
                TokenParser.tokenParser(),
                SuggestionProvider.blockingStrings((context, input) -> this.feature.suggest(context.sender(), input.peekString()))
        );
        String other = this.plugin()
                .configurationManager()
                .commandsConfig()
                .configDefinition()
                .command(this.getFeatureID())
                .getPermission() + ".other";
        manager.command(named.required("player", PlayerParser.playerParser())
                .permission(Permission.allOf(builder.commandPermission(), Permission.of(other)))
                .handler(this::execute));
        manager.command(named.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        // TODO 接入新的传送配置与处理器后实现
    }

    @Override
    public String getFeatureID() {
        return "warp";
    }
}
