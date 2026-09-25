package net.momirealms.sparrow.common.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;

public interface MessageConstants {

    TranslatableComponent.Builder NORTH = Component.translatable().key("commands.argument.blockface.north");
    TranslatableComponent.Builder EAST = Component.translatable().key("commands.argument.blockface.east");
    TranslatableComponent.Builder SOUTH = Component.translatable().key("commands.argument.blockface.south");
    TranslatableComponent.Builder WEST = Component.translatable().key("commands.argument.blockface.west");
    TranslatableComponent.Builder UP = Component.translatable().key("commands.argument.blockface.up");
    TranslatableComponent.Builder DOWN = Component.translatable().key("commands.argument.blockface.down");
    TranslatableComponent.Builder ARGUMENT_ENTITY_NOTFOUND_PLAYER = Component.translatable().key("argument.entity.notfound.player");
    TranslatableComponent.Builder ARGUMENT_ENTITY_NOTFOUND_ENTITY = Component.translatable().key("argument.entity.notfound.entity");
    TranslatableComponent.Builder ARGUMENT_PARSE_FAILURE_ENCHANTMENT = Component.translatable().key("argument.parse.failure.enchantment");
    TranslatableComponent.Builder COMMANDS_ADMIN_ENCHANT_FAILED_INCOMPATIBLE = Component.translatable().key("commands.admin.enchant.failed.incompatible");
    TranslatableComponent.Builder COMMANDS_ADMIN_ENCHANT_FAILED_LEVEL = Component.translatable().key("commands.admin.enchant.failed.level");
    TranslatableComponent.Builder COMMANDS_ADMIN_ENCHANT_FAILED_ITEMLESS = Component.translatable().key("commands.admin.enchant.failed.itemless");
    TranslatableComponent.Builder COMMANDS_ADMIN_ENCHANT_FAILED_ENTITY = Component.translatable().key("commands.admin.enchant.failed.entity");
    TranslatableComponent.Builder COMMANDS_ADMIN_ENCHANT_FAILED = Component.translatable().key("commands.admin.enchant.failed");
    TranslatableComponent.Builder COMMANDS_ADMIN_ENCHANT_SUCCESS_SINGLE = Component.translatable().key("commands.admin.enchant.success.single");
    TranslatableComponent.Builder COMMANDS_ADMIN_ENCHANT_SUCCESS_MULTIPLE = Component.translatable().key("commands.admin.enchant.success.multiple");
}
