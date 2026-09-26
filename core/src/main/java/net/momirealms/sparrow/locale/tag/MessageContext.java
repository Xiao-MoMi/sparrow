package net.momirealms.sparrow.locale.tag;

import net.kyori.adventure.pointer.Pointered;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * MiniMessage 解析时携带的上下文, 作为 target 传入后供 {@code <arg>}, {@code <papi>}, {@code <expr>} 读取.
 *
 * @param player 解析占位符时使用的玩家, 为 null 时按无玩家解析
 * @param arguments 命名参数, <strong>只读, 调用方不得修改</strong>
 */
public record MessageContext(@Nullable Player player, @NotNull Map<String, Object> arguments) implements Pointered {
    public static final MessageContext EMPTY = new MessageContext(null, Map.of());

    @NotNull
    public static MessageContext of(@NotNull Map<String, Object> arguments) {
        return new MessageContext(null, arguments);
    }

    @NotNull
    public static MessageContext of(@Nullable Player player) {
        return new MessageContext(player, Map.of());
    }

    @NotNull
    public static MessageContext of(@Nullable Player player, @NotNull Map<String, Object> arguments) {
        return new MessageContext(player, arguments);
    }

    @Nullable
    public Object argument(@NotNull String key) {
        return this.arguments.get(key);
    }
}
