package net.momirealms.sparrow.player;

import net.momirealms.sparrow.data.access.EntryView;
import net.momirealms.sparrow.data.access.MultiWriterData;
import net.momirealms.sparrow.data.access.SingleWriterData;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

// 随玩家进出本服加载和卸载的数据. 单写取得写容器, 多写固定读副本, 加载结果统一成 Loaded
sealed interface PlayerDataBinding {

    Object data();

    CompletableFuture<Loaded> load(UUID player);

    CompletableFuture<Void> saveDirty();

    record Single<E, V>(SingleWriterData<UUID, E, V> data) implements PlayerDataBinding {
        @Override
        public CompletableFuture<Loaded> load(UUID player) {
            return this.data.load(player).thenApply(writer -> new Loaded(this.data, writer, writer::save, () -> this.data.release(player)));
        }

        @Override
        public CompletableFuture<Void> saveDirty() {
            return this.data.saveDirty();
        }
    }

    record Multi<E, V>(MultiWriterData<UUID, E, V> data) implements PlayerDataBinding {
        @Override
        public CompletableFuture<Loaded> load(UUID player) {
            return this.data.entries(player).hold().thenApply(hold -> new Loaded(this.data, hold, () -> this.data.entries(player).save(), () -> {
                hold.close();
                return CompletableFuture.completedFuture(null);
            }));
        }

        @Override
        public CompletableFuture<Void> saveDirty() {
            return this.data.saveDirty();
        }
    }

    record Loaded(Object data, EntryView<?, ?> view, Supplier<CompletableFuture<Void>> save, Supplier<CompletableFuture<Void>> release) {
    }
}