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
import net.momirealms.sparrow.plugin.command.panel.PanelText;
import net.momirealms.sparrow.plugin.command.panel.PanelTone;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
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
            if (warp != null) this.createPanel(context.sender(), warp).send(context.sender());
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

    private CommandPanel createPanel(CommandSender sender, Warp warp) {
        WorldLocation location = warp.location();
        PanelText creator = warp.creator() == null
                ? PanelText.of(Component.translatable("command.edit-warp.console"))
                : PanelText.of(warp.creator().toString().substring(0, 8) + "…" + warp.creator().toString().substring(32)).copy(warp.creator().toString());
        Component noPermission = Component.translatable("command.panel.no-permission");
        Component rename = Component.translatable("command.panel.rename");
        Component go = Component.translatable("command.panel.go");
        Component relocate = Component.translatable("command.panel.relocate");
        Component delete = Component.translatable("command.panel.delete");
        Component times = Component.translatable("command.panel.audit-times", PanelText.time(warp.createdAt()).getComponent(), PanelText.time(warp.updatedAt()).getComponent());
        return CommandPanel.card()
                .title(PanelText.of(Component.translatable("command.edit-warp.panel.title", Component.text(warp.name()))).copy(warp.id().toString()))
                .titleActions(
                        PanelButton.run(Component.translatable("command.panel.refresh"), "edit_warp", warp.name()),
                        PanelButton.run(Component.translatable("command.panel.list"), "warp_list", "1")
                )
                .actionColumn("primary", rename, go, relocate, delete)
                .field(
                        Component.translatable("command.panel.name"),
                        PanelText.of(warp.name()).copy(warp.name()),
                        PanelButton.suggest(rename, "edit_warp", warp.name() + " rename ")
                                .disabledIf(!sender.hasPermission(this.permission("rename")), noPermission)
                                .column("primary")
                )
                .field(Component.translatable("command.edit-warp.panel.creator"), creator.tone(PanelTone.INFO))
                .field(
                        Component.translatable("command.panel.place"),
                        PanelText.location(warp.server(), location),
                        PanelButton.run(go, "warp", warp.name())
                                .disabledIf(!this.feature.visible(sender, warp), noPermission)
                                .disabledIf(!(sender instanceof Player), MessageConstants.COMMAND_PLAYER_REQUIRED)
                                .tone(PanelTone.SUCCESS)
                                .column("primary")
                )
                .field(
                        Component.translatable("command.panel.coordinates"),
                        PanelText.coordinates(location.x(), location.y(), location.z()),
                        PanelButton.run(relocate, "edit_warp", warp.name() + " relocate")
                                .disabledIf(!sender.hasPermission(this.permission("relocate")), noPermission)
                                .disabledIf(!(sender instanceof Player), MessageConstants.COMMAND_PLAYER_REQUIRED)
                                .hint(Component.translatable("command.panel.relocate-hint"))
                                .column("primary")
                )
                .field(Component.translatable("command.panel.rotation"), PanelText.rotation(location.yaw(), location.pitch()))
                .field(Component.translatable("command.panel.time"), PanelText.of(times), PanelButton.run(delete, "del_warp", warp.name()).tone(PanelTone.DANGER).column("primary"))
                .build();
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