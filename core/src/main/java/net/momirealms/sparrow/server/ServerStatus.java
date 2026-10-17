package net.momirealms.sparrow.server;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.ListTag;
import net.momirealms.sparrow.nbt.NBT;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 集合由内部调用方按只读约定共享.
public record ServerStatus(
        @NotNull String serverId,
        long startedAt,
        int maxPlayers,
        @NotNull String platform,
        @NotNull List<String> worlds,
        @NotNull Set<String> tags
) {

    // 从完整状态 NBT 恢复快照.
    @NotNull
    public static ServerStatus fromNbt(@NotNull String serverId, @NotNull CompoundTag data) {
        return new ServerStatus(serverId, data.getLong("started_at"), data.getInt("max_players"), data.getString("platform"), readWorlds(data), readTags(data));
    }

    // 生成完整状态 NBT
    @NotNull
    public CompoundTag toNbt() {
        CompoundTag data = new CompoundTag();
        data.putLong("started_at", this.startedAt);
        data.putInt("max_players", this.maxPlayers);
        data.putString("platform", this.platform);
        data.put("worlds", this.worldsTag());
        data.put("tags", this.tagsTag());
        return data;
    }

    // 返回从上一份状态变为当前状态所需的字段增量, 集合字段携带完整的新值.
    @NotNull
    public CompoundTag diff(@NotNull ServerStatus previous) {
        CompoundTag changes = new CompoundTag();
        if (this.startedAt != previous.startedAt) changes.putLong("started_at", this.startedAt);
        if (this.maxPlayers != previous.maxPlayers) changes.putInt("max_players", this.maxPlayers);
        if (!this.platform.equals(previous.platform)) changes.putString("platform", this.platform);
        if (!this.worlds.equals(previous.worlds)) changes.put("worlds", this.worldsTag());
        if (!this.tags.equals(previous.tags)) changes.put("tags", this.tagsTag());
        return changes;
    }

    // 合并增量并生成新快照, 缺省字段沿用当前值, 集合字段整体替换.
    @NotNull
    public ServerStatus applyChanges(@NotNull CompoundTag changes) {
        return new ServerStatus(
                this.serverId,
                changes.get("started_at") == null ? this.startedAt : changes.getLong("started_at"),
                changes.get("max_players") == null ? this.maxPlayers : changes.getInt("max_players"),
                changes.get("platform") == null ? this.platform : changes.getString("platform"),
                changes.get("worlds") == null ? this.worlds : readWorlds(changes),
                changes.get("tags") == null ? this.tags : readTags(changes)
        );
    }

    @NotNull
    private ListTag worldsTag() {
        ListTag values = new ListTag();
        for (int i = 0, size = this.worlds.size(); i < size; i++) {
            values.add(NBT.createString(this.worlds.get(i)));
        }
        return values;
    }

    @NotNull
    private ListTag tagsTag() {
        ListTag values = new ListTag();
        // 固定标签的序列化顺序, 相同集合生成相同顺序的 NBT 列表.
        List<String> names = this.tags.stream().sorted().toList();
        for (int i = 0, size = names.size(); i < size; i++) {
            values.add(NBT.createString(names.get(i)));
        }
        return values;
    }

    @NotNull
    private static List<String> readWorlds(@NotNull CompoundTag data) {
        ListTag values = data.getList("worlds");
        List<String> worlds = new ArrayList<>(values.size());
        for (int i = 0, size = values.size(); i < size; i++) {
            worlds.add(values.getString(i));
        }
        return worlds;
    }

    @NotNull
    private static Set<String> readTags(@NotNull CompoundTag data) {
        ListTag values = data.getList("tags");
        Set<String> tags = new HashSet<>(values.size());
        for (int i = 0, size = values.size(); i < size; i++) {
            tags.add(values.getString(i));
        }
        return tags;
    }
}