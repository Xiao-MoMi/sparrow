package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.util.UUIDUtils;
import net.momirealms.sparrow.util.WorldLocation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static net.momirealms.sparrow.database.HomeStore.Status.*;
import static org.junit.jupiter.api.Assertions.*;

public final class HomeStoreContract {
    private HomeStoreContract() {
    }

    public static void verify(HomeStore homes) throws Exception {
        homes.initialize().join();
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        WorldLocation location = new WorldLocation("家园", -34.75, 66.25, 18.5, 90.25f, -12.5f);
        Home first = new Home(UUIDUtils.createV7(), owner, "Home", "survival", location, 1000, 1000);
        Home second = new Home(UUIDUtils.createV7(), other, "HOME", "lobby", location, 2000, 2000);
        // 同名记录按所有者隔离, 包括 UUID 查询和创建时间的往返.
        assertEquals(new HomeStore.SaveResult(SUCCESS, first), homes.create(first).join());
        assertEquals(new HomeStore.SaveResult(SUCCESS, second), homes.create(second).join());
        assertEquals(first, homes.findByName(owner, "HOME").join().orElseThrow());
        assertEquals(second, homes.find(second.id()).join().orElseThrow());
        assertEquals(List.of(first), homes.loadByOwner(owner).join());
        assertEquals(List.of(second), homes.loadByOwner(other).join());
        assertEquals(1L, homes.countByOwner(owner).join());
        Home duplicate = new Home(UUIDUtils.createV7(), owner, "home", first.server(), location, 3000, 3000);
        assertEquals(new HomeStore.SaveResult(DUPLICATE_NAME, null), homes.create(duplicate).join());
        assertTrue(homes.find(duplicate.id()).join().isEmpty());
        Home wrongOwner = new Home(first.id(), other, "Wrong", first.server(), location, 4000, 4000);
        assertEquals(new HomeStore.SaveResult(NOT_FOUND, null), homes.update(wrongOwner).join());
        assertFalse(homes.delete(other, first.id()).join());
        assertEquals(first, homes.find(first.id()).join().orElseThrow());

        // 完整更新所有可编辑字段, 创建时间以已存记录为准.
        WorldLocation moved = new WorldLocation("world_nether", 20.5, 80, -100.25, -120.5f, 25.25f);
        Home tampered = new Home(first.id(), owner, "基地", "nether", moved, 9999, 5000);
        Home saved = new Home(first.id(), owner, tampered.name(), tampered.server(), moved, first.createdAt(), tampered.updatedAt());
        assertEquals(new HomeStore.SaveResult(SUCCESS, saved), homes.update(tampered).join());
        assertEquals(saved, homes.findByName(owner, "基地").join().orElseThrow());
        assertTrue(homes.findByName(owner, "home").join().isEmpty());

        Home occupied = new Home(UUIDUtils.createV7(), owner, "Mine", "survival", location, 1000, 1000);
        assertEquals(SUCCESS, homes.create(occupied).join().status());
        Home conflict = new Home(first.id(), owner, "MINE", "other", location, 0, 0);
        assertEquals(new HomeStore.SaveResult(DUPLICATE_NAME, null), homes.update(conflict).join());
        assertEquals(saved, homes.find(first.id()).join().orElseThrow());

        // 最大长度名称转小写可能变长, 各存储的名称键必须完整保留.
        Home expandedKey = new Home(UUIDUtils.createV7(), owner, "İ".repeat(Home.MAX_NAME_LENGTH), "survival", location, 1000, 1000);
        assertEquals(SUCCESS, homes.create(expandedKey).join().status());
        assertEquals(expandedKey, homes.findByName(owner, expandedKey.name()).join().orElseThrow());

        Home raceA = new Home(UUIDUtils.createV7(), owner, "Race", "survival", location, 1000, 1000);
        Home raceB = new Home(UUIDUtils.createV7(), owner, "RACE", "survival", location, 2000, 2000);
        List<HomeStore.SaveResult> creates = concurrent(() -> homes.create(raceA).join(), () -> homes.create(raceB).join());
        assertEquals(1L, creates.stream().filter(result -> result.status() == SUCCESS).count());
        assertEquals(1L, creates.stream().filter(result -> result.status() == DUPLICATE_NAME).count());

        // 并发编辑均能提交, 随后的写入即使修改时间更早也会覆盖.
        Home editA = new Home(first.id(), owner, "Alpha", "a", location, first.createdAt(), 6000);
        Home editB = new Home(first.id(), owner, "Beta", "b", moved, first.createdAt(), 7000);
        assertEquals(List.of(new HomeStore.SaveResult(SUCCESS, editA), new HomeStore.SaveResult(SUCCESS, editB)),
                concurrent(() -> homes.update(editA).join(), () -> homes.update(editB).join()));
        assertTrue(List.of(editA, editB).contains(homes.find(first.id()).join().orElseThrow()));
        Home latest = new Home(first.id(), owner, "Latest", "last", location, first.createdAt(), 1);
        assertEquals(new HomeStore.SaveResult(SUCCESS, latest), homes.update(latest).join());
        assertEquals(latest, homes.find(first.id()).join().orElseThrow());

        // 按 UUID 删除, 旧 UUID 的后续操作不会影响同名新建记录.
        assertTrue(homes.delete(owner, first.id()).join());
        assertEquals(new HomeStore.SaveResult(NOT_FOUND, null), homes.update(latest).join());
        assertTrue(homes.find(first.id()).join().isEmpty());
        Home recreated = new Home(UUIDUtils.createV7(), owner, "Latest", "survival", location, 8000, 8000);
        assertEquals(SUCCESS, homes.create(recreated).join().status());
        assertFalse(homes.delete(owner, first.id()).join());
        assertEquals(recreated, homes.findByName(owner, "latest").join().orElseThrow());

        assertEquals(4L, homes.countByOwner(owner).join());
        assertEquals(4L, homes.deleteAll(new HomeStore.Filter(owner, null, null)).join());
        assertTrue(homes.loadByOwner(owner).join().isEmpty());
        assertEquals(0L, homes.countByOwner(owner).join());
        assertEquals(0L, homes.deleteAll(new HomeStore.Filter(owner, null, null)).join());
        assertEquals(List.of(second), homes.loadByOwner(other).join());
        assertEquals(1L, homes.deleteAll(new HomeStore.Filter(other, null, null)).join());
        verifyFilters(homes, owner, other);
    }

