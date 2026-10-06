package net.momirealms.sparrow.proxy;

import java.util.List;
import java.util.Stack;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MinecraftPredicate implements Predicate<String> {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("&&|\\|\\||[()!]|[^\\s()&|!]+");
    private final Context context;

    public MinecraftPredicate(String version, List<String> patches) {
        this.context = new Context(parseVersionToInteger(version), patches);
    }

    @Override
    public boolean test(String expression) {
        if (expression == null || expression.isEmpty()) return true;
        return compile(expression).test(this.context);
    }

    private Condition compile(String expression) {
        Matcher matcher = TOKEN_PATTERN.matcher(expression);
        Stack<Condition> nodes = new Stack<>();
        Stack<String> ops = new Stack<>();
        while (matcher.find()) {
            handleToken(matcher.group(), nodes, ops);
        }
        while (!ops.isEmpty()) {
            processOperator(nodes, ops.pop());
        }
        return nodes.isEmpty() ? ctx -> true : nodes.pop();
    }

    private static void handleToken(String token, Stack<Condition> nodes, Stack<String> ops) {
        switch (token) {
            case "(", "!" -> ops.push(token);
            case ")" -> resolveCloseParen(nodes, ops);
            case "&&", "||" -> pushBinaryOperator(nodes, ops, token);
            default -> nodes.push(compileLeaf(token));
        }
    }

    private static void resolveCloseParen(Stack<Condition> nodes, Stack<String> ops) {
        while (!ops.isEmpty() && !"(".equals(ops.peek())) {
            processOperator(nodes, ops.pop());
        }
        if (!ops.isEmpty()) {
            ops.pop();
        }
    }

    private static void pushBinaryOperator(Stack<Condition> nodes, Stack<String> ops, String token) {
        int tokenPrecedence = precedence(token);
        while (!ops.isEmpty() && precedence(ops.peek()) >= tokenPrecedence) {
            processOperator(nodes, ops.pop());
        }
        ops.push(token);
    }

    private static void processOperator(Stack<Condition> nodes, String op) {
        if ("!".equals(op)) {
            if (nodes.isEmpty()) throw new IllegalArgumentException("Invalid syntax: '!' used without operand");
            Condition node = nodes.pop();
            nodes.push(ctx -> !node.test(ctx));
        } else {
            if (nodes.size() < 2) throw new IllegalArgumentException("Invalid syntax: missing operands for " + op);
            Condition right = nodes.pop();
            Condition left = nodes.pop();
            if ("&&".equals(op)) {
                nodes.push(ctx -> left.test(ctx) && right.test(ctx));
            } else if ("||".equals(op)) {
                nodes.push(ctx -> left.test(ctx) || right.test(ctx));
            }
        }
    }

    private static int precedence(String op) {
        if ("!".equals(op)) return 3;
        if ("&&".equals(op)) return 2;
        if ("||".equals(op)) return 1;
        return 0;
    }

    private static Condition compileLeaf(String token) {
        String[] parts = token.split("=", 2);
        if (parts.length != 2) return ctx -> false;
        String type = parts[0].trim();
        String param = parts[1].trim();
        return switch (type) {
            case "min_version" -> new VersionCheck(param, true);
            case "max_version" -> new VersionCheck(param, false);
            case "version" -> new ExactVersionCheck(param);
            case "has_patch" -> new PatchCheck(param);
            default -> throw new IllegalArgumentException("Invalid predicate: " + token);
        };
    }

    public static int parseVersionToInteger(String versionString) {
        return MinecraftVersionParser.parseVersionToInteger(versionString);
    }

    public interface Condition {
        boolean test(Context predicate);
    }

    public record Context(int version, List<String> patches) {
    }

    private static class ExactVersionCheck implements Condition {
        private final int targetVersion;

        public ExactVersionCheck(String version) {
            this.targetVersion = parseVersionToInteger(version);
        }

        @Override
        public boolean test(Context predicate) {
            return predicate.version == targetVersion;
        }
    }

    private static class VersionCheck implements Condition {
        private final int targetVersion;
        private final boolean minOrMax;

        public VersionCheck(String targetVersion, boolean minOrMax) {
            this.targetVersion = parseVersionToInteger(targetVersion);
            this.minOrMax = minOrMax;
        }

        @Override
        public boolean test(Context context) {
            if (this.minOrMax) {
                return context.version >= this.targetVersion;
            } else {
                return context.version <= this.targetVersion;
            }
        }
    }

    private record PatchCheck(String patch) implements Condition {

        @Override
        public boolean test(Context predicate) {
            return predicate.patches().contains(this.patch);
        }
    }
}