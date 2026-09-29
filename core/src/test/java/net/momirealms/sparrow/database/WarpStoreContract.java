package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.util.WorldLocation;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// 各数据库的 warp 存储共用的行为校验, 覆盖读写还原、忽略大小写的重名判断、改名与批量删除
public final class WarpStoreContract {

    private WarpStoreContract() {
    }

    public static void verify(WarpStore warps) {
        warps.initialize().join();
        UUID creator = UUID.randomUUID();
        Warp spawn = new Warp(UUID.randomUUID(), "Spawn", "", "lobby", new WorldLocation("world", 0.5, 64, -12.25, 90.5f, -10.25f), creator, 1000, 1000);
        Warp mine = new Warp(UUID.randomUUID(), "矿场", "Deep mine", "survival", new WorldLocation("world_nether", 1, 2, 3, 0, 0), null, 1100, 1100);
        assertTrue(warps.save(spawn).join());
        assertTrue(warps.save(mine).join());
        // 读回全部字段, 按名称键排序, 按名称查找时忽略大小写
        assertEquals(List.of(spawn, mine), warps.loadAll().join());
        assertEquals(spawn, warps.findByName("SPAWN").join().orElseThrow());
        assertEquals(mine, warps.find(mine.id()).join().orElseThrow());
        // 名称被另一个 warp 占用时不写入, 同一个 warp 可以改名或只改大小写
        Warp duplicate = new Warp(UUID.randomUUID(), "spawn", "", "lobby", spawn.location(), null, 1200, 1200);
        assertFalse(warps.save(duplicate).join());
        assertTrue(warps.find(duplicate.id()).join().isEmpty());
        Warp renamed = new Warp(spawn.id(), "SPAWN", "Main spawn", spawn.server(), spawn.location(), creator, spawn.createdAt(), 1300);
        assertTrue(warps.save(renamed).join());
        assertEquals(renamed, warps.findByName("spawn").join().orElseThrow());
        assertFalse(warps.save(new Warp(spawn.id(), "矿场", "", spawn.server(), spawn.location(), creator, spawn.createdAt(), 1400)).join());
        // 按世界、按服务器批量删除
        Warp arena = new Warp(UUID.randomUUID(), "Arena", "", "lobby", new WorldLocation("arena", 0, 0, 0, 0, 0), null, 1500, 1500);
        assertTrue(warps.save(arena).join());
        assertEquals(1, warps.deleteByWorld("lobby", "arena").join());
        assertEquals(1, warps.deleteByServer("survival").join());
        assertTrue(warps.delete(spawn.id()).join());
        assertFalse(warps.delete(spawn.id()).join());
        assertTrue(warps.loadAll().join().isEmpty());
    }
}
