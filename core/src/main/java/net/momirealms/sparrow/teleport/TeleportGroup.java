package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.teleport.processor.CooldownProcessor;
import net.momirealms.sparrow.teleport.processor.GeneratedChunkRule;
import net.momirealms.sparrow.teleport.processor.InvulnerabilityProcessor;
import net.momirealms.sparrow.teleport.processor.SafeLocationRule;
import net.momirealms.sparrow.teleport.processor.SoundProcessor;
import net.momirealms.sparrow.teleport.processor.TeleportProcessor;
import net.momirealms.sparrow.teleport.processor.warmup.WarmupProcessor;
import net.momirealms.sparrow.teleport.search.TeleportSearchOptions;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class TeleportGroup {
    @Comment({
            "Processors that run before departure, on the server the player is leaving.",
            "They run from top to bottom, and the teleport stops at the first one that rejects it.",
            "Every entry needs processor-type. An option left out of an entry takes its built-in default.",
            "   cooldown - rejects the teleport while the cooldown is running, and starts the cooldown after a successful teleport.",
            "     id: required. Teleports with the same cooldown ID share one cooldown.",
            "     seconds: cooldown seconds, shared across servers. 0 disables it.",
            "     bypass-permission: players with this permission are not held by the cooldown. Leave empty to let nobody skip it.",
            "   warmup - makes the player wait in place before leaving.",
            "     seconds: seconds to wait. sparrow.teleport-warmup.<seconds> overrides it.",
            "     bypass-permission: players with this permission leave at once. Leave empty to let nobody skip the warmup.",
            "     cancel-on-move, cancel-on-damage: cancel the warmup when the player moves or takes damage.",
            "     display: where the countdown is shown. ACTION_BAR, TITLE, BOSS_BAR, CHAT or NONE.",
            "     boss-bar-color: PINK, BLUE, RED, GREEN, YELLOW, PURPLE or WHITE.",
            "     boss-bar-overlay: PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12 or NOTCHED_20.",
            "     warmup-sound, cancel-sound: played every second of the countdown and when the warmup is cancelled.",
            "         A sound is a key such as block.note_block.banjo, or a section with key, volume, pitch and source. Leave empty to play nothing.",
            "   sound - plays a sound to the player when its turn comes. Placed after warmup, that is the moment of leaving.",
            "     sound: the sound to play, written the same way as above."
    })
    @Comment(lang = "zh", value = {
            "出发前的处理器, 在玩家当前所在的服务器上执行.",
            "从上到下依次执行, 其中一个拒绝后传送就此中止.",
            "每一项都要写 processor-type. 某一项没有写出的选项使用内置默认值.",
            "   cooldown - 冷却期间拒绝传送, 传送成功后开始冷却.",
            "     id: 必填. 冷却 ID 相同的传送共用一份冷却.",
            "     seconds: 冷却秒数, 跨服共享. 0 表示不限制.",
            "     bypass-permission: 拥有此权限的玩家不受冷却限制. 留空表示任何人都不能跳过.",
            "   warmup - 出发前让玩家原地等待.",
            "     seconds: 等待的秒数. sparrow.teleport-warmup.<秒> 可覆盖.",
            "     bypass-permission: 拥有此权限的玩家立即出发. 留空表示任何人都不能跳过预热.",
            "     cancel-on-move, cancel-on-damage: 等待期间移动或受伤时是否取消传送.",
            "     display: 倒计时显示位置. ACTION_BAR (动作栏)、TITLE (屏幕中央)、BOSS_BAR (进度条)、CHAT (聊天栏) 或 NONE (不显示).",
            "     boss-bar-color: PINK、BLUE、RED、GREEN、YELLOW、PURPLE 或 WHITE.",
            "     boss-bar-overlay: PROGRESS、NOTCHED_6、NOTCHED_10、NOTCHED_12 或 NOTCHED_20.",
            "     warmup-sound, cancel-sound: 分别在倒计时每秒和预热被取消时播放.",
            "         音效可以只写名称, 例如 block.note_block.banjo, 也可以写成包含 key、volume、pitch、source 的小节. 留空表示不播放.",
            "   sound - 轮到它时给玩家播放一个音效. 放在 warmup 之后就是出发的那一刻.",
            "     sound: 要播放的音效, 写法同上."
    })
    private List<TeleportProcessor.Pre> preProcessor = List.of();

    @BlankLineBefore
    @Comment({
            "Rules checked once on the destination server before searching. A rejection ends the request.",
            "generated-chunk checks whether the original destination chunk has been generated.",
            "world-blacklist uses worlds and bypass-permission to control entry into destination worlds."
    })
    @Comment(lang = "zh", value = {
            "请求规则, 在目标服搜索前执行一次, 拒绝后结束整个请求.",
            "generated-chunk 检查原始目标区块是否已生成.",
            "world-blacklist 通过 worlds 和 bypass-permission 控制是否允许进入目标世界."
    })
    private List<TeleportProcessor.RequestRule> requestRule = List.of();

    @BlankLineBefore
    @Comment({
            "Selection strategy. direct uses the requested position; nearby checks it, then searches nearby and vertically.",
            "nearby options: nearby.horizontal-radius, nearby.vertical-radius, max-checks, allow-nether-roof.",
            "force-on-failure defaults to false. A forced fallback skips only safe-location and still checks other candidate rules.",
            "fallback-min-y defaults to -63 and is also kept above the world's minimum height. Timeout never triggers fallback."
    })
    @Comment(lang = "zh", value = {
            "选点策略. direct 使用请求位置, nearby 依次检查原点, 附近区域和原 X/Z 的垂直位置.",
            "nearby 选项为 nearby.horizontal-radius, nearby.vertical-radius, max-checks, allow-nether-roof.",
            "force-on-failure 默认为 false. 强制回退仅跳过 safe-location, 其他候选规则仍然执行.",
            "fallback-min-y 默认为 -63, 同时高于世界最低高度. 超时不会触发回退."
    })
    private TeleportSearchOptions search = new TeleportSearchOptions.Direct();

    @BlankLineBefore
    @Comment({
            "Rules checked for each candidate, in order. Only a candidate passing every rule can be selected.",
            "safe-location checks support, collisions, hazards and the player's eye position using the current detection size.",
            "Other plugins may register candidate rules, such as a claim restriction. Rule errors end the search.",
            "An empty list trusts the selected position. Rules never move it."
    })
    @Comment(lang = "zh", value = {
            "候选规则, 按顺序检查每个位置, 全部放行后才能选中.",
            "safe-location 按玩家当前检测体积检查支撑, 碰撞, 危险方块和眼部位置.",
            "其他插件可以注册候选规则, 例如领地限制. 规则异常会结束搜索.",
            "列表为空时信任选定位置. 规则不会修改坐标."
    })
    private List<TeleportProcessor.CandidateRule> candidateRule = List.of();

    @BlankLineBefore
    @Comment({
            "Processors that run on the destination server after the player has arrived, from top to bottom.",
            "   invulnerability - grants temporary vanilla invulnerability.",
            "     seconds: duration in seconds (20 ticks each). 0 disables it.",
            "   sound - plays a sound to the player.",
            "     sound: a key such as entity.enderman.teleport, or a section with key, volume, pitch and source."
    })
    @Comment(lang = "zh", value = {
            "到达后的处理器, 玩家到达落点后在落点所在的服务器上从上到下依次执行.",
            "   invulnerability - 给予临时的原版无敌效果.",
            "     seconds: 持续秒数, 每秒按 20 tick 计算. 0 表示关闭.",
            "   sound - 给玩家播放一个音效.",
            "     sound: 音效名称, 例如 entity.enderman.teleport, 也可以写成包含 key、volume、pitch、source 的小节."
    })
    private List<TeleportProcessor.Post> postProcessor = List.of();

    @NotNull
    public static TeleportGroup createDefault() {
        TeleportGroup group = new TeleportGroup();
        group.preProcessor = List.of(new CooldownProcessor("teleport"), new WarmupProcessor());
        group.postProcessor = List.of(new SoundProcessor());
        return group;
    }

    @NotNull
    public static TeleportGroup createDeathBack() {
        TeleportGroup group = new TeleportGroup();
        group.preProcessor = List.of(new CooldownProcessor("death-back"), new WarmupProcessor());
        group.requestRule = List.of(new GeneratedChunkRule());
        group.search = new TeleportSearchOptions.Nearby();
        group.candidateRule = List.of(new SafeLocationRule());
        group.postProcessor = List.of(new InvulnerabilityProcessor(), new SoundProcessor());
        return group;
    }

    @NotNull
    public static TeleportGroup createInstant() {
        TeleportGroup group = new TeleportGroup();
        group.postProcessor = List.of(new SoundProcessor());
        return group;
    }

    @NotNull
    public static TeleportGroup createRtp() {
        TeleportGroup group = new TeleportGroup();
        group.preProcessor = List.of(new CooldownProcessor("rtp"), new WarmupProcessor());
        group.search = new TeleportSearchOptions.Random();
        group.candidateRule = List.of(new SafeLocationRule());
        group.postProcessor = List.of(new SoundProcessor());
        return group;
    }

    public void validate() {
        this.search.validate();
        for (TeleportProcessor processor : this.preProcessor) processor.validate();
        for (TeleportProcessor processor : this.requestRule) processor.validate();
        for (TeleportProcessor processor : this.candidateRule) processor.validate();
        for (TeleportProcessor processor : this.postProcessor) processor.validate();
    }

    @NotNull
    public List<TeleportProcessor.Pre> preProcessor() {
        return this.preProcessor;
    }

    @NotNull
    public List<TeleportProcessor.RequestRule> requestRule() {
        return this.requestRule;
    }

    @NotNull
    public TeleportSearchOptions search() {
        return this.search;
    }

    @NotNull
    public List<TeleportProcessor.CandidateRule> candidateRule() {
        return this.candidateRule;
    }

    @NotNull
    public List<TeleportProcessor.Post> postProcessor() {
        return this.postProcessor;
    }
}