package net.momirealms.sparrow.feature.home.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.momirealms.sparrow.compatibility.CompatibilityManager;
import net.momirealms.sparrow.data.access.EntryView;
import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class HomeExpansion extends PlaceholderExpansion {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final HomeFeature feature = this.plugin.featureManager().feature(HomeFeature.ID, HomeFeature.class);

    @NotNull
    @Override
    public String getIdentifier() {
        return "sparrowhome";
    }

    @NotNull
    @Override
    public String getAuthor() {
        return String.join(", ", this.plugin.javaPlugin().getPluginMeta().getAuthors());
    }

    @NotNull
    @Override
    public String getVersion() {
        return this.plugin.javaPlugin().getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Nullable
    @Override
    public String onRequest(@Nullable OfflinePlayer player, @NotNull String parameter) {
        if (!parameter.equals("count") && !parameter.equals("limit") && !parameter.equals("remaining") && !parameter.equals("list")) return null;
        if (player == null) return "";
        BukkitSparrowPlayer local = this.plugin.playerManager().getOnlinePlayer(player.getUniqueId());
        if (local == null) return "";
        EntryView<UUID, Home> entries = this.feature.data().entries(local.uniqueId()).peek();
        if (entries == null) return "";
        return switch (parameter) {
            case "count" -> Integer.toString(entries.size());
            case "limit", "remaining" -> {
                int limit = this.feature.service().getLimit(local);
                yield limit == CompatibilityManager.UNLIMITED ? "-1" : Integer.toString(parameter.equals("limit") ? limit : Math.max(0, limit - entries.size()));
            }
            case "list" -> {
                List<String> names = new ArrayList<>();
                entries.forEach((id, home) -> names.add(home.name()));
                names.sort(String.CASE_INSENSITIVE_ORDER);
                yield String.join(", ", names);
            }
            default -> null;
        };
    }
}