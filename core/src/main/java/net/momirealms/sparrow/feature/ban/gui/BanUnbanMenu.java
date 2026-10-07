package net.momirealms.sparrow.feature.ban.gui;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.FeatureState;
import net.momirealms.sparrow.feature.ban.BanFeature;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanTarget;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.ui.item.Item;
import net.momirealms.sparrow.ui.pane.NormalPane;
import net.momirealms.sparrow.ui.pane.Pane;
import net.momirealms.sparrow.ui.state.MutableSignal;
import net.momirealms.sparrow.ui.state.Signal;
import net.momirealms.sparrow.ui.window.Window;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class BanUnbanMenu {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final BanFeature feature = this.plugin.featureManager().feature(BanFeature.ID, BanFeature.class);
    private final BanRecord record;
    private final MutableSignal<Boolean> busy = Signal.of(false);
    private final MutableSignal<Boolean> silent = Signal.of(false);

    public BanUnbanMenu(@NotNull BanRecord record) {
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
                .setTitle(player.render(Component.translatable("ban.gui.confirm-title")))
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
        return Pane.builder("#########", "##Y#I#N##", "####S####")
                .addIngredient('#', BanMenuItems.FILLER)
                .addIngredient('I', Item.simple(BanMenuItems.icon(
                        viewer,
                        Material.BOOK,
                        Component.text(this.record.display()),
                        BanMenuItems.details(this.record, System.currentTimeMillis())
                )))
                .addIngredient('Y', this.buildConfirmButton(viewer))
                .addIngredient('N', Item.builder()
                        .setItemProviderConstant(BanMenuItems.icon(viewer, Material.RED_DYE, Component.translatable("ban.gui.cancel"), List.of()))
                        .addClickHandler(click -> click.window().backOrClose())
                        .build())
                .addIngredient('S', Item.builder()
                        .dependsOn(this.silent)
                        .setItemProvider(context -> BanMenuItems.icon(
                                viewer,
                                this.silent.get() ? Material.GRAY_DYE : Material.BELL,
                                Component.translatable(this.silent.get() ? "ban.gui.silent-on" : "ban.gui.silent-off"),
                                List.of(Component.translatable("ban.gui.silent-hint"))
                        ))
                        .addClickGuard((item, click) -> !this.busy.get())
                        .addClickHandler(click -> this.silent.set(!this.silent.get()))
                        .build())
                .build();
    }

    @NotNull
    private Item buildConfirmButton(@NotNull SparrowPlayer viewer) {
        return Item.builder()
                .dependsOn(this.busy)
                .setItemProvider(context -> BanMenuItems.icon(
                        viewer,
                        this.busy.get() ? Material.GRAY_DYE : Material.LIME_DYE,
                        Component.translatable(this.busy.get() ? "ban.gui.working" : "ban.gui.confirm-unban"),
                        List.of(Component.translatable("ban.gui.unban-hint"))
                ))
                .addClickGuard((item, click) -> !this.busy.get() && this.feature.enabled()
                        && BanMenuItems.allowed(click.player(), "unban") && this.record.active(System.currentTimeMillis()))
                .addClickHandler(click -> {
                    this.busy.set(true);
                    boolean silent = this.silent.get();
                    BanTarget.IdTarget target = new BanTarget.IdTarget(this.record.id());
                    this.feature.unban(target, click.player().getName(), silent)
                            .thenAccept(revoked -> {
                                if (revoked.isEmpty()) {
                                    this.plugin.commandManager().handleCommandFeedback(
                                            click.player(),
                                            MessageConstants.COMMAND_UNBAN_NONE,
                                            Component.text(this.record.display())
                                    );
                                } else if (!silent) {
                                    this.plugin.commandManager().handleCommandFeedback(
                                            click.player(),
                                            MessageConstants.COMMAND_UNBAN_SUCCESS,
                                            Component.text(this.record.display()),
                                            Component.text(revoked.size())
                                    );
                                }
                                click.window().close();
                            })
                            .exceptionally(error -> {
                                this.busy.set(false);
                                this.plugin.logger().warn("Failed to unban " + target.display() + " from the menu", error);
                                this.plugin.commandManager().handleCommandFeedback(click.player(), MessageConstants.COMMAND_DATABASE_FAILED);
                                return null;
                            });
                })
                .build();
    }
}