package net.momirealms.sparrow.plugin.command.panel;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

final class PanelContext {
    final CommandManager manager;
    final CommandSender receiver;
    final boolean interactive;
    private final TranslationManager translations;
    private final @Nullable Player player;
    private final @Nullable Locale locale;

    PanelContext(CommandSender receiver) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        this.manager = plugin.commandManager();
        this.receiver = receiver;
        this.translations = plugin.translationManager();
        this.player = receiver instanceof Player current ? current : null;
        this.interactive = this.player != null;
        BukkitSparrowPlayer local = this.player == null ? null : plugin.playerManager().getOnlinePlayer(this.player);
        this.locale = local == null ? null : local.locale();
    }

    Component text(Component component, PanelTone tone) {
        return this.translations.render(component, this.locale, this.player).colorIfAbsent(tone.color());
    }

    Component text(PanelText text, PanelTone tone) {
        Component result = this.text(this.interactive ? text.getComponent() : text.getConsoleComponent(), tone);
        return this.interactive ? result : PanelTypography.console(result);
    }

    Component caption(Component component, PanelTone tone) {
        return PanelTypography.singleLine(PanelTypography.withoutEvents(this.text(component, tone)));
    }
}