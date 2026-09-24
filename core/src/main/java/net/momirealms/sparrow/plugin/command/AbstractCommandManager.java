package net.momirealms.sparrow.plugin.command;

import net.momirealms.sparrow.util.AdventureHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.configuration.CommandsConfig;
import net.momirealms.sparrow.plugin.Plugin;
import net.momirealms.sparrow.util.ArrayUtils;
import net.momirealms.sparrow.util.TriConsumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.incendo.cloud.Command;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.StandardCaptionKeys;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.exception.*;
import org.incendo.cloud.exception.handling.ExceptionContext;
import org.incendo.cloud.minecraft.extras.MinecraftExceptionHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

public abstract class AbstractCommandManager implements CommandManager {
    protected final HashSet<CommandComponent<CommandSender>> registeredRootCommandComponents = new HashSet<>();
    protected final HashSet<CommandFeature> registeredFeatures = new HashSet<>();
    protected final org.incendo.cloud.CommandManager<CommandSender> commandManager;
    protected final Plugin plugin;
    private final CloudCaptionFormatter captionFormatter;
    private final MinecraftExceptionHandler.Decorator<CommandSender> decorator = (formatter, ctx, msg) -> msg;
    private TriConsumer<CommandSender, String, Component> feedbackConsumer;
    private final TranslatableComponentRenderer<Locale> feedbackRenderer = new TranslatableComponentRenderer<>() {
        @Override
        protected Component renderTranslatable(TranslatableComponent component, Locale locale) {
            if (!AbstractCommandManager.this.plugin.translationManager().translationKeys().contains(component.key())) {
                return super.renderTranslatable(component, locale);
            }
            Component rendered = AbstractCommandManager.this.plugin.translationManager().render(component, locale).mergeStyle(component);
            return this.render(rendered, locale);
        }
    };

    public AbstractCommandManager(Plugin plugin, org.incendo.cloud.CommandManager<CommandSender> commandManager) {
        this.commandManager = commandManager;
        this.plugin = plugin;
        this.inject(); // 修改默认异常处理器.
        this.feedbackConsumer = defaultFeedbackConsumer();
        this.captionFormatter = new CloudCaptionFormatter();
    }

    @Override
    public void setFeedbackConsumer(@NotNull TriConsumer<CommandSender, String, Component> feedbackConsumer) {
        this.feedbackConsumer = feedbackConsumer;
    }

    @Override
    public TriConsumer<CommandSender, String, Component> defaultFeedbackConsumer() {
        return ((sender, node, component) -> {
            if (sender instanceof Player player) {
                SparrowPlayer pluginPlayer = this.plugin.playerManager().getPlayer(player);
                if (pluginPlayer != null && pluginPlayer.initialized()) {
                    pluginPlayer.sendMessage(component);
                }
            } else {
                sender.sendMessage(AdventureHelper.getLegacy().serialize(component));
            }
        });
    }

    /**
     * 向 Cloud 命令管理器安装默认异常处理器.
     *
     * @throws RuntimeException 当底层 Cloud 命令管理器拒绝注册 provider 或异常处理器时可能抛出
     */
    private void inject() {
        getCommandManager().captionRegistry().registerProvider(new CloudCaptionProvider<>());
        injectExceptionHandler(InvalidSyntaxException.class, MinecraftExceptionHandler.createDefaultInvalidSyntaxHandler(), StandardCaptionKeys.EXCEPTION_INVALID_SYNTAX);
        injectExceptionHandler(InvalidCommandSenderException.class, MinecraftExceptionHandler.createDefaultInvalidSenderHandler(), StandardCaptionKeys.EXCEPTION_INVALID_SENDER);
        injectExceptionHandler(NoPermissionException.class, MinecraftExceptionHandler.createDefaultNoPermissionHandler(), StandardCaptionKeys.EXCEPTION_NO_PERMISSION);
        injectExceptionHandler(ArgumentParseException.class, MinecraftExceptionHandler.createDefaultArgumentParsingHandler(), StandardCaptionKeys.EXCEPTION_INVALID_ARGUMENT);
        injectExceptionHandler(CommandExecutionException.class, MinecraftExceptionHandler.createDefaultCommandExecutionHandler(), StandardCaptionKeys.EXCEPTION_UNEXPECTED);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectExceptionHandler(Class<? extends Throwable> type, MinecraftExceptionHandler.MessageFactory<CommandSender, ? extends Throwable> factory, Caption key) {
        getCommandManager().exceptionController().registerHandler(type, ctx -> {
            final @Nullable ComponentLike message = factory.message(captionFormatter, (ExceptionContext) ctx);
            if (message != null) {
                handleCommandFeedback(ctx.context().sender(), key.key(), decorator.decorate(captionFormatter, ctx, message.asComponent()).asComponent());
            }
        });
    }

    @Override
    public Collection<Command.Builder<CommandSender>> buildCommandBuilders(CommandConfig config) {
        ArrayList<Command.Builder<CommandSender>> list = new ArrayList<>();
        for (String usage : config.getUsages()) {
            if (!usage.startsWith("/")) continue;
            String command = usage.substring(1).trim();
            String[] split = command.split(" ");
            Command.Builder<CommandSender> builder = new ConfigurableCommandBuilder.BasicConfigurableCommandBuilder(getCommandManager(), split[0])
                        .nodes(ArrayUtils.subArray(split, 1))
                        .permission(config.getPermission())
                        .build();
            list.add(builder);
        }
        return list;
    }

    @Override
    public void registerFeature(CommandFeature feature, CommandConfig config) {
        if (!config.isEnable()) throw new RuntimeException("Registering a disabled command feature is not allowed");
        for (Command.Builder<CommandSender> builder : buildCommandBuilders(config)) {
            Command<CommandSender> command = feature.registerCommand(commandManager, builder);
            this.registeredRootCommandComponents.add(command.rootComponent());
        }
        feature.registerRelatedFunctions();
        this.registeredFeatures.add(feature);
        ((AbstractCommandFeature) feature).setCommandConfig(config);
    }

    @Override
    public void registerDefaultFeatures() {
        CommandsConfig.ConfigDefinition commands = this.plugin.configurationManager().commandsConfig().configDefinition();
        for (CommandFeature feature : this.features().values()) {
            CommandConfig config = commands.command(feature.getFeatureID());
            if (config.isEnable() && feature.isAvailable()) {
                this.registerFeature(feature, config);
            }
        }
    }

    @Override
    public void unregisterFeatures() {
        this.registeredRootCommandComponents.forEach(component -> this.commandManager.commandRegistrationHandler().unregisterRootCommand(component));
        this.registeredRootCommandComponents.clear();
        this.registeredFeatures.forEach(CommandFeature::unregisterRelatedFunctions);
        this.registeredFeatures.clear();
    }

    @Override
    public org.incendo.cloud.CommandManager<CommandSender> getCommandManager() {
        return commandManager;
    }

    @Override
    public void handleCommandFeedback(CommandSender sender, TranslatableComponent.Builder key, Component... args) {
        TranslatableComponent component = ((TranslatableComponent) key.asComponent()).arguments(args);
        this.feedbackConsumer.accept(sender, component.key(), this.feedbackRenderer.render(component, this.getLocale(sender)));
    }

    @Override
    public void handleCommandFeedback(CommandSender sender, String node, Component component) {
        this.feedbackConsumer.accept(sender, node, this.feedbackRenderer.render(component, this.getLocale(sender)));
    }

    /**
     * 获取指定发送者的语言环境.
     */
    protected abstract Locale getLocale(CommandSender sender);
}
