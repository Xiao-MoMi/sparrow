package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.teleport.processor.CooldownProcessor;
import net.momirealms.sparrow.teleport.processor.GeneratedChunkProcessor;
import net.momirealms.sparrow.teleport.processor.InvulnerabilityProcessor;
import net.momirealms.sparrow.teleport.processor.SafeLocationProcessor;
import net.momirealms.sparrow.teleport.processor.SoundProcessor;
import net.momirealms.sparrow.teleport.processor.WarmupProcessor;
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
            "Processors that run on the destination server before the landing spot is reserved. The player may still be on another server.",
            "They run from top to bottom. Each one may reject the teleport or move the landing spot.",
            "   generated-chunk - rejects destinations whose chunk has not been generated.",
            "   safe-location - checks the requested position, then searches nearby, then vertically at the original X/Z.",
            "     Place it after processors that change the destination. It also applies when teleporting another player.",
            "     Uses the player's current bounding box. Standing heights above 5 blocks use a 0.6 x 2 x 0.6 detection box.",
            "     nearby.horizontal-radius, nearby.vertical-radius: nearby search distances in blocks. Both must be at least 0.",
            "     max-checks: positive limit shared by all search stages, including the requested position.",
            "     allow-nether-roof: whether the search may go above the dimension's logical height in worlds with a ceiling.",
            "     force-on-failure: use the requested position if no safe location is found. false cancels the teleport.",
            "     fallback-min-y: minimum Y for forced teleports, also kept at least one block above the world's minimum height.",
            "   world-blacklist - rejects teleports into the listed worlds.",
            "     worlds: names of the worlds that players cannot teleport into.",
            "     bypass-permission: players with this permission may enter anyway. Leave empty to let nobody through.",
            "         Checking it for a player who is still on another server requires LuckPerms."
    })
    @Comment(lang = "zh", value = {
            "落点处理器, 在落点所在的服务器上、预留落点之前执行. 此时玩家可能还在别的服务器上.",
            "从上到下依次执行, 每一个都可以拒绝传送或改写落点.",
            "   generated-chunk - 目标坐标所在区块未生成时拒绝传送.",
            "   safe-location - 先检查原位置, 再搜索附近, 最后在原 X/Z 上垂直搜索.",
            "     放在会改写落点的处理器之后. 代其他玩家发起传送时同样执行.",
            "     使用玩家当前检测体积. 缩放后的站立高度超过 5 格时, 按 0.6 x 2 x 0.6 的体积检查.",
            "     nearby.horizontal-radius, nearby.vertical-radius: 附近搜索的水平和垂直距离, 单位为格, 均须大于等于 0.",
            "     max-checks: 所有搜索阶段共用的检查次数上限, 包含原位置检查, 必须大于 0.",
            "     allow-nether-roof: 有顶层的维度是否允许搜索超过维度逻辑高度的位置.",
            "     force-on-failure: 找不到安全位置时是否强制传送到原位置. false 表示取消传送.",
            "     fallback-min-y: 强制传送的最低 Y, 同时至少高于世界最低高度一格.",
            "   world-blacklist - 拒绝传送进入列出的世界.",
            "     worlds: 禁止传送进入的世界名称.",
            "     bypass-permission: 拥有此权限的玩家仍然可以进入. 留空表示任何人都不能进入.",
            "         玩家还在别的服务器上时, 检查这个权限需要安装 LuckPerms."
    })
    private List<TeleportProcessor.Target> targetProcessor = List.of();

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
        group.targetProcessor = List.of(new GeneratedChunkProcessor(), new SafeLocationProcessor());
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
    public List<TeleportProcessor.Pre> preProcessor() {
        return this.preProcessor;
    }

    @NotNull
    public List<TeleportProcessor.Target> targetProcessor() {
        return this.targetProcessor;
    }

    @NotNull
    public List<TeleportProcessor.Post> postProcessor() {
        return this.postProcessor;
    }
}