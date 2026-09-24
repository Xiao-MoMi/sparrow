package net.momirealms.sparrow.plugin.command;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.CaptionProvider;
import org.incendo.cloud.caption.DelegatingCaptionProvider;

public final class CloudCaptionProvider<C> extends DelegatingCaptionProvider<C> {
    private static final CaptionProvider<?> PROVIDER = CaptionProvider.constantProvider()
            .build();

    @SuppressWarnings("unchecked")
    @Override
    public @NonNull CaptionProvider<C> delegate() {
        return (CaptionProvider<C>) PROVIDER;
    }
}
