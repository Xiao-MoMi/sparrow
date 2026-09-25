package net.momirealms.sparrow.plugin.command.parser;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.bukkit.BukkitCommandContextKeys;
import org.incendo.cloud.bukkit.parser.location.LocationParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.flag.CommandFlagParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocationFlagParserTest {
    @Test
    void completedCoordinatesAllowFollowingFlagSuggestions() {
        CommandContext<CommandSender> context = this.context();
        CommandFlagParser<CommandSender> flags = this.flags();
        CommandInput input = CommandInput.of("highlight --from 4 63 55 ");
        input.cursor("highlight ".length());
        assertTrue(flags.parseCurrentFlag(context, input, Runnable::run).join().isEmpty());
        List<String> suggestions = new ArrayList<>();
        flags.suggestionsFuture(context, input).join().forEach(value -> suggestions.add(value.suggestion()));
        assertTrue(suggestions.contains("--to"));
        assertFalse(suggestions.contains("--from"));
    }

    @Test
    void reproducesCloudLocationParserConsumingTheFlagSeparator() {
        CommandFlagParser<CommandSender> original = new CommandFlagParser<>(List.of(
                CommandFlag.<CommandSender>builder("from").withComponent(LocationParser.locationParser()).build()
        ));
        assertEquals(Optional.of("--from"), original.parseCurrentFlag(this.context(), CommandInput.of("--from 4 63 55 "), Runnable::run).join());
    }

    @Test
    void incompleteSecondPointStillCompletesCoordinates() {
        assertEquals(Optional.of("--to"), this.flags().parseCurrentFlag(this.context(), CommandInput.of("--from 4 63 55 --to -3 64 "), Runnable::run).join());
    }

    @Test
    void parsesBothPointsIncludingRelativeAndNegativeCoordinates() {
        CommandContext<CommandSender> context = this.context();
        assertTrue(this.flags().parseFuture(context, CommandInput.of("--from ~1 ~ ~-2 --to -3 64 -5")).join().parsedValue().isPresent());
        Location from = context.flags().getValue("from", null);
        Location to = context.flags().getValue("to", null);
        assertEquals(11, from.getX());
        assertEquals(64, from.getY());
        assertEquals(18, from.getZ());
        assertEquals(-3, to.getX());
        assertEquals(64, to.getY());
        assertEquals(-5, to.getZ());
    }

    @SuppressWarnings("unchecked")
    private CommandContext<CommandSender> context() {
        Player sender = mock(Player.class);
        World world = mock(World.class);
        when(sender.getLocation()).thenAnswer(invocation -> new Location(world, 10, 64, 20));
        CommandManager<CommandSender> manager = mock(CommandManager.class, RETURNS_DEEP_STUBS);
        when(manager.testPermission(any(), any()).allowed()).thenReturn(true);
        CommandContext<CommandSender> context = new CommandContext<>(sender, manager);
        context.store(BukkitCommandContextKeys.BUKKIT_COMMAND_SENDER, sender);
        return context;
    }

    private CommandFlagParser<CommandSender> flags() {
        return new CommandFlagParser<>(List.of(
                CommandFlag.<CommandSender>builder("from").withComponent(LocationFlagParser.locationFlagParser()).build(),
                CommandFlag.<CommandSender>builder("to").withComponent(LocationFlagParser.locationFlagParser()).build()
        ));
    }
}
