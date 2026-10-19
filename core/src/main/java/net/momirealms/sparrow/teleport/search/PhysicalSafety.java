package net.momirealms.sparrow.teleport.search;

import net.momirealms.sparrow.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PhysicalSafety(@NotNull WaterPolicy water, boolean leafSupport, boolean allowNetherRoof) {

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    @NotNull
    public static PhysicalSafety fromNbt(@NotNull CompoundTag tag) {
        // 显式的 false 是有效配置, 两个开关是否填写由字段是否存在判断.
        if (!tag.containsKey("leaf_support") || !tag.containsKey("allow_nether_roof")) {
            throw new IllegalArgumentException("Physical safety requires leaf support and nether roof settings");
        }
        return new PhysicalSafety(WaterPolicy.valueOf(tag.getString("water")), tag.getBoolean("leaf_support"), tag.getBoolean("allow_nether_roof"));
    }

    @NotNull
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("water", this.water.name());
        tag.putBoolean("leaf_support", this.leafSupport);
        tag.putBoolean("allow_nether_roof", this.allowNetherRoof);
        return tag;
    }

    public static final class Builder {
        private WaterPolicy water = WaterPolicy.DISALLOW;   // 水接触规则, null 表示未设置
        private boolean leafSupport = false;                // 树叶支撑开关, null 表示未设置
        private boolean allowNetherRoof = false;            // 下界顶层开关, null 表示未设置

        private Builder() {
        }

        /**
         * 指定候选位置允许怎样接触水.
         *
         * @param water 水接触规则, <strong>必须显式设置</strong>
         * @return 此构建器
         */
        @NotNull
        public Builder water(@NotNull WaterPolicy water) {
            this.water = water;
            return this;
        }

        /**
         * 指定树叶能否成为候选位置的脚底支撑.
         * 树叶与身体的接触由碰撞检查判定.
         *
         * @param leafSupport true 表示允许树叶支撑, false 表示排除树叶支撑
         * @return 此构建器
         */
        @NotNull
        public Builder leafSupport(boolean leafSupport) {
            this.leafSupport = leafSupport;
            return this;
        }

        /**
         * 指定下界顶层区域能否进入安全候选的允许范围.
         *
         * @param allowNetherRoof true 表示允许顶层区域, false 表示排除顶层区域
         * @return 此构建器
         */
        @NotNull
        public Builder allowNetherRoof(boolean allowNetherRoof) {
            this.allowNetherRoof = allowNetherRoof;
            return this;
        }

        @NotNull
        public PhysicalSafety build() {
            return new PhysicalSafety(this.water, this.leafSupport, this.allowNetherRoof);
        }
    }

    public enum WaterPolicy {
        HEAD_ABOVE_WATER, // 允许身体接触水, 要求眼部位于水面以上.
        DISALLOW // 要求玩家身体的检测体积与水分离.
    }
}