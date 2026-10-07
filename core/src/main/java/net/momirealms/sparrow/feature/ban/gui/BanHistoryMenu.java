package net.momirealms.sparrow.feature.ban.gui;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.FeatureState;
import net.momirealms.sparrow.feature.ban.BanFeature;
import net.momirealms.sparrow.feature.ban.BanQuery;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.panel.TextPage;
import net.momirealms.sparrow.ui.item.Item;
import net.momirealms.sparrow.ui.pane.Element;
import net.momirealms.sparrow.ui.pane.NormalPane;
import net.momirealms.sparrow.ui.pane.Pane;
import net.momirealms.sparrow.ui.state.MutableSignal;
import net.momirealms.sparrow.ui.state.Signal;
import net.momirealms.sparrow.ui.window.Window;
import net.momirealms.sparrow.util.DateTimeUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class BanHistoryMenu {
    private static final int PAGE_SIZE = 28;

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final BanFeature feature = this.plugin.featureManager().feature(BanFeature.ID, BanFeature.class);
    private final BanQuery query;
    private final MutableSignal<TextPage<Item>> page;
    private final MutableSignal<Boolean> loading = Signal.of(false);
    private final MutableSignal<Boolean> activeOnly;

    public BanHistoryMenu(@NotNull BanQuery query, int page) {
        this.query = query;
        this.page = Signal.of(new TextPage<>(page, PAGE_SIZE, 0, List.of()));
        this.activeOnly = Signal.of(query.activeOnly());
    }

    @NotNull
    public CompletableFuture<Window.OpenResult> open(@NotNull Player viewer) {
        SparrowPlayer player = this.plugin.playerManager().getPlayer(viewer);
        if (player == null) {
            return CompletableFuture.completedFuture(Window.OpenResult.VIEWER_UNAVAILABLE);
        }
        Component title = this.query.target() == null
                ? MessageConstants.COMMAND_BAN_HISTORY_TITLE_ALL
                : MessageConstants.COMMAND_BAN_HISTORY_TITLE.arguments(Component.text(this.query.target().display()));
        Window window = Window.builder(this.buildPane(player))
                .setTitle(player.render(title))
                .addOpenHandler(opened -> {
                    if (!this.feature.enabled() || !BanMenuItems.allowed(viewer, "ban-history")) {
                        opened.close();
                        return;
                    }
                    this.load(this.page.get().index(), player);
                })
                .build(viewer);
        window.bind(this.feature.state(), current -> {
            if (this.feature.state().get() != FeatureState.ENABLED) {
                current.close();
            }
        });
        return window.open();
    }

    @NotNull
    private NormalPane buildPane(@NotNull SparrowPlayer viewer) {
        return Pane.builder("####I####", "#RRRRRRR#", "#RRRRRRR#", "#RRRRRRR#", "#RRRRRRR#", "P#AF#N#X#")
                .addIngredient('#', BanMenuItems.FILLER)
                .addIngredient('R', this.page.map(TextPage::content), Element::item)
                .addIngredient('I', this.buildSummary(viewer))
                .addIngredient('P', this.buildPageButton(viewer, -1))
                .addIngredient('N', this.buildPageButton(viewer, 1))
                .addIngredient('A', Item.builder()
                        .dependsOn(this.activeOnly, this.loading)
                        .setItemProvider(context -> BanMenuItems.icon(
                                viewer,
                                this.activeOnly.get() ? Material.REDSTONE_TORCH : Material.LEVER,
                                Component.translatable(this.activeOnly.get() ? "ban.gui.active-only" : "ban.gui.all-records"),
                                List.of(Component.translatable("ban.gui.toggle-active"))
                        ))
                        .addClickGuard((item, click) -> !this.loading.get())
                        .addClickHandler(click -> {
                            this.activeOnly.set(!this.activeOnly.get());
                            this.load(0, viewer);
                        })
                        .build())
                .addIngredient('F', Item.builder()
                        .setItemProviderConstant(BanMenuItems.icon(viewer, Material.SUNFLOWER, Component.translatable("ban.gui.refresh"), List.of()))
                        .addClickGuard((item, click) -> !this.loading.get())
                        .addClickHandler(click -> this.load(this.page.get().index(), viewer))
                        .build())
                .addIngredient('X', Item.builder()
                        .setItemProviderConstant(BanMenuItems.icon(viewer, Material.BARRIER, Component.translatable("ban.gui.close"), List.of()))
                        .addClickHandler(click -> click.window().close())
                        .build())
                .build();
    }

    @NotNull
    private Item buildSummary(@NotNull SparrowPlayer viewer) {
        return Item.builder()
                .dependsOn(this.page, this.loading)
                .setItemProvider(context -> {
                    TextPage<Item> current = this.page.get();
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.translatable("ban.gui.total", Component.text(current.total())));
                    if (this.query.operator() != null) {
                        lore.add(Component.translatable("ban.gui.operator", Component.text(this.query.operator())));
                    }
                    if (this.query.since() != 0) {
                        lore.add(Component.translatable("ban.gui.since", Component.text(DateTimeUtils.fullTime(this.query.since()))));
                    }
                    if (!this.loading.get() && current.total() == 0) {
                        lore.add(Component.translatable("command.panel.empty"));
                    }
                    Component name = this.loading.get()
                            ? Component.translatable("ban.gui.loading")
                            : Component.translatable("ban.gui.page", Component.text(current.index() + 1), Component.text(current.count()));
                    return BanMenuItems.icon(viewer, this.loading.get() ? Material.CLOCK : Material.BOOK, name, lore);
                })
                .build();
    }

    @NotNull
    private Item buildPageButton(@NotNull SparrowPlayer viewer, int step) {
        return Item.builder()
                .dependsOn(this.page, this.loading)
                .setItemProvider(context -> {
                    TextPage<Item> current = this.page.get();
                    boolean available = !this.loading.get() && (step < 0 ? current.hasPrevious() : current.hasNext());
                    return BanMenuItems.icon(
                            viewer,
                            available ? Material.ARROW : Material.GRAY_DYE,
                            Component.translatable(step < 0 ? "ban.gui.previous" : "ban.gui.next"),
                            List.of()
                    );
                })
                .addClickGuard((item, click) -> !this.loading.get() && (step < 0 ? this.page.get().hasPrevious() : this.page.get().hasNext()))
                .addClickHandler(click -> this.load(this.page.get().index() + step, viewer))
                .build();
    }

    private void load(int index, @NotNull SparrowPlayer viewer) {
        if (this.loading.get() || !this.feature.enabled() || !BanMenuItems.allowed(viewer.platformPlayer(), "ban-history")) {
            return;
        }
        this.loading.set(true);
        long now = System.currentTimeMillis();
        BanQuery current = new BanQuery(this.query.target(), this.query.operator(), this.query.since(), this.activeOnly.get(), now);
        TextPage.load(
                () -> this.feature.store().countBans(current),
                (offset, limit) -> this.feature.store().listBans(current, offset, limit),
                index,
                PAGE_SIZE
        )
                .thenAccept(loaded -> {
                    List<BanRecord> records = loaded.content();
                    List<Item> items = new ArrayList<>(records.size());
                    int size = records.size();
                    for (int i = 0; i < size; i++) {
                        BanRecord record = records.get(i);
                        List<Component> lore = BanMenuItems.details(record, now);
                        lore.add(Component.empty());
                        lore.add(Component.translatable("ban.gui.view-details"));
                        Material material = record.active(now) ? Material.RED_CONCRETE : Material.PAPER;
                        items.add(Item.builder()
                                .setItemProviderConstant(BanMenuItems.icon(viewer, material, Component.text(record.display()), lore))
                                .addClickGuard((item, click) -> this.feature.enabled() && BanMenuItems.allowed(click.player(), "ban-history"))
                                .addClickHandler(click -> click.window().navigate(new BanDetailsMenu(record).build(click.player())))
                                .build());
                    }
                    this.page.set(new TextPage<>(loaded.index(), loaded.size(), loaded.total(), items));
                    this.loading.set(false);
                })
                .exceptionally(error -> {
                    this.loading.set(false);
                    this.plugin.logger().warn("Failed to load the ban menu", error);
                    this.plugin.commandManager().handleCommandFeedback(viewer.platformPlayer(), MessageConstants.COMMAND_DATABASE_FAILED);
                    return null;
                });
    }
}