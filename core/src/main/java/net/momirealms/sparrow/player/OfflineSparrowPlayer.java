package net.momirealms.sparrow.player;

import com.mojang.datafixers.util.Pair;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.DeathProtection;
import net.momirealms.sparrow.compatibility.CompatibilityManager.PermissionValueMode;
import net.momirealms.sparrow.player.message.ClearTitleMessage;
import net.momirealms.sparrow.player.message.HideBossBarMessage;
import net.momirealms.sparrow.player.message.KickPlayerMessage;
import net.momirealms.sparrow.player.message.PlaySoundMessage;
import net.momirealms.sparrow.player.message.SendMessageMessage;
import net.momirealms.sparrow.player.message.SendTitleMessage;
import net.momirealms.sparrow.player.message.SendToastMessage;
import net.momirealms.sparrow.player.message.SendTotemAnimationMessage;
import net.momirealms.sparrow.player.message.ShowBossBarMessage;
import net.momirealms.sparrow.player.message.UpdateBossBarProgressMessage;
import net.momirealms.sparrow.player.message.UpdateBossBarTitleMessage;
import net.momirealms.sparrow.player.message.proxy.ConnectRequest;
import net.momirealms.sparrow.player.message.proxy.ConnectResponse;
import net.momirealms.sparrow.player.message.proxy.ConnectResult;
import net.momirealms.sparrow.player.message.proxy.DisconnectMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.proxy.bukkit.util.CraftChatMessageProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.AdvancementProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.CriterionProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.DisplayInfoProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.ImpossibleTriggerProxy;
import net.momirealms.sparrow.proxy.minecraft.server.level.ServerPlayerProxy;
import net.momirealms.sparrow.proxy.minecraft.world.item.ItemStackTemplateProxy;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.PacketBossEvent;
import net.momirealms.sparrow.util.VersionHelper;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class OfflineSparrowPlayer implements SparrowPlayer {
    private final UUID uniqueId;
    private final String name;
    private final Locale locale;

    OfflineSparrowPlayer(@NotNull UUID uniqueId, @NotNull String name) {
        this(uniqueId, name, Locale.US);
    }

    OfflineSparrowPlayer(@NotNull UUID uniqueId, @NotNull String name, @NotNull Locale locale) {
        this.uniqueId = uniqueId;
        this.name = name;
        this.locale = locale;
    }

    @Override
    @NotNull
    public UUID uniqueId() {
        return this.uniqueId;
    }

    @Override
    @NotNull
    public String name() {
        return this.name;
    }

    @NotNull
    public Locale locale() {
        return this.locale;
    }

    @Override
    public boolean hasPermission(@NotNull String permission) {
        return SparrowPlugin.instance().compatibilityManager().hasPermission(this.uniqueId, permission);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> hasPermissionAsync(@NotNull String permission) {
        return SparrowPlugin.instance().compatibilityManager().hasPermissionAsync(this.uniqueId, permission);
    }

    @Override
    public int getPermissionValue(@NotNull String node, int defaultValue, @NotNull PermissionValueMode mode) {
        return SparrowPlugin.instance().compatibilityManager().getPermissionValue(this.uniqueId, node, defaultValue, mode);
    }

    @Override
    @NotNull
    public CompletableFuture<Integer> getPermissionValueAsync(@NotNull String node, int defaultValue, @NotNull PermissionValueMode mode) {
        return SparrowPlugin.instance().compatibilityManager().getPermissionValueAsync(this.uniqueId, node, defaultValue, mode);
    }

    @Override
    public boolean sendMessage(@NotNull Component message) {
        return this.sendMessage(message, false);
    }

    @Override
    public boolean sendMessage(@NotNull Component message, boolean overlay) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer local = plugin.playerManager().getOnlinePlayer(this.uniqueId);
        if (local != null) {
            Object component = CraftChatMessageProxy.INSTANCE.fromJSON(AdventureHelper.componentToJson(message));
            ServerPlayerProxy.INSTANCE.sendSystemMessage(local.nmsPlayer, component, overlay);
            return true;
        }
        String serverId = this.getCurrentServerId();
        if (serverId == null) return false;
        plugin.messageBrokerManager().publishOneWay(new SendMessageMessage(this.uniqueId, message, overlay), serverId);
        return true;
    }

    @Override
    public boolean sendMessage(@NotNull TranslatableComponent message) {
        return this.sendMessage(SparrowPlugin.instance().translationManager().render(message, this.locale()));
    }

    @Override
    public boolean sendMessage(@NotNull TranslatableComponent key, @NotNull Component... arguments) {
        return this.sendMessage(SparrowPlugin.instance().translationManager().render(key.arguments(arguments), this.locale()));
    }

    @Override
    public boolean sendActionBar(@NotNull Component message) {
        return this.sendMessage(message, true);
    }

    @Override
    public boolean sendActionBar(@NotNull TranslatableComponent message) {
        return this.sendMessage(SparrowPlugin.instance().translationManager().render(message, this.locale()), true);
    }

    @Override
    public boolean sendActionBar(@NotNull TranslatableComponent key, @NotNull Component... arguments) {
        return this.sendMessage(SparrowPlugin.instance().translationManager().render(key.arguments(arguments), this.locale()), true);
    }

    @Override
    public boolean sendTitle(@NotNull Component title, @NotNull Component subtitle, int fadeIn, int stay, int fadeOut) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer local = plugin.playerManager().getOnlinePlayer(this.uniqueId);
        if (local != null) {
            local.connection.sendPacket(new ClientboundBundlePacket(List.of(
                    new ClientboundSetTitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(title))),
                    new ClientboundSetSubtitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(subtitle))),
                    new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut)
            )));
            return true;
        }
        String serverId = this.getCurrentServerId();
        if (serverId == null) return false;
        plugin.messageBrokerManager().publishOneWay(new SendTitleMessage(this.uniqueId, title, subtitle, fadeIn, stay, fadeOut), serverId);
        return true;
    }

    @Override
    public boolean clearTitle() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer local = plugin.playerManager().getOnlinePlayer(this.uniqueId);
        if (local != null) {
            local.connection.sendPacket(new ClientboundClearTitlesPacket(true));
            return true;
        }
        String serverId = this.getCurrentServerId();
        if (serverId == null) return false;
        plugin.messageBrokerManager().publishOneWay(new ClearTitleMessage(this.uniqueId), serverId);
        return true;
    }

    @Override
    public boolean showBossBar(@NotNull UUID id, @NotNull Component title, float progress, @NotNull BossEvent.BossBarColor color, @NotNull BossEvent.BossBarOverlay overlay) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer local = plugin.playerManager().getOnlinePlayer(this.uniqueId);
        if (local != null) {
            PacketBossEvent event = new PacketBossEvent(id, CraftChatMessage.fromJSON(AdventureHelper.componentToJson(title)), color, overlay);
            event.setProgress(progress);
            local.connection.sendPacket(ClientboundBossEventPacket.createAddPacket(event));
            return true;
        }
        String serverId = this.getCurrentServerId();
        if (serverId == null) return false;
        plugin.messageBrokerManager().publishOneWay(new ShowBossBarMessage(this.uniqueId, id, title, progress, color, overlay), serverId);
        return true;
    }

    @Override
    public boolean updateBossBarProgress(@NotNull UUID id, float progress) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer local = plugin.playerManager().getOnlinePlayer(this.uniqueId);
        if (local != null) {
            PacketBossEvent event = new PacketBossEvent(id);
            event.setProgress(progress);
            local.connection.sendPacket(ClientboundBossEventPacket.createUpdateProgressPacket(event));
            return true;
        }
        String serverId = this.getCurrentServerId();
        if (serverId == null) return false;
        plugin.messageBrokerManager().publishOneWay(new UpdateBossBarProgressMessage(this.uniqueId, id, progress), serverId);
        return true;
    }

    @Override
    public boolean updateBossBarTitle(@NotNull UUID id, @NotNull Component title) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer local = plugin.playerManager().getOnlinePlayer(this.uniqueId);
        if (local != null) {
            PacketBossEvent event = new PacketBossEvent(id);
            event.setName(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(title)));
            local.connection.sendPacket(ClientboundBossEventPacket.createUpdateNamePacket(event));
            return true;
        }
        String serverId = this.getCurrentServerId();
        if (serverId == null) return false;
        plugin.messageBrokerManager().publishOneWay(new UpdateBossBarTitleMessage(this.uniqueId, id, title), serverId);
        return true;
    }

    @Override
    public boolean hideBossBar(@NotNull UUID id) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer local = plugin.playerManager().getOnlinePlayer(this.uniqueId);
        if (local != null) {
            local.connection.sendPacket(ClientboundBossEventPacket.createRemovePacket(id));
            return true;
        }
        String serverId = this.getCurrentServerId();
        if (serverId == null) return false;
        plugin.messageBrokerManager().publishOneWay(new HideBossBarMessage(this.uniqueId, id), serverId);
        return true;
    }

    @Override
    public boolean playSound(@NotNull Sound sound) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer local = plugin.playerManager().getOnlinePlayer(this.uniqueId);
        if (local != null) {
            Holder<SoundEvent> event = Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(sound.name().namespace(), sound.name().value())));
            long seed = sound.seed().orElseGet(() -> ThreadLocalRandom.current().nextLong());
            local.connection.sendPacket(new ClientboundSoundEntityPacket(event, AdventureHelper.toNmsSoundSource(sound.source()), local.nmsPlayer, sound.volume(), sound.pitch(), seed));
            return true;
        }
        String serverId = this.getCurrentServerId();
        if (serverId == null) return false;
        plugin.messageBrokerManager().publishOneWay(new PlaySoundMessage(this.uniqueId, sound), serverId);
        return true;
    }

    @Override
    public boolean sendToast(@NotNull Component text, @NotNull ItemStack icon, @NotNull AdvancementType frame) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer local = plugin.playerManager().getOnlinePlayer(this.uniqueId);
        if (local != null) {
            net.minecraft.world.item.ItemStack minecraftIcon = CraftItemStack.unwrap(icon);
            net.minecraft.network.chat.Component title = CraftChatMessage.fromJSON(AdventureHelper.componentToJson(text));
            // 26.1 起进度图标改用物品模板
            DisplayInfo display;
            if (VersionHelper.isOrAbove26_1) {
                display = (DisplayInfo) DisplayInfoProxy.INSTANCE.create$0(
                        ItemStackTemplateProxy.INSTANCE.fromNonEmptyStack(minecraftIcon),
                        title,
                        net.minecraft.network.chat.Component.empty(),
                        Optional.empty(),
                        frame,
                        true,
                        false,
                        true
                );
            } else {
                display = (DisplayInfo) DisplayInfoProxy.INSTANCE.create(
                        minecraftIcon,
                        title,
                        net.minecraft.network.chat.Component.empty(),
                        Optional.empty(),
                        frame,
                        true,
                        false,
                        true
                );
            }
            Object criterion = CriterionProxy.INSTANCE.create(
                    ImpossibleTriggerProxy.INSTANCE.create(),
                    ImpossibleTriggerProxy.TriggerInstanceProxy.INSTANCE.create()
            );
            AdvancementRequirements requirements = new AdvancementRequirements(List.of(List.of("impossible")));
            Advancement advancement = (Advancement) AdvancementProxy.INSTANCE.create(
                    Optional.empty(),
                    Optional.of(display),
                    AdvancementRewards.EMPTY,
                    Map.of("impossible", criterion),
                    requirements,
                    false
            );
            AdvancementProgress progress = new AdvancementProgress();
            progress.update(requirements);
            progress.grantProgress("impossible");
            // 客户端处理完成进度时会保存 Toast, 后续移除临时进度不会移除已排队的提示.
            Identifier identifier = Identifier.fromNamespaceAndPath("sparrow", "toast");
            ClientboundBundlePacket packet = new ClientboundBundlePacket(List.of(
                    new ClientboundUpdateAdvancementsPacket(
                            false,
                            List.of(new AdvancementHolder(identifier, advancement)),
                            Set.of(),
                            Map.of(identifier, progress),
                            true
                    ),
                    new ClientboundUpdateAdvancementsPacket(false, List.of(), Set.of(identifier), Map.of(), true)
            ));
            local.connection.sendPacket(packet);
            return true;
        }
        String serverId = this.getCurrentServerId();
        if (serverId == null) return false;
        plugin.messageBrokerManager().publishOneWay(new SendToastMessage(this.uniqueId, text, icon, frame), serverId);
        return true;
    }

    @Override
    public boolean sendTotemAnimation(@NotNull ItemStack totem) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer local = plugin.playerManager().getOnlinePlayer(this.uniqueId);
        if (local != null) {
            int entityId = local.nmsPlayer.getId();
            net.minecraft.world.item.ItemStack mainHand = local.nmsPlayer.getInventory().getSelectedItem();
            net.minecraft.world.item.ItemStack offHand = local.nmsPlayer.getOffhandItem().copy();
            boolean mainHandTotem = mainHand.has(DataComponents.DEATH_PROTECTION);
            net.minecraft.world.item.ItemStack animation = CraftItemStack.unwrap(totem);
            if (!animation.has(DataComponents.DEATH_PROTECTION)) {
                animation = animation.copy();
                animation.set(DataComponents.DEATH_PROTECTION, DeathProtection.TOTEM_OF_UNDYING);
            }
            List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>(5);
            if (mainHandTotem) {
                packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY))));
            }
            packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.OFFHAND, animation))));
            packets.add(new ClientboundEntityEventPacket(local.nmsPlayer, (byte) 35));
            if (mainHandTotem) {
                packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.MAINHAND, mainHand.copy()))));
            }
            packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.OFFHAND, offHand))));
            local.connection.sendPacket(new ClientboundBundlePacket(packets));
            return true;
        }
        String serverId = this.getCurrentServerId();
        if (serverId == null) return false;
        plugin.messageBrokerManager().publishOneWay(new SendTotemAnimationMessage(this.uniqueId, totem), serverId);
        return true;
    }

    @Override
    @NotNull
    public CompletableFuture<ConnectResult> connect(@NotNull String server) {
        String sourceServer = this.getCurrentServerId();
        if (sourceServer == null) return CompletableFuture.completedFuture(ConnectResult.PLAYER_OFFLINE);
        return SparrowPlugin.instance().messageBrokerManager().proxyBroker()
                .publishTwoWay(new ConnectRequest(this.uniqueId, sourceServer, server), "proxy")
                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(ConnectResponse::result);
    }

    @Override
    public boolean kick(boolean disconnectFromProxy, @Nullable Component reason) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        Component kickReason = reason == null ? Component.empty() : reason;
        if (disconnectFromProxy) {
            plugin.messageBrokerManager().proxyBroker()
                    .publishOneWay(new DisconnectMessage(this.uniqueId, AdventureHelper.componentToJson(kickReason)), "proxy");
            return true;
        }
        BukkitSparrowPlayer local = plugin.playerManager().getOnlinePlayer(this.uniqueId);
        if (local != null) {
            PlatformExecutor executor = plugin.scheduler().platform();
            Runnable kick = () -> {
                if (!local.isOnlineLocally()) return;
                local.nmsPlayer.connection.disconnect(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(kickReason)), PlayerKickEvent.Cause.PLUGIN);
            };
            if (executor.isOwnedByCurrentRegion(local.platformPlayer)) {
                kick.run();
            } else {
                executor.run(kick, () -> {}, local.platformPlayer);
            }
            return true;
        }
        String serverId = this.getCurrentServerId();
        if (serverId == null) return false;
        plugin.messageBrokerManager().publishOneWay(new KickPlayerMessage(this.uniqueId, kickReason), serverId);
        return true;
    }

    @Override
    public boolean isOnlineLocally() {
        return SparrowPlugin.instance().playerManager().getOnlinePlayer(this.uniqueId) == this;
    }

    @Override
    public boolean isOnlineGlobally() {
        return SparrowPlugin.instance().playerManager().getGlobalOnlinePlayer(this.uniqueId) != null;
    }

    @Nullable
    @Override
    public String getCurrentServerId() {
        return SparrowPlugin.instance().playerManager().registry.getCurrentServerId(this.uniqueId);
    }
}