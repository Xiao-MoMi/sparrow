package net.momirealms.sparrow.plugin.command;

import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.incendo.cloud.permission.Permission;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FeaturePermissionTest {

    @Test
    void disabledFeatureRejectsWithItsOwnPermission() {
        AtomicBoolean enabled = new AtomicBoolean(false);
        AtomicInteger executed = new AtomicInteger();
        FeaturePermission requirement = new FeaturePermission("ban", enabled::get);
        org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(CommandSender sender, String permission) {
                return true;
            }
        };
        manager.command(Command.<CommandSender>newBuilder("ban", CommandMeta.empty())
                .permission(Permission.allOf(Permission.of("sparrow.command.ban"), requirement))
                .handler(context -> executed.incrementAndGet()));
        CommandSender sender = mock(CommandSender.class);

        // 异常里带的是命令的完整组合权限, 需要从中找出未启用的模块
        CompletionException failure = assertThrows(CompletionException.class, () -> manager.commandExecutor().executeCommand(sender, "ban").join());
        NoPermissionException denied = assertInstanceOf(NoPermissionException.class, failure.getCause());
        assertSame(requirement, FeaturePermission.findDisabled(denied.permissionResult().permission()));
        assertEquals(0, executed.get());

        enabled.set(true);
        manager.commandExecutor().executeCommand(sender, "ban").join();
        assertEquals(1, executed.get());
        assertNull(FeaturePermission.findDisabled(Permission.allOf(Permission.of("sparrow.command.ban"), requirement)));
    }

    @Test
    void ordinaryPermissionsHaveNoDisabledFeature() {
        assertNull(FeaturePermission.findDisabled(Permission.of("sparrow.command.ban")));
        assertNull(FeaturePermission.findDisabled(Permission.empty()));
    }
}
