package net.momirealms.sparrow.plugin.command.parser;

import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TimeParserTest {
    private final CommandManager<String> manager = new CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
        @Override
        public boolean hasPermission(String sender, String permission) {
            return true;
        }
    };

    @ParameterizedTest
    @CsvSource({"0,0", "20,20", "5s,100", "1m30s,1800", "1d2h3m4s5t,1875685", "2147483647t,2147483647"})
    void convertsEntireDurationWithoutTruncation(String input, int expected) {
        var result = new TimeParser<String>().parseFuture(new CommandContext<>("sender", this.manager), CommandInput.of(input)).join();
        assertEquals(expected, result.parsedValue().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"garbage", "-1s", "1sxxx", "xxx1s", "1.5s", "1m30", "2147483648", "107374183s", "999999999999999999999999999d"})
    void rejectsMalformedOrOverflowingDurations(String input) {
        var result = new TimeParser<String>().parseFuture(new CommandContext<>("sender", this.manager), CommandInput.of(input)).join();
        assertTrue(result.failure().isPresent());
    }
}
