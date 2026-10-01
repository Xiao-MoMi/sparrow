package net.momirealms.sparrow.feature.warp;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.teleport.TeleportOptions;
import net.momirealms.sparrow.player.teleport.TeleportResult;
import net.momirealms.sparrow.player.teleport.TeleportService;
import net.momirealms.sparrow.player.teleport.TeleportType;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.incendo.cloud.suggestion.Suggestion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WarpCommandsTest {
    private final InMemoryWarpStore store = new InMemoryWarpStore();
    private final WarpSettings settings = new WarpSettings();
    private final SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
    private final CommandManager feedback = mock(CommandManager.class);
    private final Player player = mock(Player.class);
    private final TeleportService teleport = mock(TeleportService.class);
    private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
        @Override
        public boolean hasPermission(CommandSender sender, String permission) {
            return permission.isEmpty() || sender.hasPermission(permission);
        }
    };
    private MockedStatic<ServerConfig> serverConfig;
    private MockedStatic<SparrowPlugin> pluginInstance;
    private WarpFeature feature;

    @BeforeEach
    void setUp() {
        when(this.plugin.configurationManager().commandsConfig().configDefinition().command("warp").getPermission()).thenReturn("sparrow.command.warp");
        when(this.plugin.configurationManager().featuresConfig().config().warp()).thenReturn(this.settings);
        when(this.plugin.dataStorage().warpStore()).thenReturn(this.store);
        PlatformExecutor platform = this.plugin.scheduler().platform();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(platform).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
        when(this.plugin.playerManager().teleportService()).thenReturn(this.teleport);
        when(this.plugin.compatibilityManager().permissionMinimum(any(), any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(2));
        when(this.teleport.teleport(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(TeleportResult.SUCCESS));
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(this.player.getName()).thenReturn("Steve");
        when(this.player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(this.player.getLocation()).thenReturn(new Location(world, 10.5, 70, -3.25, 45, 10));
        this.store.put(warp("Spawn", "lobby"), warp("Shop", "survival"), warp("shrine", "lobby"), warp("矿场", "survival"));
        // 静态模拟放在最后创建, 前面的配置出错时不会遗留给其他测试
        this.serverConfig = mockStatic(ServerConfig.class);
        this.serverConfig.when(ServerConfig::serverId).thenReturn("lobby");
        this.pluginInstance = mockStatic(SparrowPlugin.class);
        this.pluginInstance.when(SparrowPlugin::instance).thenReturn(this.plugin);
        this.feature = new WarpFeature(this.plugin);
        this.feature.loadConfig();
        this.feature.onLoad();
        this.feature.onEnable();
        new WarpCommand(this.feedback, this.plugin, this.feature).registerCommand(this.manager, Command.newBuilder("warp", CommandMeta.empty()));
        new SetWarpCommand(this.feedback, this.plugin, this.feature).registerCommand(this.manager, Command.newBuilder("set-warp", CommandMeta.empty()));
        new DelWarpCommand(this.feedback, this.plugin, this.feature).registerCommand(this.manager, Command.newBuilder("del-warp", CommandMeta.empty()));
    }

    @AfterEach
    void tearDown() {
        this.feature.onDisable();
        this.serverConfig.close();
        this.pluginInstance.close();
    }

    @Test
    void completesByPrefixWithinTheLimit() throws Exception {
        assertEquals(List.of("Shop", "shrine"), this.suggest("warp sh"));
        assertEquals(List.of("矿场"), this.suggest("warp 矿"));
        set(this.settings, "suggestionLimit", 2);
        this.feature.loadConfig();
        assertEquals(List.of("Shop", "shrine"), this.suggest("warp s"));
    }

    @Test
    void teleportsToWarpsIncludingChineseNames() {
        this.execute(this.player, "warp 矿场");
        Warp mine = this.feature.registry().get("矿场");
        verify(this.teleport).teleport(this.player, "survival", mine.location(), this.settings.teleportOptions());
        this.verifyFeedback(this.player, MessageConstants.COMMAND_WARP_SUCCESS_SELF);
        // 不存在的 warp 不会传送
        this.execute(this.player, "warp nowhere");
        this.verifyFeedback(this.player, MessageConstants.COMMAND_WARP_UNKNOWN);
        // 控制台需要指定玩家
        CommandSender console = mock(CommandSender.class);
        this.execute(console, "warp spawn");
        this.verifyFeedback(console, MessageConstants.COMMAND_PLAYER_REQUIRED);
    }

    @Test
    void sendingOthersNeedsOtherPermissionAndSkipsChecks() {
        Player other = mock(Player.class);
        when(other.getName()).thenReturn("Alex");
        TeleportOptions immediate = new TeleportOptions(TeleportType.WARP, 0, 0, true, true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer("Alex")).thenReturn(other);
            bukkit.when(() -> Bukkit.getPlayer("Steve")).thenReturn(this.player);
            // 没有 .other 权限时无法执行; 客户端补全靠 Brigadier 节点的权限要求隐藏, Cloud 自身的补全不过滤参数节点
            assertThrows(CompletionException.class, () -> this.execute(this.player, "warp 矿场 Alex"));
            verify(this.teleport, never()).teleport(any(), any(), any(), any());
            // 有权限时立即传送, 不预热也不冷却
            when(this.player.hasPermission("sparrow.command.warp.other")).thenReturn(true);
            this.execute(this.player, "warp 矿场 Alex");
            verify(this.teleport).teleport(same(other), eq("survival"), any(), eq(immediate));
            this.verifyFeedback(this.player, MessageConstants.COMMAND_WARP_SUCCESS);
            // 显式指定自己仍按自己传送处理
            this.execute(this.player, "warp 矿场 Steve");
            verify(this.teleport).teleport(same(this.player), eq("survival"), any(), eq(this.settings.teleportOptions()));
            // 找不到的玩家不会传送
            assertThrows(CompletionException.class, () -> this.execute(this.player, "warp 矿场 Nobody"));
        }
        verify(this.teleport, times(2)).teleport(any(), any(), any(), any());
    }

    @Test
    void hidesWarpsWithoutPermissionWhenRestricted() throws Exception {
        set(this.settings, "permissionRestrict", true);
        when(this.player.hasPermission(WarpFeature.PERMISSION_PREFIX + "shop")).thenReturn(true);
        assertEquals(List.of("Shop"), this.suggest("warp s"));
        this.execute(this.player, "warp spawn");
        this.verifyFeedback(this.player, MessageConstants.COMMAND_WARP_UNKNOWN);
        verify(this.teleport, never()).teleport(any(), any(), any(), any());
    }

    @Test
    void setsNewWarpsAndMovesExistingOnes() throws Exception {
        this.execute(this.player, "set-warp Arena");
        Warp arena = this.feature.registry().get("arena");
        assertEquals(new WorldLocation("world", 10.5, 70, -3.25, 45, 10), arena.location());
        assertEquals("lobby", arena.server());
        assertEquals(this.player.getUniqueId(), arena.creator());
        this.verifyFeedback(this.player, MessageConstants.COMMAND_SET_WARP_CREATED);
        // 覆盖时保留 id、描述与创建信息, 名称大小写按这次输入
        Warp shop = this.feature.registry().get("shop");
        this.execute(this.player, "set-warp SHOP");
        Warp moved = this.feature.registry().get("shop");
        assertEquals(shop.id(), moved.id());
        assertEquals("SHOP", moved.name());
        assertEquals(shop.description(), moved.description());
        assertEquals(shop.createdAt(), moved.createdAt());
        assertEquals("lobby", moved.server());
        this.verifyFeedback(this.player, MessageConstants.COMMAND_SET_WARP_MOVED);
        set(this.settings, "overwriteExisting", false);
        this.execute(this.player, "set-warp spawn");
        this.verifyFeedback(this.player, MessageConstants.COMMAND_SET_WARP_EXISTS);
    }

    @Test
    void rejectsInvalidNames() {
        this.execute(this.player, "set-warp -dash");
        this.execute(this.player, "set-warp two words");
        this.execute(this.player, "set-warp " + "a".repeat(Warp.MAX_NAME_LENGTH + 1));
        verify(this.feedback, times(3)).handleCommandFeedback(eq(this.player), same(MessageConstants.COMMAND_WARP_INVALID_NAME), any(Component[].class));
        assertEquals(4, this.feature.registry().size());
    }

    @Test
    void deletesWarps() {
        this.execute(this.player, "del-warp SPAWN");
        assertNull(this.feature.registry().get("spawn"));
        this.verifyFeedback(this.player, MessageConstants.COMMAND_DEL_WARP_SUCCESS);
        this.execute(this.player, "del-warp spawn");
        this.verifyFeedback(this.player, MessageConstants.COMMAND_WARP_UNKNOWN);
    }

    private void execute(CommandSender sender, String input) {
        this.manager.commandExecutor().executeCommand(sender, input).join();
    }

    private List<String> suggest(String input) {
        return this.manager.suggestionFactory().suggestImmediately(this.player, input).list().stream().map(Suggestion::suggestion).toList();
    }

    private void verifyFeedback(CommandSender sender, net.kyori.adventure.text.TranslatableComponent.Builder key) {
        verify(this.feedback, atLeastOnce()).handleCommandFeedback(eq(sender), same(key), any(Component[].class));
    }

    private static Warp warp(String name, String server) {
        return new Warp(UUID.randomUUID(), name, "desc " + name, server, new WorldLocation("world", 0, 64, 0, 0, 0), null, 100, 100);
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
