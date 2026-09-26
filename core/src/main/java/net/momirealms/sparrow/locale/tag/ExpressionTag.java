package net.momirealms.sparrow.locale.tag;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.expr.CompiledExpression;
import net.momirealms.sparrow.expr.ExpressionCompiler;
import net.momirealms.sparrow.expr.binding.ParameterBinding;
import net.momirealms.sparrow.message.Context;
import net.momirealms.sparrow.message.ParsingException;
import net.momirealms.sparrow.message.internal.parser.Token;
import net.momirealms.sparrow.message.internal.parser.TokenParser;
import net.momirealms.sparrow.message.internal.parser.TokenType;
import net.momirealms.sparrow.message.tag.Tag;
import net.momirealms.sparrow.message.tag.resolver.ArgumentQueue;
import net.momirealms.sparrow.message.tag.resolver.StaticTagResolver;
import net.momirealms.sparrow.util.AdventureHelper;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * {@code <expr:format:expression>}, 计算表达式并按 {@link DecimalFormat} 格式输出, format 为 {@code bool} 时输出 true / false.
 * 表达式中的标识符读取 {@link MessageContext} 的命名参数, 其中的 MiniMessage 标签会先解析为纯文本再参与计算.
 */
public final class ExpressionTag extends StaticTagResolver {
    public static final ExpressionTag INSTANCE = new ExpressionTag();
    private static final String TAG_VARIABLE_PREFIX = "__sparrow_tag_";
    private static final Cache<String, CompiledExpression<Context>> EXPRESSIONS = Caffeine.newBuilder()
            .maximumSize(256)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    // DecimalFormat 线程不安全, 缓存的是原型, 使用时 clone 一份
    private static final Cache<String, DecimalFormat> FORMATS = Caffeine.newBuilder()
            .maximumSize(64)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    private ExpressionTag() {
        super("expr");
    }

    @Override
    @NotNull
    public Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx) throws ParsingException {
        String format = arguments.popOr("No format provided").value();
        String source = arguments.popOr("No expression provided").value();
        CompiledExpression<Context> expression;
        try {
            expression = EXPRESSIONS.get(source, ExpressionTag::compile);
        } catch (RuntimeException exception) {
            throw ctx.newException("Invalid expression: " + source, exception, arguments);
        }
        String result;
        try {
            result = format.equals("bool") ? Boolean.toString(expression.test(ctx)) : formatNumber(format, expression.evaluate(ctx));
        } catch (RuntimeException exception) {
            throw ctx.newException("Failed to evaluate expression: " + source, exception, arguments);
        }
        return Tag.selfClosingInserting(Component.text(result));
    }

    // 把表达式中的标签换成占位变量后编译, 求值时再用当前解析上下文展开标签
    private static CompiledExpression<Context> compile(String source) {
        StringBuilder substituted = new StringBuilder(source.length());
        Map<String, String> tags = new HashMap<>(2);
        List<Token> tokens = TokenParser.tokenize(source, true);
        int size = tokens.size();
        for (int i = 0; i < size; i++) {
            Token token = tokens.get(i);
            TokenType type = token.type();
            if (type == TokenType.OPEN_TAG || type == TokenType.OPEN_CLOSE_TAG) {
                String variable = TAG_VARIABLE_PREFIX + tags.size();
                tags.put(variable, token.get(source).toString());
                substituted.append(variable);
            } else {
                substituted.append(token.get(source));
            }
        }
        return new ExpressionCompiler<Context>(variable -> {
            String tag = tags.get(variable);
            if (tag != null) {
                return ParameterBinding.auto(ctx -> toNumber(resolveTag(ctx, tag)), ctx -> resolveTag(ctx, tag));
            }
            // 其余标识符按命名参数处理, 参数缺失时在求值阶段报错
            return ParameterBinding.auto(ctx -> toNumber(argument(ctx, variable)), ctx -> toText(argument(ctx, variable)));
        }).compile(substituted.toString());
    }

    private static String resolveTag(Context ctx, String tag) {
        return AdventureHelper.plainTextContent(ctx.deserialize(tag));
    }

    private static Object argument(Context ctx, String key) {
        Object value = ctx.target() instanceof MessageContext context ? context.argument(key) : null;
        if (value == null) {
            throw new IllegalArgumentException("Unknown expression argument: " + key);
        }
        return value;
    }

    private static String formatNumber(String pattern, double value) {
        DecimalFormat prototype = FORMATS.get(pattern, DecimalFormat::new);
        return ((DecimalFormat) prototype.clone()).format(value);
    }

    private static double toNumber(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof Boolean bool) return bool ? 1D : 0D;
        String text = toText(value);
        return switch (text) {
            case "true", "TRUE", "yes", "YES" -> 1D;
            case "false", "FALSE", "no", "NO" -> 0D;
            default -> Double.parseDouble(text);
        };
    }

    private static String toText(Object value) {
        return value instanceof Component component ? AdventureHelper.plainTextContent(component) : value.toString();
    }
}
