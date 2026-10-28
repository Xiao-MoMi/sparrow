package net.momirealms.sparrow.feature.deathmessages;

import net.momirealms.sparrow.util.DurationUtils;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class DeathMessagesSettings {
    @Comment("Default audience: false for this server, true for the Sparrow network.")
    @Comment(lang = "zh", value = "默认广播范围: false 为本服, true 为 Sparrow 网络.")
    private boolean broadcast = false;
    @YamlProperty("disabled_worlds")
    @Comment("Death world names, matched exactly on the source server. Omitted rule lists inherit; explicit lists replace, including [].")
    @Comment(lang = "zh", value = "按来源服的死亡世界名称精确匹配. 规则省略 disabled_worlds 时继承, 显式列表完全替换, [] 清空限制. 禁用后隐藏此次公告.")
    private Set<String> disabledWorlds = Set.of();
    @BlankLineBefore
    @Comment("Per-player sliding window on the source server. Excess deaths still count. Hidden deaths do not count. Cleared on quit or disable.")
    @Comment(lang = "zh", value = "来源服按玩家统计滑动时间窗口, 超额死亡仍计数, 隐藏的死亡不计数. 退出或模块关闭时清理, 不跨服同步计数.")
    private AntiSpamSettings antiSpam = new AntiSpamSettings();
    @BlankLineBefore
    @Comment("Outer chat format. <arg:message> is the selected complete sentence. PAPI here and in rule messages uses each receiver.")
    @Comment(lang = "zh", value = "聊天外层格式, <arg:message> 为选中的完整句子. 此处和正文的 <papi:...> 按每位接收者解析. 始终尊重事件取消和已有隐藏状态.")
    private String format = "<dark_gray>☠ <gray><arg:message>";
    @BlankLineBefore
    @Comment("Participant display. Player PAPI is resolved for the displayed player on the source server; components travel with the message.")
    @Comment(lang = "zh", value = "参与者展示. 玩家展示中的 PAPI 在来源服按被展示的玩家解析, 解析后的组件随消息传递. 生物展示没有玩家 PAPI 上下文.")
    private DisplaySettings display = new DisplaySettings();
    @BlankLineBefore
    @Comment("Unmatched keys use a random fallback with public broadcast and disabled_worlds. [] hides unmatched deaths.")
    @Comment(lang = "zh", value = "未匹配的死因随机使用一条 fallback, 采用公共 broadcast 和 disabled_worlds. [] 隐藏未匹配死亡. <arg:original> 保留完整原版组件.")
    private List<String> fallback = List.of("<arg:original>");
    @BlankLineBefore
    @Comment({
            "Exact vanilla message_key lookup, case-sensitive; the first duplicate key wins. IDs must be unique. One random sentence is selected per death.",
            "Omit broadcast/disabled_worlds to inherit public values. An explicit false or [] overrides them. Disabled worlds hide without trying another rule.",
            "messages: [] or a selected empty string hides the announcement, including its outer format. Unknown keys use fallback.",
            "Arguments: victim, attacker, weapon, death_reason, original, server, world, x, y, z, message_key. Attacker/weapon/reason exist only in applicable vanilla branches.",
            "Vanilla combat attribution is preserved. Every default .item sentence includes weapon with its original hover; no attack-time weapon snapshot is captured."
    })
    @Comment(lang = "zh", value = {
            "按原版 message_key 精确查找, 区分大小写, 重复键采用第一条, id 必须唯一. 每次死亡仅随机选择一次完整句子.",
            "省略 broadcast/disabled_worlds 继承公共值, 显式 false 或 [] 覆盖公共值. 禁用世界直接隐藏, 不继续匹配或进入 fallback.",
            "messages: [] 或选中的空字符串隐藏此次公告及外层格式. 未知消息键使用 fallback.",
            "参数: victim, attacker, weapon, death_reason, original, server, world, x, y, z, message_key. attacker/weapon/death_reason 仅在对应原版分支存在.",
            "沿用原版战斗归因, 不单独维护战斗历史. 默认 .item 文案均包含 weapon 及原有物品悬浮, 不记录攻击瞬间的武器快照."
    })
    private List<RuleSettings> rules = DeathMessagesDefaults.rules();

    @NotNull
    DeathMessagesConfig toConfig() {
        Set<String> worlds = Set.copyOf(this.disabledWorlds);
        Set<String> ids = new HashSet<>();
        Map<String, DeathMessagesConfig.Rule> indexed = new HashMap<>();
        for (int i = 0, size = this.rules.size(); i < size; i++) {
            RuleSettings rule = this.rules.get(i);
            String path = "rules[" + i + "]";
            if (rule.id.isBlank()) throw new IllegalArgumentException(path + ".id must not be blank");
            if (!ids.add(rule.id)) throw new IllegalArgumentException(path + ".id is duplicated: " + rule.id);
            if (rule.messageKey.isBlank()) throw new IllegalArgumentException(path + ".message_key must not be blank");
            if (rule.messages == null) throw new IllegalArgumentException(path + ".messages must be a list; use [] to hide this death");
            DeathMessagesConfig.Rule resolved = new DeathMessagesConfig.Rule(
                    rule.id,
                    rule.broadcast == null ? this.broadcast : rule.broadcast,
                    rule.disabledWorlds == null ? worlds : Set.copyOf(rule.disabledWorlds),
                    List.copyOf(rule.messages)
            );
            indexed.putIfAbsent(rule.messageKey, resolved);
        }
        return new DeathMessagesConfig(
                this.broadcast,
                worlds,
                this.antiSpam.toConfig(),
                this.format,
                new DeathMessagesConfig.Display(this.display.player.name, List.copyOf(this.display.player.hover), this.display.mob.name),
                List.copyOf(this.fallback),
                Map.copyOf(indexed)
        );
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class AntiSpamSettings {
        private boolean enabled = true;
        @Comment("Positive duration with units, for example 30s or 1m.")
        @Comment(lang = "zh", value = "带单位的正时长, 例如 30s 或 1m.")
        private String interval = "30s";
        @Comment("Maximum normal announcements in the window; must be positive.")
        @Comment(lang = "zh", value = "窗口内正常公告的最大次数, 必须为正数.")
        private int maxMessages = 3;
        @Comment("Send excess announcements only to the victim. Does not bypass world or message hiding.")
        @Comment(lang = "zh", value = "超额消息仅发给死者, 不绕过世界限制或文案隐藏规则.")
        private boolean showToVictim = true;

        private DeathMessagesConfig.AntiSpam toConfig() {
            long millis;
            try {
                millis = DurationUtils.parsePositive(this.interval).toMillis();
            } catch (IllegalArgumentException | ArithmeticException exception) {
                throw new IllegalArgumentException("anti-spam.interval must be a positive duration: " + this.interval, exception);
            }
            if (this.maxMessages <= 0) throw new IllegalArgumentException("anti-spam.max-messages must be positive");
            return new DeathMessagesConfig.AntiSpam(this.enabled, millis, this.maxMessages, this.showToVictim);
        }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class DisplaySettings {
        private PlayerDisplaySettings player = new PlayerDisplaySettings();
        private MobDisplaySettings mob = new MobDisplaySettings();
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class PlayerDisplaySettings {
        @Comment("name/display_name refer to this participant. server refers to the death's source server.")
        @Comment(lang = "zh", value = "name/display_name 为此人的账号名/显示名, server 为死亡来源服 ID.")
        private String name = "<white><arg:display_name>";
        @Comment("An empty list adds no custom hover text.")
        @Comment(lang = "zh", value = "空列表表示不附加自定义悬浮文本.")
        private List<String> hover = List.of("<gray>玩家：<white><arg:name>", "<gray>服务器：<white><arg:server>");
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class MobDisplaySettings {
        @Comment("entity_name preserves the vanilla custom or translatable entity name.")
        @Comment(lang = "zh", value = "entity_name 保留原版实体名字组件, 包括自定义名字和可翻译名称.")
        private String name = "<yellow><arg:entity_name>";
    }

    @Configuration(naming = Configuration.Naming.SNAKE_CASE)
    public static final class RuleSettings {
        private String id = "";
        private String messageKey = "";
        private @Nullable Boolean broadcast;
        private @Nullable Set<String> disabledWorlds;
        private @Nullable List<String> messages;

        static RuleSettings of(String id, String messageKey, String... messages) {
            RuleSettings rule = new RuleSettings();
            rule.id = id;
            rule.messageKey = messageKey;
            rule.messages = List.of(messages);
            return rule;
        }
    }
}