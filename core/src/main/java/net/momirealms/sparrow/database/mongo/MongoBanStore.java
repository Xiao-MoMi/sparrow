package net.momirealms.sparrow.database.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import net.momirealms.sparrow.database.BanStore;
import net.momirealms.sparrow.feature.ban.BanQuery;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanResult;
import net.momirealms.sparrow.feature.ban.BanTarget;
import net.momirealms.sparrow.player.PlayerIdentity;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.IpRange;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.regex.Pattern;

// 封禁文档以处罚 ID 为 _id, player 与 ip_start/ip_end 至少有一组, IPv4 存为 Long.
@ApiStatus.Internal
final class MongoBanStore implements BanStore {
    private static final String SCHEMA_ID = "ban_schema";
    private static final Map<String, List<IndexReconciler.IndexDeclaration>> INDEXES = Map.of(
            "bans",
            List.of(
                    new IndexReconciler.IndexDeclaration(new Document("player", 1).append("revoked_at", 1), false, "bans_player"),
                    new IndexReconciler.IndexDeclaration(new Document("ip_start", 1).append("ip_end", 1), false, "bans_ip"),
                    new IndexReconciler.IndexDeclaration(new Document("created_at", -1).append("_id", -1), false, "bans_created")
            )
    );

    private static final String BAN_PLAYER = "player";
    private static final String BAN_PLAYER_NAME = "player_name";
    private static final String BAN_IP_START = "ip_start";
    private static final String BAN_IP_END = "ip_end";
    private static final String BAN_REASON = "reason";
    private static final String BAN_OPERATOR_NAME = "operator_name";
    private static final String BAN_SERVER = "server";
    private static final String BAN_CREATED_AT = "created_at";
    private static final String BAN_EXPIRES_AT = "expires_at";
    private static final String BAN_REVOKED_AT = "revoked_at";
    private static final String BAN_REVOKED_BY = "revoked_by";

    private final Supplier<MongoDatabase> database;
    private final Executor executor;
    private final PluginLogger logger;
    private final String prefix;
    private volatile MongoCollection<Document> bans; // 第一次使用时准备好索引后赋值

    MongoBanStore(@NotNull Supplier<MongoDatabase> database, @NotNull Executor executor, @NotNull PluginLogger logger, @NotNull String prefix) {
        this.database = database;
        this.executor = executor;
        this.logger = logger;
        this.prefix = prefix;
    }

