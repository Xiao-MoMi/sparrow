package net.momirealms.sparrow.plugin.command.parser;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.leangen.geantyref.TypeToken;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import org.incendo.cloud.brigadier.parser.WrappedBrigadierParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.ParserDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

public final class GlobalPlayerParser<C, T> extends AbstractTargetParser<C, T> {
    private final Function<List<String>, T> result;

    private GlobalPlayerParser(boolean single, @NotNull Function<List<String>, T> result) {
        super(single, true);
        this.result = result;
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> clusterPlayerParser() {
        return ParserDescriptor.of(new GlobalPlayerParser<>(true, List::getFirst), String.class);
    }

    @NotNull
    public static <C> ParserDescriptor<C, List<String>> clusterPlayersParser() {
        return ParserDescriptor.of(new GlobalPlayerParser<>(false, Function.identity()), new TypeToken<List<String>>() {});
    }

    @NotNull
    @Override
    protected T resolve(@NotNull CommandContext<C> context, @NotNull String input, @NotNull EntitySelector selector) throws CommandSyntaxException {
        if (!input.startsWith("@")) return this.result.apply(List.of(input));
        CommandSourceStack source = context.get(WrappedBrigadierParser.COMMAND_CONTEXT_BRIGADIER_NATIVE_SENDER);
        List<ServerPlayer> players = selector.findPlayers(source);
        if (players.isEmpty()) throw EntityArgument.NO_PLAYERS_FOUND.create();
        return this.result.apply(players.stream().map(player -> player.getBukkitEntity().getName()).toList());
    }
}