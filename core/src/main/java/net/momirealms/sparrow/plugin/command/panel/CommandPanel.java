package net.momirealms.sparrow.plugin.command.panel;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

public final class CommandPanel {
    final boolean list;
    final PanelText title;
    final @Nullable PanelText status;
    final @Nullable PanelText summary;
    final @Nullable PanelText hint;
    final List<PanelButton> titleActions;
    final List<ActionColumn> columns;
    final List<Row> rows;
    final List<PanelButton> footer;
    final @Nullable Pagination pagination;

    private CommandPanel(Builder builder) {
        this.list = builder.list;
        this.title = builder.title;
        this.status = builder.status;
        this.summary = builder.summary;
        this.hint = builder.hint;
        this.titleActions = List.copyOf(builder.titleActions);
        this.columns = List.copyOf(builder.columns);
        this.rows = List.copyOf(builder.rows);
        this.footer = List.copyOf(builder.footer);
        this.pagination = builder.pagination;
    }

    @NotNull
    public static Builder card() {
        return new Builder(false);
    }

    @NotNull
    public static Builder list() {
        return new Builder(true);
    }

    public void send(@NotNull CommandSender receiver) {
        PanelContext context = new PanelContext(receiver);
        Component message = new PanelLayout(this, context).render();
        context.manager.handleCommandFeedback(receiver, "command.panel.message", message);
    }

    sealed interface Row permits Field, Paragraph, Section, Entry, RecordEntry, NamedEntry, DatedEntry, IndexedEntry {
    }

    record Field(Component label, PanelText value, List<PanelButton> actions) implements Row {
    }

    record Paragraph(Component label, PanelText value) implements Row {
    }

    record Section(Component title) implements Row {
    }

    record Entry(PanelText value, List<PanelButton> actions) implements Row {
    }

    record RecordEntry(PanelText reference, PanelText status, PanelText subject, PanelText time, List<PanelButton> actions) implements Row {
    }

    record NamedEntry(PanelText name, PanelText value, boolean status, List<PanelButton> actions) implements Row {
    }

    record DatedEntry(PanelText value, PanelText badge, PanelText time, boolean leading, List<PanelButton> actions) implements Row {
    }

    record IndexedEntry(PanelText index, PanelText value, List<PanelButton> actions) implements Row {
    }

    record ActionColumn(String key, List<Component> captions) {
    }

    record Pagination(int page, int pages, String commandId, String previous, String next, String refresh) {
    }

    public static final class Builder {
        private final boolean list;
        private PanelText title = PanelText.of(Component.translatable("command.panel.title"));
        private @Nullable PanelText status;
        private @Nullable PanelText summary;
        private @Nullable PanelText hint;
        private final List<PanelButton> titleActions = new ArrayList<>();
        private final List<ActionColumn> columns = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();
        private final List<PanelButton> footer = new ArrayList<>();
        private @Nullable Pagination pagination;

        private Builder(boolean list) {
            this.list = list;
        }

        @NotNull
        public Builder title(@NotNull String title) {
            return this.title(PanelText.of(title));
        }

        @NotNull
        public Builder title(@NotNull Component title) {
            return this.title(PanelText.of(title));
        }

        @NotNull
        public Builder title(@NotNull PanelText title) {
            this.title = title;
            return this;
        }

        @NotNull
        public Builder status(@NotNull PanelText status) {
            this.status = status;
            return this;
        }

        @NotNull
        public Builder titleActions(@NotNull PanelButton... buttons) {
            this.titleActions.addAll(Arrays.asList(buttons));
            return this;
        }

        @NotNull
        public Builder actionColumn(@NotNull String key, @NotNull Component... captions) {
            this.columns.add(new ActionColumn(key, List.of(captions)));
            return this;
        }

        @NotNull
        public Builder summary(@NotNull PanelText summary) {
            this.summary = summary;
            return this;
        }

        @NotNull
        public Builder hint(@NotNull PanelText hint) {
            this.hint = hint;
            return this;
        }

        @NotNull
        public Builder field(@NotNull String label, @NotNull String value) {
            return this.field(Component.text(label), PanelText.of(value));
        }

        @NotNull
        public Builder field(@NotNull String label, @NotNull Component value) {
            return this.field(Component.text(label), PanelText.of(value));
        }

        @NotNull
        public Builder field(@NotNull Component label, @NotNull String value) {
            return this.field(label, PanelText.of(value));
        }

        @NotNull
        public Builder field(@NotNull Component label, @NotNull Component value) {
            return this.field(label, PanelText.of(value));
        }

