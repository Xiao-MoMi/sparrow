package net.momirealms.sparrow.locale.tag;

import net.momirealms.sparrow.compatibility.CompatibilityManager;
import net.momirealms.sparrow.message.Context;
import net.momirealms.sparrow.message.ParsingException;
import net.momirealms.sparrow.message.tag.Tag;
import net.momirealms.sparrow.message.tag.resolver.ArgumentQueue;
import net.momirealms.sparrow.message.tag.resolver.StaticTagResolver;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code <papi:placeholder[:default]>}, 按 {@link MessageContext} 中的玩家解析 PlaceholderAPI 占位符.
 */
public final class PlaceholderTag extends StaticTagResolver {
    public static final PlaceholderTag INSTANCE = new PlaceholderTag();

    private PlaceholderTag() {
        super("papi");
    }

    @Override
    @Nullable
    public Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx) throws ParsingException {
        CompatibilityManager compatibility = SparrowPlugin.instance().compatibilityManager();
        if (!compatibility.hasPlaceholderAPI()) return null;
        // 占位符名本身也可以带标签, 例如 <papi:'server_online_<arg:world>'>
        String raw = arguments.popOr("No placeholder provided").value();
        if (raw.indexOf('<') >= 0) {
            raw = AdventureHelper.plainTextContent(ctx.deserialize(raw));
        }
        String placeholder = "%" + raw + "%";
        Player player = ctx.target() instanceof MessageContext context ? context.player() : null;
        String parsed = compatibility.parsePlaceholders(player, placeholder);
        // 原样返回说明占位符不存在, 改用默认值
        if (parsed.equals(placeholder)) {
            parsed = arguments.popOr("No default placeholder value provided").value();
        }
        return Tag.selfClosingInserting(ctx.deserialize(parsed));
    }
}
