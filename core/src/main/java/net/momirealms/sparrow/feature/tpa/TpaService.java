package net.momirealms.sparrow.feature.tpa;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.cluster.PlayerPresence;
import net.momirealms.sparrow.feature.tpa.TpaRequest.Direction;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.teleport.TeleportDestination;
import net.momirealms.sparrow.teleport.TeleportType;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class TpaService {
    private static final CompletableFuture<Long> NO_WAIT = CompletableFuture.completedFuture(0L);
    private static final Duration SUGGESTION_TTL = Duration.ofSeconds(3); // 同一位玩家连续补全时, 这段时间内只读一次 Redis

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final TpaFeature feature = this.plugin.featureManager().feature(TpaFeature.ID, TpaFeature.class);
    private final TpaStore store = new TpaStore();
    private final Cache<UUID, CompletableFuture<List<String>>> requesterNames = Caffeine.newBuilder().expireAfterWrite(SUGGESTION_TTL).build(); // 补全用, 向玩家发起请求的人
    private final Cache<UUID, CompletableFuture<List<String>>> targetNames = Caffeine.newBuilder().expireAfterWrite(SUGGESTION_TTL).build();    // 补全用, 玩家发起请求的对象

    /**
     * 向一位在线玩家发起传送请求.
     */
    public void request(@NotNull BukkitSparrowPlayer requester, @NotNull String targetName, @NotNull Direction direction) {
        PlayerPresence target = this.plugin.playerDirectory().find(targetName);
        if (target == null) {
            requester.sendMessage(MessageConstants.COMMAND_TPA_OFFLINE, Component.text(targetName));
            return;
        }
        if (target.uuid().equals(requester.uniqueId())) {
            requester.sendMessage(MessageConstants.COMMAND_TPA_SELF);
            return;
        }
        UUID from = requester.uniqueId();
        UUID to = target.uuid();
        Component name = Component.text(target.name());
        boolean bypass = requester.hasPermission(TpaFeature.BYPASS_COOLDOWN_PERMISSION);
        // 三项检查依次进行, 前一项通过才轮到下一项. 发起冷却放在最后, 没发出去的请求不会占用它
        CompletableFuture<Long> denied = bypass ? NO_WAIT : this.store.deniedMillis(from, to);
        denied.thenCompose(millis -> millis > 0
                        ? CompletableFuture.completedFuture(MessageConstants.COMMAND_TPA_DENIED_COOLDOWN.arguments(name, Component.text((millis + 999) / 1000)))
                        : this.store.find(from, to).thenApply(pending -> pending == null ? null : MessageConstants.COMMAND_TPA_PENDING.arguments(name)))
                .thenCompose(reason -> {
                    if (reason != null) return CompletableFuture.completedFuture(reason);
                    CompletableFuture<Long> cooldown = bypass ? NO_WAIT : this.store.claimCooldown(from);
                    return cooldown.thenApply(millis -> millis > 0 ? MessageConstants.COMMAND_TPA_COOLDOWN.arguments(Component.text((millis + 999) / 1000)) : null);
                })
                .thenCompose(reason -> {
                    if (reason != null) {
                        requester.sendMessage(reason);
                        return CompletableFuture.completedFuture(null);
                    }
                    TpaRequest request = new TpaRequest(from, requester.name(), to, target.name(), direction, System.currentTimeMillis());
                    int expireSeconds = this.feature.config().expireSeconds();
                    return this.store.save(request).thenRun(() -> {
                        requester.sendMessage(MessageConstants.COMMAND_TPA_SENT, name, Component.text(expireSeconds));
                        this.plugin.messageBrokerManager().publishOneWay(new TpaMessage(TpaMessage.Event.REQUESTED, request, ""), "");
                        // 到期由发出请求的本服清理. 本服在到期前停掉的话没有过期提示, 请求留到记录自己失效
                        this.plugin.scheduler().asyncLater(() -> {
                            // 请求到期时删除并通知双方
                            this.store.find(request.requester(), request.target())
                                    .thenCompose(current -> current != null && current.createdAt() == request.createdAt()
                                            ? this.store.take(request)
                                            : CompletableFuture.completedFuture(false))
                                    .thenAccept(taken -> {
                                        if (taken) this.plugin.messageBrokerManager().publishOneWay(new TpaMessage(TpaMessage.Event.EXPIRED, request, ""), "");
                                    })
                                    .exceptionally(error -> {
                                        this.plugin.logger().warn("Failed to expire the tpa request from " + request.requesterName() + " to " + request.targetName(), error);
                                        return null;
                                    });
                        }, expireSeconds, TimeUnit.SECONDS);
                    });
                })
                .exceptionally(error -> {
                    this.plugin.logger().warn("Failed to send a tpa request from " + requester.name() + " to " + target.name(), error);
                    requester.sendMessage(MessageConstants.COMMAND_TPA_FAILED);
                    return null;
                });
    }

    /**
     * 接受一条收到的请求.
     *
     * @param requesterName 发起者的名字, 为 null 时接受最近收到的那条
     */
    public void accept(@NotNull BukkitSparrowPlayer target, @Nullable String requesterName) {
        this.takeIncoming(target, requesterName)
                .thenAccept(request -> {
                    if (request == null) return;
                    Component name = Component.text(request.requesterName());
                    PlayerPresence requester = this.plugin.playerDirectory().find(request.requester());
                    if (requester == null) {
                        target.sendMessage(MessageConstants.COMMAND_TPA_ACCEPT_OFFLINE, name);
                        return;
                    }
                    // 落点所在的服务器在接受时定下. 发起者过来时是接受者所在的本服, 接受者过去时是发起者所在的服务器
                    String server = request.direction() == Direction.TO_TARGET ? ServerConfig.serverId() : requester.server();
                    target.sendMessage(MessageConstants.COMMAND_TPA_ACCEPT_SUCCESS, name);
                    this.plugin.messageBrokerManager().publishOneWay(new TpaMessage(TpaMessage.Event.ACCEPTED, request, server), "");
                })
                .exceptionally(error -> {
                    this.plugin.logger().warn("Failed to accept the tpa request from " + (requesterName == null ? "the latest requester" : requesterName) + " to " + target.name(), error);
                    target.sendMessage(MessageConstants.COMMAND_TPA_FAILED);
                    return null;
                });
    }

    /**
     * 拒绝一条收到的请求. 发起者随后一段时间内不能再向对方发起.
     *
     * @param requesterName 发起者的名字, 为 null 时拒绝最近收到的那条
     */
    public void deny(@NotNull BukkitSparrowPlayer target, @Nullable String requesterName) {
        this.takeIncoming(target, requesterName)
                .thenAccept(request -> {
                    if (request == null) return;
                    this.store.markDenied(request.requester(), request.target());
                    target.sendMessage(MessageConstants.COMMAND_TPA_DENY_SUCCESS, Component.text(request.requesterName()));
                    this.plugin.messageBrokerManager().publishOneWay(new TpaMessage(TpaMessage.Event.DENIED, request, ""), "");
                })
                .exceptionally(error -> {
                    this.plugin.logger().warn("Failed to deny the tpa request from " + (requesterName == null ? "the latest requester" : requesterName) + " to " + target.name(), error);
                    target.sendMessage(MessageConstants.COMMAND_TPA_FAILED);
                    return null;
                });
    }

    /**
     * 取消发出的请求, 每取消一条提示发起者一次.
     *
     * @param targetName 目标的名字, 为 null 时取消发出的全部请求
     */
    public void cancel(@NotNull BukkitSparrowPlayer requester, @Nullable String targetName) {
        this.store.outgoing(requester.uniqueId())
                .thenCompose(requests -> {
                    List<CompletableFuture<Void>> cancelling = new ArrayList<>(requests.size());
                    for (TpaRequest request : requests) {
                        if (targetName != null && !request.targetName().equalsIgnoreCase(targetName)) continue;
                        cancelling.add(this.store.take(request).thenAccept(taken -> {
                            // 对方刚好接受或拒绝了的那条删不掉, 发起者会收到对方的回应
                            if (!taken) return;
                            requester.sendMessage(MessageConstants.COMMAND_TPA_CANCEL_SUCCESS, Component.text(request.targetName()));
                            this.plugin.messageBrokerManager().publishOneWay(new TpaMessage(TpaMessage.Event.CANCELLED, request, ""), "");
                        }));
                    }
                    if (cancelling.isEmpty()) {
                        if (targetName == null) {
                            requester.sendMessage(MessageConstants.COMMAND_TPA_CANCEL_NONE);
                        } else {
                            requester.sendMessage(MessageConstants.COMMAND_TPA_CANCEL_NONE_TO, Component.text(targetName));
                        }
                    }
                    return CompletableFuture.allOf(cancelling.toArray(CompletableFuture[]::new));
                })
                .exceptionally(error -> {
                    this.plugin.logger().warn("Failed to cancel tpa requests from " + requester.name() + " to " + (targetName == null ? "everyone" : targetName), error);
                    requester.sendMessage(MessageConstants.COMMAND_TPA_FAILED);
                    return null;
                });
    }

    /**
     * 补全回应和取消命令里的玩家名.
     *
     * @param incoming true 时补全向这位玩家发起请求的人, false 时补全这位玩家发起请求的对象
     */
    @NotNull
    public CompletableFuture<List<Suggestion>> suggest(@NotNull UUID player, @NotNull String input, boolean incoming) {
        // Spigot 的补全等不了异步结果, 在 Spigot 上不补全
        if (!this.plugin.commandManager().asynchronousCompletion()) return CompletableFuture.completedFuture(List.of());
        CompletableFuture<List<String>> names = incoming
                ? this.requesterNames.get(player, uuid -> this.store.incoming(uuid).thenApply(requests -> requests.stream().map(TpaRequest::requesterName).toList()))
                : this.targetNames.get(player, uuid -> this.store.outgoing(uuid).thenApply(requests -> requests.stream().map(TpaRequest::targetName).toList()));
        return names.thenApply(found -> found.stream()
                .filter(name -> name.regionMatches(true, 0, input, 0, input.length()))
                .map(Suggestion::suggestion)
                .toList());
    }

    // 每台服务器都会收到通知, 各自只处理此刻在本服的那位玩家
    void handle(@NotNull TpaMessage message) {
        TpaRequest request = message.request();
        BukkitSparrowPlayer requester = this.plugin.playerManager().getPlayer(request.requester());
        BukkitSparrowPlayer target = this.plugin.playerManager().getPlayer(request.target());
        Component requesterName = Component.text(request.requesterName());
        Component targetName = Component.text(request.targetName());
        boolean toTarget = request.direction() == Direction.TO_TARGET;
        switch (message.event()) {
            case REQUESTED -> {
                if (target != null) {
                    target.sendMessage(
                            (toTarget ? MessageConstants.COMMAND_TPA_RECEIVED : MessageConstants.COMMAND_TPA_HERE_RECEIVED),
                            requesterName,
                            this.button(MessageConstants.COMMAND_TPA_BUTTON_ACCEPT, "tpa-accept", request.requesterName()),
                            this.button(MessageConstants.COMMAND_TPA_BUTTON_DENY, "tpa-deny", request.requesterName())
                    );
                }
            }
            case ACCEPTED -> {
                if (requester != null) requester.sendMessage(MessageConstants.COMMAND_TPA_ACCEPTED, targetName);
                // 被传送的是发起者还是目标由请求方向决定, 另一位是落点
                if (toTarget) {
                    if (requester != null) this.teleport(requester, request.target(), targetName, message.server());
                } else if (target != null) {
                    this.teleport(target, request.requester(), requesterName, message.server());
                }
            }
            case DENIED -> {
                if (requester != null) requester.sendMessage(MessageConstants.COMMAND_TPA_DENIED, targetName);
            }
            case CANCELLED -> {
                if (target != null) target.sendMessage(MessageConstants.COMMAND_TPA_CANCELLED, requesterName);
            }
            case EXPIRED -> {
                if (requester != null) requester.sendMessage(MessageConstants.COMMAND_TPA_EXPIRED, targetName);
                if (target != null) target.sendMessage(MessageConstants.COMMAND_TPA_EXPIRED_FROM, requesterName);
            }
        }
    }

    // 取出目标收到的一条请求并从记录里删掉, 不指定发起者时取最近收到的那条. 没有能处理的请求时提示目标, 结果为 null
    private CompletableFuture<@Nullable TpaRequest> takeIncoming(BukkitSparrowPlayer target, @Nullable String requesterName) {
        return this.store.incoming(target.uniqueId()).thenCompose(requests -> {
            TpaRequest found = null;
            for (TpaRequest request : requests) {
                boolean matched = requesterName == null
                        ? found == null || request.createdAt() > found.createdAt()
                        : request.requesterName().equalsIgnoreCase(requesterName);
                if (matched) found = request;
            }
            if (found == null) {
                if (requesterName == null) {
                    target.sendMessage(MessageConstants.COMMAND_TPA_NONE);
                } else {
                    target.sendMessage(MessageConstants.COMMAND_TPA_NONE_FROM, Component.text(requesterName));
                }
                return CompletableFuture.completedFuture(null);
            }
            TpaRequest request = found;
            return this.store.take(request).thenApply(taken -> {
                if (taken) return request;
                target.sendMessage(MessageConstants.COMMAND_TPA_NONE_FROM, Component.text(request.requesterName()));
                return null;
            });
        });
    }

    // 把玩家送到另一位玩家此刻所在的位置, 结果提示给被传送的玩家
    private void teleport(BukkitSparrowPlayer player, UUID destination, Component destinationName, String server) {
        this.plugin.teleportService()
                .teleport(player.platformPlayer(), TeleportType.TPA, server, new TeleportDestination.OfPlayer(destination), true)
                .thenAccept(result -> {
                    switch (result) {
                        case LOCAL_SUCCESS -> player.sendMessage(MessageConstants.COMMAND_TPA_TELEPORT_SUCCESS, destinationName);
                        case SERVER_OFFLINE -> player.sendMessage(MessageConstants.COMMAND_TPA_TELEPORT_SERVER_OFFLINE, destinationName, Component.text(server));
                        case INVALID -> player.sendMessage(MessageConstants.COMMAND_TPA_TELEPORT_INVALID, destinationName);
                        case FAILED -> player.sendMessage(MessageConstants.COMMAND_TELEPORT_FAILURE_SELF);
                        case REMOTE_SUCCESS, REJECTED -> {
                        }
                    }
                })
                .exceptionally(error -> {
                    Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                    if (cause instanceof TimeoutException) {
                        player.sendMessage(MessageConstants.COMMAND_TPA_TELEPORT_TIMEOUT);
                    } else {
                        this.plugin.logger().warn("Failed to teleport " + player.name() + " for a tpa request", cause);
                        player.sendMessage(MessageConstants.COMMAND_TELEPORT_FAILURE_SELF);
                    }
                    return null;
                });
    }

    // 点击后替玩家执行回应这位发起者的命令
    private Component button(TranslatableComponent label, String command, String requesterName) {
        String usage = this.plugin.configurationManager().commandsConfig().configDefinition().command(command).getUsages()
                .stream()
                .filter(candidate -> candidate.startsWith("/"))
                .findFirst()
                .orElse("/" + command);
        String line = usage + " " + requesterName;
        return Component.empty().append(label).hoverEvent(Component.text(line)).clickEvent(ClickEvent.runCommand(line));
    }
}