        @NotNull
        public Builder field(@NotNull Component label, @NotNull PanelText value, @NotNull PanelButton... actions) {
            if (this.list) throw new IllegalStateException("List panels use entry rows");
            this.rows.add(new Field(label, value, List.of(actions)));
            return this;
        }

        @NotNull
        public Builder paragraph(@NotNull Component label, @NotNull PanelText value) {
            if (this.list) throw new IllegalStateException("Paragraphs belong to card panels");
            this.rows.add(new Paragraph(label, value));
            return this;
        }

        @NotNull
        public Builder copyField(@NotNull String label, @NotNull String value) {
            return this.copyField(Component.text(label), value);
        }

        @NotNull
        public Builder copyField(@NotNull Component label, @NotNull String value) {
            return this.field(label, PanelText.of(value).copy(value));
        }

        @NotNull
        public Builder section(@NotNull String title) {
            return this.section(Component.text(title));
        }

        @NotNull
        public Builder section(@NotNull Component title) {
            if (this.list) throw new IllegalStateException("Sections belong to card panels");
            this.rows.add(new Section(title));
            return this;
        }

        @NotNull
        public Builder entry(@NotNull PanelText value, @NotNull PanelButton... actions) {
            if (!this.list) throw new IllegalStateException("Entry rows belong to list panels");
            this.rows.add(new Entry(value, List.of(actions)));
            return this;
        }

        @NotNull
        public Builder entry(@NotNull PanelText reference, @NotNull PanelText status, @NotNull PanelText subject, @NotNull PanelText time, @NotNull PanelButton... actions) {
            if (!this.list) throw new IllegalStateException("Entry rows belong to list panels");
            this.rows.add(new RecordEntry(reference, status, subject, time, List.of(actions)));
            return this;
        }

        public int getPageCapacity() {
            return this.summary == null ? 8 : 7;
        }

        @NotNull
        public Builder locationEntry(@NotNull PanelText name, @NotNull PanelText location, @NotNull PanelButton... actions) {
            if (!this.list) throw new IllegalStateException("Location rows belong to list panels");
            this.rows.add(new NamedEntry(name, location, false, List.of(actions)));
            return this;
        }

        @NotNull
        public Builder statusEntry(@NotNull PanelText name, @NotNull PanelText status, @NotNull PanelButton... actions) {
            if (!this.list) throw new IllegalStateException("Status rows belong to list panels");
            this.rows.add(new NamedEntry(name, status, true, List.of(actions)));
            return this;
        }

        @NotNull
        public Builder footer(@NotNull PanelButton... buttons) {
            this.footer.addAll(Arrays.asList(buttons));
            return this;
        }

        @NotNull
        public Builder historyEntry(@NotNull PanelText value, @NotNull PanelText badge, @NotNull PanelText time, @NotNull PanelButton... actions) {
            if (!this.list) throw new IllegalStateException("History rows belong to list panels");
            this.rows.add(new DatedEntry(value, badge, time, false, List.of(actions)));
            return this;
        }

        @NotNull
        public Builder addressEntry(@NotNull PanelText value, @NotNull PanelText badge, @NotNull PanelText time, @NotNull PanelButton... actions) {
            if (!this.list) throw new IllegalStateException("Address rows belong to list panels");
            this.rows.add(new DatedEntry(value, badge, time, true, List.of(actions)));
            return this;
        }

        @NotNull
        public Builder indexedEntry(@NotNull PanelText index, @NotNull PanelText value, @NotNull PanelButton... actions) {
            if (!this.list) throw new IllegalStateException("Indexed rows belong to list panels");
            this.rows.add(new IndexedEntry(index, value, List.of(actions)));
            return this;
        }

        // 页码从 1 开始, 参数工厂在构建期间求值.
        @NotNull
        public Builder pagination(int page, int pages, @NotNull String commandId, @NotNull IntFunction<String> arguments) {
            this.pagination = new Pagination(page, pages, commandId, page > 1 ? arguments.apply(page - 1) : "", page < pages ? arguments.apply(page + 1) : "", arguments.apply(page));
            return this;
        }

        @NotNull
        public Builder pagination(@NotNull TextPage<?> page, @NotNull String commandId, @NotNull IntFunction<String> arguments) {
            return this.pagination(page.index() + 1, page.count(), commandId, arguments);
        }

        @NotNull
        public CommandPanel build() {
            if (this.list && this.rows.size() > this.getPageCapacity()) throw new IllegalStateException("Too many entries for one panel page");
            return new CommandPanel(this);
        }
    }
}