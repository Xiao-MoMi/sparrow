package net.momirealms.sparrow.feature.home.placehoder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.momirealms.sparrow.compatibility.CompatibilityManager;
import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MaxHomesPlaceholder extends PlaceholderExpansion {
    private final HomeFeature feature = SparrowPlugin.instance().featureManager().feature(HomeFeature.ID, HomeFeature.class);

    @Override
    @NotNull
    public String getIdentifier() {
        return "sparrow-max-homes";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "XiaoMoMi";
    }

    @Override
    @NotNull
    public String getVersion() {
        return SparrowPlugin.instance().pluginVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    @Nullable
    public String onPlaceholderRequest(@Nullable Player player, @NotNull String params) {
        if (player == null || !params.equals("value")) return null;
        if (!this.feature.enabled()) return "";
        int limit = this.feature.limit(player);
        return Integer.toString(limit == CompatibilityManager.UNLIMITED ? -1 : limit);
    }
}