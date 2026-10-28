package net.momirealms.sparrow.feature.deathmessages;

import net.minecraft.network.chat.Component;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.message.SendMessageMessage;
import net.momirealms.sparrow.player.message.TemplateBroadcastMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.proxy.bukkit.event.entity.PlayerDeathEventProxy;
import net.momirealms.sparrow.proxy.paper.adventure.PaperAdventureProxy;
import net.momirealms.sparrow.ui.Subscription;
import net.momirealms.sparrow.util.ThrowableUtils;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class DeathMessagesFeature implements Feature, Listener {
    public static final String ID = "death_messages";

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final DeathMessageRenderer renderer = new DeathMessageRenderer();
    private final DeathMessageRateLimiter rateLimiter = new DeathMessageRateLimiter();
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private volatile @Nullable DeathMessagesConfig config;
    private @Nullable Subscription quitSubscription;

    @NotNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supportsHotEnable() {
        return true;
    }

    @Override
    public boolean supportsHotDisable() {
        return true;
    }

    private DeathMessagesConfig readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features/death_messages.yml");
        try {
            return YamlMapperFactory.builder()
                    .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                    .build()
                    .create(DeathMessagesSettings.class, DeathMessagesSettings::new)
                    .load(path)
                    .value()
                    .toConfig();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load " + path, exception);
        }
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onEnable() {
        DeathMessagesConfig loaded = this.readConfig();
        this.lifecycle.writeLock().lock();
        try {
            this.config = loaded;
            this.quitSubscription = this.plugin.playerManager().registerQuitListener(this::onQuit);
            this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this, this.plugin.javaPlugin());
            return CompletableFuture.completedFuture(null);
        } finally {
            this.lifecycle.writeLock().unlock();
        }
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onReload() {
        DeathMessagesConfig loaded = this.readConfig();
        this.lifecycle.writeLock().lock();
        try {
            this.config = loaded;
            return CompletableFuture.completedFuture(null);
        } finally {
            this.lifecycle.writeLock().unlock();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(@NotNull PlayerDeathEvent event) {
        this.lifecycle.readLock().lock();
        try {
            DeathMessagesConfig current = this.config;
            if (current == null || event.isCancelled() || !event.getShowDeathMessages()) return;
            PlayerDeathEventProxy proxy = PlayerDeathEventProxy.INSTANCE;
            Object originalAnnouncement = proxy.deathMessage(event);
            if (originalAnnouncement == null) return;
            Component nativeAnnouncement = (Component) PaperAdventureProxy.INSTANCE.asVanilla(originalAnnouncement);
            if (nativeAnnouncement.getString().isEmpty()) return;

            DeathMessageContext context = DeathMessageContext.capture(event.getEntity());
            DeathMessagesConfig.Rule rule = current.rules().get(context.getMessageKey());
            Set<String> disabledWorlds = rule == null ? current.disabledWorlds() : rule.disabledWorlds();
            List<String> messages = rule == null ? current.fallback() : rule.messages();
            // 聊天公告由模块接管, 死亡界面保留事件原有的消息及独立覆盖.
            if (proxy.deathScreenMessageOverride(event) == null) {
                proxy.deathScreenMessageOverride(event, originalAnnouncement);
            }
            proxy.deathMessage(event, null);
            if (disabledWorlds.contains(context.world())) return;
            PreparedDeathMessage message = this.renderer.prepare(context, current, messages);
            if (message == null) return;

            Player victim = context.victim();
            if (!this.rateLimiter.record(victim.getUniqueId(), current.antiSpam())) {
                if (current.antiSpam().showToVictim()) {
                    this.send(victim, message);
                }
                return;
            }
            TemplateBroadcastMessage announcement = new TemplateBroadcastMessage(message.template());
            if (rule == null ? current.broadcast() : rule.broadcast()) {
                // 广播订阅包含来源服, 所有服务器都从同一份 Redis 消息发送.
                this.plugin.messageBrokerManager()
                        .publishOneWay(announcement, "")
                        .whenComplete((ignored, error) -> {
                            if (error != null) this.plugin.logger().warn("Failed to broadcast death message for " + victim.getUniqueId(), ThrowableUtils.unwrap(error));
                        });
            } else {
                announcement.sendLocally();
            }
        } finally {
            this.lifecycle.readLock().unlock();
        }
    }

    private void send(@NotNull Player receiver, @NotNull PreparedDeathMessage message) {
        new SendMessageMessage(receiver.getUniqueId(), message.render(receiver)).sendLocally();
    }

    @NotNull
    private CompletableFuture<Void> onQuit(@NotNull BukkitSparrowPlayer player) {
        this.lifecycle.readLock().lock();
        try {
            this.rateLimiter.remove(player.uniqueId());
            return CompletableFuture.completedFuture(null);
        } finally {
            this.lifecycle.readLock().unlock();
        }
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onDisable() {
        // 等正在发送的死亡公告退出后, 再注销监听并释放全部防刷屏记录.
        this.lifecycle.writeLock().lock();
        try {
            this.config = null;
            HandlerList.unregisterAll(this);
            if (this.quitSubscription != null) {
                this.quitSubscription.close();
                this.quitSubscription = null;
            }
            this.rateLimiter.clear();
            return CompletableFuture.completedFuture(null);
        } finally {
            this.lifecycle.writeLock().unlock();
        }
    }

    @Nullable
    DeathMessagesConfig config() {
        return this.config;
    }
}