package net.momirealms.sparrow.teleport.profile;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public record TeleportProfiles(@NotNull Map<String, TeleportProfile> profiles) {
    public static final String DEFAULT = "default";

    public TeleportProfiles {
        if (!profiles.containsKey(DEFAULT)) throw new IllegalArgumentException("Teleport profiles require a '" + DEFAULT + "' profile");
    }

    @NotNull
    public TeleportProfile get(@NotNull String name) {
        TeleportProfile profile = this.profiles.get(name);
        if (profile == null) throw new IllegalArgumentException("Unknown teleport profile: " + name);
        return profile;
    }

    @NotNull
    public TeleportProfile defaultProfile() {
        return this.profiles.get(DEFAULT);
    }
}