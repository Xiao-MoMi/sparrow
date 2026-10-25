package net.momirealms.sparrow.teleport.profile;

import net.momirealms.sparrow.teleport.condition.ConditionFactory;
import net.momirealms.sparrow.teleport.processor.post.PostProcessor;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessor;
import org.bukkit.permissions.Permissible;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class TeleportProfile {
    private final ProfileVariant base;
    private final List<PermissionOverride> overrides;
    private final ProfileVariant[] variants; // 与 overrides 按下标对应, 已经把覆盖条目写出的段换进原始设置

    public TeleportProfile(@NotNull ProfileVariant base, @NotNull List<PermissionOverride> overrides) {
        this.base = base;
        this.overrides = overrides;
        this.variants = new ProfileVariant[overrides.size()];
        for (int i = 0; i < this.variants.length; i++) {
            PermissionOverride override = overrides.get(i);
            this.variants[i] = new ProfileVariant(
                    override.before() == null ? base.before() : override.before(),
                    override.target() == null ? base.target() : override.target(),
                    override.after() == null ? base.after() : override.after()
            );
        }
    }

    @NotNull
    public ProfileVariant select(@NotNull Permissible subject) {
        // 从上往下取第一条拥有权限的覆盖, 都没有就用原始设置
        for (int i = 0; i < this.variants.length; i++) {
            if (subject.hasPermission(this.overrides.get(i).permission())) return this.variants[i];
        }
        return this.base;
    }

    @NotNull
    public ProfileVariant base() {
        return this.base;
    }

    @NotNull
    public List<PermissionOverride> overrides() {
        return this.overrides;
    }

    // 段为 null 表示这条覆盖没有写这一段, 沿用原始设置
    public record PermissionOverride(
            @NotNull String permission,
            @Nullable Map<String, ConditionFactory> before,
            @Nullable Map<String, PositionProcessor> target,
            @Nullable Map<String, PostProcessor> after
    ) {
    }
}