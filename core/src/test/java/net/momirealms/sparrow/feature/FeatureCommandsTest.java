package net.momirealms.sparrow.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.util.Index;
import net.momirealms.sparrow.feature.quickshulker.QuickShulkerSettings;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.locale.tag.IndexedArgumentTag;
import net.momirealms.sparrow.plugin.Plugin;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.AbstractCommandManager;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.command.feature.FeatureEnableCommand;
import net.momirealms.sparrow.plugin.command.feature.FeatureDisableCommand;
import net.momirealms.sparrow.plugin.command.feature.FeatureListCommand;
import net.momirealms.sparrow.plugin.configuration.CommandsConfig;
import net.momirealms.sparrow.plugin.configuration.ConfigurationManager;
import net.momirealms.sparrow.plugin.scheduler.SchedulerAdapter;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.route.Route;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.suggestion.Suggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FeatureCommandsTest {
    @TempDir
    Path directory;
    private SparrowPlugin plugin;
    private ConfigurationManager configuration;
    private FeatureManager features;
    private TestManager commands;
    private final List<Component> messages = new ArrayList<>();
    private final AtomicBoolean reloading = new AtomicBoolean();

    @BeforeEach
    void setUp() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        this.plugin = (SparrowPlugin) ((Unsafe) unsafeField.get(null)).allocateInstance(SparrowPlugin.class);
        Plugin configPlugin = proxy(Plugin.class, (instance, method, args) -> {
            if (method.getName().equals("dataFolderPath")) return this.directory;
            throw new UnsupportedOperationException(method.getName());
        });
        this.configuration = new ConfigurationManager(configPlugin);
        this.configuration.featuresConfig().saveEnabled("quick-shulker", false);
        this.features = new FeatureManager(this.configuration.featuresConfig(), Runnable::run, Runnable::run);
        this.features.register(new TestFeature("quick-shulker", false, true) {
            @Override
            public void loadConfig() {
                this.config = FeatureCommandsTest.this.configuration.featuresConfig().load().quickShulker();
            }
        });
        this.features.register(new TestFeature("always-on", true, false));
        this.features.onEnable();
        PlatformExecutor executor = proxy(PlatformExecutor.class, (instance, method, args) -> {
            if (method.getName().equals("execute") || method.getName().equals("run")) {
                ((Runnable) args[0]).run();
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        this.set("scheduler", proxy(SchedulerAdapter.class, (instance, method, args) -> executor));
        this.set("featureManager", this.features);
        this.set("configurationManager", this.configuration);
        this.set("reloading", this.reloading);
        SparrowYaml yaml = SparrowYaml.builder().build();
        YamlDocument en = yaml.load(new String(this.getClass().getResourceAsStream("/translations/en.yml").readAllBytes(), StandardCharsets.UTF_8));
        YamlDocument zh = yaml.load(new String(this.getClass().getResourceAsStream("/translations/zh_cn.yml").readAllBytes(), StandardCharsets.UTF_8));
        this.set("translationManager", proxy(TranslationManager.class, (instance, method, args) -> switch (method.getName()) {
            case "translationKeys" -> en.value().keySet().stream().map(Object::toString).collect(Collectors.toSet());
            case "render" -> {
                TranslatableComponent component = (TranslatableComponent) args[0];
                YamlDocument source = Locale.SIMPLIFIED_CHINESE.equals(args[1]) ? zh : en;
                yield MiniMessage.miniMessage().deserialize(source.getString(Route.from(component.key())), new IndexedArgumentTag(component.arguments()));
            }
            default -> throw new UnsupportedOperationException(method.getName());
        }));
        this.commands = new TestManager(this.plugin);
        this.commands.setFeedbackConsumer((sender, key, component) -> this.messages.add(component));
        for (CommandFeature command : List.of(new FeatureEnableCommand(this.commands, this.plugin),
                new FeatureDisableCommand(this.commands, this.plugin))) {
            this.commands.registerFeature(command, new CommandsConfig.ConfigDefinition().command(command.getFeatureID()));
        }
        this.commands.registerFeature(new FeatureListCommand(this.commands, this.plugin), new CommandsConfig.ConfigDefinition().command("feature_list"));
    }

    @Test
    void suggestionsTrackStateAndSwitchesArePersisted() throws Exception {
        CommandSender sender = this.sender(false, Set.of("sparrow.command.admin.feature"));
        assertEquals(List.of("quick-shulker"), this.suggestions(sender, "feature-enable"));
        assertTrue(this.suggestions(sender, "feature-disable").isEmpty());
        this.execute(sender, "sparrow feature-enable quick-shulker");
        assertTrue(this.features.feature("quick-shulker").enabled());
        assertTrue(this.configuration.featuresConfig().load().quickShulker().enabled());
        assertTrue(this.suggestions(sender, "feature-enable").isEmpty());
        assertEquals(List.of("quick-shulker"), this.suggestions(sender, "feature-disable"));
        this.execute(sender, "sparrow feature-disable quick-shulker");
        assertFalse(this.features.feature("quick-shulker").enabled());
        assertFalse(this.configuration.featuresConfig().load().quickShulker().enabled());
        assertEquals(List.of("quick-shulker"), this.suggestions(sender, "feature-enable"));
        this.execute(sender, "sparrow feature-list");
        assertTrue(this.text().contains("Disabled"));
        assertThrows(Exception.class, () -> this.execute(sender, "sparrow feature quick-shulker on"));
    }

    @Test
    void reloadBlocksSwitchesButAllowsList() throws Exception {
        this.reloading.set(true);
        CommandSender sender = this.sender(false, Set.of("sparrow.command.admin.feature"));
        this.execute(sender, "sparrow feature-enable quick-shulker");
        assertFalse(this.features.feature("quick-shulker").enabled());
        assertTrue(this.text().contains("reload is in progress"));
        this.execute(sender, "sparrow feature-list");
        assertTrue(this.text().contains("Not installed"));
    }

    @Test
    void commandPermissionsAndUnknownIdsLeaveFeaturesUnchanged() throws Exception {
        assertThrows(Exception.class, () -> this.execute(this.sender(false, Set.of()), "sparrow feature-enable quick-shulker"));
        assertFalse(this.features.feature("quick-shulker").enabled());
        this.execute(this.sender(false, Set.of("sparrow.command.admin.feature")), "sparrow feature-enable missing");
        assertTrue(this.text().contains("missing"));
        assertTrue(this.text().contains("was not found"));
        assertFalse(this.configuration.featuresConfig().load().quickShulker().enabled());
    }

    @Test
    void panelUsesConfiguredUsagesAndChecksCurrentPermissions() throws Exception {
        FeatureEnableCommand enable = (FeatureEnableCommand) this.commands.features().value("feature_enable");
        enable.setCommandConfig(new CommandConfig(true, List.of("/custom start"), "custom.start"));
        this.commands.locale = Locale.SIMPLIFIED_CHINESE;
        this.execute(this.sender(true, Set.of("sparrow.command.admin.feature", "custom.start")), "sparrow feature-list");
        Component panel = this.messages.getLast();
        assertTrue(this.text().contains("模块管理"));
        assertTrue(this.text().contains("quick-shulker · 未安装 [启] [停] [查]"));
        assertTrue(hasClick(panel, "/custom start quick-shulker"));
        assertFalse(hasClick(panel, "/sparrow feature-disable quick-shulker"));
        assertFalse(hasClick(panel, "/custom start always-on"));
        assertFalse(hasClick(panel, "/sparrow feature-status quick-shulker"));
        assertTrue(hasClick(panel, "/sparrow feature-list 1"));
        assertTranslated(panel);

        this.messages.clear();
        this.execute(this.sender(true, Set.of("sparrow.command.admin.feature")), "sparrow feature-list");
        assertFalse(hasClick(this.messages.getLast(), "/custom start quick-shulker"));
        enable.setCommandConfig(new CommandConfig(false, List.of("/custom start"), "custom.start"));
        this.execute(this.sender(true, Set.of("sparrow.command.admin.feature", "custom.start")), "sparrow feature-list");
        assertFalse(hasClick(this.messages.getLast(), "/custom start quick-shulker"));
    }

    @Test
    void panelReflectsEnabledStateAndConsoleShowsCommands() throws Exception {
        CommandSender player = this.sender(true, Set.of("sparrow.command.admin.feature"));
        this.execute(player, "sparrow feature-enable quick-shulker");
        this.execute(player, "sparrow feature-list");
        assertFalse(hasClick(this.messages.getLast(), "/sparrow feature-enable quick-shulker"));
        assertTrue(hasClick(this.messages.getLast(), "/sparrow feature-disable quick-shulker"));
        this.messages.clear();
        this.execute(this.sender(false, Set.of("sparrow.command.admin.feature")), "sparrow feature-list");
        assertTrue(this.text().contains("/sparrow feature-disable quick-shulker"));
        assertNoClicks(this.messages.getLast());
    }

    @Test
    void paginationLimitsRowsAndClampsToLastPage() throws Exception {
        for (int i = 0; i < 8; i++) {
            this.features.register(new TestFeature("extra-" + i, false, true));
        }
        CommandSender sender = this.sender(true, Set.of("sparrow.command.admin.feature"));
        this.execute(sender, "sparrow feature-list");
        assertTrue(this.text().contains("extra-4"));
        assertFalse(this.text().contains("extra-5"));
        assertTrue(hasClick(this.messages.getLast(), "/sparrow feature-list 2"));
        this.messages.clear();
        this.execute(sender, "sparrow feature-list 2147483647");
        assertTrue(this.text().contains("2/2"));
        assertFalse(this.text().contains("extra-4"));
        assertTrue(this.text().contains("extra-7"));
        assertTrue(hasClick(this.messages.getLast(), "/sparrow feature-list 1"));
        assertFalse(hasClick(this.messages.getLast(), "/sparrow feature-list 3"));
        assertThrows(Exception.class, () -> this.execute(sender, "sparrow feature-list 0"));
    }

    private void execute(CommandSender sender, String command) throws Exception {
        this.commands.getCommandManager().commandExecutor().executeCommand(sender, command).get(5, TimeUnit.SECONDS);
    }

    private List<String> suggestions(CommandSender sender, String command) throws Exception {
        return this.commands.getCommandManager().suggestionFactory().suggest(sender, "sparrow " + command + " ").get(5, TimeUnit.SECONDS)
                .list().stream().map(Suggestion::suggestion).toList();
    }

    private CommandSender sender(boolean player, Set<String> permissions) {
        return proxy(player ? Player.class : CommandSender.class, (instance, method, args) -> switch (method.getName()) {
            case "hasPermission" -> permissions.contains(args[0]);
            case "getName", "toString" -> "Tester";
            case "isOp" -> false;
            default -> null;
        });
    }

    private void set(String name, Object value) throws Exception {
        Field field = SparrowPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(this.plugin, value);
    }

    private String text() {
        return String.join("\n", this.messages.stream().map(FeatureCommandsTest::text).toList());
    }

    private static String text(Component component) {
        return (component instanceof TextComponent text ? text.content() : "") + String.join("", component.children().stream().map(FeatureCommandsTest::text).toList());
    }

    private static boolean hasClick(Component component, String command) {
        return ClickEvent.runCommand(command).equals(component.clickEvent()) || component.children().stream().anyMatch(child -> hasClick(child, command));
    }

    private static void assertTranslated(Component component) {
        assertFalse(component instanceof TranslatableComponent);
        if (component.hoverEvent() != null && component.hoverEvent().value() instanceof Component hover) {
            assertTranslated(hover);
        }
        for (Component child : component.children()) {
            assertTranslated(child);
        }
    }

    private static void assertNoClicks(Component component) {
        assertNull(component.clickEvent());
        for (Component child : component.children()) {
            assertNoClicks(child);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<? extends T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static class TestFeature extends Feature<QuickShulkerSettings> {
        private final boolean toggleable;

        private TestFeature(String id, boolean enabled, boolean toggleable) {
            super(id);
            this.config = new QuickShulkerSettings();
            this.config.enabled(enabled);
            this.toggleable = toggleable;
        }

        @Override
        public void loadConfig() {
        }

        @Override
        public boolean hotToggleable() {
            return this.toggleable;
        }
    }

    private static final class TestCloud extends org.incendo.cloud.CommandManager<CommandSender> {
        private TestCloud() {
            super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
        }

        @Override
        public boolean hasPermission(CommandSender sender, String permission) {
            return sender.hasPermission(permission);
        }
    }

    private static final class TestManager extends AbstractCommandManager {
        private Locale locale = Locale.ENGLISH;

        private TestManager(SparrowPlugin plugin) {
            super(plugin, new TestCloud());
        }

        @Override
        protected Locale getLocale(CommandSender sender) {
            return this.locale;
        }

        @Override
        public Index<String, CommandFeature> features() {
            return Index.create(CommandFeature::getFeatureID, List.copyOf(this.registeredFeatures));
        }
    }
}
