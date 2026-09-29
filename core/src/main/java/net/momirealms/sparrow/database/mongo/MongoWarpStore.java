package net.momirealms.sparrow.database.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import net.momirealms.sparrow.database.WarpStore;
import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.WorldLocation;
import org.bson.Document;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

// warp 文档以 UUID 为 _id, name_key 上有唯一索引. 控制台创建的 warp 没有 creator 字段
@ApiStatus.Internal
final class MongoWarpStore implements WarpStore {
    private static final String SCHEMA_ID = "warp_schema";
    private static final Map<String, List<IndexReconciler.IndexDeclaration>> INDEXES = Map.of(
            "warps", List.of(
                    new IndexReconciler.IndexDeclaration(new Document("name_key", 1), true, "warps_name"),
                    new IndexReconciler.IndexDeclaration(new Document("server", 1).append("world", 1), false, "warps_location"))
    );

    private static final String WARP_NAME_KEY = "name_key";
    private static final String WARP_NAME = "name";
    private static final String WARP_DESCRIPTION = "description";
    private static final String WARP_SERVER = "server";
    private static final String WARP_WORLD = "world";
    private static final String WARP_X = "x";
    private static final String WARP_Y = "y";
    private static final String WARP_Z = "z";
    private static final String WARP_YAW = "yaw";
    private static final String WARP_PITCH = "pitch";
    private static final String WARP_CREATOR = "creator";
    private static final String WARP_CREATED_AT = "created_at";
    private static final String WARP_UPDATED_AT = "updated_at";

    private final Supplier<MongoDatabase> database;
    private final Executor executor;
    private final PluginLogger logger;
    private final String prefix;
    private volatile MongoCollection<Document> warps; // 第一次使用时准备好索引后赋值

    MongoWarpStore(@NotNull Supplier<MongoDatabase> database, @NotNull Executor executor, @NotNull PluginLogger logger, @NotNull String prefix) {
        this.database = database;
        this.executor = executor;
        this.logger = logger;
        this.prefix = prefix;
    }

    @Override
    @NotNull
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(this::warps, this.executor);
    }

    // 第一次使用时在数据库线程上准备索引, 失败后下一次使用会重新尝试
    private MongoCollection<Document> warps() {
        MongoCollection<Document> prepared = this.warps;
        if (prepared != null) return prepared;
        synchronized (this) {
            if (this.warps == null) {
                MongoDatabase database = this.database.get();
                IndexReconciler.reconcile(this.logger, database, this.prefix, SCHEMA_ID, DependencyVersions.MONGODB_WARP_INDEX_VERSION, INDEXES);
                this.warps = database.getCollection(this.prefix + "warps");
            }
            return this.warps;
        }
    }

    @Override
    @NotNull
    public CompletableFuture<List<Warp>> loadAll() {
        return CompletableFuture.supplyAsync(() -> this.warps().find().sort(Sorts.ascending(WARP_NAME_KEY)).map(MongoWarpStore::readWarp).into(new ArrayList<>()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<Warp>> find(@NotNull UUID id) {
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(this.warps().find(Filters.eq("_id", id)).map(MongoWarpStore::readWarp).first()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<Warp>> findByName(@NotNull String nameIgnoreCase) {
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(this.warps().find(Filters.eq(WARP_NAME_KEY, Warp.key(nameIgnoreCase))).map(MongoWarpStore::readWarp).first()), this.executor);
    }

    // 检查与写入分两步执行, 不要求副本集. 两者之间被其他服务器抢先写入同名时由唯一索引拒绝
    @Override
    @NotNull
    public CompletableFuture<Boolean> save(@NotNull Warp warp) {
        WorldLocation location = warp.location();
        Document document = new Document("_id", warp.id())
                .append(WARP_NAME_KEY, warp.key())
                .append(WARP_NAME, warp.name())
                .append(WARP_DESCRIPTION, warp.description())
                .append(WARP_SERVER, warp.server())
                .append(WARP_WORLD, location.world())
                .append(WARP_X, location.x())
                .append(WARP_Y, location.y())
                .append(WARP_Z, location.z())
                .append(WARP_YAW, (double) location.yaw())
                .append(WARP_PITCH, (double) location.pitch())
                .append(WARP_CREATED_AT, warp.createdAt())
                .append(WARP_UPDATED_AT, warp.updatedAt());
        if (warp.creator() != null) {
            document.append(WARP_CREATOR, warp.creator());
        }
        return CompletableFuture.supplyAsync(() -> {
            MongoCollection<Document> warps = this.warps();
            if (warps.countDocuments(Filters.and(Filters.eq(WARP_NAME_KEY, warp.key()), Filters.ne("_id", warp.id())), new CountOptions().limit(1)) > 0) return false;
            warps.replaceOne(Filters.eq("_id", warp.id()), document, new ReplaceOptions().upsert(true));
            return true;
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> delete(@NotNull UUID id) {
        return CompletableFuture.supplyAsync(() -> this.warps().deleteOne(Filters.eq("_id", id)).getDeletedCount() > 0, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Integer> deleteByWorld(@NotNull String server, @NotNull String world) {
        return CompletableFuture.supplyAsync(() -> (int) this.warps().deleteMany(Filters.and(Filters.eq(WARP_SERVER, server), Filters.eq(WARP_WORLD, world))).getDeletedCount(), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Integer> deleteByServer(@NotNull String server) {
        return CompletableFuture.supplyAsync(() -> (int) this.warps().deleteMany(Filters.eq(WARP_SERVER, server)).getDeletedCount(), this.executor);
    }

    private static Warp readWarp(Document document) {
        WorldLocation location = new WorldLocation(document.getString(WARP_WORLD), document.getDouble(WARP_X), document.getDouble(WARP_Y), document.getDouble(WARP_Z),
                document.getDouble(WARP_YAW).floatValue(), document.getDouble(WARP_PITCH).floatValue());
        return new Warp(document.get("_id", UUID.class), document.getString(WARP_NAME), document.getString(WARP_DESCRIPTION), document.getString(WARP_SERVER),
                location, document.get(WARP_CREATOR, UUID.class), document.getLong(WARP_CREATED_AT), document.getLong(WARP_UPDATED_AT));
    }
}
