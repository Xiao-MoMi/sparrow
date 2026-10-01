package net.momirealms.sparrow.plugin.command.parser;

import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * 读取到下一个空格为止的一段文本, 允许中文等任意字符, 后面还可以接其他参数.
 * Brigadier 的单词参数只接受英文字母、数字和少数符号, 因此在 Brigadier 中映射为同样读到空格为止的原版参数类型, 见 {@code BukkitCommandManager}.
 */
public final class TokenParser<C> implements ArgumentParser<C, String> {

    @NotNull
    public static <C> ParserDescriptor<C, String> tokenParser() {
        return ParserDescriptor.of(new TokenParser<>(), String.class);
    }

    @Override
    @NotNull
    public ArgumentParseResult<String> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return ArgumentParseResult.success(input.readString());
    }
}
