package net.momirealms.sparrow.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;

public interface MessageConstants {
    TranslatableComponent.Builder COMMAND_PLAYER_REQUIRED = Component.translatable().key("command.player.required");
    TranslatableComponent.Builder COMMAND_PLAYER_DEAD = Component.translatable().key("command.player.dead");
    TranslatableComponent.Builder COMMAND_WORKBENCH_SUCCESS = Component.translatable().key("command.workbench.success");
    TranslatableComponent.Builder COMMAND_ANVIL_SUCCESS = Component.translatable().key("command.anvil.success");
    TranslatableComponent.Builder COMMAND_GRINDSTONE_SUCCESS = Component.translatable().key("command.grindstone.success");
    TranslatableComponent.Builder COMMAND_SMITHINGTABLE_SUCCESS = Component.translatable().key("command.smithingtable.success");
    TranslatableComponent.Builder COMMAND_STONECUTTER_SUCCESS = Component.translatable().key("command.stonecutter.success");
    TranslatableComponent.Builder COMMAND_CARTOGRAPHYTABLE_SUCCESS = Component.translatable().key("command.cartographytable.success");
    TranslatableComponent.Builder COMMAND_LOOM_SUCCESS = Component.translatable().key("command.loom.success");
    TranslatableComponent.Builder COMMAND_HEAL_SUCCESS = Component.translatable().key("command.heal.success");
    TranslatableComponent.Builder COMMAND_FEED_SUCCESS = Component.translatable().key("command.feed.success");
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
