package net.momirealms.sparrow.player;

import com.mojang.datafixers.util.Pair;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.advancement.AdvancementFrame;
import net.momirealms.sparrow.advancement.ToastPackets;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundGameTestHighlightPosPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.DeathProtection;
import net.momirealms.sparrow.proxy.bukkit.util.CraftChatMessageProxy;
import net.momirealms.sparrow.proxy.minecraft.server.level.ServerPlayerProxy;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.proxy.DisconnectMessage;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.VersionHelper;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class BukkitSparrowPlayer implements SparrowPlayer {
    private final PlayerConnection connection;
    private final Player platformPlayer;
    private final ServerPlayer nmsPlayer;

    BukkitSparrowPlayer(@NotNull PlayerConnection connection, @NotNull Player platformPlayer) {
        this.connection = connection;
        this.platformPlayer = platformPlayer;
        this.nmsPlayer = ((CraftPlayer) platformPlayer).getHandle();
    }

    @Override
    @NotNull
    public Player platformPlayer() {
        return this.platformPlayer;
    }

    @Override
    @NotNull
    public ServerPlayer nmsPlayer() {
        return this.nmsPlayer;
    }

    @Override
    @NotNull
    public net.minecraft.world.item.ItemStack getItemInMainHand() {
        return this.nmsPlayer.getInventory().getSelectedItem();
    }

    @Override
    @NotNull
    public UUID uniqueId() {
        return this.connection.uniqueId();
    }

    @Override
    @NotNull
    public String name() {
        return this.connection.name();
    }

    @Override
    @NotNull
    public PlayerConnection connection() {
        return this.connection;
    }

    @Override
    @NotNull
    @SuppressWarnings("deprecation")
    public Locale locale() {
        return Locale.forLanguageTag(this.platformPlayer.getLocale().replace('_', '-'));
    }

    @Override
    public boolean hasPermission(@NotNull String permission) {
        return this.platformPlayer.hasPermission(permission);
    }

    @Override
    public void kick(@NotNull Component reason) {
        // 未开启代理直接 kick.
        if (!VersionHelper.isBehindProxy()) {
            this.kickFromServer(reason);
            return;
        }
        // 若 1秒 后仍然在线则手动 kick.
        SparrowPlugin plugin = SparrowPlugin.instance();
        plugin.scheduler().platform().runLater(() -> this.kickFromServer(reason), () -> {}, 20, this.platformPlayer);
        // 开启了代理则通过 Redis 通知所有代理, 由持有该玩家的代理断开连接.
        plugin.messageBrokerManager().proxyBroker().publishOneWay(new DisconnectMessage(this.uniqueId(), AdventureHelper.componentToJson(reason)), "");
    }

    // Spigot 只有字符串版本的踢出接口
    @Override
    @SuppressWarnings("deprecation")
    public void kickFromServer(@NotNull Component reason) {
        this.platformPlayer.kickPlayer(AdventureHelper.componentToLegacy(reason));
    }

    @Override
    public void dropItem(@NotNull ItemStack stack) {
        Player player = this.platformPlayer;
        Location location = player.getLocation();
        Item item = player.getWorld().dropItem(player.getEyeLocation().subtract(0, 0.3, 0), stack);
        item.setPickupDelay(0);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double spread = random.nextDouble(0.02);
        double angle = random.nextDouble(Math.PI * 2);
        Vector velocity = location.getDirection()
                .multiply(0.3)
                .setY(-Math.sin(Math.toRadians(location.getPitch())) * 0.3 + 0.1 + (random.nextDouble() - random.nextDouble()) * 0.1);
        velocity.add(new Vector(Math.cos(angle) * spread, 0, Math.sin(angle) * spread));
        item.setVelocity(velocity);
    }

    @Override
    public void sendMessage(@NotNull Component message, boolean overlay) {
        Object component = CraftChatMessageProxy.INSTANCE.fromJSON(AdventureHelper.componentToJson(message));
        ServerPlayerProxy.INSTANCE.sendSystemMessage(this.nmsPlayer, component, overlay);
    }

    @Override
    public void sendTitle(@NotNull Component title, @NotNull Component subtitle, int fadeIn, int stay, int fadeOut) {
        this.connection.sendPacket(new ClientboundBundlePacket(List.of(
                new ClientboundSetTitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(title))),
                new ClientboundSetSubtitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(subtitle))),
                new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut)
        )));
    }

    @Override
    public void clearTitle() {
        this.connection.sendPacket(new ClientboundClearTitlesPacket(true));
    }

    @Override
    public void showBossBar(
            @NotNull UUID id,
            @NotNull Component title,
            float progress,
            @NotNull BossEvent.BossBarColor color,
            @NotNull BossEvent.BossBarOverlay overlay
    ) {
        PacketBossEvent event = new PacketBossEvent(id, CraftChatMessage.fromJSON(AdventureHelper.componentToJson(title)), color, overlay);
        event.setProgress(progress);
        this.connection.sendPacket(ClientboundBossEventPacket.createAddPacket(event));
    }

    @Override
    public void updateBossBarProgress(@NotNull UUID id, float progress) {
        PacketBossEvent event = new PacketBossEvent(id);
        event.setProgress(progress);
        this.connection.sendPacket(ClientboundBossEventPacket.createUpdateProgressPacket(event));
    }

    @Override
    public void updateBossBarTitle(@NotNull UUID id, @NotNull Component title) {
        PacketBossEvent event = new PacketBossEvent(id);
        event.setName(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(title)));
        this.connection.sendPacket(ClientboundBossEventPacket.createUpdateNamePacket(event));
    }

    @Override
    public void hideBossBar(@NotNull UUID id) {
        this.connection.sendPacket(ClientboundBossEventPacket.createRemovePacket(id));
    }

    @Override
    public void sendTotemAnimation(@NotNull ItemStack totem) {
        int entityId = this.nmsPlayer.getId();
        net.minecraft.world.item.ItemStack mainHand = this.getItemInMainHand();
        net.minecraft.world.item.ItemStack offHand = this.nmsPlayer.getOffhandItem().copy();
        boolean mainHandTotem = mainHand.has(DataComponents.DEATH_PROTECTION);
        net.minecraft.world.item.ItemStack animation = CraftItemStack.asNMSCopy(totem);
        animation.set(DataComponents.DEATH_PROTECTION, DeathProtection.TOTEM_OF_UNDYING);
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>(5);
        if (mainHandTotem) {
            packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY))));
        }
        packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.OFFHAND, animation))));
        packets.add(new ClientboundEntityEventPacket(this.nmsPlayer, (byte) 35));
        if (mainHandTotem) {
            packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.MAINHAND, mainHand.copy()))));
        }
        packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.OFFHAND, offHand))));
        this.connection.sendPacket(new ClientboundBundlePacket(packets));
    }

    @Override
    public void playSound(@NotNull Sound sound) {
        Holder<SoundEvent> event = Holder.direct(
                SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(sound.name().namespace(), sound.name().value()))
        );
        long seed = sound.seed().orElseGet(() -> ThreadLocalRandom.current().nextLong());
        this.connection.sendPacket(new ClientboundSoundEntityPacket(event, source(sound.source()), this.nmsPlayer, sound.volume(), sound.pitch(), seed));
    }

    private static SoundSource source(Sound.Source source) {
        return switch (source) {
            case MASTER -> SoundSource.MASTER;
            case MUSIC -> SoundSource.MUSIC;
            case RECORD -> SoundSource.RECORDS;
            case WEATHER -> SoundSource.WEATHER;
            case BLOCK -> SoundSource.BLOCKS;
            case HOSTILE -> SoundSource.HOSTILE;
            case NEUTRAL -> SoundSource.NEUTRAL;
            case PLAYER -> SoundSource.PLAYERS;
            case AMBIENT -> SoundSource.AMBIENT;
            case VOICE -> SoundSource.VOICE;
            case UI -> SoundSource.UI;
        };
    }

    @Override
    public void sendDemo() {
        this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.DEMO_EVENT, 0.0f));
    }

    @Override
    public void sendCredits() {
        this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 1.0f));
    }

    @Override
    public void sendToast(@NotNull Component text, @NotNull ItemStack icon, @NotNull AdvancementFrame frame) {
        this.connection.sendPacket(ToastPackets.create(text, icon, frame));
    }

    @Override
    public void sendDebugMarker(int x, int y, int z) {
        BlockPos position = new BlockPos(x, y, z);
        this.connection.sendPacket(new ClientboundGameTestHighlightPosPacket(position, position));
    }

    // 各版本 ServerBossEvent 构造器不同, 继承签名稳定的 BossEvent 仅用于构造封包
    private static final class PacketBossEvent extends BossEvent {

        private PacketBossEvent(@NotNull UUID id) {
            this(id, net.minecraft.network.chat.Component.empty(), BossBarColor.WHITE, BossBarOverlay.PROGRESS);
        }

        private PacketBossEvent(UUID id, net.minecraft.network.chat.Component name, BossBarColor color, BossBarOverlay overlay) {
            super(id, name, color, overlay);
        }
    }
}
