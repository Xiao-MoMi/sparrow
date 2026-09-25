package net.momirealms.sparrow.bukkit.command;

import io.leangen.geantyref.TypeToken;
import net.kyori.adventure.util.Index;
import net.momirealms.sparrow.bukkit.SparrowBukkitPlugin;
import net.momirealms.sparrow.bukkit.command.feature.container.*;
import net.momirealms.sparrow.bukkit.command.feature.item.*;
import net.momirealms.sparrow.bukkit.command.feature.item.editor.ItemCustomModelDataPlayerCommand;
import net.momirealms.sparrow.bukkit.command.feature.item.editor.ItemDisplayNamePlayerCommand;
import net.momirealms.sparrow.bukkit.command.feature.item.editor.ItemLorePlayerCommand;
import net.momirealms.sparrow.bukkit.command.feature.misc.*;
import net.momirealms.sparrow.bukkit.command.feature.player.*;
import net.momirealms.sparrow.bukkit.command.processor.SelectorPostProcessor;
import net.momirealms.sparrow.common.command.AbstractSparrowCommandManager;
import net.momirealms.sparrow.common.command.CommandFeature;
import net.momirealms.sparrow.common.event.Cancellable;
import net.momirealms.sparrow.common.event.SparrowEvent;
import net.momirealms.sparrow.common.event.type.CommandFeedbackEvent;
import net.momirealms.sparrow.common.sender.Sender;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.bukkit.internal.BukkitBrigadierMapper;
import org.incendo.cloud.bukkit.internal.CraftBukkitReflection;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.paper.parser.KeyedWorldParser;
import org.incendo.cloud.setting.ManagerSetting;

import java.util.List;

public final class SparrowBukkitCommandManager extends AbstractSparrowCommandManager<CommandSender> {

    private final List<CommandFeature<CommandSender>> FEATURES = List.of(
            new EnderChestPlayerCommand(this),
            new EnderChestAdminCommand(this),
            new EnchantAdminCommand(this),
            new ColorPlayerCommand(this),
            new DyePlayerCommand(this),
            new DyeAdminCommand(this),
            new TpOfflineAdminCommand(this),
            new TpOfflinePlayerCommand(this),
            new HeadAdminCommand(this),
            new URLHeadAdminCommand(this),
            new HeadPlayerCommand(this),
            new ItemCustomModelDataPlayerCommand(this),
            new ItemDisplayNamePlayerCommand(this),
            new ItemLorePlayerCommand(this)
    );

    private final Index<String, CommandFeature<CommandSender>> INDEX = Index.create(CommandFeature::getFeatureID, FEATURES);

    @Override
    public Index<String, CommandFeature<CommandSender>> getFeatures() {
        return INDEX;
    }

    @Override
    protected Sender wrapSender(CommandSender sender) {
        return ((SparrowBukkitPlugin) plugin).getSenderFactory().wrap(sender);
    }

    public SparrowBukkitCommandManager(SparrowBukkitPlugin plugin) {
        super(plugin, new LegacyPaperCommandManager<>(
                plugin.getLoader(),
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.identity()
        ));
        this.registerMappings();
        this.registerCommandPostProcessors();
        this.setFeedbackConsumer((sender, node, component) -> {
            SparrowEvent event = plugin.getEventManager().dispatch(CommandFeedbackEvent.class, sender, node, component);
            if (event instanceof Cancellable cancellable) {
                if (cancellable.cancelled())
                    return;
            }
            Sender wrapped = SparrowBukkitPlugin.getInstance().getSenderFactory().wrap(sender);
            wrapped.sendMessage(component, true);
        });
    }

    private void registerCommandPostProcessors() {
        this.getCommandManager().registerCommandPostProcessor(new SelectorPostProcessor(this));
    }

    private void registerMappings() {
        final LegacyPaperCommandManager<CommandSender> manager = (LegacyPaperCommandManager<CommandSender>) getCommandManager();
        manager.settings().set(ManagerSetting.ALLOW_UNSAFE_REGISTRATION, true);
        if (manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            manager.registerBrigadier();
            manager.brigadierManager().setNativeNumberSuggestions(true);
            BukkitBrigadierMapper<CommandSender> mapper = new BukkitBrigadierMapper<>(((SparrowBukkitPlugin) plugin).getLoader().getLogger(), manager.brigadierManager());
            final Class<?> keyed = CraftBukkitReflection.findClass("org.bukkit.Keyed");
            if (keyed != null && keyed.isAssignableFrom(World.class)) {
                mapper.mapSimpleNMS(new TypeToken<KeyedWorldParser<CommandSender>>() {}, "resource_location", true);
            }
        } else if (manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            manager.registerAsynchronousCompletions();
        }
    }
}
