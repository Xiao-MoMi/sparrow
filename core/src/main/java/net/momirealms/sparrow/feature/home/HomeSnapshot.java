package net.momirealms.sparrow.feature.home;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HomeSnapshot {
    private final List<Home> homes;
    private final Map<String, Home> byName;

    public HomeSnapshot(@NotNull List<Home> homes) {
        this.homes = homes.stream().sorted(Comparator.comparing(Home::key)).toList();
        Map<String, Home> byName = new HashMap<>();
        for (Home home : this.homes) {
            byName.put(home.key(), home);
        }
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
        return this.homes.stream()
                .filter(home -> home.key().startsWith(prefix))
                .limit(limit)
                .map(Home::name)
                .toList();
    }
}