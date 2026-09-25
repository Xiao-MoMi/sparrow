package net.momirealms.sparrow.feature.head;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.util.DurationUtils;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class HeadSettings implements FeatureSettings {
    private boolean enabled = true;
    @Comment("Sources in priority order: online, api. Online textures are not stored in the API cache.")
    @Comment(lang = "zh", value = "获取源优先顺序, 可选 online、api. 在线纹理不会写入 API 缓存.")
    private List<String> sourceOrder = List.of("online", "api");

    @Comment("Total lookup timeout, including cache and online-player access. Durations support d/h/m/s/ms.")
    @Comment(lang = "zh", value = "查询总超时, 包含缓存和在线玩家读取. 时长支持 d/h/m/s/ms.")
    private String requestTimeout = "15s";

    private CacheOptions cache = new CacheOptions();
    private ApiOptions api = new ApiOptions();

    @Override
    public boolean enabled() { return this.enabled; }

    @Override
    public void enabled(boolean enabled) { this.enabled = enabled; }

    public List<String> sourceOrder() { return this.sourceOrder; }
    public String requestTimeout() { return this.requestTimeout; }
    public CacheOptions cache() { return this.cache; }
    public ApiOptions api() { return this.api; }

    // 配置错误在启用前报告, HTTP 模板也在这里验证.
    void validate() {
        if (this.sourceOrder.isEmpty() || this.sourceOrder.stream().anyMatch(source -> !source.equals("online") && !source.equals("api"))
                || this.sourceOrder.stream().distinct().count() != this.sourceOrder.size()) {
            throw new IllegalArgumentException("head.source-order must contain online and/or api without duplicates");
        }
        DurationUtils.parsePositive(this.requestTimeout);
        DurationUtils.parsePositive(this.api.connectTimeout);
        DurationUtils.parsePositive(this.api.requestTimeout);
        if (this.cache.memory.enabled) DurationUtils.parsePositive(this.cache.memory.ttl);
        if (this.cache.redis.enabled) DurationUtils.parsePositive(this.cache.redis.ttl);
        if (this.cache.memory.maxSize <= 0) throw new IllegalArgumentException("head.cache.memory.max-size must be positive");
        if (!this.api.nameUrl.contains("{name}") || !(this.api.profileUrl.contains("{uuid}") || this.api.profileUrl.contains("{uuid-dashed}"))) {
            throw new IllegalArgumentException("Head API URLs require {name} and {uuid} or {uuid-dashed}");
        }
        ProfileClient.endpoint(this.api.nameUrl.replace("{name}", "Player"));
        ProfileClient.endpoint(this.api.profileUrl.replace("{uuid}", "00000000000000000000000000000000").replace("{uuid-dashed}", "00000000-0000-0000-0000-000000000000"));
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class CacheOptions {
        private MemoryOptions memory = new MemoryOptions();
        private RedisOptions redis = new RedisOptions();

        public MemoryOptions memory() { return this.memory; }
        public RedisOptions redis() { return this.redis; }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class MemoryOptions {
        private boolean enabled = true;

        @Comment("Maximum age since fetching the data. Reading does not renew this duration.")
        @Comment(lang = "zh", value = "数据获取后的最长保留时长, 读取不会续期.")
        private String ttl = "5m";

        private int maxSize = 4096;

        public boolean enabled() { return this.enabled; }
        public String ttl() { return this.ttl; }
        public int maxSize() { return this.maxSize; }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class RedisOptions {
        @Comment("Use the shared Redis connection configured in config.yml for head caching.")
        @Comment(lang = "zh", value = "使用 config.yml 中的共享 Redis 连接缓存头颅资料.")
        private boolean enabled = true;
        private String ttl = "24h";

        public boolean enabled() { return this.enabled; }
        public String ttl() { return this.ttl; }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class ApiOptions {
        @Comment("Mojang-compatible name lookup, returning id and name. {name} is URL-encoded.")
        @Comment(lang = "zh", value = "Mojang 兼容的名称查询接口, 返回 id 和 name. {name} 会经过 URL 编码.")
        private String nameUrl = "https://api.mojang.com/users/profiles/minecraft/{name}";

        @Comment("Mojang-compatible profile endpoint. {uuid} has no dashes; {uuid-dashed} includes dashes.")
        @Comment(lang = "zh", value = "Mojang 兼容的资料接口. {uuid} 不带连字符, {uuid-dashed} 带连字符.")
        private String profileUrl = "https://sessionserver.mojang.com/session/minecraft/profile/{uuid}";

        @Comment("Extra headers sent only to the two profile endpoints, for example Authorization.")
        @Comment(lang = "zh", value = "只发送给上述两个资料接口的请求头, 如 Authorization.")
        private Map<String, String> headers = Map.of();
        private String connectTimeout = "3s";

        @Comment("Timeout for each HTTP request.")
        @Comment(lang = "zh", value = "每次 HTTP 请求的超时时长.")
        private String requestTimeout = "5s";

        public String nameUrl() { return this.nameUrl; }
        public String profileUrl() { return this.profileUrl; }
        public Map<String, String> headers() { return this.headers; }
        public String connectTimeout() { return this.connectTimeout; }
        public String requestTimeout() { return this.requestTimeout; }
    }

}
