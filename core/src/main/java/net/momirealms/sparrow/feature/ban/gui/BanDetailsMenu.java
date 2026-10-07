package net.momirealms.sparrow.feature.ban.gui;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.FeatureState;
import net.momirealms.sparrow.feature.ban.BanFeature;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.ui.item.Item;
import net.momirealms.sparrow.ui.pane.NormalPane;
import net.momirealms.sparrow.ui.pane.Pane;
import net.momirealms.sparrow.ui.window.Window;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class BanDetailsMenu {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final BanFeature feature = this.plugin.featureManager().feature(BanFeature.ID, BanFeature.class);
    private final BanRecord record;

    public BanDetailsMenu(@NotNull BanRecord record) {
        this.record = record;
    }

    @NotNull
    public CompletableFuture<Window.OpenResult> open(@NotNull Player viewer) {
        return this.build(viewer).open();
    }

    @NotNull
    public Window build(@NotNull Player viewer) {
        SparrowPlayer player = this.plugin.playerManager().getPlayer(viewer);
        Window window = Window.builder(this.buildPane(player))
                .setTitle(player.render(Component.translatable("ban.gui.details-title", Component.text(BanRecord.ID_PREFIX + this.record.id()))))
                .setBackOnPlayerClose(true)
                .build(viewer);
        window.bind(this.feature.state(), current -> {
            if (this.feature.state().get() != FeatureState.ENABLED) {
                current.close();
            }
        });
        return window;
    }

    @NotNull
    private NormalPane buildPane(@NotNull SparrowPlayer viewer) {
        return Pane.builder("####I####", "#P##U##C#", "####B####")
                .addIngredient('#', BanMenuItems.FILLER)
                .addIngredient('I', Item.simple(BanMenuItems.icon(
                        viewer,
                        Material.BOOK,
                        Component.text(this.record.display()),
                        BanMenuItems.details(this.record, System.currentTimeMillis())
                )))
                .addIngredient('P', this.buildPlayerButton(viewer))
                .addIngredient('U', this.buildUnbanButton(viewer))
                .addIngredient('C', Item.builder()
                        .setItemProviderConstant(BanMenuItems.icon(
                                viewer,
                                Material.WRITABLE_BOOK,
                                Component.translatable("ban.gui.send-chat"),
                                List.of()
                        ))
                        .addClickHandler(click -> {
                            CommandPanel panel = new CommandPanel(this.plugin.commandManager(), click.player());
                            List<Component> details = BanMenuItems.details(this.record, System.currentTimeMillis());
                            int size = details.size();
                            for (int i = 0; i < size; i++) {
                                panel.line(details.get(i));
                            }
                            panel.send();
                            click.window().close();
                        })
                        .build())
                .addIngredient('B', Item.builder()
                        .setItemProviderConstant(BanMenuItems.icon(viewer, Material.ARROW, Component.translatable("ban.gui.back"), List.of()))
                        .addClickHandler(click -> click.window().backOrClose())
                        .build())
                .build();
    }

    @NotNull
    private Item buildPlayerButton(@NotNull SparrowPlayer viewer) {
        boolean available = this.record.player() != null && BanMenuItems.allowed(viewer.platformPlayer(), "player-info");
        return Item.builder()
                .setItemProviderConstant(BanMenuItems.icon(
                        viewer,
                        available ? Material.PLAYER_HEAD : Material.GRAY_DYE,
                        Component.translatable("ban.gui.player-info"),
                        available ? List.of() : List.of(Component.translatable("command.panel.unavailable"))
                ))
                .addClickGuard((item, click) -> this.record.player() != null && BanMenuItems.allowed(click.player(), "player-info"))
                .addClickHandler(click -> {
                    click.window().close();
                    BanMenuItems.executeCommand(click.player(), "player-info", this.record.player().toString());
                })
                .build();
    }

    @NotNull
    private Item buildUnbanButton(@NotNull SparrowPlayer viewer) {
        boolean available = this.record.active(System.currentTimeMillis()) && BanMenuItems.allowed(viewer.platformPlayer(), "unban");
        return Item.builder()
                .setItemProviderConstant(BanMenuItems.icon(
                        viewer,
                        available ? Material.LIME_DYE : Material.GRAY_DYE,
                        Component.translatable("ban.gui.unban"),
                        List.of(Component.translatable(available ? "ban.gui.unban-hint" : "ban.gui.unban-unavailable"))
                ))
                .addClickGuard((item, click) -> this.feature.enabled()
                        && this.record.active(System.currentTimeMillis()) && BanMenuItems.allowed(click.player(), "unban"))
                .addClickHandler(click -> click.window().navigate(new BanUnbanMenu(this.record).build(click.player())))
                .build();
    }
}
