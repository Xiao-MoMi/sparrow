package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.ban.BanQuery;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanTarget;
import net.momirealms.sparrow.util.IpRange;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// 各数据库的封禁存储共用的行为校验, 覆盖旧封禁、登录匹配、分页筛选与撤销
public final class BanStoreContract {

    private BanStoreContract() {
    }

    public static void verify(BanStore bans, UUID player) {
        bans.initialize().join();
        BanTarget.PlayerTarget target = new BanTarget.PlayerTarget(player, "Renamed");
        BanRecord account = new BanRecord(BanRecord.newId(), player, "Renamed", null, "first", "Admin", "survival", 1000, 0, 0, null);
        assertFalse(bans.saveBan(account).join());
        // 账号加 IP 的封禁覆盖同一玩家的旧封禁
        BanRecord withIp = new BanRecord(BanRecord.newId(), player, "Renamed", IpRange.parse("10.0.0.2"), "second", "Mod", "survival", 2000, 9000, 0, null);
        assertTrue(bans.saveBan(withIp).join());
        BanRecord ipOnly = new BanRecord(BanRecord.newId(), null, null, IpRange.parse("10.0.1.*"), "", "Admin", "survival", 2500, 0, 0, null);
        assertFalse(bans.saveBan(ipOnly).join());
        // 其他账号从被封的 IP 登录也会命中, 到期后不再命中; 同时命中时优先返回封禁该账号的记录
        assertEquals(withIp.id(), bans.findActiveBan(UUID.randomUUID(), IpRange.parse("10.0.0.2").start(), 3000).join().orElseThrow().id());
        assertEquals(ipOnly.id(), bans.findActiveBan(UUID.randomUUID(), IpRange.parse("10.0.1.77").start(), 3000).join().orElseThrow().id());
        assertEquals(withIp.id(), bans.findActiveBan(player, IpRange.parse("10.0.1.77").start(), 3000).join().orElseThrow().id());
        assertTrue(bans.findActiveBan(UUID.randomUUID(), IpRange.parse("10.0.0.2").start(), 9001).join().isEmpty());
        assertTrue(bans.findActiveBan(UUID.randomUUID(), IpRange.NONE, 3000).join().isEmpty());
        // 筛选按对象、执行人 (忽略大小写)、封禁时间和是否生效组合, IP 匹配覆盖它的段
        assertEquals(2L, bans.countBans(new BanQuery(target, null, 0, false, 3000)).join());
        assertEquals(1L, bans.countBans(new BanQuery(target, null, 0, true, 3000)).join());
        assertEquals(1L, bans.countBans(new BanQuery(null, "mod", 0, false, 3000)).join());
        assertEquals(2L, bans.countBans(new BanQuery(null, null, 2000, false, 3000)).join());
        assertEquals(1L, bans.countBans(new BanQuery(new BanTarget.IpTarget(IpRange.parse("10.0.1.5")), null, 0, false, 3000)).join());
        List<BanRecord> page = bans.listBans(new BanQuery(null, null, 0, false, 3000), 1, 2).join();
        assertEquals(List.of(withIp.id(), account.id()), page.stream().map(BanRecord::id).toList());
        // IP 只解除完全相同的纯 IP 封禁, ID 精确解除
        assertTrue(bans.revokeBans(new BanTarget.IpTarget(IpRange.parse("10.0.0.2")), 3000, "Admin").join().isEmpty());
        assertEquals(1, bans.revokeBans(new BanTarget.IpTarget(IpRange.parse("10.0.1.*")), 3000, "Admin").join().size());
        assertEquals(List.of(withIp.id()), bans.revokeBans(new BanTarget.IdTarget(withIp.id()), 3000, "Admin").join().stream().map(BanRecord::id).toList());
        assertTrue(bans.findActiveBan(player, IpRange.parse("10.0.0.2").start(), 3000).join().isEmpty());
        BanRecord revoked = bans.listBans(new BanQuery(new BanTarget.IdTarget(withIp.id()), null, 0, false, 3000), 0, 1).join().getFirst();
        assertEquals("Admin", revoked.revokedBy());
        assertEquals(IpRange.parse("10.0.0.2"), revoked.ip());
    }
}
