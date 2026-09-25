package net.momirealms.sparrow.database.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class IndexReconciler {
    private static final int SCHEMA_VERSION = DependencyVersions.MONGODB_INDEX_VERSION; // 业务集合的索引声明版本
    private static final String SCHEMA_DOCUMENT_ID = "schema";
    private static final String SCHEMA_FIELD_VERSION = "version";

    private static final List<IndexDeclaration> DATA_INDEXES = List.of(
            new IndexDeclaration(new Document("name", 1).append("updated_at", -1).append("_id", -1), false, "data_name_updated")
    );

    // 按当前版本准备业务索引
    static void reconcile(
            @NotNull PluginLogger logger,
            @NotNull MongoDatabase database,
            @NotNull String prefix
    ) {
        MongoCollection<Document> metaCollection = database.getCollection(prefix + "meta");
        // 新版本可能已经换过索引, 旧插件继续启动会把数据库改回自己的声明
        Document schema = metaCollection.find(new Document("_id", SCHEMA_DOCUMENT_ID)).first();
        int stored = schema != null && schema.get(SCHEMA_FIELD_VERSION) instanceof Number number ? number.intValue() : 0;
        if (stored > SCHEMA_VERSION) {
            logger.error(TranslationManager.console(LogConstants.STORAGE_SCHEMA_TOO_NEW, String.valueOf(stored), String.valueOf(SCHEMA_VERSION)));
            throw new IllegalStateException("database schema generation " + stored + " is newer than this plugin supports (" + SCHEMA_VERSION + ")");
        }
        reconcileIndexes(logger, database, database.getCollection(prefix + "data"), DATA_INDEXES);
        // 索引都准备好后再记版本, 中途失败不会留下错误的完成标记
        metaCollection.replaceOne(
                new Document("_id", SCHEMA_DOCUMENT_ID),
                new Document("_id", SCHEMA_DOCUMENT_ID).append(SCHEMA_FIELD_VERSION, SCHEMA_VERSION),
                new ReplaceOptions().upsert(true)
        );
    }

    // 每次启动都以当前声明为准, 旧版本留下或手工添加的索引会在这里清掉
    private static void reconcileIndexes(PluginLogger logger, MongoDatabase database, MongoCollection<Document> collection, List<IndexDeclaration> declarations) {
        // 创建索引和业务查询均继承集合的默认排序规则, 比较时使用相同的有效规则.
        Document collectionInfo = database.listCollections().filter(new Document("name", collection.getNamespace().getCollectionName())).first();
        Document collation = collectionInfo == null ? null : collectionInfo.get("options", new Document()).get("collation", Document.class);
        List<Document> existing = new ArrayList<>();
        collection.listIndexes().into(existing);
        // _id_ 由 MongoDB 管理, 不参与业务索引对账
        for (int i = 0; i < existing.size(); i++) {
            Document index = existing.get(i);
            String name = index.getString("name");
            if ("_id_".equals(name)) continue;
            boolean declared = false;
            for (int j = 0; j < declarations.size(); j++) {
                if (matches(index, declarations.get(j), collation)) {
                    declared = true;
                    break;
                }
            }
            if (!declared) {
                collection.dropIndex(name);
                logger.info(TranslationManager.console(LogConstants.STORAGE_STALE_INDEX_DROPPED, name));
            }
        }
        // 当前版本缺哪条索引就补哪条
        for (int i = 0; i < declarations.size(); i++) {
            IndexDeclaration declaration = declarations.get(i);
            boolean present = false;
            for (int j = 0; j < existing.size(); j++) {
                if (matches(existing.get(j), declaration, collation)) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                collection.createIndex(declaration.keys(), new IndexOptions().unique(declaration.unique()).name(declaration.name()));
            }
        }
    }

    // 声明要求覆盖全部文档的普通可见索引, 同定义的已有索引可沿用原名.
    private static boolean matches(Document index, IndexDeclaration declaration, Document collation) {
        if (Boolean.TRUE.equals(index.getBoolean("sparse")) || Boolean.TRUE.equals(index.getBoolean("hidden"))
                || index.containsKey("partialFilterExpression") || index.containsKey("expireAfterSeconds")
                || !Objects.equals(index.get("collation", Document.class), collation)) return false;
        Document actualKeys = index.get("key", Document.class);
        boolean actualUnique = Boolean.TRUE.equals(index.getBoolean("unique"));
        if (declaration.unique() != actualUnique || actualKeys == null || actualKeys.size() != declaration.keys().size()) return false;
        Iterator<Map.Entry<String, Object>> actualEntries = actualKeys.entrySet().iterator();
        Iterator<Map.Entry<String, Object>> declaredEntries = declaration.keys().entrySet().iterator();
        while (declaredEntries.hasNext()) {
            Map.Entry<String, Object> actual = actualEntries.next();
            Map.Entry<String, Object> declared = declaredEntries.next();
            if (!actual.getKey().equals(declared.getKey())) return false;
            if (!(actual.getValue() instanceof Number actualDirection) || !(declared.getValue() instanceof Number declaredDirection) || actualDirection.doubleValue() != declaredDirection.doubleValue()) {
                return false;
            }
        }
        return true;
    }

    private record IndexDeclaration(Document keys, boolean unique, String name) {
    }
}
