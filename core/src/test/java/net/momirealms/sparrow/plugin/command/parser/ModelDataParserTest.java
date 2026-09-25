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

class ModelDataParserTest {
    private final CommandManager<String> manager = new CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
        @Override
        public boolean hasPermission(String sender, String permission) {
            return true;
        }
    };

    @ParameterizedTest
    @CsvSource({"0,0", "123,123", "-4,-4", "2147483647,2147483647", "-2147483648,-2147483648"})
    void integersKeepTheirType(String input, int expected) {
        var result = new ModelDataParser<String>().parse(new CommandContext<>("sender", this.manager), CommandInput.of(input));
        assertEquals(expected, assertInstanceOf(Integer.class, result.parsedValue().orElseThrow()));
    }

    @ParameterizedTest
    @CsvSource({"123.0,123.0", "123.5,123.5", "-4.5,-4.5", "2147483648.0,2147483648.0"})
    void explicitDecimalsAreFloats(String input, float expected) {
        var result = new ModelDataParser<String>().parse(new CommandContext<>("sender", this.manager), CommandInput.of(input));
        assertEquals(expected, assertInstanceOf(Float.class, result.parsedValue().orElseThrow()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "[1,2]", "1f", "1e3", "NaN", "Infinity", "1.", ".5", "2147483648", "9999999999999999999999999999999999999999.0"})
    void rejectsUnsupportedTypesAndOverflow(String input) {
        var result = new ModelDataParser<String>().parse(new CommandContext<>("sender", this.manager), CommandInput.of(input));
        assertInstanceOf(ModelDataParser.ModelDataParseException.class, result.failure().orElseThrow());
    }
}
