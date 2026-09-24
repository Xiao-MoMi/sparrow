package net.momirealms.sparrow.plugin.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.minecraft.extras.caption.ComponentCaptionFormatter;

import java.util.List;

public class CloudCaptionFormatter implements ComponentCaptionFormatter<org.bukkit.command.CommandSender> {
    /**
     * 保留 caption 的翻译键与参数, 交由反馈渲染器按接收者语言递归翻译.
     */
    @Override
    public @NonNull Component formatCaption(@NonNull Caption captionKey, @NonNull CommandSender recipient, @NonNull String caption, @NonNull List<@NonNull CaptionVariable> variables) {
        return ComponentCaptionFormatter.translatable().formatCaption(captionKey, recipient, caption, variables);
    }
}
