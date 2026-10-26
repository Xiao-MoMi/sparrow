package net.momirealms.sparrow.feature.home;

import net.momirealms.sparrow.data.access.MultiWriterData;
import net.momirealms.sparrow.data.access.MultiWriterEntries;
import net.momirealms.sparrow.data.definition.MultiWriterDefinition;
import net.momirealms.sparrow.data.storage.DataCodec;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.GsonHelper;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.UUIDUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class HomeData {
    public static final SparrowKey ID = SparrowKey.sparrow("home");
    private static final DataCodec<UUID> ENTRY_CODEC = DataCodec.of(UUIDUtils::toBytes, UUIDUtils::fromBytes);
    private static final DataCodec<Home> VALUE_CODEC = DataCodec.of(
            home -> GsonHelper.get().toJson(home).getBytes(StandardCharsets.UTF_8),
            bytes -> GsonHelper.get().fromJson(new String(bytes, StandardCharsets.UTF_8), Home.class)
    );

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final MultiWriterData<UUID, Home> data;

    public HomeData() {
        this.data = this.plugin.dataAccess().register(MultiWriterDefinition.of(ID, ENTRY_CODEC, VALUE_CODEC, this.plugin.dataStorage().homeStore()));
    }

    @NotNull
    public MultiWriterEntries<UUID, Home> entries(@NotNull UUID owner) {
        return this.data.entries(owner);
    }

    @NotNull
    public CompletableFuture<Void> saveDirty() {
        return this.data.saveDirty();
    }

    @NotNull
    public CompletableFuture<Void> closeAsync() {
        return this.data.saveDirty().thenCompose(ignored -> this.plugin.dataAccess().unregister(ID));
    }
}