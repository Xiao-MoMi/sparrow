package net.momirealms.sparrow.feature.warp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.momirealms.sparrow.util.WorldLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WarpRegistryTest {
    private final InMemoryWarpStore store = new InMemoryWarpStore();
    private final List<WarpMessage> published = new ArrayList<>();
    private final WarpRegistry registry = new WarpRegistry(this.store, "lobby", this.published::add);

    @Test
    void loadsEverythingAndCompletesByPrefix() {
        this.store.put(warp("Spawn", 1), warp("Shop", 1), warp("shrine", 1), warp("Arena", 1), warp("矿场", 1));
        this.registry.load();
        assertEquals(5, this.registry.size());
        assertEquals("Spawn", this.registry.get("SPAWN").name());
        assertEquals(List.of("Arena", "Shop", "shrine", "Spawn", "矿场"), this.registry.all().stream().map(Warp::name).toList());
        // 前缀忽略大小写, 结果按名称键排序, 超过上限时截断
        assertEquals(List.of("Shop", "shrine", "Spawn"), this.registry.complete("S", warp -> true, 10));
        assertEquals(List.of("Shop", "shrine"), this.registry.complete("sh", warp -> true, 10));
        assertEquals(List.of("Arena", "Shop"), this.registry.complete("", warp -> true, 2));
        assertEquals(List.of("shrine"), this.registry.complete("s", warp -> warp.name().equals("shrine"), 10));
        assertEquals(List.of("矿场"), this.registry.complete("矿", warp -> true, 10));
        assertTrue(this.registry.complete("x", warp -> true, 10).isEmpty());
    }

    @Test
    void savesToTheStoreBeforeUpdatingAndBroadcasting() {
        this.registry.load();
        Warp spawn = warp("Spawn", 1);
        assertTrue(this.registry.save(spawn).join());
        assertSame(spawn, this.registry.get("spawn"));
        assertEquals(WarpMessage.Type.SAVE, this.published.getFirst().type());
        // 名称被另一个 warp 占用时不更新也不广播
        assertFalse(this.registry.save(warp("SPAWN", 2)).join());
        assertSame(spawn, this.registry.get("spawn"));
        assertEquals(1, this.published.size());
        assertTrue(this.registry.delete(spawn.id()).join());
        assertNull(this.registry.get("spawn"));
        assertEquals(WarpMessage.Type.DELETE, this.published.getLast().type());
    }

    @Test
    void appliesMessagesFromOtherServers() {
        Warp spawn = warp("Spawn", 10);
        this.store.put(spawn);
        this.registry.load();
        // 自己发出的通知不重复处理
        Warp mine = warp("Mine", 10);
        this.registry.accept(WarpMessage.save("lobby", mine));
        assertNull(this.registry.get("mine"));
        // 改名后旧名称查不到, 更早的旧通知不覆盖新数据
        Warp renamed = copy(spawn, "Hub", 20);
        this.registry.accept(WarpMessage.save("survival", renamed));
        assertNull(this.registry.get("spawn"));
        assertSame(renamed, this.registry.get("hub"));
        this.registry.accept(WarpMessage.save("survival", copy(spawn, "Old", 15)));
        assertSame(renamed, this.registry.get(spawn.id()));
        assertNull(this.registry.get("old"));
        // 本地同名的另一条是旧数据, 被新数据替换
        Warp stale = warp("Arena", 1);
        this.registry.accept(WarpMessage.save("survival", stale));
        Warp arena = warp("arena", 30);
        this.registry.accept(WarpMessage.save("survival", arena));
        assertNull(this.registry.get(stale.id()));
        assertSame(arena, this.registry.get("ARENA"));
        this.registry.accept(WarpMessage.delete("survival", arena.id()));
        assertNull(this.registry.get("arena"));
        assertEquals(List.of("Hub"), this.registry.complete("", warp -> true, 10));
        // 整表重读删除数据库里已经不存在的条目
        this.store.warps.clear();
        this.store.put(warp("Fresh", 1));
        this.registry.accept(WarpMessage.reload("survival"));
        assertNull(this.registry.get("hub"));
        assertEquals(List.of("Fresh"), this.registry.all().stream().map(Warp::name).toList());
    }

    @Test
    void messagesRoundTripEveryField() {
        Warp full = new Warp(UUID.randomUUID(), "矿场", "Deep mine", "survival", new WorldLocation("world_nether", -1.5, 64.25, 3.75, 90.5f, -12.25f), UUID.randomUUID(), 100, 200);
        Warp console = new Warp(UUID.randomUUID(), "Spawn", "", "lobby", new WorldLocation("world", 0, 0, 0, 0, 0), null, 1, 1);
        assertEquals(full, roundTrip(WarpMessage.save("lobby", full)).warp());
        assertEquals(console, roundTrip(WarpMessage.save("lobby", console)).warp());
        WarpMessage delete = roundTrip(WarpMessage.delete("survival", full.id()));
        assertEquals(WarpMessage.Type.DELETE, delete.type());
        assertEquals(full.id(), delete.id());
        assertEquals("survival", delete.origin());
        assertEquals(WarpMessage.Type.RELOAD, roundTrip(WarpMessage.reload("lobby")).type());
    }

    private static WarpMessage roundTrip(WarpMessage message) {
        message.setTargetServer("");
        ByteBuf buffer = Unpooled.buffer();
        WarpMessage.CODEC.encode(buffer, message);
        return WarpMessage.CODEC.decode(buffer);
    }

    private static Warp warp(String name, long updatedAt) {
        return new Warp(UUID.randomUUID(), name, "", "lobby", new WorldLocation("world", 0, 64, 0, 0, 0), null, updatedAt, updatedAt);
    }

    private static Warp copy(Warp warp, String name, long updatedAt) {
        return new Warp(warp.id(), name, warp.description(), warp.server(), warp.location(), warp.creator(), warp.createdAt(), updatedAt);
    }
}
