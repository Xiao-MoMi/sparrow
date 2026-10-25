package net.momirealms.sparrow.feature.rtp;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.permissions.Permissible;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

// 解析时取当前配置中的区域, 命令执行用的就是解析出的这一份
final class RtpRegionParser<C> implements ArgumentParser<C, RtpConfig.Region>, BlockingSuggestionProvider<C> {

    @NotNull
    static <C> ParserDescriptor<C, RtpConfig.Region> rtpRegionParser() {
        return ParserDescriptor.of(new RtpRegionParser<>(), RtpConfig.Region.class);
    }

    @NotNull
    @Override
    public ArgumentParseResult<RtpConfig.Region> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String name = input.readString();
        RtpConfig.Region region = config().regions().get(name);
        if (region == null) return ArgumentParseResult.failure(new ParseException(context, CaptionVariable.of("input", name)));
        return ArgumentParseResult.success(region);
    }

    // 只补全发起者有权限前往的区域, 控制台能看到全部
    @NotNull
    @Override
    public List<Suggestion> suggestions(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String prefix = input.peekString();
        List<Suggestion> suggestions = new ArrayList<>();
        for (RtpConfig.Region region : config().regions().values()) {
            if (!region.name().regionMatches(true, 0, prefix, 0, prefix.length())) continue;
            if (region.permission() != null && context.sender() instanceof Permissible sender && !sender.hasPermission(region.permission())) continue;
            suggestions.add(Suggestion.suggestion(region.name()));
        }
        return suggestions;
    }

    private static RtpConfig config() {
        return SparrowPlugin.instance().featureManager().feature(RtpFeature.ID, RtpFeature.class).config();
    }

    private static final class ParseException extends ParserException {

        private ParseException(@NotNull CommandContext<?> context, @NotNull CaptionVariable... variables) {
            super(RtpRegionParser.class, context, Caption.of("argument.parse.failure.rtp-region-unknown"), variables);
        }
    }
}