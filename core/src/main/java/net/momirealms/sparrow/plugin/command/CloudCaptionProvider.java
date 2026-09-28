package net.momirealms.sparrow.plugin.command;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.CaptionProvider;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.DelegatingCaptionProvider;

public final class CloudCaptionProvider<C> extends DelegatingCaptionProvider<C> {
    private static final CaptionProvider<?> PROVIDER = CaptionProvider.constantProvider()
            .putCaption(Caption.of("argument.parse.failure.time"), "Invalid duration '{input}'. Use ticks or d/h/m/s/t units, up to 2147483647 ticks")
            .putCaption(Caption.of("argument.parse.failure.modeldata"), "Invalid model data '{input}'. Use an integer (123) or an explicit decimal (123.0)")
            .putCaption(Caption.of("argument.parse.failure.duration"), "Invalid duration '{input}'. Units: y, mo, w, d, h, m, s; decimals and combinations work, e.g. 1.5h, 1mo2w")
            .build();

    @SuppressWarnings("unchecked")
    @Override
    public @NonNull CaptionProvider<C> delegate() {
        return (CaptionProvider<C>) PROVIDER;
    }
}
