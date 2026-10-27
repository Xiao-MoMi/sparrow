package net.momirealms.sparrow.feature.ban;

import net.momirealms.sparrow.util.IpRange;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public sealed interface BanTarget permits BanTarget.PlayerTarget, BanTarget.IpTarget {

    @NotNull
    String getActiveKey();

    record PlayerTarget(@NotNull UUID uuid) implements BanTarget {

        @NotNull
        @Override
        public String getActiveKey() {
            return "player:" + this.uuid;
        }
    }

    record IpTarget(@NotNull IpRange range) implements BanTarget {

        @NotNull
        @Override
        public String getActiveKey() {
            return "ip:" + this.range.start() + "-" + this.range.end();
        }
    }
}