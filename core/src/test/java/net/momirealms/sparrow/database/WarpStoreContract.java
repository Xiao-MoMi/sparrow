package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.util.UUIDUtils;
import net.momirealms.sparrow.util.WorldLocation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.momirealms.sparrow.database.WarpStore.Status.*;

import static org.junit.jupiter.api.Assertions.*;

// 各数据库的 warp 存储共用的行为校验, 覆盖读写还原、忽略大小写的重名判断、改名与批量删除
public final class WarpStoreContract {

    private WarpStoreContract() {
    }

    public static void verify(WarpStore warps) {
        warps.initialize().join();
        UUID creator = UUID.randomUUID();
        Warp spawn = new Warp(UUIDUtils.createV7(), "Spawn", "", "lobby", new WorldLocation("world", 0.5, 64, -12.25, 90.5f, -10.25f), creator, 1000, 1000);
        Warp mine = new Warp(UUID.randomUUID(), "矿场", "Deep mine", "survival", new WorldLocation("world_nether", 1, 2, 3, 0, 0), null, 1100, 1100);
        assertEquals(new WarpStore.SaveResult(SUCCESS, spawn), warps.create(spawn).join());
        assertEquals(new WarpStore.SaveResult(SUCCESS, mine), warps.create(mine).join());
        // 读回全部字段, 按名称键排序, 按名称查找时忽略大小写
        assertEquals(List.of(spawn, mine), warps.loadAll().join());
        assertEquals(spawn, warps.findByName("SPAWN").join().orElseThrow());
        assertEquals(mine, warps.find(mine.id()).join().orElseThrow());
        // 名称被另一个 warp 占用时不写入, 同一个 warp 可以改名或只改大小写
        Warp duplicate = new Warp(UUID.randomUUID(), "spawn", "", "lobby", spawn.location(), null, 1200, 1200);
        assertEquals(DUPLICATE_NAME, warps.create(duplicate).join().status());
        assertTrue(warps.find(duplicate.id()).join().isEmpty());
        Warp renamed = new Warp(spawn.id(), "SPAWN", "Main spawn", spawn.server(), spawn.location(), creator, spawn.createdAt(), 1300);
        Warp tampered = new Warp(spawn.id(), renamed.name(), renamed.description(), renamed.server(), renamed.location(), UUID.randomUUID(), 9999, renamed.updatedAt());
        assertEquals(new WarpStore.SaveResult(SUCCESS, renamed), warps.update(tampered).join());
        assertEquals(new WarpStore.SaveResult(SUCCESS, renamed), warps.update(renamed).join());
        assertEquals(renamed, warps.findByName("spawn").join().orElseThrow());
        assertEquals(DUPLICATE_NAME, warps.update(new Warp(spawn.id(), "矿场", "", spawn.server(), spawn.location(), creator, spawn.createdAt(), 1400)).join().status());
        assertEquals(renamed, warps.find(spawn.id()).join().orElseThrow());
        // 时间戳只保存信息, 后一次更新可以使用更小的修改时间.
        Warp later = new Warp(spawn.id(), "Hub", "latest", "other", mine.location(), creator, spawn.createdAt(), 1200);
        assertEquals(new WarpStore.SaveResult(SUCCESS, later), warps.update(later).join());
        assertTrue(warps.findByName("spawn").join().isEmpty());
        Warp consoleEdit = new Warp(mine.id(), mine.name(), "edited", mine.server(), mine.location(), creator, 9999, 1400);
        Warp consoleSaved = warps.update(consoleEdit).join().warp();
        assertNull(consoleSaved.creator());
        assertEquals(mine.createdAt(), consoleSaved.createdAt());
        assertEquals(consoleSaved, warps.find(mine.id()).join().orElseThrow());

        Warp raceA = new Warp(UUID.randomUUID(), "Race", "", "race", spawn.location(), null, 1, 1);
        Warp raceB = new Warp(UUID.randomUUID(), "RACE", "", "race", spawn.location(), null, 2, 2);
        CompletableFuture<WarpStore.SaveResult> first = warps.create(raceA);
        CompletableFuture<WarpStore.SaveResult> second = warps.create(raceB);
        List<WarpStore.Status> statuses = List.of(first.join().status(), second.join().status());
        assertEquals(1, statuses.stream().filter(status -> status == SUCCESS).count());
        assertEquals(1, statuses.stream().filter(status -> status == DUPLICATE_NAME).count());
        assertEquals(1, warps.deleteByServer("race").join());
        // 按世界、按服务器批量删除
        Warp arena = new Warp(UUID.randomUUID(), "Arena", "", "lobby", new WorldLocation("arena", 0, 0, 0, 0, 0), null, 1500, 1500);
        assertEquals(SUCCESS, warps.create(arena).join().status());
        assertEquals(1, warps.deleteByWorld("lobby", "arena").join());
        assertEquals(1, warps.deleteByServer("survival").join());
        assertTrue(warps.delete(spawn.id()).join());
        assertFalse(warps.delete(spawn.id()).join());
        assertEquals(NOT_FOUND, warps.update(later).join().status());
        assertTrue(warps.find(spawn.id()).join().isEmpty());
        assertTrue(warps.loadAll().join().isEmpty());
    }
}
