package net.momirealms.sparrow.plugin.command;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.CaptionProvider;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.DelegatingCaptionProvider;

public final class CloudCaptionProvider<C> extends DelegatingCaptionProvider<C> {
    private static final CaptionProvider<?> PROVIDER = CaptionProvider.constantProvider()
            .putCaption(Caption.of("argument.parse.failure.time"), "Invalid duration '{input}'. Use ticks or d/h/m/s/t units, up to 2147483647 ticks")
            .build();

    @SuppressWarnings("unchecked")
    @Override
    public @NonNull CaptionProvider<C> delegate() {
        return (CaptionProvider<C>) PROVIDER;
    }
}
