package net.momirealms.sparrow.feature.ban;

import net.momirealms.sparrow.player.PlayerRef;
import net.momirealms.sparrow.util.IpRange;
import org.jetbrains.annotations.NotNull;

public sealed interface BanTarget {

    @NotNull
    String display();

    record PlayerTarget(@NotNull PlayerRef player) implements BanTarget {

        @NotNull
        @Override
        public String display() {
            return this.player.name();
        }
    }

    record IpTarget(@NotNull IpRange range) implements BanTarget {

        @Override
        @NotNull
        public String display() {
            return this.range.toString();
        }
    }

    // id 不含 # 前缀
    record IdTarget(@NotNull String id) implements BanTarget {

        @Override
        @NotNull
        public String display() {
            return BanRecord.ID_PREFIX + this.id;
        }
    }
}
