package net.momirealms.sparrow.feature.home.placehoder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.feature.home.HomeSnapshot;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;

public final class HomesListPlaceholder extends PlaceholderExpansion {
    private final HomeFeature feature = SparrowPlugin.instance().featureManager().feature(HomeFeature.ID, HomeFeature.class);

    @Override
    @NotNull
    public String getIdentifier() {
        return "sparrow-homes-list";
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
        HomeSnapshot snapshot = this.feature.service().cachedSnapshot(player.getUniqueId());
        return snapshot == null ? "" : snapshot.homes().stream().map(Home::name).collect(Collectors.joining(", "));
    }
}