    private static void verifyFilters(HomeStore homes, UUID owner, UUID other) {
        assertThrows(IllegalArgumentException.class, () -> new HomeStore.Filter(null, null, null));
        for (int mask = 1; mask < 8; mask++) {
            for (int i = 0; i < 8; i++) {
                Home home = new Home(UUIDUtils.createV7(), (i & 1) == 0 ? owner : other, "filter" + i,
                        (i & 2) == 0 ? "survival" : "lobby", new WorldLocation((i & 4) == 0 ? "world" : "World", 1, 2, 3, 4, 5), 0, 0);
                assertEquals(SUCCESS, homes.create(home).join().status());
            }
            HomeStore.Filter filter = new HomeStore.Filter((mask & 1) != 0 ? owner : null, (mask & 2) != 0 ? "survival" : null, (mask & 4) != 0 ? "world" : null);
            long expected = 8L >> Integer.bitCount(mask);
            assertEquals(expected, homes.deleteAll(filter).join(), "filter mask " + mask);
            assertEquals(0L, homes.deleteAll(filter).join());
            assertEquals(8L - expected, homes.countByOwner(owner).join() + homes.countByOwner(other).join());
            assertEquals(0L, homes.deleteAll(new HomeStore.Filter(null, "' OR 1=1 --", null)).join());
            homes.deleteAll(new HomeStore.Filter(owner, null, null)).join();
            homes.deleteAll(new HomeStore.Filter(other, null, null)).join();
        }
    }

    private static <T> List<T> concurrent(Callable<T> first, Callable<T> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CyclicBarrier start = new CyclicBarrier(2);
            var left = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return first.call();
            });
            var right = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return second.call();
            });
            return List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
        }
    }
}
