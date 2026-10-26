package net.momirealms.sparrow.feature.warp.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.feature.warp.WarpFeature;
import net.momirealms.sparrow.feature.warp.WarpService;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.DateTimeUtils;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public final class EditWarpCommand extends BukkitCommandFeature {
    private final WarpFeature feature;

    public EditWarpCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin, @NotNull WarpFeature feature) {
        super(manager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> named = builder.required(
                "name",
                TokenParser.tokenParser(),
                SuggestionProvider.blockingStrings((context, input) -> this.feature.registry().complete(input.peekString(), warp -> true))
        );
        manager.command(named.handler(context -> {
            Warp warp = this.find(context);
            if (warp != null) this.show(context.sender(), warp);
        }));
        manager.command(named.literal("rename")
                .permission(Permission.allOf(builder.commandPermission(), Permission.of(this.permission("rename"))))
                .required("new_name", TokenParser.tokenParser())
                .handler(this::rename));
        manager.command(named.literal("relocate")
                .senderType(Player.class)
                .permission(Permission.allOf(builder.commandPermission(), Permission.of(this.permission("relocate"))))
                .handler(this::relocate));
    }

    private String permission(String operation) {
        return this.commandConfig().getPermission() + "." + operation;
    }

    @Nullable
    private Warp find(CommandContext<? extends CommandSender> context) {
        String name = context.get("name");
        Warp warp = this.feature.registry().get(name);
        if (warp == null) this.handleFeedback(context, MessageConstants.COMMAND_WARP_UNKNOWN, Component.text(name));
        return warp;
    }

    private void show(CommandSender sender, Warp warp) {
        WorldLocation location = warp.location();
        String coordinates = location.x() + " " + location.y() + " " + location.z() + " " + location.yaw() + " " + location.pitch();
        Component creator = warp.creator() == null ? Component.translatable("command.edit-warp.console") : Component.text(warp.creator().toString());
        Component noPermission = Component.translatable("command.panel.no-permission");
        CommandPanel.builder()
                .title(Component.translatable("command.edit-warp.title", Component.text(warp.name())))
                .copyField(Component.translatable("command.edit-warp.name"), warp.name())
                .copyField(Component.translatable("command.edit-warp.id"), warp.id().toString())
                .field(Component.translatable("command.edit-warp.server"), warp.server())
                .field(Component.translatable("command.edit-warp.world"), location.world())
                .copyField(Component.translatable("command.edit-warp.location"), coordinates)
                .field(Component.translatable("command.edit-warp.creator"), creator)
                .field(Component.translatable("command.edit-warp.created-at"), DateTimeUtils.fullTime(warp.createdAt()))
                .field(Component.translatable("command.edit-warp.updated-at"), DateTimeUtils.fullTime(warp.updatedAt()))
                .buttons(
                        PanelButton.run(Component.translatable("command.warp.button.teleport"), "warp", warp.name()).disabledIf(!this.feature.visible(sender, warp), noPermission),
                        PanelButton.suggest(Component.translatable("command.warp.button.rename"), "edit_warp", warp.name() + " rename ").disabledIf(!sender.hasPermission(this.permission("rename")), noPermission),
                        PanelButton.run(Component.translatable("command.warp.button.relocate"), "edit_warp", warp.name() + " relocate")
                                .disabledIf(!sender.hasPermission(this.permission("relocate")), noPermission)
                                .disabledIf(!(sender instanceof Player), MessageConstants.COMMAND_PLAYER_REQUIRED),
                        PanelButton.run(Component.translatable("command.warp.button.delete"), "del_warp", warp.name())
                )
                .build()
                .send(sender);
    }

    private void rename(CommandContext<CommandSender> context) {
        Warp warp = this.find(context);
        if (warp == null) return;
        String name = context.get("new_name");
        this.save(
                context,
                this.feature.service().rename(warp.id(), name),
                name,
                MessageConstants.COMMAND_EDIT_WARP_RENAMED,
                Component.text(warp.name()),
                Component.text(name)
        );
    }

    private void relocate(CommandContext<Player> context) {
        Warp warp = this.find(context);
        if (warp == null) return;
        this.save(
                context,
                this.feature.service().relocate(warp.id(), ServerConfig.serverId(), WorldLocation.from(context.sender().getLocation())),
                warp.name(),
                MessageConstants.COMMAND_EDIT_WARP_RELOCATED,
                Component.text(warp.name())
        );
    }

    private void save(CommandContext<?> context, CompletableFuture<WarpService.Result> operation, String name, TranslatableComponent success, Component... arguments) {
        operation.whenComplete((result, error) -> {
            if (error != null) {
                this.plugin().logger().warn("Failed to edit warp " + name, error);
                this.handleFeedback(context, MessageConstants.COMMAND_WARP_STORAGE_FAILED, Component.text(name));
                return;
            }
            switch (result.status()) {
                case UPDATED -> this.handleFeedback(context, success, arguments);
                case DUPLICATE_NAME -> this.handleFeedback(context, MessageConstants.COMMAND_WARP_EXISTS, Component.text(name));
                case NOT_FOUND -> this.handleFeedback(context, MessageConstants.COMMAND_WARP_UNKNOWN, Component.text(name));
                case INVALID_NAME -> this.handleFeedback(context, MessageConstants.COMMAND_WARP_INVALID_NAME, Component.text(name), Component.text(Warp.MAX_NAME_LENGTH));
                case CREATED -> throw new IllegalStateException("Unexpected edit result: " + result.status());
            }
        });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "edit_warp";
    }
}