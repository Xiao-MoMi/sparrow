package net.momirealms.sparrow.plugin.command.parser;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.leangen.geantyref.TypeToken;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.incendo.cloud.brigadier.parser.WrappedBrigadierParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.ParserDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class EntityTargetParser<C, T> extends AbstractTargetParser<C, T> {
    private final Function<List<EntityTarget>, T> result;

    private EntityTargetParser(boolean single, @NotNull Function<List<EntityTarget>, T> result) {
        super(single, false);
        this.result = result;
    }

    @NotNull
    public static <C> ParserDescriptor<C, EntityTarget> entityParser() {
        return ParserDescriptor.of(new EntityTargetParser<>(true, List::getFirst), EntityTarget.class);
    }

    @NotNull
    public static <C> ParserDescriptor<C, List<EntityTarget>> entitiesParser() {
        return ParserDescriptor.of(new EntityTargetParser<>(false, Function.identity()), new TypeToken<List<EntityTarget>>() {});
    }

    @NotNull
    @Override
    protected T resolve(@NotNull CommandContext<C> context, @NotNull String input, @NotNull EntitySelector selector) throws CommandSyntaxException {
        if (!input.startsWith("@")) {
            UUID uuid = UUIDUtils.parse(input);
            Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
            EntityTarget target = entity == null ? new EntityTarget.Player(input) : this.toTarget(entity);
            return this.result.apply(List.of(target));
        }
        CommandSourceStack source = context.get(WrappedBrigadierParser.COMMAND_CONTEXT_BRIGADIER_NATIVE_SENDER);
        List<EntityTarget> targets = selector.findEntities(source).stream()
                .map(entity -> this.toTarget(entity.getBukkitEntity()))
                .toList();
        if (targets.isEmpty()) throw EntityArgument.NO_ENTITIES_FOUND.create();
        return this.result.apply(targets);
    }

    @NotNull
    private EntityTarget toTarget(@NotNull Entity entity) {
        return entity instanceof Player ? new EntityTarget.Player(entity.getUniqueId().toString()) : new EntityTarget.Local(entity);
    }
}