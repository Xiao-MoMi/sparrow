package net.momirealms.sparrow.feature.highlight;

import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.momirealms.sparrow.player.PlayerConnection;
import net.momirealms.sparrow.proxy.minecraft.world.entity.EntityProxy;
import net.momirealms.sparrow.proxy.minecraft.world.entity.SlimeDataProxy;
import net.momirealms.sparrow.proxy.minecraft.world.entity.EntityTypesProxy;
import net.momirealms.sparrow.proxy.minecraft.world.scores.PlayerTeamProxy;
import net.momirealms.sparrow.proxy.minecraft.world.scores.TeamColorProxy;
import net.momirealms.sparrow.util.VersionHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** 向选定玩家发送大小为 2 的隐形发光史莱姆, 显示方块轮廓. */
final class HighlightBlocks {
    private static final int BATCH_SIZE = 1024;
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 2));
    private static final AtomicInteger TEAM_IDS = new AtomicInteger();
    // 元数据索引随实体继承层次变化, 从当前版本声明字段的类读取索引及序列化器.
    @SuppressWarnings("unchecked")
    private static final List<SynchedEntityData.DataValue<?>> DATA = List.of(
            SynchedEntityData.DataValue.create((EntityDataAccessor<Byte>) EntityProxy.INSTANCE.getDataSharedFlagsId(), (byte) 0x60),
            SynchedEntityData.DataValue.create((EntityDataAccessor<Integer>) SlimeDataProxy.INSTANCE.getIdSize(), 2)
    );

    private final List<ClientboundBundlePacket> spawn = new ArrayList<>();
    private final ClientboundBundlePacket destroy;

    HighlightBlocks(@NotNull HighlightRegion region, boolean @Nullable [] solid, @NotNull NamedTextColor color) {
        EntityType<?> entityType = (EntityType<?>) EntityTypesProxy.INSTANCE.getSlime();
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>(BATCH_SIZE * 2 + 1);
        List<Packet<? super ClientGamePacketListener>> removals = new ArrayList<>();
        int[] ids = new int[BATCH_SIZE];
        int count = 0;
        PlayerTeam team = null;
        for (int x = 0; x < region.sizeX(); x++) {
            for (int y = 0; y < region.sizeY(); y++) {
                for (int z = 0; z < region.sizeZ(); z++) {
                    if (!region.visible(x, y, z, solid)) {
                        continue;
                    }
                    if (count == 0) {
                        team = this.createTeam(color);
                    }
                    int id = ENTITY_IDS.getAndIncrement();
                    UUID uuid = UUID.randomUUID();
                    ids[count++] = id;
                    team.getPlayers().add(uuid.toString());
                    packets.add(new ClientboundAddEntityPacket(id, uuid, region.minX() + x + 0.5, region.minY() + y, region.minZ() + z + 0.5, 0, 0, entityType, 0, Vec3.ZERO, 0));
                    packets.add(new ClientboundSetEntityDataPacket(id, DATA));
                    if (count == BATCH_SIZE) {
                        this.finishBatch(team, packets, ids, count, removals);
                        count = 0;
                    }
                }
            }
        }
        if (count > 0) {
            this.finishBatch(team, packets, ids, count, removals);
        }
        this.destroy = new ClientboundBundlePacket(removals);
    }

    @NotNull
    private PlayerTeam createTeam(NamedTextColor color) {
        PlayerTeam team = new PlayerTeam(new Scoreboard(), "sparrow_highlight_" + TEAM_IDS.getAndIncrement());
        if (VersionHelper.isOrAbove26_2) {
            PlayerTeamProxy.INSTANCE.setColor(team, Optional.of(TeamColorProxy.INSTANCE.byName(color.toString())));
        } else {
            team.setColor(ChatFormatting.getByName(color.toString()));
        }
        team.setCollisionRule(Team.CollisionRule.NEVER);
        return team;
    }

    private void finishBatch(PlayerTeam team, List<Packet<? super ClientGamePacketListener>> packets, int[] ids, int count, List<Packet<? super ClientGamePacketListener>> removals) {
        // 每批先生成实体并设置大小和发光, 最后加入颜色队伍, 共至多 2049 个子包.
        packets.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        this.spawn.add(new ClientboundBundlePacket(List.copyOf(packets)));
        packets.clear();
        removals.add(ClientboundSetPlayerTeamPacket.createRemovePacket(team));
        removals.add(new ClientboundRemoveEntitiesPacket(Arrays.copyOf(ids, count)));
    }

    void show(@NotNull PlayerConnection connection) {
        for (int i = 0; i < this.spawn.size(); i++) {
            connection.sendPacket(this.spawn.get(i));
        }
    }

    void destroy(@NotNull PlayerConnection connection) {
        connection.sendPacket(this.destroy);
    }

    @NotNull
    ClientboundBundlePacket removalPacket() {
        return this.destroy;
    }
}