    @Override
    @NotNull
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(this::bans, this.executor);
    }

    // 第一次使用时在数据库线程上准备索引, 失败后下一次使用会重新尝试
    private MongoCollection<Document> bans() {
        MongoCollection<Document> prepared = this.bans;
        if (prepared != null) {
            return prepared;
        }
        synchronized (this) {
            if (this.bans == null) {
                MongoDatabase database = this.database.get();
                IndexReconciler.reconcile(this.logger, database, this.prefix, SCHEMA_ID, DependencyVersions.MONGODB_BAN_INDEX_VERSION, INDEXES);
                this.bans = database.getCollection(this.prefix + "bans");
            }
            return this.bans;
        }
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<BanRecord>> findActiveBan(@NotNull UUID player, long ip, long now) {
        Bson filter = Filters.and(
                active(now),
                Filters.or(Filters.eq(BAN_PLAYER, player), Filters.and(Filters.lte(BAN_IP_START, ip), Filters.gte(BAN_IP_END, ip)))
        );
        // 命中的封禁很少, 取回后按封禁该账号的记录、永久封禁、到期时间排序
        Comparator<BanRecord> priority = Comparator.comparingInt((BanRecord record) -> player.equals(record.player()) ? 0 : 1)
                .thenComparingInt(record -> record.permanent() ? 0 : 1)
                .thenComparing(Comparator.comparingLong(BanRecord::expiresAt).reversed());
        return CompletableFuture.supplyAsync(
                () -> this.bans().find(filter).map(MongoBanStore::readBan).into(new ArrayList<>()).stream().min(priority),
                this.executor
        );
    }

    // 撤销与写入分两步执行, 不要求副本集. 中途失败时旧封禁已撤销而新封禁未写入
    @NotNull
    @Override
    public CompletableFuture<BanResult> saveBan(@NotNull BanRecord record, boolean force) {
        Document document = new Document("_id", record.id())
                .append(BAN_REASON, record.reason())
                .append(BAN_OPERATOR_NAME, record.operatorName())
                .append(BAN_SERVER, record.server())
                .append(BAN_CREATED_AT, record.createdAt())
                .append(BAN_EXPIRES_AT, record.expiresAt())
                .append(BAN_REVOKED_AT, 0L);
        if (record.player() != null) {
            document.append(BAN_PLAYER, record.player()).append(BAN_PLAYER_NAME, record.playerName());
        }
        IpRange ip = record.ip();
        if (ip != null) {
            document.append(BAN_IP_START, ip.start()).append(BAN_IP_END, ip.end());
        }
        // 带玩家的记录覆盖该玩家的旧封禁, 纯 IP 记录覆盖同一段的纯 IP 封禁
        Bson sameTarget = record.player() != null
                ? Filters.eq(BAN_PLAYER, record.player())
                : Filters.and(Filters.exists(BAN_PLAYER, false), Filters.eq(BAN_IP_START, ip.start()), Filters.eq(BAN_IP_END, ip.end()));
        Bson revoke = Updates.combine(Updates.set(BAN_REVOKED_AT, record.createdAt()), Updates.set(BAN_REVOKED_BY, record.operatorName()));
        return CompletableFuture.supplyAsync(() -> {
            MongoCollection<Document> bans = this.bans();
            List<BanRecord> active = bans.find(Filters.and(active(record.createdAt()), sameTarget))
                    .map(MongoBanStore::readBan)
                    .into(new ArrayList<>());
            BanResult result = BanResult.evaluate(record, active, force);
            if (result.status() == BanResult.Status.REPLACEMENT_REJECTED) {
                return result;
            }
            if (!active.isEmpty()) {
                int size = active.size();
                List<String> ids = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    ids.add(active.get(i).id());
                }
                bans.updateMany(Filters.in("_id", ids), revoke);
            }
            bans.insertOne(document);
            return result;
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<List<BanRecord>> revokeBans(@NotNull BanTarget target, long now, @NotNull String revokedBy) {
        Bson match = switch (target) {
            case BanTarget.PlayerTarget(PlayerIdentity player) -> Filters.eq(BAN_PLAYER, player.uuid());
            case BanTarget.IpTarget ip -> Filters.and(
                    Filters.exists(BAN_PLAYER, false),
                    Filters.eq(BAN_IP_START, ip.range().start()),
                    Filters.eq(BAN_IP_END, ip.range().end())
            );
            case BanTarget.IdTarget id -> Filters.eq("_id", id.id());
        };
        Bson update = Updates.combine(Updates.set(BAN_REVOKED_AT, now), Updates.set(BAN_REVOKED_BY, revokedBy));
        return CompletableFuture.supplyAsync(() -> {
            MongoCollection<Document> bans = this.bans();
            List<BanRecord> revoked = bans.find(Filters.and(active(now), match)).map(MongoBanStore::readBan).into(new ArrayList<>());
            if (revoked.isEmpty()) {
                return revoked;
            }
            int size = revoked.size();
            List<String> ids = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ids.add(revoked.get(i).id());
            }
            bans.updateMany(Filters.in("_id", ids), update);
            return revoked;
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Long> countBans(@NotNull BanQuery query) {
        Bson filter = filter(query);
        return CompletableFuture.supplyAsync(() -> this.bans().countDocuments(filter), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<List<BanRecord>> listBans(@NotNull BanQuery query, int offset, int limit) {
        Bson filter = filter(query);
        return CompletableFuture.supplyAsync(() -> this.bans()
                .find(filter)
                .sort(Sorts.descending(BAN_CREATED_AT, "_id"))
                .skip(offset)
                .limit(limit)
                .map(MongoBanStore::readBan)
                .into(new ArrayList<>()), this.executor);
    }

    // IP 目标匹配完整覆盖它的 IP 段, 包括账号加 IP 的封禁
    private static Bson filter(BanQuery query) {
        List<Bson> conditions = new ArrayList<>(4);
        BanTarget target = query.target();
        if (target != null) {
            conditions.add(switch (target) {
                case BanTarget.PlayerTarget(PlayerIdentity player) -> Filters.eq(BAN_PLAYER, player.uuid());
                case BanTarget.IpTarget ip -> Filters.and(Filters.lte(BAN_IP_START, ip.range().start()), Filters.gte(BAN_IP_END, ip.range().end()));
                case BanTarget.IdTarget id -> Filters.eq("_id", id.id());
            });
        }
        if (query.operator() != null) {
            conditions.add(Filters.regex(BAN_OPERATOR_NAME, "^" + Pattern.quote(query.operator()) + "$", "i"));
        }
        if (query.since() > 0) {
            conditions.add(Filters.gte(BAN_CREATED_AT, query.since()));
        }
        if (query.activeOnly()) {
            conditions.add(active(query.now()));
        }
        return conditions.isEmpty() ? new Document() : Filters.and(conditions);
    }

    private static Bson active(long now) {
        return Filters.and(Filters.eq(BAN_REVOKED_AT, 0L), Filters.or(Filters.eq(BAN_EXPIRES_AT, 0L), Filters.gt(BAN_EXPIRES_AT, now)));
    }

    private static BanRecord readBan(Document document) {
        Long start = document.getLong(BAN_IP_START);
        IpRange ip = start == null ? null : new IpRange(start, document.getLong(BAN_IP_END));
        return new BanRecord(
                document.getString("_id"),
                document.get(BAN_PLAYER, UUID.class),
                document.getString(BAN_PLAYER_NAME),
                ip,
                document.getString(BAN_REASON),
                document.getString(BAN_OPERATOR_NAME),
                document.getString(BAN_SERVER),
                document.getLong(BAN_CREATED_AT),
                document.getLong(BAN_EXPIRES_AT),
                document.getLong(BAN_REVOKED_AT),
                document.getString(BAN_REVOKED_BY)
        );
    }
}