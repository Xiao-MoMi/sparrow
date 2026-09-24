package net.momirealms.sparrow.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;

public interface MessageConstants {
    TranslatableComponent.Builder COMMAND_RELOAD_TOO_FAST = Component.translatable().key("command.reload.too_fast");
    TranslatableComponent.Builder COMMAND_RELOAD_CONFIG_SUCCESS = Component.translatable().key("command.reload.config.success");
    TranslatableComponent.Builder COMMAND_RELOAD_CONFIG_FAILURE = Component.translatable().key("command.reload.config.failure");
    TranslatableComponent.Builder COMMAND_FEATURE_UNKNOWN = Component.translatable().key("command.feature.unknown");
    TranslatableComponent.Builder COMMAND_FEATURE_BUSY = Component.translatable().key("command.feature.busy");
    TranslatableComponent.Builder COMMAND_FEATURE_RESTART_REQUIRED = Component.translatable().key("command.feature.restart_required");
    TranslatableComponent.Builder COMMAND_FEATURE_SUCCESS = Component.translatable().key("command.feature.success");
    TranslatableComponent.Builder COMMAND_FEATURE_FAILURE = Component.translatable().key("command.feature.failure");
    TranslatableComponent.Builder COMMAND_FEATURE_STATUS = Component.translatable().key("command.feature.status");
}
