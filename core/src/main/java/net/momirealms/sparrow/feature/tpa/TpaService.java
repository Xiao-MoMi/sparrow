package net.momirealms.sparrow.feature.tpa;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.feature.tpa.TpaMessage.Event;
import net.momirealms.sparrow.feature.tpa.TpaRequest.Direction;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import net.momirealms.sparrow.util.ThrowableUtils;
import net.momirealms.sparrow.util.UUIDUtils;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class TpaService {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final TpaFeature feature;
    private final TpaStore store = new TpaStore();
    private final Map<UUID, SchedulerTask> expirations = new ConcurrentHashMap<>();
    private final Cache<UUID, CompletableFuture<List<TpaRequest>>> incoming = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(3)).build();
    private final Cache<UUID, CompletableFuture<List<TpaRequest>>> outgoing = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(3)).build();

    TpaService(TpaFeature feature) {
        this.feature = feature;
    }

    public void request(BukkitSparrowPlayer requester, String input, Direction direction) {
        if (!this.feature.enabled()) return;
        UUID uuid = UUIDUtils.parse(input);
        OfflineSparrowPlayer target = uuid == null
                ? this.plugin.playerManager().getGlobalOnlinePlayer(input)
                : this.plugin.playerManager().getGlobalOnlinePlayer(uuid);
        if (target == null) {
            requester.sendTranslatedMessage(MessageConstants.COMMAND_TPA_OFFLINE.arguments(Component.text(input)));
            return;
        }
        if (target.uniqueId().equals(requester.uniqueId())) {
            requester.sendTranslatedMessage(MessageConstants.COMMAND_TPA_SELF);
            return;
        }
        TpaConfig config = this.feature.config();
        long generation = this.feature.generation();
        long now = System.currentTimeMillis();
        TpaRequest request = new TpaRequest(UUID.randomUUID(), requester.uniqueId(), requester.name(), target.uniqueId(), target.name(), direction, now, now + config.expireMillis());
        boolean bypass = requester.hasPermission(TpaFeature.BYPASS_COOLDOWN_PERMISSION);
        this.observe(this.store.create(request, config.requestCooldownMillis(), bypass).thenAccept(result -> {
            if (!this.feature.active(generation)) return;
            Component name = Component.text(target.name());
            Component seconds = Component.text((result.remainingMillis() + 999) / 1000);
            switch (result.status()) {
                case 1 -> {
                    requester.sendTranslatedMessage(MessageConstants.COMMAND_TPA_SENT.arguments(name, Component.text(config.expireMillis() / 1000)));
                    this.publish(Event.REQUESTED, request, "");
                    this.scheduleExpiry(request, generation);
                }
                case 0 -> requester.sendTranslatedMessage(MessageConstants.COMMAND_TPA_PENDING.arguments(name));
                case -1 -> requester.sendTranslatedMessage(MessageConstants.COMMAND_TPA_DENIED_COOLDOWN.arguments(name, seconds));
                case -2 -> requester.sendTranslatedMessage(MessageConstants.COMMAND_TPA_COOLDOWN.arguments(seconds));
                default -> throw new IllegalStateException("Unknown TPA creation result: " + result.status());
            }
        }), requester);
    }

    public void reply(BukkitSparrowPlayer target, @Nullable String requesterName, boolean accept) {
        if (!this.feature.enabled()) return;
        long generation = this.feature.generation();
        long denyMillis = accept ? 0 : this.feature.config().denyCooldownMillis();
        this.observe(this.store.list(target.uniqueId(), true).thenCompose(requests -> {
            if (!this.feature.active(generation)) return CompletableFuture.completedFuture(null);
            TpaRequest request = requests.stream()
                    .filter(candidate -> requesterName == null || candidate.requesterName().equalsIgnoreCase(requesterName))
                    .findFirst()
                    .orElse(null);
            if (request == null) {
                this.noIncoming(target, requesterName);
                return CompletableFuture.completedFuture(null);
            }
            return this.store.take(request, false, denyMillis).thenAccept(taken -> {
                if (!this.feature.active(generation)) return;
                if (!taken) {
                    this.noIncoming(target, requesterName);
                    return;
                }
                this.invalidate(request);
                Component name = Component.text(request.requesterName());
                if (!accept) {
                    target.sendTranslatedMessage(MessageConstants.COMMAND_TPA_DENY_SUCCESS.arguments(name));
                    this.publish(Event.DENIED, request, "");
                    return;
                }
                OfflineSparrowPlayer requester = this.plugin.playerManager().getGlobalOnlinePlayer(request.requester());
                String requesterServer = requester == null ? null : requester.getCurrentServerId();
                String targetServer = target.getCurrentServerId();
                if (requesterServer == null || !target.isOnlineLocally()) {
                    target.sendTranslatedMessage(MessageConstants.COMMAND_TPA_ACCEPT_OFFLINE.arguments(name));
                    return;
                }
                target.sendTranslatedMessage(MessageConstants.COMMAND_TPA_ACCEPT_SUCCESS.arguments(name));
                this.publish(Event.ACCEPTED, request, request.direction() == Direction.TO_TARGET ? targetServer : requesterServer);
            });
        }), target);
    }

    public void cancel(BukkitSparrowPlayer requester, @Nullable String targetName) {
        if (!this.feature.enabled()) return;
        long generation = this.feature.generation();
        this.observe(this.store.list(requester.uniqueId(), false).thenCompose(requests -> {
            if (!this.feature.active(generation)) return CompletableFuture.completedFuture(null);
            List<CompletableFuture<Boolean>> cancellations = new ArrayList<>();
            for (int i = 0, size = requests.size(); i < size; i++) {
                TpaRequest request = requests.get(i);
                if (targetName != null && !request.targetName().equalsIgnoreCase(targetName)) continue;
                cancellations.add(this.store.take(request, false, 0).thenApply(taken -> {
                    if (taken && this.feature.active(generation)) {
                        requester.sendTranslatedMessage(MessageConstants.COMMAND_TPA_CANCEL_SUCCESS.arguments(Component.text(request.targetName())));
                        this.publish(Event.CANCELLED, request, "");
                    }
                    return taken;
                }));
            }
            return CompletableFuture.allOf(cancellations.toArray(CompletableFuture[]::new)).thenRun(() -> {
                if (!this.feature.active(generation)) return;
                if (cancellations.stream().anyMatch(CompletableFuture::join)) return;
                requester.sendTranslatedMessage((targetName == null ? MessageConstants.COMMAND_TPA_CANCEL_NONE : MessageConstants.COMMAND_TPA_CANCEL_NONE_TO).arguments(
                        Component.text(targetName == null ? "" : targetName)
                    ));
            });
        }), requester);
    }

    public CompletableFuture<List<Suggestion>> suggest(UUID player, String prefix, boolean received) {
        if (!this.feature.enabled()) return CompletableFuture.completedFuture(List.of());
        Cache<UUID, CompletableFuture<List<TpaRequest>>> cache = received ? this.incoming : this.outgoing;
        return cache.get(player, id -> this.store.list(id, received)).thenApply(requests -> requests.stream()
                .filter(request -> request.expiresAt() > System.currentTimeMillis())
                .map(request -> received ? request.requesterName() : request.targetName())
                .filter(name -> name.regionMatches(true, 0, prefix, 0, prefix.length()))
                .map(Suggestion::suggestion)
                .toList());
    }

    private synchronized void scheduleExpiry(TpaRequest request, long generation) {
        if (!this.feature.active(generation)) return;
        SchedulerTask task = this.plugin.scheduler().asyncLater(() -> {
            this.expirations.remove(request.id());
            if (!this.feature.active(generation)) return;
            this.store.take(request, true, 0).thenAccept(taken -> {
                if (taken && this.feature.active(generation)) this.publish(Event.EXPIRED, request, "");
            }).exceptionally(error -> {
                this.plugin.logger().warn("Failed to expire TPA request " + request.id(), ThrowableUtils.unwrap(error));
                return null;
            });
        }, Math.max(0, request.expiresAt() - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
        this.expirations.put(request.id(), task);
    }

    private void publish(Event event, TpaRequest request, String server) {
        this.invalidate(request);
        this.plugin.messageBrokerManager().publishOneWay(new TpaMessage(event, request, server), "");
    }

    void handle(TpaMessage message) {
        TpaRequest request = message.request();
        this.invalidate(request);
        if (message.event() != Event.REQUESTED) {
            SchedulerTask task = this.expirations.remove(request.id());
            if (task != null) task.cancel();
        }
        BukkitSparrowPlayer requester = this.plugin.playerManager().getOnlinePlayer(request.requester());
        BukkitSparrowPlayer target = this.plugin.playerManager().getOnlinePlayer(request.target());
        Component requesterName = Component.text(request.requesterName());
        Component targetName = Component.text(request.targetName());
        boolean toTarget = request.direction() == Direction.TO_TARGET;
        switch (message.event()) {
            case REQUESTED -> {
                if (target != null) {
                    TranslatableComponent received = toTarget ? MessageConstants.COMMAND_TPA_RECEIVED : MessageConstants.COMMAND_TPA_HERE_RECEIVED;
                    target.sendTranslatedMessage(received.arguments(
                            requesterName,
                            this.button(MessageConstants.COMMAND_TPA_BUTTON_ACCEPT, "tpa_accept", request.requesterName()),
                            this.button(MessageConstants.COMMAND_TPA_BUTTON_DENY, "tpa_deny", request.requesterName())
                    ));
                }
            }
            case ACCEPTED -> {
                if (requester != null) requester.sendTranslatedMessage(MessageConstants.COMMAND_TPA_ACCEPTED.arguments(targetName));
                BukkitSparrowPlayer traveler = toTarget ? requester : target;
                if (traveler != null) {
                    long generation = this.feature.generation();
                    this.plugin.scheduler().platform().run(() -> {
                        if (!this.feature.active(generation)) return;
                        this.feature.teleport(
                                traveler,
                                toTarget ? request.target() : request.requester(),
                                toTarget ? request.targetName() : request.requesterName(),
                                message.server()
                        );
                    }, () -> {}, traveler.platformPlayer);
                }
            }
            case DENIED -> {
                if (requester != null) requester.sendTranslatedMessage(MessageConstants.COMMAND_TPA_DENIED.arguments(targetName));
            }
            case CANCELLED -> {
                if (target != null) target.sendTranslatedMessage(MessageConstants.COMMAND_TPA_CANCELLED.arguments(requesterName));
            }
            case EXPIRED -> {
                if (requester != null) requester.sendTranslatedMessage(MessageConstants.COMMAND_TPA_EXPIRED.arguments(targetName));
                if (target != null) target.sendTranslatedMessage(MessageConstants.COMMAND_TPA_EXPIRED_FROM.arguments(requesterName));
            }
        }
    }

    private Component button(TranslatableComponent label, String id, String requesterName) {
        CommandConfig config = this.plugin.configurationManager().commandsConfig().configDefinition().command(id);
        if (!config.isEnable()) return Component.empty();
        String usage = config.getUsages().stream().filter(value -> value.startsWith("/")).findFirst().orElse(null);
        if (usage == null) return Component.empty();
        String command = usage + " " + requesterName;
        return label.hoverEvent(Component.text(command)).clickEvent(ClickEvent.runCommand(command));
    }

    private void noIncoming(BukkitSparrowPlayer player, @Nullable String name) {
        if (name == null) {
            player.sendTranslatedMessage(MessageConstants.COMMAND_TPA_NONE);
        } else {
            player.sendTranslatedMessage(MessageConstants.COMMAND_TPA_NONE_FROM.arguments(Component.text(name)));
        }
    }

    private void invalidate(TpaRequest request) {
        this.incoming.invalidate(request.target());
        this.outgoing.invalidate(request.requester());
    }

    private void observe(CompletableFuture<?> operation, BukkitSparrowPlayer actor) {
        operation.exceptionally(error -> {
            this.plugin.logger().warn("TPA request operation failed for " + actor.name(), ThrowableUtils.unwrap(error));
            actor.sendTranslatedMessage(MessageConstants.COMMAND_TPA_FAILED);
            return null;
        });
    }

    synchronized void stop() {
        for (SchedulerTask task : this.expirations.values()) {
            task.cancel();
        }
        this.expirations.clear();
        this.incoming.invalidateAll();
        this.outgoing.invalidateAll();
    }
}