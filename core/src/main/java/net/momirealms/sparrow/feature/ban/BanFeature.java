package net.momirealms.sparrow.feature.ban;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.database.BanStore;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.feature.ban.command.BanCommand;
import net.momirealms.sparrow.feature.ban.command.BanHistoryCommand;
import net.momirealms.sparrow.feature.ban.command.BanIpCommand;
import net.momirealms.sparrow.feature.ban.command.UnbanCommand;
import net.momirealms.sparrow.player.PlayerLookup;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.player.PlayerIdentity;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.cluster.PlayerPresence;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.redis.proxy.DisconnectMessage;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.IpRange;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class BanFeature extends Feature<BanSettings> implements PlayerListener {
    public static final String ID = "ban";
    public static final String NOTIFY_PERMISSION = DependencyVersions.PROJECT_ID + ".notify.ban";
    private static final long PROXY_DISCONNECT_WAIT_MILLIS = 1000;

    private final SparrowPlugin plugin;

    public BanFeature(@NotNull SparrowPlugin plugin) {
        super(ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        super.config = this.plugin.configurationManager().featuresConfig().config().ban();
    }

    @Override
    protected void onLoad() {
        this.plugin.playerManager().registerListener(this);
    }

    @Override
    protected void registerCommand(@NotNull Consumer<CommandFeature> register) {
        CommandManager manager = this.plugin.commandManager();
        register.accept(new BanCommand(manager, this.plugin, this));
        register.accept(new BanIpCommand(manager, this.plugin, this));
        register.accept(new UnbanCommand(manager, this.plugin, this));
        register.accept(new BanHistoryCommand(manager, this.plugin, this));
    }

    // 表结构在数据库线程上准备, 失败只记日志, 之后每次使用都会重新尝试
    @Override
    protected void onEnable() {
        this.store().initialize().whenComplete((ignored, failure) -> {
            if (failure != null) {
                this.plugin.logger().warn("Failed to prepare the ban tables", failure);
            }
        });
    }

    @NotNull
    public BanStore store() {
        return this.plugin.dataStorage().banStore();
    }

    // 在登录线程同步查库, 数据库出错时异常交给 Bukkit 记录, 本次登录照常放行.
    @Override
    @SuppressWarnings("deprecation")
    public void onPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        if (!this.enabled() || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        long now = System.currentTimeMillis();
        BanRecord ban = this.store().findActiveBan(event.getUniqueId(), IpRange.address(event.getAddress()), now).join().orElse(null);
        if (ban == null) {
            return;
        }
        boolean account = event.getUniqueId().equals(ban.player());
        Component screen = this.plugin.translationManager()
                .render(
                        BanTexts.kickScreen(
                                account,
                                event.getName(),
                                ban.id(),
                                ban.reason(),
                                ban.operatorName(),
                                ban.createdAt(),
                                ban.expiresAt(),
                                now
                        ),
                        null
                );
        // 先拒绝登录
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, AdventureHelper.componentToLegacy(screen));
        // 本服拒绝登录会被代理转到下一个服务器, 所以先让代理断开整条连接, 等待期间代理没有处理再由本服拒绝.
        this.plugin.messageBrokerManager().proxyBroker().publishOneWay(new DisconnectMessage(event.getUniqueId(), AdventureHelper.componentToJson(screen)), "");
        try {
            Thread.sleep(PROXY_DISCONNECT_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 按玩家名或 UUID 找到要封禁的玩家.
     * 玩家名必须有记录, UUID 没有记录时用 UUID 文本代替名字.
     *
     * @return 解析任务, 玩家名没有记录时结果为空
     */
    @NotNull
    public CompletableFuture<Optional<PlayerIdentity>> resolvePlayer(@NotNull String input) {
        UUID uuid = UUIDUtils.parse(input);
        PlayerLookup players = this.plugin.playerLookup();
        if (uuid == null) {
            return players.resolvePlayer(input);
        }
        return players.resolvePlayer(uuid).thenApply(found ->
                found.isEmpty() ? Optional.of(new PlayerIdentity(uuid, uuid.toString())) : found
        );
    }

    /**
     * 把 #处罚ID、IP 或通配 IP、UUID、玩家名解析为解封或查询的对象.
     *
     * @return 解析任务, 玩家名没有记录时结果为空
     * @throws IllegalArgumentException 当文本以 # 开头却不是处罚 ID, 或像 IP 却无法解析时
     */
    @NotNull
    public CompletableFuture<Optional<BanTarget>> resolveTarget(@NotNull String input) {
        if (input.charAt(0) == BanRecord.ID_PREFIX) {
            String id = BanRecord.parseId(input);
            if (id == null) {
                throw new IllegalArgumentException("Invalid punishment id: " + input);
            }
            return CompletableFuture.completedFuture(Optional.of(new BanTarget.IdTarget(id)));
        }
        if (IpRange.looksLikeIp(input)) {
            return CompletableFuture.completedFuture(Optional.of(new BanTarget.IpTarget(IpRange.parse(input))));
        }
        return this.resolvePlayer(input).thenApply(found -> found.map(BanTarget.PlayerTarget::new));
    }

    /**
     * 写入封禁并踢出各服命中的在线玩家.
     * 带玩家的封禁覆盖该玩家的旧封禁, 纯 IP 封禁覆盖同一段的纯 IP 封禁.
     *
     * @param player 被封禁的玩家, 纯 IP 封禁时为 null
     * @param ip 被封禁的 IP 段, 纯玩家封禁时为 null. <strong>两者不能同时为 null</strong>
     * @param reason 封禁原因, 空字符串表示未提供
     * @param expiresAt 到期时间, 单位为 Unix 毫秒, 0 表示永久
     * @param silent 是否跳过管理员通知
     * @param force 是否允许覆盖仍生效的封禁
     * @return 写入结果, 覆盖被拒绝时保留旧记录并跳过踢人与通知
     */
    @NotNull
    public CompletableFuture<BanResult> ban(
            @Nullable PlayerIdentity player,
            @Nullable IpRange ip,
            @NotNull String reason,
            long expiresAt,
            @NotNull String operatorName,
            boolean silent,
            boolean force
    ) {
        UUID uuid = player == null ? null : player.uuid();
        String name = player == null ? null : player.name();
        BanRecord record = new BanRecord(
                BanRecord.newId(),
                uuid,
                name,
                ip,
                reason,
                operatorName,
                ServerConfig.serverId(),
                System.currentTimeMillis(),
                expiresAt,
                0,
                null
        );
        return this.store()
                .saveBan(record, force)
                .thenApply(result -> {
                    if (result.status() == BanResult.Status.REPLACEMENT_REJECTED) {
                        return result;
                    }
                    this.publish(
                            new BanMessage(
                                    true,
                                    record.id(),
                                    record.display(),
                                    uuid,
                                    ip,
                                    reason,
                                    operatorName,
                                    record.createdAt(),
                                    expiresAt,
                                    silent
                            )
                    );
                    return result;
                });
    }

    /**
     * 撤销仍生效的封禁, 有撤销且非静默时通知各服管理员.
     * 匹配规则见 {@link BanStore#revokeBans}.
     *
     * @param silent 是否跳过管理员通知
     * @return 撤销任务, 结果为被撤销的记录
     */
    @NotNull
    public CompletableFuture<List<BanRecord>> unban(@NotNull BanTarget target, @NotNull String operatorName, boolean silent) {
        return this.store()
                .revokeBans(target, System.currentTimeMillis(), operatorName)
                .thenApply(revoked -> {
                    if (!revoked.isEmpty()) {
                        this.publish(new BanMessage(false, "", target.display(), null, null, "", operatorName, 0, 0, silent));
                    }
                    return revoked;
                });
    }

    // 需要通知时发给全部服务器. 静默时只有封禁需要踢人, 纯玩家封禁只发给玩家所在的服务器.
    // 名单过期导致漏踢时, 封禁已经写库, 玩家进入其他服务器会在登录检查被拦下.
    private void publish(BanMessage message) {
        String server = "";
        if (message.silent()) {
            if (!message.banned()) return;
            if (message.ip() == null) {
                PlayerPresence online = this.plugin.playerDirectory().find(message.player());
                if (online == null) return;
                server = online.server();
            }
        }
        this.plugin.messageBrokerManager().broker().publishOneWay(message, server);
    }

    // 收到消息后只处理本服玩家
    void accept(@NotNull BanMessage message) {
        if (!this.enabled()) return;
        long now = System.currentTimeMillis();
        if (message.banned()) {
            this.kickTargets(message, now);
        }
        if (!message.silent()) {
            this.notifyStaff(message, now);
        }
    }

    // 纯玩家封禁按 UUID 直接查找, 带 IP 时比对每名玩家的地址, 同一 IP 上的所有账号都会被踢出
    private void kickTargets(BanMessage message, long now) {
        IpRange range = message.ip();
        if (range == null) {
            SparrowPlayer player = this.plugin.playerManager().getPlayer(message.player());
            if (player != null) {
                this.kick(player, true, message, now);
            }
            return;
        }
        for (SparrowPlayer player : this.plugin.playerManager().getOnlinePlayers()) {
            if (player.uniqueId().equals(message.player())) {
                this.kick(player, true, message, now);
                continue;
            }
            InetSocketAddress address = player.platformPlayer().getAddress();
            if (address != null && range.contains(IpRange.address(address.getAddress()))) {
                this.kick(player, false, message, now);
            }
        }
    }

    // 文本在当前线程渲染好, 踢出放到玩家所属线程
    private void kick(SparrowPlayer player, boolean account, BanMessage message, long now) {
        Component screen = this.plugin.translationManager().render(
                BanTexts.kickScreen(
                        account,
                        player.platformPlayer().getName(),
                        message.banId(),
                        message.reason(),
                        message.operatorName(),
                        message.createdAt(),
                        message.expiresAt(),
                        now
                ),
                player.locale()
        );
        this.plugin.scheduler().platform().run(() -> player.kick(screen, true), () -> {}, player.platformPlayer());
    }

    // 同一语言的通知只渲染一次
    private void notifyStaff(BanMessage message, long now) {
        Map<Locale, Component> rendered = new HashMap<>(4);
        Sound sound = super.config.getNotifySound(message.banned());
        for (SparrowPlayer player : this.plugin.playerManager().getOnlinePlayers()) {
            if (player.hasPermission(NOTIFY_PERMISSION)) {
                player.sendMessage(rendered.computeIfAbsent(
                        player.locale(),
                        locale -> this.plugin.translationManager().render(BanTexts.notification(message, now), locale)
                ));
                if (sound != null) {
                    player.playSound(sound);
                }
            }
        }
    }
}
