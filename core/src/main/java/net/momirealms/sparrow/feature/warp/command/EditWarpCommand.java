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
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.parser.standard.UUIDParser;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class EditWarpCommand extends BukkitCommandFeature {
    private final WarpFeature feature;

    public EditWarpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull WarpFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> named = builder.required(
                "name",
                TokenParser.tokenParser(),
                SuggestionProvider.blockingStrings((context, input) -> this.feature.suggest(context.sender(), input.peekString()))
        );
        manager.command(named.handler(this::execute));
        manager.command(named.literal("rename")
                .permission(Permission.allOf(builder.commandPermission(), Permission.of(this.permission("rename"))))
                .required("new_name", StringParser.greedyStringParser())
                .handler(this::rename));
        manager.command(named.literal("description")
                .permission(Permission.allOf(builder.commandPermission(), Permission.of(this.permission("description"))))
                .required("text", StringParser.greedyStringParser())
                .handler(this::description));
        manager.command(named.literal("relocate")
                .permission(Permission.allOf(builder.commandPermission(), Permission.of(this.permission("relocate"))))
                .senderType(Player.class)
                .handler(this::relocate));
        Command.Builder<CommandSender> delete = named.literal("delete").permission(
                Permission.allOf(builder.commandPermission(), Permission.of(this.permission("delete")))
        );
        manager.command(delete.handler(this::confirmDelete));
        manager.command(delete.literal("confirm").required("id", UUIDParser.uuidParser()).handler(this::delete));
    }

    @NotNull
    private String permission(@NotNull String operation) {
        return this.commandConfig().getPermission() + "." + operation;
    }

    @Nullable
    private Warp find(@NotNull CommandContext<? extends CommandSender> context) {
        String name = context.get("name");
        Warp warp = this.feature.registry().get(name);
        if (warp == null || !this.feature.visible(context.sender(), warp)) {
            this.handleFeedback(context, MessageConstants.COMMAND_WARP_UNKNOWN, Component.text(name));
            return null;
        }
        return warp;
    }

    private void execute(CommandContext<CommandSender> context) {
        Warp warp = this.find(context);
        if (warp != null) {
            this.show(context.sender(), warp);
        }
    }

    private void show(@NotNull CommandSender sender, @NotNull Warp warp) {
        WorldLocation location = warp.location();
        CommandPanel panel = new CommandPanel(this.commandManager(), sender);
        panel.line(Component.translatable(
                "command.edit-warp.info",
                Component.text(warp.name()),
                Component.text(warp.id().toString()),
                Component.text(warp.key()),
                Component.text(warp.description()),
                Component.text(warp.server()),
                Component.text(location.world()),
                Component.text(location.x()),
                Component.text(location.y()),
                Component.text(location.z()),
                Component.text(location.yaw()),
                Component.text(location.pitch()),
                warp.creator() == null ? Component.translatable("command.edit-warp.console") : Component.text(warp.creator().toString()),
                Component.text(DateTimeUtils.fullTime(warp.createdAt())),
                Component.text(DateTimeUtils.fullTime(warp.updatedAt()))
        ));
        panel.actions(
                panel.suggest(CommandPanel.label("rename"), this.getFeatureID(), warp.name() + " rename ")
                        .permission(this.permission("rename"))
                        .build(),
                panel.suggest(CommandPanel.label("description"), this.getFeatureID(), warp.name() + " description ")
                        .permission(this.permission("description"))
                        .build(),
                panel.run(CommandPanel.label("relocate"), this.getFeatureID(), warp.name() + " relocate")
                        .style(PanelButton.Style.POSITIVE)
                        .permission(this.permission("relocate"))
                        .playersOnly()
                        .build(),
                panel.run(CommandPanel.label("delete"), this.getFeatureID(), warp.name() + " delete")
                        .style(PanelButton.Style.DANGER)
                        .permission(this.permission("delete"))
                        .build()
        );
        panel.send();
    }

    private void rename(CommandContext<CommandSender> context) {
        Warp warp = this.find(context);
        if (warp == null) {
            return;
        }
        String name = context.get("new_name");
        this.save(
                context,
                name,
                this.feature.service().rename(warp.id(), name),
                MessageConstants.COMMAND_EDIT_WARP_RENAMED,
                Component.text(warp.name()),
                Component.text(name)
        );
    }

    private void description(CommandContext<CommandSender> context) {
        Warp warp = this.find(context);
        if (warp == null) {
            return;
        }
        String text = context.get("text");
        this.save(
                context,
                warp.name(),
                this.feature.service().setDescription(warp.id(), text),
                MessageConstants.COMMAND_EDIT_WARP_DESCRIPTION,
                Component.text(warp.name())
        );
    }

    private void relocate(CommandContext<Player> context) {
        Warp warp = this.find(context);
        if (warp == null) {
            return;
        }
        this.save(
                context,
                warp.name(),
                this.feature.service().relocate(warp.id(), ServerConfig.serverId(), WorldLocation.from(context.sender().getLocation())),
                MessageConstants.COMMAND_SET_WARP_MOVED,
                Component.text(warp.name())
        );
    }

    private void save(
            @NotNull CommandContext<? extends CommandSender> context,
            @NotNull String name,
            @NotNull CompletableFuture<WarpService.Result> operation,
            @NotNull TranslatableComponent message,
            @NotNull Component... arguments
    ) {
        operation.thenAccept(result -> {
            switch (result.status()) {
                case UPDATED -> {
                    this.handleFeedback(context, message, arguments);
                    this.show(context.sender(), result.warp());
                }
                case DUPLICATE_NAME -> this.handleFeedback(context, MessageConstants.COMMAND_SET_WARP_EXISTS, Component.text(name));
                case NOT_FOUND -> this.handleFeedback(context, MessageConstants.COMMAND_WARP_UNKNOWN, Component.text(name));
                case INVALID_NAME -> this.handleFeedback(
                        context,
                        MessageConstants.COMMAND_WARP_INVALID_NAME,
                        Component.text(name),
                        Component.text(Warp.MAX_NAME_LENGTH)
                );
                case DESCRIPTION_TOO_LONG -> this.handleFeedback(
                        context,
                        MessageConstants.COMMAND_EDIT_WARP_DESCRIPTION_TOO_LONG,
                        Component.text(Warp.MAX_DESCRIPTION_LENGTH)
                );
                case CREATED -> throw new AssertionError();
            }
        }).exceptionally(error -> {
            this.plugin().logger().warn("Failed to save warp " + name, error);
            this.handleFeedback(context, MessageConstants.COMMAND_WARP_STORAGE_FAILED, Component.text(name));
            return null;
        });
    }

    private void confirmDelete(CommandContext<CommandSender> context) {
        Warp warp = this.find(context);
        if (warp == null) return;
        CommandPanel panel = new CommandPanel(this.commandManager(), context.sender());
        panel.line(Component.translatable("command.edit-warp.confirm-delete", Component.text(warp.name())))
                .actions(
                        panel.run(CommandPanel.label("confirm_delete"), this.getFeatureID(), warp.name() + " delete confirm " + warp.id())
                        .style(PanelButton.Style.DANGER)
                        .permission(this.permission("delete"))
                        .build(),
                        panel.run(CommandPanel.label("cancel"), this.getFeatureID(), warp.name()).build()
                );
        panel.send();
    }

    private void delete(CommandContext<CommandSender> context) {
        Warp warp = this.find(context);
        if (warp == null) return;
        UUID id = context.get("id");
        // 确认按钮绑定记录的 UUID, 原记录被删后重建同名 warp 不会被旧按钮删除.
        if (!warp.id().equals(id)) {
            this.handleFeedback(context, MessageConstants.COMMAND_WARP_UNKNOWN, Component.text(warp.name()));
            return;
        }
        this.feature.service()
                .delete(id)
                .thenAccept(deleted -> {
                    this.handleFeedback(
                            context,
                            deleted ? MessageConstants.COMMAND_DEL_WARP_SUCCESS : MessageConstants.COMMAND_WARP_UNKNOWN,
                            Component.text(warp.name())
                    );
                })
                .exceptionally(error -> {
                    this.plugin().logger().warn("Failed to delete warp " + warp.name(), error);
                    this.handleFeedback(context, MessageConstants.COMMAND_WARP_STORAGE_FAILED, Component.text(warp.name()));
                    return null;
                });
    }

    @Override
    public String getFeatureID() {
        return "edit-warp";
    }
}
