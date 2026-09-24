package net.momirealms.sparrow.util;

import java.util.stream.IntStream;

public final class CharacterUtils {
    private CharacterUtils() {}

    /**
     * 将连续的 Unicode 转义片段解码为字符数组.
     * 输入字符串必须由多个 `\\uXXXX` 片段首尾相连组成, 每个片段固定占 6 个字符.
     * 方法会按 6 个字符一组截取十六进制部分, 再逐段转换为对应的 `char` 值.
     * 该方法按 UTF-16 代码单元解码, 如果输入表示的是代理对字符, 返回结果会包含 2 个 `char`.
     *
     * @param unicodeString 待解码的 Unicode 转义字符串, 例如 `\u0041\u4F60`
     * @return 按输入顺序解码得到的字符数组
     * @throws NullPointerException 当 `unicodeString` 为 `null` 时, 调用 `length()` 会抛出该异常
     * @throws IllegalArgumentException 当输入长度不是 6 的整数倍时抛出, 说明存在不完整的转义片段
     * @throws IllegalArgumentException 当任一片段的十六进制部分不是合法的 4 位十六进制数时抛出
     * @apiNote 该方法不会校验每组前缀是否一定为 `\\u`, 调用方需要保证输入格式正确
     */
    public static char[] decodeUnicodeToChars(String unicodeString) {
        if (unicodeString.length() % 6 != 0) {
            throw new IllegalArgumentException("Malformed Unicode escape sequence length: " + unicodeString);
        }

        int count = unicodeString.length() / 6;
        char[] chars = new char[count];

        for (int i = 0, j = 0; j < count; i += 6, j++) {
            String hex = unicodeString.substring(i + 2, i + 6);
            try {
                chars[j] = (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid Unicode hex sequence: " + hex + " in string: " + unicodeString, e);
            }
        }
        return chars;
    }

    /**
     * 将 1 个或 2 个 UTF-16 字符单元转换为单个 Unicode 码点.
     * 当数组长度为 1 时, 直接返回该字符的数值.
     * 当数组长度为 2 时, 会要求第一个字符是高代理项且第二个字符是低代理项, 然后组合为完整码点.
     *
     * @param chars 需要转换的字符数组, 只允许包含 1 个普通字符或 1 对合法代理项
     * @return 转换得到的 Unicode 码点整数值
     * @throws NullPointerException 当 `chars` 为 `null` 时, 访问 `length` 会抛出该异常
     * @throws IllegalArgumentException 当数组长度不是 1 或 2 时抛出
     * @throws IllegalArgumentException 当数组长度为 2 但不是合法的高代理项加低代理项组合时抛出
     * @apiNote 该方法只适用于单个字符或单个代理对场景, 不能直接处理包含多个字符的数组
     */
    public static int charsToCodePoint(char[] chars) {
        if (chars.length == 1) {
            return chars[0];
        } else if (chars.length == 2) {
            if (Character.isHighSurrogate(chars[0]) && Character.isLowSurrogate(chars[1])) {
                return Character.toCodePoint(chars[0], chars[1]);
            } else {
                throw new IllegalArgumentException("Invalid surrogate pair: not a valid high and low surrogate combination.");
            }
        } else {
            throw new IllegalArgumentException("The given chars array must contain either 1 or 2 characters.");
        }
    }

    /**
     * 将 UTF-16 字符数组按顺序转换为 Unicode 码点数组.
     * 方法会遍历输入数组, 普通字符直接转为码点, 遇到高代理项时会与其后的低代理项组合为单个码点.
     * 为避免重复处理代理对中的第二个字符, 流中过滤掉了所有低代理项索引, 仅从非低代理项位置开始解析.
     *
     * @param chars 待转换的 UTF-16 字符数组
     * @return 与输入字符序列对应的 Unicode 码点数组
     * @throws NullPointerException 当 `chars` 为 `null` 时, 访问 `length` 会抛出该异常
     * @throws IllegalArgumentException 当存在孤立高代理项, 即其后没有匹配的低代理项时抛出
     * @apiNote 孤立低代理项会被过滤逻辑直接跳过, 不会出现在返回结果中, 调用方应确保输入为合法 UTF-16 序列
     */
    public static int[] charsToCodePoints(char[] chars) {
        return IntStream.range(0, chars.length)
                .filter(i -> !Character.isLowSurrogate(chars[i]))
                .map(i -> {
                    char c1 = chars[i];
                    if (Character.isHighSurrogate(c1)) {
                        if (i + 1 < chars.length && Character.isLowSurrogate(chars[i + 1])) {
                            char c2 = chars[++i];
                            return Character.toCodePoint(c1, c2);
                        } else {
                            throw new IllegalArgumentException("Illegal surrogate pair: High surrogate without matching low surrogate at index " + i);
                        }
                    } else {
                        return c1;
                    }
                }).toArray();
    }

    /**
     * 将单个 UTF-16 字符单元编码为 `\\uXXXX` 形式的 Unicode 转义字符串.
     * 编码结果固定使用 4 位小写十六进制表示.
     *
     * @param c 需要编码的字符
     * @return 对应的 Unicode 转义字符串, 例如字符 `A` 会返回 `\u0041`
     * @apiNote 该方法按单个 `char` 编码, 对于补充平面字符需要分别编码其高低代理项
     */
    public static String encodeCharToUnicode(char c) {
        return String.format("\\u%04x", (int) c);
    }

    /**
     * 将字符数组中的每个 UTF-16 字符单元依次编码为 Unicode 转义字符串并拼接返回.
     * 方法内部会逐个调用 `encodeCharToUnicode(char)` 处理每个字符, 因此输出顺序与输入顺序完全一致.
     *
     * @param chars 待编码的字符数组
     * @return 由多个 `\\uXXXX` 片段拼接而成的字符串
     * @throws NullPointerException 当 `chars` 为 `null` 时, `for-each` 遍历会抛出该异常
     * @apiNote 如果数组中包含代理对, 返回结果会是两个连续的转义片段, 而不是单个超过 4 位的码点表示
     */
    public static String encodeCharsToUnicode(char[] chars) {
        StringBuilder builder = new StringBuilder();
        for (char value : chars) {
            builder.append(encodeCharToUnicode(value));
        }
        return builder.toString();
    }

    /**
     * 将普通字符串整体转义为连续的 Unicode 转义字符串.
     * 方法会先将字符串拆分为 UTF-16 字符数组, 再调用 `encodeCharsToUnicode(char[])` 完成批量编码.
     *
     * @param string 待转义的原始字符串
     * @return 由输入字符串所有 UTF-16 字符单元编码后拼接得到的结果
     * @throws NullPointerException 当 `string` 为 `null` 时, 调用 `toCharArray()` 会抛出该异常
     * @apiNote 返回值适合用于需要显式保留 Unicode 转义内容的文本场景, 但不会保留原始字符串中的其他转义语义
     */
    public static String escape(String string) {
        return encodeCharsToUnicode(string.toCharArray());
    }

    /**
     * 将字符串中的反斜杠 `\` 全部替换为正斜杠 `/`.
     * 该方法会保留除反斜杠外的所有字符不变, 并通过逐字符构建新字符串避免使用正则替换.
     *
     * @param input 待处理的路径或普通字符串, 允许为 `null` 或空字符串
     * @return 替换后的字符串, 如果输入为 `null` 或空字符串则直接返回原值
     * @apiNote 该方法常用于统一资源路径分隔符, 例如将 Windows 风格路径转换为类路径常用的 `/` 形式
     */
    public static String replaceBackslashWithSlash(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            sb.append(c == '\\' ? '/' : c);
        }
        return sb.toString();
    }

    /**
     * 将字符串中的 `\\\\u` 序列收缩为 `\\u`.
     * 方法按字符顺序扫描输入, 当检测到连续两个反斜杠后紧跟字符 `u` 时, 会将这 3 个字符替换为单个 Unicode 转义前缀 `\\u`.
     * 其余内容保持原样写入结果, 适合处理被二次转义后的 Unicode 文本.
     *
     * @param input 待处理的字符串, 允许为 `null` 或空字符串
     * @return 替换后的字符串, 如果输入为 `null` 或空字符串则直接返回原值
     * @apiNote 该方法只处理精确的 `\\u` 模式, 不会进一步校验其后是否真的跟随 4 位十六进制数字
     */
    public static String replaceDoubleBackslashU(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        int length = input.length();
        StringBuilder sb = new StringBuilder(length);
        int i = 0;
        while (i < length) {
            if (i + 2 < length
                    && input.charAt(i) == '\\'
                    && input.charAt(i + 1) == '\\'
                    && input.charAt(i + 2) == 'u') {
                sb.append("\\u");
                i += 3;
            } else {
                sb.append(input.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }
}
