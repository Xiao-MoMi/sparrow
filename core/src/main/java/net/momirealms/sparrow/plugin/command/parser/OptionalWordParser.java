package net.momirealms.sparrow.plugin.command.parser;

import io.leangen.geantyref.TypeToken;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class OptionalWordParser<C> implements ArgumentParser<C, Optional<String>> {

    /** 创建可选单词参数, 后接 flag 时返回空值并保留输入. */
    @NotNull
    public static <C> ParserDescriptor<C, Optional<String>> optionalWordParser() {
        return ParserDescriptor.of(new OptionalWordParser<>(), new TypeToken<>() {});
    }

    @Override
    @NotNull
    public ArgumentParseResult<Optional<String>> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        // 省略参数时保留 flag 输入, 交给后续的 Cloud flag 解析器处理.
        if (input.peekString().startsWith("-")) return ArgumentParseResult.success(Optional.empty());
        return ArgumentParseResult.success(Optional.of(input.readString()));
    }
}
