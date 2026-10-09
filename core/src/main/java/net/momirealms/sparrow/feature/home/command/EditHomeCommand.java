package net.momirealms.sparrow.feature.home.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.feature.home.HomeService;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.PlayerIdentity;
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
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public final class EditHomeCommand extends AbstractHomeCommand {

    public EditHomeCommand(HomeFeature feature) {
        super(feature);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> named = builder.required(
                "name",
                TokenParser.tokenParser(),
                (context, input) -> super.feature.suggest(context.sender(), input.peekString(), true, this.commandConfig().getPermission())
        );
        manager.command(named.handler(this::execute));
        manager.command(named.literal("rename")
                .permission(Permission.allOf(builder.commandPermission(), Permission.of(this.permission("rename"))))
                .required("new_name", StringParser.greedyStringParser())
                .handler(this::rename));
        manager.command(named.literal("relocate")
                .permission(Permission.allOf(builder.commandPermission(), Permission.of(this.permission("relocate"))))
                .senderType(Player.class)
                .handler(this::relocate));
        manager.command(
                named.literal("delete")
                        .permission(Permission.allOf(builder.commandPermission(), Permission.of(this.permission("delete"))))
                        .handler(this::delete)
        );
    }

    @NotNull
    private String permission(String operation) {
        return this.commandConfig().getPermission() + "." + operation;
    }

    private void execute(CommandContext<CommandSender> context) {
        this.withHome(context, (owner, home) -> {
            this.show(context.sender(), owner, home);
            return CompletableFuture.completedFuture(null);
        });
    }

    private void rename(CommandContext<CommandSender> context) {
        String name = context.get("new_name");
        this.withHome(context, (owner, home) -> super.feature.service()
                .rename(owner.uuid(), home.id(), name)
                .thenAccept(result ->
                this.saved(
                        context,
                        owner,
                        result,
                        name,
                        MessageConstants.COMMAND_EDIT_HOME_RENAMED,
                        Component.text(home.name()),
                        Component.text(name)
                )));
    }

    private void relocate(CommandContext<Player> context) {
        WorldLocation location = WorldLocation.from(context.sender().getLocation());
        this.withHome(
                context,
                (owner, home) -> super.feature.service()
                        .relocate(owner.uuid(), home.id(), ServerConfig.serverId(), location)
                        .thenAccept(result ->
                this.saved(context, owner, result, home.name(), MessageConstants.COMMAND_EDIT_HOME_RELOCATED, Component.text(home.name())))
        );
    }

    private void saved(
            CommandContext<? extends CommandSender> context,
            PlayerIdentity owner,
            HomeService.Result result,
            String name,
            TranslatableComponent success,
            Component... arguments
    ) {
        switch (result.status()) {
            case UPDATED -> {
                this.handleFeedback(context, success, arguments);
                this.show(context.sender(), owner, result.home());
            }
            case DUPLICATE_NAME -> this.handleFeedback(context, MessageConstants.COMMAND_EDIT_HOME_EXISTS, Component.text(name));
            case INVALID_NAME -> this.handleFeedback(
                    context,
                    MessageConstants.COMMAND_HOME_INVALID_NAME,
                    Component.text(name),
                    Component.empty(),
                    Component.text(Home.MAX_NAME_LENGTH)
            );
            case NOT_FOUND -> this.handleFeedback(context, MessageConstants.COMMAND_HOME_UNKNOWN, Component.text(context.<String>get("name")));
            case CREATED, LIMIT_REACHED -> throw new AssertionError();
        }
    }

    private void delete(CommandContext<CommandSender> context) {
        this.target(context.sender(), context.get("name"))
                .thenCompose(found -> {
                    if (found.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    Target target = found.get();
                    return super.feature.service()
                            .delete(target.owner().uuid(), target.name())
                            .thenAccept(deleted -> this.handleFeedback(
                                    context,
                                    deleted ? MessageConstants.COMMAND_DEL_HOME_SUCCESS : MessageConstants.COMMAND_HOME_UNKNOWN,
                                    Component.text(target.name()),
                                    Component.text(target.owner().name())
                            ));
                })
                .exceptionally(error -> {
                    this.failed(context.sender(), error); return null;
                });
    }

    private void withHome(CommandContext<? extends CommandSender> context, BiFunction<PlayerIdentity, Home, CompletableFuture<Void>> action) {
        this.target(context.sender(), context.get("name"))
                .thenCompose(found -> {
                    if (found.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    Target target = found.get();
                    return super.feature.service()
                            .find(target.owner().uuid(), target.name())
                            .thenCompose(home -> {
                                if (home.isEmpty()) {
                                    this.handleFeedback(
                                            context,
                                            MessageConstants.COMMAND_HOME_UNKNOWN,
                                            Component.text(context.<String>get("name"))
                                    );
                                    return CompletableFuture.completedFuture(null);
                                }
                                return action.apply(target.owner(), home.get());
                            });
                })
                .exceptionally(error -> {
                    this.failed(context.sender(), error); return null;
                });
    }

    private void show(CommandSender sender, PlayerIdentity owner, Home home) {
        boolean self = sender instanceof Player player && player.getUniqueId().equals(owner.uuid());
        String target = self ? home.name() : owner.name() + "." + home.name();
        WorldLocation location = home.location();
        CommandPanel panel = new CommandPanel(this.commandManager(), sender);
        panel.line(Component.translatable(
                "command.edit-home.info",
                Component.text(home.name()),
                Component.text(owner.name()),
                Component.text(home.owner().toString()),
                Component.text(home.id().toString()),
                Component.text(home.server()),
                Component.text(location.world()),
                Component.text(location.x()),
                Component.text(location.y()),
                Component.text(location.z()),
                Component.text(location.yaw()),
                Component.text(location.pitch()),
                Component.text(DateTimeUtils.fullTime(home.createdAt())),
                Component.text(DateTimeUtils.fullTime(home.updatedAt()))
        ));
        PanelButton travel = panel.suggest(CommandPanel.label("teleport"), "home", target).playersOnly();
        if (!self) {
            travel.permission(super.feature.permission("home") + ".other");
        }
        List<PanelButton> buttons = List.of(
                travel,
                panel.suggest(CommandPanel.label("rename"), this.getFeatureID(), target + " rename ").permission(this.permission("rename")),
                panel.suggest(CommandPanel.label("relocate"), this.getFeatureID(), target + " relocate")
                        .permission(this.permission("relocate"))
                        .playersOnly()
                        .style(PanelButton.Style.POSITIVE),
                panel.suggest(CommandPanel.label("delete"), this.getFeatureID(), target + " delete")
                        .permission(this.permission("delete"))
                        .style(PanelButton.Style.DANGER),
                this.backToList(panel, owner, self)
        );
        panel.actions(buttons.stream().filter(PanelButton::available).map(PanelButton::build).toArray(Component[]::new)).send();
    }

    private PanelButton backToList(CommandPanel panel, PlayerIdentity owner, boolean self) {
        PanelButton back = panel.suggest(CommandPanel.label("home_list"), "home-list", self ? "" : "other " + owner.name());
        if (!self) {
            back.permission(super.feature.permission("home-list") + ".other");
        }
        return back;
    }

    @Override
    public String getFeatureID() {
        return "edit-home";
    }
}