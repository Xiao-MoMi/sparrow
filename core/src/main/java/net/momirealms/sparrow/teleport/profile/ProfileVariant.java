package net.momirealms.sparrow.teleport.profile;

import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.processor.post.PostProcessor;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public record ProfileVariant(@NotNull Map<String, ConditionFactory> before, @NotNull Map<String, PositionProcessor> target, @NotNull Map<String, PostProcessor> after) {

    public void apply(@NotNull Teleport teleport, @NotNull BukkitSparrowPlayer player) {
        // 条件按书写顺序现场生成, 生成后就开始运行
        for (ConditionFactory condition : this.before.values()) {
            teleport.condition(condition.create(player));
        }
        for (PositionProcessor processor : this.target.values()) {
            teleport.targetProcessor(processor);
        }
        for (PostProcessor processor : this.after.values()) {
            teleport.postProcessor(processor);
        }
    }
}