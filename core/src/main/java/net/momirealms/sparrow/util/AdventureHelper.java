package net.momirealms.sparrow.util;

import com.google.gson.JsonElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.momirealms.sparrow.locale.tag.ExpressionTag;
import net.momirealms.sparrow.locale.tag.MessageContext;
import net.momirealms.sparrow.locale.tag.NamedArgumentTag;
import net.momirealms.sparrow.locale.tag.PlaceholderTag;
import net.momirealms.sparrow.message.MiniMessage;
import net.momirealms.sparrow.message.tag.resolver.TagResolver;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Adventure 组件工具类, 提供 MiniMessage, JSON, NBT, 旧版颜色代码等多种文本格式之间的转换功能.
 * 该类采用单例模式, 通过 {@link #getInstance()} 获取唯一实例.
 */
public final class AdventureHelper {
    public static final String EMPTY_COMPONENT = componentToJson(Component.empty());
    private final MiniMessage miniMessage;
    private final MiniMessage miniMessageStrict;
    private final MiniMessage miniMessageCustom;
    private final GsonComponentSerializer gsonComponentSerializer;
    private final LegacyComponentSerializer legacyComponentSerializer;
    private final LegacyComponentSerializer legacyHexSerializer;
    // 文本替换配置, 将字符串中的换行符 '\n' 替换为 Adventure 的换行组件.
    private static final TextReplacementConfig REPLACE_LF = TextReplacementConfig.builder().matchLiteral("\n").replacement(Component.newline()).build();

    /**
     * 静态初始化块, 通过反射禁用 Adventure 内部的旧版格式化检测警告.
     * 将 TextComponentImpl.WARN_WHEN_LEGACY_FORMATTING_DETECTED 设为 false,
     * 避免在使用旧版颜色代码时产生不必要的控制台警告输出.
     */
    static {
//        SparrowClass.of(NyanaClass.findNoRemap("net.kyori.adventure.text.TextComponentImpl")).getDeclaredSparrowField(FieldMatcher.named("WARN_WHEN_LEGACY_FORMATTING_DETECTED")).mh().set(null, false);
    }

    /**
     * 私有构造方法, 初始化所有序列化器实例.
     */
    private AdventureHelper() {
        TagResolver sparrowTags = TagResolver.resolver(NamedArgumentTag.INSTANCE, PlaceholderTag.INSTANCE, ExpressionTag.INSTANCE);
        this.miniMessage = MiniMessage.builder().tags(TagResolver.resolver(TagResolver.standard(), sparrowTags)).build();
        this.miniMessageStrict = MiniMessage.builder().strict(true).build();
        this.miniMessageCustom = MiniMessage.builder().tags(sparrowTags).build();
        this.legacyComponentSerializer = LegacyComponentSerializer.builder().build();
        this.legacyHexSerializer = LegacyComponentSerializer.builder().hexColors().useUnusualXRepeatedCharacterHexFormat().build();
        this.gsonComponentSerializer = GsonComponentSerializer.builder().build();
    }

    /**
     * 静态内部类, 利用类加载机制实现线程安全的懒加载单例.
     * 仅在首次调用 {@link #getInstance()} 时触发类加载并创建实例.
     */
    private static class SingletonHolder {
        private static final AdventureHelper INSTANCE = new AdventureHelper();
    }

    public static AdventureHelper getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * 获取默认的 MiniMessage 实例, 支持所有标准标签以及 {@code <arg>}, {@code <papi>}, {@code <expr>}.
     * 后三者需要以 {@link MessageContext} 作为 target 才能读取参数和玩家.
     *
     * @return 默认 MiniMessage 实例
     */
    public static MiniMessage miniMessage() {
        return getInstance().miniMessage;
    }

    public static MiniMessage strictMiniMessage() {
        return getInstance().miniMessageStrict;
    }

    // 只有 <arg>, <papi>, <expr>, 不解析颜色等标准标签
    public static MiniMessage customMiniMessage() {
        return getInstance().miniMessageCustom;
    }


    /**
     * 获取 Gson 组件序列化器, 用于 Component 与 JSON 之间的互相转换.
     *
     * @return GsonComponentSerializer 实例
     */
    public static GsonComponentSerializer getGson() {
        return getInstance().gsonComponentSerializer;
    }

    public static LegacyComponentSerializer getLegacy() {
        return getInstance().legacyComponentSerializer;
    }

    /**
     * 将组件转换为 § 格式的旧版文本, RGB 颜色使用 Bukkit 可识别的 §x§r§r§g§g§b§b 格式.
     * 用于只接受字符串的 Bukkit 接口, 例如 Spigot 的踢出和登录拒绝.
     *
     * @param component 需要转换的组件
     * @return 旧版格式文本, 点击和悬浮等交互内容会丢失
     */
    public static String componentToLegacy(Component component) {
        return getInstance().legacyHexSerializer.serialize(component);
    }

    /**
     * 将 JSON 文本组件转换为 MiniMessage 字符串.
     * 该方法会先使用 Gson 序列化器将 JSON 反序列化为 `Component`, 再使用严格模式的 MiniMessage 序列化器输出字符串.
     *
     * @param json JSON 格式的文本组件字符串
     * @return 转换后的 MiniMessage 字符串
     * @throws RuntimeException 当输入 JSON 结构非法或无法反序列化为 Adventure 组件时, 底层序列化器可能抛出运行时异常
     */
    public static String jsonToMiniMessage(String json) {
        return getInstance().miniMessageStrict.serialize(getInstance().gsonComponentSerializer.deserialize(json));
    }

    public static String componentToMiniMessage(Component component) {
        return getInstance().miniMessageStrict.serialize(component);
    }

    /**
     * 将 JSON 文本组件字符串转换为 Adventure 组件对象.
     *
     * @param json JSON 格式的文本组件字符串
     * @return 反序列化得到的 Adventure 组件
     * @throws RuntimeException 当输入 JSON 结构非法或无法解析时, 底层序列化器可能抛出运行时异常
     */
    public static Component jsonToComponent(String json) {
        return getInstance().gsonComponentSerializer.deserialize(json);
    }

    public static Component jsonElementToComponent(JsonElement json) {
        return getInstance().gsonComponentSerializer.deserializeFromTree(json);
    }


    /**
     * 将 Adventure 组件转换为 Json 对象.
     *
     * @param component 需要序列化的 Adventure 组件
     * @return 序列化得到的 Json
     * @throws RuntimeException 当组件包含当前 NBT 序列化器无法处理的内容时, 底层实现可能抛出运行时异常
     */
    public static String componentToJson(Component component) {
        return getGson().serialize(component);
    }

    public static JsonElement componentToJsonElement(Component component) {
        return getGson().serializeToTree(component);
    }


    /**
     * 递归替换组件树中所有 `SHOW_ITEM` 悬浮事件的物品描述数据.
     * 算法步骤为先处理当前节点的悬浮事件, 再递归处理全部子节点, 最后重建当前节点的 children 列表.
     * 使用注意事项: 仅当悬浮事件类型为 `SHOW_ITEM` 时才会调用 `replacer`.
     *
     * @param component 需要遍历和替换的根组件
     * @param replacer 用于生成新 `ShowItem` 对象的替换函数
     * @return 替换完成后的新组件树
     * @throws ClassCastException 当悬浮事件值类型与 `HoverEvent.ShowItem` 不匹配时, 强制转换可能抛出此异常
     */
    public static Component replaceShowItem(Component component, Function<HoverEvent.ShowItem, HoverEvent.ShowItem> replacer) {
        HoverEvent<?> hoverEvent = component.hoverEvent();
        if (hoverEvent != null && hoverEvent.action() == HoverEvent.Action.SHOW_ITEM) {
            Object showItem = hoverEvent.value();
            component = component.hoverEvent(HoverEvent.showItem(replacer.apply((HoverEvent.ShowItem) showItem)));
        }
        List<Component> newChildren = new ArrayList<>();
        for (Component child : component.children()) {
            newChildren.add(replaceShowItem(child, replacer));
        }
        return component.children(newChildren);
    }

    /**
     * 按换行语义将一个复合组件拆分为多行组件列表.
     * 该方法会先将文本中的字面量换行符替换为 `Component.newline()`, 再借助自定义切片迭代器逐片遍历组件树, 按换行组件切分并在每行结束时执行 `compact()`.
     * 使用注意事项: 返回列表中的每个元素都代表一行独立组件, 且尽可能保留原有样式信息.
     *
     * @param component 需要拆分的根组件
     * @return 按行拆分后的组件列表
     */
    public static List<Component> splitLines(Component component) {
        List<Component> result = new ArrayList<>(1);
        Component line = Component.empty();
        // 将一个复合组件平铺为所有独立的组件, 并且应用父组件的样式.
        ArrayDeque<Component> allComponents = new ArrayDeque<>();
        final List<Component> children = component.replaceText(REPLACE_LF).children();
        for (int i = children.size() - 1; i >= 0; i--) {
            allComponents.addFirst(children.get(i).applyFallbackStyle(component.style()));
        }
        // 遍历所有的平铺组件.
        for (Component it : allComponents) {
            Component child = it.children(Collections.emptyList()); // 移除平铺后, 旧的父组件对子组件仍然存在的的关联.
            if (child instanceof TextComponent text && text.content().equals(Component.newline().content())) {
                result.add(line.compact());
                line = Component.empty();
            } else {
                line = line.append(child);
            }
        }
        if (Component.IS_NOT_EMPTY.test(line)) {
            result.add(line.compact());
        }
        return result;
    }

    /**
     * 判断一个字符是否为旧版颜色代码前缀.
     * 当前支持 `§` 与 `&` 两种前缀.
     *
     * @param c 需要判断的字符
     * @return 若字符是旧版颜色代码前缀则返回 true, 否则返回 false
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isLegacyColorCode(char c) {
        return c == '§' || c == '&';
    }

    /**
     * 判断一个字符是否为十六进制颜色值字符.
     * 支持 `0-9`, `a-f`, `A-F`.
     *
     * @param c 需要判断的字符
     * @return 若字符可作为十六进制颜色位则返回 true, 否则返回 false
     */
    public static boolean isHexColorCode(char c) {
        return (c >= '0' && c <= '9') ||
                (c >= 'a' && c <= 'f') ||
                (c >= 'A' && c <= 'F');
    }

    /**
     * 将旧版颜色代码字符串转换为 MiniMessage 字符串.
     * 该方法按字符扫描输入内容, 识别普通颜色代码, 格式代码以及 `&#RRGGBB` 和 `&x&R&R&G&G&B&B` 两种十六进制颜色格式, 并替换为对应的 MiniMessage 标签.
     * 若遇到不完整或非法的颜色代码片段, 方法会尽量保留原始字符.
     *
     * @param legacy 旧版颜色代码字符串
     * @return 转换后的 MiniMessage 字符串
     */
    public static String legacyToMiniMessage(String legacy) {
        StringBuilder stringBuilder = new StringBuilder();
        char[] chars = legacy.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (!isLegacyColorCode(chars[i])) {
                stringBuilder.append(chars[i]);
                continue;
            }
            if (i + 1 >= chars.length) {
                stringBuilder.append(chars[i]);
                continue;
            }
            switch (chars[i+1]) {
                case '0' -> stringBuilder.append("<black>");
                case '1' -> stringBuilder.append("<dark_blue>");
                case '2' -> stringBuilder.append("<dark_green>");
                case '3' -> stringBuilder.append("<dark_aqua>");
                case '4' -> stringBuilder.append("<dark_red>");
                case '5' -> stringBuilder.append("<dark_purple>");
                case '6' -> stringBuilder.append("<gold>");
                case '7' -> stringBuilder.append("<gray>");
                case '8' -> stringBuilder.append("<dark_gray>");
                case '9' -> stringBuilder.append("<blue>");
                case 'a' -> stringBuilder.append("<green>");
                case 'b' -> stringBuilder.append("<aqua>");
                case 'c' -> stringBuilder.append("<red>");
                case 'd' -> stringBuilder.append("<light_purple>");
                case 'e' -> stringBuilder.append("<yellow>");
                case 'f' -> stringBuilder.append("<white>");
                case 'r' -> stringBuilder.append("<reset><!i>");
                case 'l' -> stringBuilder.append("<b>");
                case 'm' -> stringBuilder.append("<st>");
                case 'o' -> stringBuilder.append("<i>");
                case 'n' -> stringBuilder.append("<u>");
                case 'k' -> stringBuilder.append("<obf>");
                case '#' -> {
                    if (i + 7 >= chars.length
                            || !isHexColorCode(chars[i+2])
                            || !isHexColorCode(chars[i+3])
                            || !isHexColorCode(chars[i+4])
                            || !isHexColorCode(chars[i+5])
                            || !isHexColorCode(chars[i+6])
                            || !isHexColorCode(chars[i+7])) {
                        stringBuilder.append(chars[i]);
                        continue;
                    }
                    stringBuilder
                            .append("<#")
                            .append(chars[i+2])
                            .append(chars[i+3])
                            .append(chars[i+4])
                            .append(chars[i+5])
                            .append(chars[i+6])
                            .append(chars[i+7])
                            .append(">");
                    i += 6;
                }
                case 'x' -> {
                    if (i + 13 >= chars.length
                            || !isLegacyColorCode(chars[i+2])
                            || !isLegacyColorCode(chars[i+4])
                            || !isLegacyColorCode(chars[i+6])
                            || !isLegacyColorCode(chars[i+8])
                            || !isLegacyColorCode(chars[i+10])
                            || !isLegacyColorCode(chars[i+12])) {
                        stringBuilder.append(chars[i]);
                        continue;
                    }
                    stringBuilder
                            .append("<#")
                            .append(chars[i+3])
                            .append(chars[i+5])
                            .append(chars[i+7])
                            .append(chars[i+9])
                            .append(chars[i+11])
                            .append(chars[i+13])
                            .append(">");
                    i += 12;
                }
                default -> {
                    stringBuilder.append(chars[i]);
                    continue;
                }
            }
            i++;
        }
        return stringBuilder.toString();
    }

    /**
     * 提取组件树中的纯文本内容.
     * 算法步骤为先读取当前节点的文本内容, 再递归拼接全部子节点的纯文本结果.
     *
     * @param component 需要提取文本的根组件
     * @return 组件树拼接后的纯文本字符串
     */
    public static String plainTextContent(Component component) {
        StringBuilder sb = new StringBuilder();
        if (component instanceof TextComponent textComponent) {
            sb.append(textComponent.content());
        }
        for (Component child : component.children()) {
            sb.append(plainTextContent(child));
        }
        return sb.toString();
    }

    /**
     * 判断组件树是否完全由 `TextComponent` 组成.
     * 该方法会递归检查当前节点及其所有子节点, 只要存在任意非文本组件就返回 false.
     *
     * @param component 需要检查的组件
     * @return 若整个组件树均为纯文本组件则返回 true, 否则返回 false
     */
    public static boolean isPureTextComponent(Component component) {
        if (!(component instanceof TextComponent textComponent)) {
            return false;
        }
        for (Component child : textComponent.children()) {
            if (!isPureTextComponent(child)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析字符串中的 MiniMessage 标签, 并返回解析后组件的纯文本结果.
     * 该方法适用于只关心标签展开结果文本, 不关心最终样式信息的场景.
     *
     * @param raw 原始 MiniMessage 字符串
     * @param resolvers 参与解析的标签解析器列表
     * @return 解析并去除样式后的纯文本字符串
     * @throws RuntimeException 当标签语法非法或解析器处理失败时, 底层 MiniMessage 可能抛出运行时异常
     */
    public static String resolvePlainStringTags(String raw, TagResolver... resolvers) {
        Component resultComponent = AdventureHelper.customMiniMessage().deserialize(raw, resolvers);
        return AdventureHelper.plainTextContent(resultComponent);
    }

    /**
     * 按给定映射批量替换组件中的文本片段.
     * 该方法会先对所有待替换键进行正则转义并拼接为联合匹配模式, 然后通过 Adventure 的文本替换 API 将命中的文本片段替换为新的纯文本组件.
     * 使用注意事项: 替换值会以 `Component.text()` 形式写入, 不会自动解析 MiniMessage 或其他样式语法.
     *
     * @param text 需要执行替换的原始组件
     * @param replacements 替换映射, 键为原文本, 值为替换后文本
     * @return 替换完成后的组件
     * @throws IllegalStateException 当正则命中结果在映射表中找不到对应值时抛出
     */
    public static Component replaceText(Component text, Map<String, String> replacements) {
        if (replacements.isEmpty()) return text;
        String patternString = replacements.keySet().stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return text.replaceText(builder ->
                builder.match(Pattern.compile(patternString))
                        .replacement((result, b) -> {
                            String target = Optional.ofNullable(replacements.get(result.group())).orElseThrow(() -> new IllegalStateException("Could not find tag '" + result.group() + "'"));
                            return Component.text(target);
                        })
        );
    }
}
