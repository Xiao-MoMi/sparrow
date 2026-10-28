package net.momirealms.sparrow.feature.deathmessages;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class DeathMessagesFeature implements Feature {
    public static final String ID = "death_messages";

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private volatile @Nullable DeathMessagesConfig config;

    @NotNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supportsHotEnable() {
        return true;
    }

    @Override
    public boolean supportsHotDisable() {
        return true;
    }

    private DeathMessagesConfig readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features/death_messages.yml");
        try {
            return YamlMapperFactory.builder()
                    .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                    .build()
                    .create(DeathMessagesSettings.class, DeathMessagesSettings::new)
                    .load(path)
                    .value()
                    .toConfig();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load " + path, exception);
        }
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onEnable() {
        this.config = this.readConfig();
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onReload() {
        this.config = this.readConfig();
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onDisable() {
        this.config = null;
        return CompletableFuture.completedFuture(null);
    }

    @Nullable
    DeathMessagesConfig config() {
        return this.config;
    }
}