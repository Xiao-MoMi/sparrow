package net.momirealms.sparrow.feature.home;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HomeSnapshot {
    private final List<Home> homes;
    private final Map<String, Home> byName;

    public HomeSnapshot(@NotNull List<Home> homes) {
        this.homes = homes.stream().sorted(Comparator.comparing(Home::key)).toList();
        Map<String, Home> byName = new HashMap<>();
        for (Home home : this.homes) byName.put(home.key(), home);
        this.byName = Map.copyOf(byName);
    }

    @NotNull
    public List<Home> homes() {
        return this.homes;
    }

    public int size() {
        return this.homes.size();
    }

    @Nullable
    public Home get(@NotNull String name) {
        return this.byName.get(Home.key(name));
    }

    @NotNull
    public List<String> complete(@NotNull String input, int limit) {
        String prefix = Home.key(input);
        return this.homes.stream().filter(home -> home.key().startsWith(prefix)).limit(limit).map(Home::name).toList();
    }

    @NotNull
    HomeSnapshot save(@NotNull Home home) {
        List<Home> updated = new ArrayList<>(this.homes);
        // 改名移除旧名称, 同名重建则由新 UUID 替换原记录.
        updated.removeIf(existing -> existing.id().equals(home.id()) || existing.key().equals(home.key()));
        updated.add(home);
        return new HomeSnapshot(updated);
    }

    @NotNull
    HomeSnapshot delete(@NotNull UUID id) {
        return new HomeSnapshot(this.homes.stream().filter(home -> !home.id().equals(id)).toList());
    }
}