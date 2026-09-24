package net.momirealms.sparrow.feature;

import net.momirealms.sparrow.feature.quickshulker.QuickShulkerSettings;
import net.momirealms.sparrow.plugin.Plugin;
import net.momirealms.sparrow.plugin.configuration.ConfigurationManager;
import net.momirealms.sparrow.plugin.configuration.FeaturesConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FeatureManagerTest {
    @TempDir
    Path directory;
    private ConfigurationManager configurationManager;
    private FeaturesConfig config;
    private FeatureManager manager;
    private TestFeature feature;

    @BeforeEach
    void setUp() {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("dataFolderPath")) return this.directory;
                    throw new UnsupportedOperationException(method.getName());
                });
        this.configurationManager = new ConfigurationManager(plugin);
        this.config = this.configurationManager.featuresConfig();
        this.manager = new FeatureManager(this.config, Runnable::run, Runnable::run);
        this.feature = new TestFeature(this.config);
        this.manager.register(this.feature);
        this.manager.onEnable();
    }

    @Test
    void startupLoadsConfigAndEnablesInOrderOnTheCallingThread() {
        this.config.saveEnabled("quick-shulker", true);
        FeatureManager manager = new FeatureManager(this.config, Runnable::run, Runnable::run);
        TestFeature feature = new TestFeature(this.config);
        manager.register(feature);

        manager.onEnable();

        assertEquals(List.of("loadConfig", "onLoad", "onEnable"), feature.calls);
        assertTrue(feature.threads.stream().allMatch(thread -> thread == Thread.currentThread()));
        assertTrue(feature.enabled());
    }

    @Test
    void coldDisabledFeatureLoadsOnceAcrossRepeatedSwitches() {
        assertEquals(FeatureState.UNINSTALLED, this.feature.state().get());
        assertEquals(List.of("loadConfig"), this.feature.calls);
        this.feature.calls.clear();

        assertEquals(FeatureState.ENABLED, this.manager.setEnabled("quick-shulker", true).join());
        this.manager.setEnabled("quick-shulker", true).join();
        assertEquals(List.of("loadConfig", "onLoad", "onEnable"), this.feature.calls);
        assertTrue(this.config.load().quickShulker().enabled());

        this.feature.calls.clear();
        assertEquals(FeatureState.DISABLED, this.manager.setEnabled("quick-shulker", false).join());
        this.manager.setEnabled("quick-shulker", false).join();
        assertEquals(List.of("onDisable"), this.feature.calls);
        assertFalse(this.feature.enabledDuringDisable);
        assertFalse(this.config.load().quickShulker().enabled());

        this.feature.calls.clear();
        this.manager.setEnabled("quick-shulker", true).join();
        assertEquals(List.of("loadConfig", "onEnable"), this.feature.calls);
        this.feature.calls.clear();
        this.manager.onDisable();
        assertEquals(List.of("onDisable", "onUnload"), this.feature.calls);
        assertEquals(FeatureState.UNLOADED, this.feature.state().get());
        assertFalse(this.feature.installed());
        assertFalse(this.feature.enabled());
        assertThrows(IllegalStateException.class, () -> this.manager.setEnabled("quick-shulker", true).join());
    }

    @Test
    void stoppedFeatureLoadsConfigAsynchronouslyBeforeStartingOnThePlatformThread() throws Exception {
        LinkedBlockingQueue<Runnable> platformTasks = new LinkedBlockingQueue<>();
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            FeatureManager manager = new FeatureManager(this.config, executor, platformTasks::add);
            TestFeature feature = new TestFeature(this.config);
            manager.register(feature);
            manager.onEnable();
            feature.calls.clear();
            feature.threads.clear();

            assertTrue(manager.setEnabled("quick-shulker", true).isDone());
            assertEquals(List.of("loadConfig", "onLoad", "onEnable"), feature.calls);
            assertTrue(feature.threads.stream().allMatch(thread -> thread == Thread.currentThread()));

            manager.setEnabled("quick-shulker", false).join();
            assertTrue(feature.installed());
            feature.calls.clear();
            feature.threads.clear();
            CompletableFuture<FeatureState> start = manager.setEnabled("quick-shulker", true);
            Runnable platformTask = platformTasks.poll(5, TimeUnit.SECONDS);

            assertNotNull(platformTask);
            assertEquals(List.of("loadConfig"), feature.calls);
            assertNotSame(Thread.currentThread(), feature.threads.getFirst());
            assertFalse(start.isDone());
            assertFalse(feature.enabled());

            platformTask.run();
            assertEquals(FeatureState.ENABLED, start.join());
            assertEquals(List.of("loadConfig", "onEnable"), feature.calls);
            assertSame(Thread.currentThread(), feature.threads.get(1));
        }
    }

    @Test
    void unloadedFeatureCanBeInstalledAgain() {
        this.manager.setEnabled("quick-shulker", true).join();
        this.feature.uninstall();
        assertEquals(FeatureState.UNLOADED, this.feature.state().get());
        assertFalse(this.feature.installed());
        this.feature.calls.clear();

        this.feature.install();

        assertEquals(List.of("loadConfig", "onLoad", "onEnable"), this.feature.calls);
        assertTrue(this.feature.installed());
        assertTrue(this.feature.enabled());
    }

    @Test
    void reloadDisablesThenLoadsConfigAsynchronouslyAndEnablesWithNewOptions() throws Exception {
        this.manager.setEnabled("quick-shulker", true).join();
        this.feature.calls.clear();
        this.feature.threads.clear();
        Path path = this.directory.resolve("features.yml");
        String original = Files.readString(path);
        Files.writeString(path, original.replace("require-sneaking: true", "require-sneaking: false"));

        this.manager.onReloadStart();
        assertEquals(List.of("onDisable"), this.feature.calls);
        assertFalse(this.feature.enabled());
        assertTrue(this.feature.requireSneakingDuringDisable);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CompletableFuture.runAsync(() -> {
                this.configurationManager.reload();
                this.manager.onReloadAsync();
            }, executor).get(5, TimeUnit.SECONDS);
        }

        assertEquals(List.of("onDisable", "loadConfig"), this.feature.calls);
        assertNotSame(Thread.currentThread(), this.feature.threads.get(1));
        assertFalse(this.config.config().quickShulker().requireSneaking());
        assertFalse(this.feature.config().requireSneaking());
        assertFalse(this.feature.enabled());

        Files.writeString(path, original);
        this.manager.onReloadFinish();

        assertEquals(List.of("onDisable", "loadConfig", "onEnable"), this.feature.calls);
        assertSame(Thread.currentThread(), this.feature.threads.getFirst());
        assertSame(Thread.currentThread(), this.feature.threads.get(2));
        assertFalse(this.feature.enabledDuringDisable);
        assertTrue(this.feature.requireSneakingDuringDisable);
        assertFalse(this.feature.requireSneakingDuringEnable);
        assertFalse(this.feature.config().requireSneaking());
        assertTrue(this.feature.enabled());
    }

    @Test
    void reloadStopsAnEnabledFeatureWhenItsNewConfigIsDisabled() throws Exception {
        this.manager.setEnabled("quick-shulker", true).join();
        this.feature.calls.clear();
        Path path = this.directory.resolve("features.yml");
        Files.writeString(path, Files.readString(path).replace("enabled: true", "enabled: false"));
        this.manager.onReloadStart();
        this.configurationManager.reload();
        this.manager.onReloadAsync();

        this.manager.onReloadFinish();

        assertEquals(List.of("onDisable", "loadConfig"), this.feature.calls);
        assertEquals(FeatureState.DISABLED, this.feature.state().get());
        this.feature.calls.clear();
        this.manager.onDisable();
        assertEquals(List.of("onUnload"), this.feature.calls);
    }

    @Test
    void reloadCanLoadAFeatureThatWasDisabledAtStartup() throws Exception {
        this.feature.calls.clear();
        Path path = this.directory.resolve("features.yml");
        Files.writeString(path, Files.readString(path).replace("enabled: false", "enabled: true"));
        this.manager.onReloadStart();
        this.configurationManager.reload();
        this.manager.onReloadAsync();

        this.manager.onReloadFinish();

        assertEquals(List.of("loadConfig", "onLoad", "onEnable"), this.feature.calls);
        assertTrue(this.feature.enabled());
    }

    @Test
    void disabledFeatureStaysUnloadedAcrossReloadAndShutdown() {
        this.feature.calls.clear();
        this.manager.onReloadStart();
        this.configurationManager.reload();
        this.manager.onReloadAsync();
        this.manager.onReloadFinish();
        this.manager.onDisable();

        assertEquals(List.of("loadConfig"), this.feature.calls);
        assertEquals(FeatureState.UNINSTALLED, this.feature.state().get());
    }

    @Test
    void failedActivationKeepsTheGateClosedAndStillReleasesResources() {
        this.feature.failEnable = true;

        assertThrows(IllegalStateException.class, () -> this.manager.setEnabled("quick-shulker", true).join());
        assertTrue(this.config.load().quickShulker().enabled());
        assertEquals(FeatureState.FAILED, this.feature.state().get());
        assertFalse(this.feature.enabled());

        this.feature.calls.clear();
        this.manager.onDisable();
        assertEquals(List.of("onDisable", "onUnload"), this.feature.calls);
    }

    @Test
    void failedPersistenceDoesNotActivateTheFeature() throws Exception {
        Path path = this.directory.resolve("features.yml");
        Files.delete(path);
        Files.createDirectory(path);
        this.feature.calls.clear();

        assertThrows(RuntimeException.class, () -> this.manager.setEnabled("quick-shulker", true).join());
        assertTrue(this.feature.calls.isEmpty());
        assertFalse(this.feature.enabled());
        assertFalse(this.config.config().quickShulker().enabled());
    }

    @Test
    void shutdownStillUnloadsWhenDisableFails() {
        this.manager.setEnabled("quick-shulker", true).join();
        this.feature.calls.clear();
        this.feature.failDisable = true;

        assertThrows(IllegalStateException.class, this.manager::onDisable);

        assertEquals(List.of("onDisable", "onUnload"), this.feature.calls);
        assertFalse(this.feature.enabled());
    }

    @Test
    void loadedConfigCannotEnableAFeatureAfterShutdown() {
        this.config.saveEnabled("quick-shulker", true);
        this.manager.onReloadAsync();
        this.manager.onDisable();
        this.feature.calls.clear();

        assertThrows(IllegalStateException.class, this.manager::onReloadFinish);
        assertTrue(this.feature.calls.isEmpty());
        assertEquals(FeatureState.UNINSTALLED, this.feature.state().get());
    }

    private static final class TestFeature extends Feature<QuickShulkerSettings> {
        private final FeaturesConfig source;
        private final List<String> calls = new ArrayList<>();
        private final List<Thread> threads = new ArrayList<>();
        private boolean enabledDuringDisable;
        private boolean requireSneakingDuringDisable;
        private boolean requireSneakingDuringEnable;
        private boolean failEnable;
        private boolean failDisable;

        private TestFeature(FeaturesConfig source) {
            super("quick-shulker");
            this.source = source;
        }

        @Override
        public void loadConfig() {
            this.record("loadConfig");
            this.config = this.source.config().quickShulker();
        }

        @Override
        protected void onLoad() {
            this.record("onLoad");
        }

        @Override
        protected void onEnable() {
            this.record("onEnable");
            this.requireSneakingDuringEnable = this.config().requireSneaking();
            if (this.failEnable) {
                throw new IllegalStateException("activation failed");
            }
        }

        @Override
        protected void onDisable() {
            this.record("onDisable");
            this.enabledDuringDisable = this.enabled();
            this.requireSneakingDuringDisable = this.config().requireSneaking();
            if (this.failDisable) {
                throw new IllegalStateException("cleanup failed");
            }
        }

        @Override
        protected void onUnload() {
            this.record("onUnload");
        }

        private void record(String call) {
            this.calls.add(call);
            this.threads.add(Thread.currentThread());
        }
    }
}
