package net.momirealms.sparrow.teleport;

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
            "         A sound is a key such as block.note_block.banjo, or a section with key, volume, pitch and source. Leave empty to play nothing."
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
            "         音效可以只写名称, 例如 block.note_block.banjo, 也可以写成包含 key、volume、pitch、source 的小节. 留空表示不播放."
    })
    private List<TeleportProcessor.Pre> preProcessor = List.of();

    @BlankLineBefore
    @Comment({
            "Processors that run after the player has arrived, from top to bottom.",
            "   sound - plays a sound to the player.",
            "     sound: a key such as entity.enderman.teleport, or a section with key, volume, pitch and source."
    })
    @Comment(lang = "zh", value = {
            "到达后的处理器, 玩家到达落点后从上到下依次执行.",
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
    public List<TeleportProcessor.Pre> preProcessor() {
        return this.preProcessor;
    }

    @NotNull
    public List<TeleportProcessor.Post> postProcessor() {
        return this.postProcessor;
    }
}