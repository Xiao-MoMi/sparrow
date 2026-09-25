package net.momirealms.sparrow.feature.highlight;

import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.scores.Team;
import net.momirealms.sparrow.proxy.MinecraftPredicate;
import net.momirealms.sparrow.proxy.minecraft.world.entity.EntityTypesProxy;
import net.momirealms.sparrow.reflection.SReflection;
import net.momirealms.sparrow.util.VersionHelper;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class HighlightCompatibilitySmoke {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        SReflection.setActivePredicate(new MinecraftPredicate(args[0], List.of()));
        Class<?> sizeOwner = Class.forName(VersionHelper.isOrAbove26_2 ? "net.minecraft.world.entity.monster.cubemob.AbstractCubeMob" : "net.minecraft.world.entity.monster.Slime");
        Field sizeField = sizeOwner.getDeclaredField("ID_SIZE");
        sizeField.setAccessible(true);
        EntityDataAccessor<?> sizeAccessor = (EntityDataAccessor<?>) sizeField.get(null);
        assert sizeAccessor.serializer() == EntityDataSerializers.INT;
        HighlightBlocks blocks = new HighlightBlocks(new HighlightRegion(0, 64, 0, 20, 20, 20), null, NamedTextColor.RED);
        Field spawnField = HighlightBlocks.class.getDeclaredField("spawn");
        spawnField.setAccessible(true);
        List<ClientboundBundlePacket> bundles = (List<ClientboundBundlePacket>) spawnField.get(blocks);
        Set<Integer> ids = new HashSet<>();
        Set<String> teamNames = new HashSet<>();
        int metadata = 0;
        int teams = 0;
        for (ClientboundBundlePacket bundle : bundles) {
            int count = 0;
            Set<String> members = new HashSet<>();
            boolean teamSeen = false;
            for (var packet : bundle.subPackets()) {
                assert !teamSeen;
                count++;
                if (packet instanceof ClientboundAddEntityPacket spawn) {
                    assert ids.add(spawn.getId());
                    assert spawn.getId() >= Integer.MAX_VALUE / 4;
                    assert spawn.getType() == EntityTypesProxy.INSTANCE.getSlime();
                    members.add(spawn.getUUID().toString());
                    assert spawn.getX() % 1 == 0.5 && spawn.getZ() % 1 == 0.5;
                    assert spawn.getY() % 1 == 0;
                } else if (packet instanceof ClientboundSetEntityDataPacket data) {
                    assert ids.contains(data.id());
                    assert data.packedItems().size() == 2;
                    assert data.packedItems().getFirst().id() == 0;
                    assert data.packedItems().getFirst().value().equals((byte) 0x60);
                    assert data.packedItems().get(1).id() == sizeAccessor.id();
                    assert data.packedItems().get(1).serializer() == sizeAccessor.serializer();
                    assert data.packedItems().get(1).value().equals(2);
                    metadata++;
                } else if (packet instanceof ClientboundSetPlayerTeamPacket team) {
                    assert teamNames.add(team.getName());
                    assert new HashSet<>(team.getPlayers()).equals(members);
                    assert team.getParameters().isPresent();
                    Object parameters = team.getParameters().get();
                    String collisionAccessor = VersionHelper.isOrAbove26_2 ? "collisionRule" : "getCollisionRule";
                    assert parameters.getClass().getMethod(collisionAccessor).invoke(parameters) == Team.CollisionRule.NEVER;
                    teamSeen = true;
                    teams++;
                }
            }
            assert teamSeen;
            assert members.size() <= 1024;
            assert count == members.size() * 2 + 1;
        }
        assert ids.size() == 2168;
        assert metadata == 2168;
        assert teams == 3;
        Field destroyField = HighlightBlocks.class.getDeclaredField("destroy");
        destroyField.setAccessible(true);
        ClientboundBundlePacket destroy = (ClientboundBundlePacket) destroyField.get(blocks);
        Set<Integer> removed = new HashSet<>();
        Set<String> removedTeams = new HashSet<>();
        boolean expectTeam = true;
        for (var packet : destroy.subPackets()) {
            if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
                assert !expectTeam;
                Object entityIds = remove.getClass().getMethod(VersionHelper.isOrAbove26_3 ? "entityIds" : "getEntityIds").invoke(remove);
                if (entityIds instanceof int[] values) {
                    for (int value : values) {
                        removed.add(value);
                    }
                } else {
                    removed.addAll((Collection<Integer>) entityIds);
                }
                expectTeam = true;
            } else if (packet instanceof ClientboundSetPlayerTeamPacket team) {
                assert expectTeam;
                assert team.getParameters().isEmpty();
                removedTeams.add(team.getName());
                expectTeam = false;
            }
        }
        assert expectTeam;
        assert removed.equals(ids);
        assert removedTeams.equals(teamNames);
        System.out.println("PASS " + args[0] + ": slime size index=" + sizeAccessor.id() + ", integer serializer, collision rule, batch order and removal parity");
    }
}
